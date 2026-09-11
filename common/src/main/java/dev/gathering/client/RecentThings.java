package dev.gathering.client;

import dev.gathering.core.ui.Recents;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The token and counter names this player keeps typing, per server, across restarts.
 * <p>Making a token means typing its name, and a deck that makes Treasures makes a hundred of
 * them in an evening. This is the row of what you used last, so the second one is a click.
 * <p>Its own file rather than a section of {@link ClientSettings}, because the keys here are
 * not known in advance: there is one line per server this player has been to, and the settings
 * file is a fixed set of named options with a schema. Two files with two shapes, each simple,
 * beats one file that is both.
 * <p><b>Names, never identities.</b> What is written is the word somebody typed. An instance id
 * is a handle on one particular card in one particular game; putting one in a file on disk
 * would keep a pointer to something that stopped existing when the game ended, and would make
 * a settings file into a record of what was in somebody's game. {@link Recents} is where the
 * bound and the packing live, and it has never heard of a card.
 * <p>Client-only.
 */
public final class RecentThings {

    private static final String FILE_NAME = "gathering-recent.txt";

    /** What kind of thing a line is about. The prefix of its key. */
    private static final String TOKENS = "tokens";
    private static final String COUNTERS = "counters";

    /**
     * A ceiling on how many servers are remembered at all.
     * <p>One line per server this player has ever joined would otherwise grow for as long as
     * they play. Past this, the file is left alone rather than added to: losing the offer of a
     * shortcut on a new server is nothing, and an unbounded file is not.
     */
    private static final int MOST_SERVERS = 64;

    /** Every line, by key. Read once, written back whole. */
    private static final Map<String, List<String>> LINES = new LinkedHashMap<>();

    private static boolean loaded;
    private static boolean unsaved;

    /** How many ticks since the last change, so a burst of tokens is one write. */
    private static int sinceChanged;

    /** As {@link ClientSettings}, a burst is written once rather than once per change. */
    private static final int SETTLE_TICKS = 20;

    private static Path insteadForTesting;

    private RecentThings() {
    }

    /**
     * Points this at another file, for a test that wants one of its own.
     * <p>The same reason {@link ClientSettings#fileForTesting} exists: Minecraft runs game
     * tests concurrently, and tests sharing one real file race each other.
     */
    public static void fileForTesting(Path where) {
        insteadForTesting = where;
        forgetForTesting();
    }

    /** Forgets what was read, so the next ask reads the file again. */
    public static void forgetForTesting() {
        LINES.clear();
        loaded = false;
        unsaved = false;
        sinceChanged = 0;
    }

    private static Path file() {
        Path chosen = insteadForTesting;
        return chosen != null ? chosen : Platform.get().configDirectory().resolve(FILE_NAME);
    }

    /**
     * How to ask which server this is. Bound at client init by both loaders.
     * <p>A supplier rather than a call to {@code Minecraft.getInstance()} here, and not for
     * tidiness: this class is in {@code :common}, which is loaded on a dedicated server too,
     * and touching a client class from it throws {@code Attempted to load class
     * net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER} the moment anything
     * server-side reaches it. The in-world test caught exactly that. Same shape as
     * {@link TableShortcuts#bindKeyLookup}.
     * <p>Until one is bound the answer is nothing, which keys everything under one scope -
     * the honest answer when nobody has said where we are.
     */
    private static volatile java.util.function.Supplier<String> where = () -> "";

    /** Bound at client init to this loader's way of naming the server. */
    public static void bindServerLookup(java.util.function.Supplier<String> lookup) {
        if (lookup != null) {
            where = lookup;
        }
    }

    /**
     * Which server this is, as something a key can be made from.
     * <p>Not a promise about identity - two servers behind one address share a row of token
     * names, which is a trivial thing to share - and stable across restarts, which is the
     * property that matters.
     */
    private static String whereWeAre() {
        try {
            String said = where.get();
            return said == null ? "" : said;
        } catch (RuntimeException nobodyKnows) {
            return "";
        }
    }

