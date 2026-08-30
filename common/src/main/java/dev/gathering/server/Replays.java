package dev.gathering.server;

import dev.gathering.platform.Platform;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionRecord;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.persistence.SessionCodec;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Games that are over, kept so they can be watched back.
 *
 * <p>This is the moment the mod's architecture was built for and had never claimed. A session
 * is an event log and a seed, so a finished game reproduces exactly - and until now every one
 * of them was thrown away the instant it ended. The argument about what was on top of the
 * library when somebody scried is settleable, and nothing was settling it.
 *
 * <p><b>Only a finished game.</b> A replay reveals hands, libraries and face-down cards -
 * everything the visibility rules spend the whole game hiding - and the only reason that is
 * safe is that there is nothing left to exploit. {@link #keep} refuses to write a session that
 * has not ended, so no file here is of a game still being played, and {@link #frameOf} is the
 * one place in the mod a {@link Viewer.Historian} is constructed.
 *
 * <p>Kept as files under the server's own data directory rather than on the table: a table
 * gets broken, and the game played on it is worth more than the block was. The newest
 * {@link #KEPT} are held and the rest are dropped, because a server that never forgets a game
 * is a server whose disk fills up with them.
 */
public final class Replays {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String FOLDER = "replays";
    private static final String SUFFIX = ".replay";

    /** How many finished games a server keeps. Weeks of play for a busy table. */
    public static final int KEPT = 64;

    /** The byte layout's own version, so a later change can refuse an older file plainly. */
    private static final int VERSION = 2;

    private Replays() {
    }

    /**
     * One finished game, as much as a list of them needs to say.
     *
     * @param players who sat at the table, name and account both. The name is what the list
     *                shows; the account is what decides who may open it on a server that
     *                keeps replays for the people who played them
     */
    public record Record(String id, long when, List<Played> players, int turns, int steps, int seats) {

        /** The names, in the order they sat, for anything that only wants to print them. */
        public List<String> names() {
            List<String> named = new ArrayList<>(players.size());
            for (Played played : players) {
                named.add(played.name());
            }
            return List.copyOf(named);
        }

        /** Whether this account is one of the people who played this game. */
        public boolean wasPlayedBy(java.util.UUID who) {
            for (Played played : players) {
                if (played.id().equals(who)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Somebody who sat at the table this game was played on. */
    public record Played(String name, java.util.UUID id) {
    }

    /**
     * Writes a finished game down.
     *
     * <p>Called as the session ends and before it is forgotten. The records are written in
     * their plain form rather than the sealed one: the sealed stream exists to keep hidden
     * events from a live table, and this file is only ever read to show somebody a game that
     * is over. It lives in the server's own directory, which is not the world folder - copying
     * a save does not carry the replays with it.
     *
     * @return whether a file was actually written, so nobody is told a game was kept when the
     *         server has replays switched off or the disk refused it
     */
    public static boolean keep(GameSession session, int startingLife, List<Played> players) {
        if (session == null || !session.state().ended() || !ReplayWatch.keeping()) {
            return false;
        }
        try {
            SessionCodec.Streams streams = SessionCodec.write(session.records());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                out.writeLong(System.currentTimeMillis());
                out.writeInt(session.state().seats().size());
                out.writeInt(session.state().turn().turnNumber());
                out.writeInt(startingLife);
                out.writeInt(session.records().size());
                out.writeUTF(session.undoMode().name());
                // The seed in plain. It is the one thing the live game guards hardest and the
                // one thing a replay cannot do without - a shuffle is only reproducible from
                // it. Safe here for the same reason the hands are: the game is over. The file
                // sits in the server's own directory rather than the world folder, so copying
                // a save does not carry it.
                byte[] seed = session.seed().toBytes();
                out.writeInt(seed.length);
                out.write(seed);
                out.writeInt(players.size());
                for (Played player : players) {
                    out.writeUTF(player.name());
                    out.writeLong(player.id().getMostSignificantBits());
                    out.writeLong(player.id().getLeastSignificantBits());
                }
                out.writeInt(streams.publicLog().length);
                out.write(streams.publicLog());
                out.writeInt(streams.secretLog().length);
                out.write(streams.secretLog());
            }
            Path folder = folder();
            Files.createDirectories(folder);
            Files.write(folder.resolve(System.currentTimeMillis() + "-"
                    + Integer.toHexString(session.hashCode()) + SUFFIX), bytes.toByteArray());
            forgetTheOldest();
            return true;
        } catch (IOException | RuntimeException couldNotWrite) {
            // A replay that could not be written is a replay nobody gets to watch, and that is
            // all. It must never be the thing that stops a game from ending.
            LOGGER.warn("Could not keep a replay of the finished game: {}", couldNotWrite.toString());
            return false;
        }
    }

    /** What has been kept, newest first. */
    public static List<Record> kept() {
        List<Record> found = new ArrayList<>();
        for (Path file : files()) {
            headerOf(file).ifPresent(found::add);
        }
        found.sort(Comparator.comparingLong(Record::when).reversed());
        return List.copyOf(found);
    }

    /**
     * The board at one step of a replay, as somebody entitled to all of it would have seen it.
     *
     * <p>Folded here rather than on the client, and this is the whole security argument: the
     * seed and the sealed events never leave the server, so what crosses the wire is a board
     * and not the means to reconstruct one. A client that asked for step forty gets step forty
     * and learns nothing about how it was arrived at.
     *
     * @param step how many of the game's actions to apply, from none to all of them
     */
    public static java.util.Optional<GameView> frameOf(String id, int step) {
        return hold(id).map(watching -> watching.frameAt(step));
    }

    /**
     * A replay held open, so watching one costs a fold of the whole game once.
     *
     * <p>Playback asks for step N, then N+1, then N+2, several times a second, and the board
     * at each of those is the board at the one before it with a single record applied. Folding
     * from the start each time measured at thirty-three milliseconds on a four-thousand-event
     * game - two thirds of a tick, per frame, per watcher - and this is what makes it one
     * event instead. Scrubbing backwards folds again from the front, which is rare and is
     * what somebody dragging a bar has already accepted the cost of.
     *
     * <p>Server thread only, like everything that touches a session.
     */
    public static final class Watching {

        private final String id;
        private final List<SessionRecord> records;
        private final List<SeatId> seats;
        private final int startingLife;
        private final SessionSeed seed;
        private final UndoMode undoMode;

        private GameSession session;
        private int at;

        private Watching(String id, List<SessionRecord> records, List<SeatId> seats,
                int startingLife, SessionSeed seed, UndoMode undoMode) {
            this.id = id;
            this.records = records;
            this.seats = seats;
            this.startingLife = startingLife;
            this.seed = seed;
            this.undoMode = undoMode;
            this.session = GameSession.restore(seats, startingLife, seed, undoMode, List.of());
            this.at = 0;
        }

        public String id() {
            return id;
        }

        /** How many steps this game has, which is what a scrubber is drawn against. */
        public int steps() {
            return records.size();
        }

        /** The board at one step, as somebody entitled to all of it would have seen it. */
        public GameView frameAt(int step) {
            int wanted = Math.clamp(step, 0, records.size());
            if (wanted < at) {
                // Backwards. Nothing can be taken off a fold, so it starts again.
                session = GameSession.restore(
                        seats, startingLife, seed, undoMode, records.subList(0, wanted));
            } else if (wanted > at) {
                session.extendWith(records.subList(at, wanted));
            }
            at = wanted;
            return VisibilityRules.viewFor(session.state(), Viewer.HISTORIAN, session.log());
        }
    }

    /**
     * Opens a replay and holds it, ready to be scrubbed.
     *
     * <p>The id is matched against the names of the files that are really there rather than
     * resolved as a path, so a client cannot name a file the server never offered it.
     */
    public static java.util.Optional<Watching> hold(String id) {
        for (Path file : files()) {
            if (file.getFileName().toString().equals(id)) {
                return read(file).map(game -> new Watching(id, game.records(), game.seats(),
                        game.startingLife(), game.seed(), game.undoMode()));
            }
        }
        return java.util.Optional.empty();
    }

    /** How many steps a replay has, which is what a scrubber is drawn against. */
    public static int stepsIn(String id) {
        return headerOf(id).map(Record::steps).orElse(0);
    }

    /**
     * One kept game by its id, without reading the rest of the shelf.
     *
     * <p>Its own method because scrubbing asks for a frame four times a second and the
     * obvious way to answer - list everything and find the one - reads and parses every
     * header on the server for each of those. The id is matched against the file names that
     * are really there, exactly as {@link #frameOf} does, so a name the server never offered
     * still opens nothing.
     */
    public static java.util.Optional<Record> headerOf(String id) {
        for (Path file : files()) {
            if (file.getFileName().toString().equals(id)) {
                return headerOf(file);
            }
        }
        return java.util.Optional.empty();
    }

    /** Everything a replay file holds, once it has been read. */
    private record Game(
            List<SeatId> seats, int startingLife, SessionSeed seed, UndoMode undoMode,
            List<SessionRecord> records) {
    }

    /**
     * Reads a replay file whole.
     *
     * <p>The records are handed back rather than folded, because what wants folding depends
     * on which step somebody is looking at - see {@link Watching}.
     */
    private static java.util.Optional<Game> read(Path file) {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != VERSION) {
                return java.util.Optional.empty();
            }
            in.readLong();
            int seats = in.readInt();
            in.readInt();
            int startingLife = in.readInt();
            in.readInt();
            UndoMode undoMode = UndoMode.valueOf(in.readUTF());
            SessionSeed seed = SessionSeed.fromBytes(in.readNBytes(in.readInt()));
            int players = in.readInt();
            for (int index = 0; index < players; index++) {
                in.readUTF();
                in.readLong();
                in.readLong();
            }
            byte[] publicLog = new byte[in.readInt()];
            in.readFully(publicLog);
            byte[] secretLog = new byte[in.readInt()];
            in.readFully(secretLog);

            return java.util.Optional.of(new Game(
                    seatsOf(seats), startingLife, seed, undoMode,
                    SessionCodec.read(publicLog, secretLog)));
        } catch (IOException | RuntimeException unreadable) {
            LOGGER.warn("A replay would not open: {}", unreadable.toString());
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<Record> headerOf(Path file) {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != VERSION) {
                return java.util.Optional.empty();
            }
            long when = in.readLong();
            int seats = in.readInt();
            int turns = in.readInt();
            in.readInt();
            int steps = in.readInt();
            in.readUTF();
            in.readNBytes(in.readInt());
            int players = in.readInt();
            List<Played> named = new ArrayList<>(players);
            for (int index = 0; index < players; index++) {
                String name = in.readUTF();
                named.add(new Played(name, new java.util.UUID(in.readLong(), in.readLong())));
            }
            return java.util.Optional.of(new Record(
                    file.getFileName().toString(), when, named, turns, steps, seats));
        } catch (IOException | RuntimeException unreadable) {
            return java.util.Optional.empty();
        }
    }

    private static List<SeatId> seatsOf(int howMany) {
        List<SeatId> seats = new ArrayList<>(howMany);
        for (int index = 0; index < howMany; index++) {
            seats.add(new SeatId(index));
        }
        return seats;
    }

    private static List<Path> files() {
        Path folder;
        try {
            folder = folder();
        } catch (RuntimeException noPlatform) {
            return List.of();
        }
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (var listing = Files.list(folder)) {
            return listing.filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /** Drops the oldest past the cap, so a long-lived server does not fill up with games. */
    private static void forgetTheOldest() {
        List<Path> files = files();
        for (int index = KEPT; index < files.size(); index++) {
            try {
                Files.deleteIfExists(files.get(index));
            } catch (IOException couldNotDelete) {
                LOGGER.warn("Could not drop an old replay: {}", couldNotDelete.toString());
            }
        }
    }

    private static Path folder() {
        return Platform.get().dataDirectory().resolve(FOLDER);
    }
}