    private static String keyFor(String what) {
        return what + "." + Recents.scopeKey(whereWeAre());
    }

    /** The token names used most recently on this server, most recent first. */
    public static List<String> tokens() {
        return read(keyFor(TOKENS));
    }

    /** The counter names used most recently on this server, most recent first. */
    public static List<String> counters() {
        return read(keyFor(COUNTERS));
    }

    /** Remembers a token name, at the front. */
    public static void rememberToken(String name) {
        remember(keyFor(TOKENS), name);
    }

    /** Remembers a counter name, at the front. */
    public static void rememberCounter(String name) {
        remember(keyFor(COUNTERS), name);
    }

    /**
     * Drops a token name.
     * <p>For the one that turned out to be a typo, or one this server has not got - a
     * remembered name is an offer, and an offer that does not work is worth withdrawing.
     */
    public static void forgetToken(String name) {
        List<String> had = read(keyFor(TOKENS));
        List<String> next = Recents.forget(had, name);
        if (!next.equals(had)) {
            LINES.put(keyFor(TOKENS), next);
            unsaved = true;
            sinceChanged = 0;
        }
    }

    private static List<String> read(String key) {
        load();
        return LINES.getOrDefault(key, List.of());
    }

    private static void remember(String key, String name) {
        load();
        List<String> next = Recents.remember(LINES.get(key), name);
        if (next.isEmpty() || next.equals(LINES.get(key))) {
            return;
        }
        if (!LINES.containsKey(key) && LINES.size() >= MOST_SERVERS) {
            // Losing the offer of a shortcut on a new server is nothing. An unbounded file is
            // not, and this is the only place the number of lines can grow.
            return;
        }
        LINES.put(key, next);
        unsaved = true;
        sinceChanged = 0;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path where = file();
        if (!Files.isRegularFile(where)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(where, StandardCharsets.UTF_8)) {
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                int equals = text.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = text.substring(0, equals).strip();
                List<String> names = Recents.unpack(text.substring(equals + 1).strip());
                if (!key.isEmpty() && !names.isEmpty() && LINES.size() < MOST_SERVERS * 2) {
                    LINES.put(key, names);
                }
            }
        } catch (IOException | RuntimeException couldNotRead) {
            // A file that will not read is a row of shortcuts nobody gets, which is a
            // disappointment rather than a fault. It is rewritten the next time one is used.
            LINES.clear();
        }
    }

    /** Called once a tick, so a burst of tokens is one write rather than one write each. */
    public static void tick() {
        if (!unsaved) {
            return;
        }
        if (++sinceChanged >= SETTLE_TICKS) {
            flush();
        }
    }

    /** Writes now, for a client that is shutting down. */
    public static void flush() {
        if (!unsaved) {
            return;
        }
        unsaved = false;
        sinceChanged = 0;
        Path where = file();
        StringBuilder text = new StringBuilder();
        text.append("# The token and counter names you use most, per server.\n");
        text.append("# Names only, and at most ").append(Recents.MOST_KEPT)
                .append(" of each. Safe to delete.\n\n");
        List<String> keys = new ArrayList<>(LINES.keySet());
        java.util.Collections.sort(keys);
        for (String key : keys) {
            String packed = Recents.pack(LINES.get(key));
            if (!packed.isEmpty()) {
                text.append(key).append(" = ").append(packed).append('\n');
            }
        }
        try {
            Path folder = where.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            // Written beside and moved into place, so an interrupted write leaves the old
            // file rather than half of a new one.
            Path writing = where.resolveSibling(where.getFileName() + ".writing");
            Files.writeString(writing, text.toString(), StandardCharsets.UTF_8);
            Files.move(writing, where, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException couldNotWrite) {
            // Nothing to be done and nothing worth saying: these are shortcuts, and the game
            // is entirely usable without them.
            unsaved = true;
        }
    }
}
