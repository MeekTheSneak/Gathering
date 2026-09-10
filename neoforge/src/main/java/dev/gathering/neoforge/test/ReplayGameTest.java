package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.server.Replays;
import dev.gathering.server.Settings;
import dev.gathering.service.ServerSettings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Watching a finished game back.
 * <p>These run against the real disk, because that is where the risk is: a replay is a file
 * written by one process and read by another, and every fault worth catching - a header that
 * does not read back, a step that folds to the wrong board, a client naming a file it was
 * never offered - lives in that gap rather than in the pure core.
 * <p>The one that matters most is {@link #aHistorianSeesWhatThePlayersHid}. The whole reason
 * a replay is allowed to exist is that a game which is over has nothing left to protect; if
 * that stopped being true the mod would be handing out hidden information, so it is asserted
 * rather than assumed.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReplayGameTest {

    private static final SeatId ALICE = SeatId.of(0);
    private static final SeatId BOB = SeatId.of(1);

    private static final int DRAWN = 3;

    private ReplayGameTest() {
    }

    /** A game that ended is on the shelf, with who played it and how long it ran. */
    @GameTest(template = "empty")
    public static void aFinishedGameIsKept(GameTestHelper helper) {
        withReplaysOn(helper, () -> {
            GameSession session = aFinishedGame();
            if (!Replays.keep(session, 40, twoPlayers())) {
                helper.fail("a finished game was not kept");
                return;
            }
            Replays.Record kept = newest().orElse(null);
            if (kept == null) {
                helper.fail("the shelf is empty right after a game was put on it");
                return;
            }
            if (!kept.names().contains("Alice") || !kept.names().contains("Bob")) {
                helper.fail("the replay does not say who played it: " + kept.names());
                return;
            }
            if (!kept.wasPlayedBy(ALICE_ACCOUNT) || kept.wasPlayedBy(UUID.randomUUID())) {
                helper.fail("the replay does not remember whose game it was, so a server that"
                        + " keeps them for the people who played cannot tell");
                return;
            }
            if (kept.steps() != session.records().size()) {
                helper.fail("the header says " + kept.steps() + " steps, the game had "
                        + session.records().size());
                return;
            }
            if (Replays.stepsIn(kept.id()) != kept.steps()) {
                helper.fail("the shelf and the scrubber disagree about how long the game was");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The disclosure the whole feature turns on: a replay shows the hands.
     * <p>And shows them <em>only</em> here. The same board asked for during play sends a count
     * and no cards, which is what {@code HistorianTest} pins in the core; what this adds is
     * that the file, the fold and the wire in between do not quietly lose it.
     */
    @GameTest(template = "empty")
    public static void aHistorianSeesWhatThePlayersHid(GameTestHelper helper) {
        withReplaysOn(helper, () -> {
            Replays.keep(aFinishedGame(), 40, twoPlayers());
            Replays.Record kept = newest().orElse(null);
            if (kept == null) {
                helper.fail("nothing was kept");
                return;
            }
            GameView last = Replays.frameOf(kept.id(), kept.steps()).orElse(null);
            if (last == null) {
                helper.fail("the last frame of the game would not open");
                return;
            }
            int held = last.seat(ALICE).zone(Zone.HAND).cards().size();
            if (held != DRAWN) {
                helper.fail("a replay showed " + held + " of " + DRAWN + " cards in a hand");
                return;
            }
            if (last.seat(ALICE).zone(Zone.LIBRARY).cards().isEmpty()) {
                helper.fail("a replay would not show the library, which is the point of one");
                return;
            }
            helper.succeed();
        });
    }

    /** Step zero is the table before anybody did anything, and the end is the whole game. */
    @GameTest(template = "empty")
    public static void scrubbingWindsTheGameBack(GameTestHelper helper) {
        withReplaysOn(helper, () -> {
            Replays.keep(aFinishedGame(), 40, twoPlayers());
            Replays.Record kept = newest().orElse(null);
            if (kept == null) {
                helper.fail("nothing was kept");
                return;
            }
            GameView opening = Replays.frameOf(kept.id(), 0).orElse(null);
            if (opening == null) {
                helper.fail("the opening frame would not open");
                return;
            }
            if (opening.seat(ALICE).zone(Zone.LIBRARY).count() != 0) {
                helper.fail("step zero already had a deck down, so nothing was wound back");
                return;
            }
            // Past the end is the whole game rather than nothing, which is what a scrubber
            // dragged hard to the right has to give.
            GameView past = Replays.frameOf(kept.id(), kept.steps() + 500).orElse(null);
            if (past == null || past.seat(ALICE).zone(Zone.HAND).cards().size() != DRAWN) {
                helper.fail("dragging past the end did not land on the end of the game");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A client cannot name a file the server never offered it.
     * <p>The id on the wire is a file name, and the guard is that it is matched against the
     * names of the files that are actually there rather than resolved as a path. Without that
     * a replay id would be a way to ask the server to read anything it can reach.
     */
    @GameTest(template = "empty")
    public static void anIdTheServerNeverOfferedReadsNothing(GameTestHelper helper) {
        withReplaysOn(helper, () -> {
            for (String made : List.of(
                    "../../../server.properties",
                    "..\\..\\eula.txt",
                    "/etc/passwd",
                    "nothing-like-this.replay")) {
                if (Replays.frameOf(made, 0).isPresent()) {
                    helper.fail("a made-up replay id opened something: " + made);
                    return;
                }
                if (Replays.stepsIn(made) != 0) {
                    helper.fail("a made-up replay id had a length: " + made);
                    return;
                }
            }
            helper.succeed();
        });
    }

    /** A server with replays off keeps none, which is the whole of what the switch does. */
    @GameTest(template = "empty")
    public static void aServerWithReplaysOffKeepsNone(GameTestHelper helper) {
        var before = ServerSettings.get().modes().replays();
        try {
            Settings.set("modes.replays", "off");
            if (Replays.keep(aFinishedGame(), 40, twoPlayers())) {
                helper.fail("a game was kept on a server that keeps none");
                return;
            }
            helper.succeed();
        } finally {
            Settings.set("modes.replays", before.toString());
        }
    }

    /**
     * With replays kept for the people who played them, nobody else may open one.
     * <p>The setting the brief asked for and the one most groups actually want. What it is
     * protecting is small and real: a deck list, read off a replay, before the rematch.
     */
    @GameTest(template = "empty")
    public static void participantsOnlyMeansTheyPlayedIt(GameTestHelper helper) {
        var before = ServerSettings.get().modes().replays();
        try {
            Settings.set("modes.replays", "public");
            Replays.keep(aFinishedGame(), 40, twoPlayers());
            Replays.Record kept = newest().orElse(null);
            if (kept == null) {
                helper.fail("nothing was kept");
                return;
            }
            ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
            if (!dev.gathering.server.ReplayWatch.mayWatch(stranger, kept)) {
                helper.fail("a public replay was refused to somebody on the server");
                return;
            }
            Settings.set("modes.replays", "participants");
            if (dev.gathering.server.ReplayWatch.mayWatch(stranger, kept)) {
                helper.fail("somebody who was not at the table can read the game back");
                return;
            }
            Settings.set("modes.replays", "off");
            if (dev.gathering.server.ReplayWatch.keeping()) {
                helper.fail("a server with replays off is still keeping them");
                return;
            }
            helper.succeed();
        } finally {
            Settings.set("modes.replays", before.toString());
        }
    }

    // ------------------------------------------------------------------ setup

    /**
     * A long game is opened and scrubbed without any of that happening in a tick.
     * <p>The reviewer's point, and it was fair: replay headers were streamed and cached, and
     * the two costs that actually matter were left where they were. Opening a replay reads a
     * whole file and deserializes every record; a scrub backwards folds the game again from
     * the front, which measured at two thirds of a tick on a four-thousand-event game - per
     * frame, per watcher, several times a second while a bar is being dragged.
     * <p>So this builds a game long enough to be worth measuring, folds it both ways, and
     * prints what each costs. It asserts on the one thing that is a rule rather than a number:
     * a step <em>forward</em> stays cheap, because that is the only case the server thread
     * still does itself. The rest is printed so a change that makes it worse is visible in a
     * run rather than discovered by a player dragging a scrubber.
     */
    @GameTest(template = "empty")
    public static void alongReplayIsFoldedOffTheTick(GameTestHelper helper) {
        withReplaysOn(helper, () -> {
            GameSession session = aLongGame(LONG_ENOUGH_TO_MEASURE);
            Replays.keep(session, 40, twoPlayers());
            Replays.Record kept = newest().orElse(null);
            if (kept == null) {
                helper.fail("nothing was kept");
                return;
            }

            long openedAt = System.nanoTime();
            Replays.Watching watching = Replays.hold(kept.id()).orElse(null);
            long openMicros = (System.nanoTime() - openedAt) / 1000;
            if (watching == null) {
                helper.fail("a long replay would not open");
                return;
            }

            // Forward, one step at a time: what playback does, and the only thing the server
            // thread still does for itself.
            watching.frameAt(0);
            long steppedAt = System.nanoTime();
            for (int step = 1; step <= 20; step++) {
                watching.frameAt(step);
            }
            long stepMicros = (System.nanoTime() - steppedAt) / 1000 / 20;

            // Backwards into the middle, which restores the session and folds half the game
            // again. Not to the front: a fold to step zero restores from no records at all,
            // which is the one rewind that costs nothing and would have measured this as
            // cheap while a dragged scrubber was costing two thirds of a tick a frame.
            watching.frameAt(watching.steps());
            long rewoundAt = System.nanoTime();
            watching.frameAt(watching.steps() / 2);
            long rewindMicros = (System.nanoTime() - rewoundAt) / 1000;

            System.out.println("[replay] " + watching.steps() + " steps: open " + openMicros
                    + "us, one step forward " + stepMicros + "us, rewind into the middle "
                    + rewindMicros + "us");

            // The rule rather than the number, and only the rule. A microsecond budget taken
            // inside a shared game-test run measures the machine and its neighbours: two
            // drafts of this asserted an absolute ceiling on a step forward and the same code
            // printed 1.4 ms, 2.3 ms and 11.7 ms across three runs, failing two of them. What
            // does hold across all of that is the shape - a rewind stays multiples dearer than
            // a step forward, which is the whole reason one of them is on another thread. The
            // numbers are printed for a person to read; this is what fails a build.
            if (rewindMicros < stepMicros * WORTH_A_THREAD) {
                helper.fail("rewinding costs " + rewindMicros + "us against a step forward's "
                        + stepMicros + "us, so there is nothing here worth a worker thread");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * How many moves the game to be measured has.
     * <p>Long enough that folding it is a real cost and short enough that building it does not
     * leave a heap's worth of garbage for the wall-clock tests sharing this run - two thousand
     * did, and the collection scale test next door started failing its own budget.
     */
    private static final int LONG_ENOUGH_TO_MEASURE = 600;

    /**
     * How much dearer a rewind has to be than a step forward before moving it is worth it.
     * <p>It measures around seventeen times. Three is the point at which the split stops
     * paying for itself, and if this ever fails the split should go rather than the number.
     */
    private static final int WORTH_A_THREAD = 3;

    /** A game with a lot in it, so folding it is worth measuring. */
    private static GameSession aLongGame(int moves) {
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 40, seed(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE, new PlayerRef(UUID.randomUUID(), "Alice")));
        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(UUID.randomUUID(), "Bob")));
        session.submit(new GameEvent.DeckLoaded(ALICE, deck(200), List.of()));
        session.submit(new GameEvent.DeckLoaded(BOB, deck(200), List.of()));
        for (int move = 0; move < moves; move++) {
            SeatId who = move % 2 == 0 ? ALICE : BOB;
            session.submit(new GameEvent.LifeChanged(who, who, move % 2 == 0 ? -1 : 1));
        }
        session.submit(new GameEvent.SessionEnded(ALICE, "test"));
        return session;
    }

    /**
     * Two seats, two decks, three cards drawn, and a game that is over.
     * <p>Deliberately a game with something hidden in it. A replay of a board where every card
     * was already face up would pass every check here while proving nothing.
     */
    private static GameSession aFinishedGame() {
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 40, seed(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE, new PlayerRef(UUID.randomUUID(), "Alice")));
        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(UUID.randomUUID(), "Bob")));
        session.submit(new GameEvent.DeckLoaded(ALICE, deck(20), List.of()));
        session.submit(new GameEvent.DeckLoaded(BOB, deck(20), List.of()));
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, DRAWN));
        session.submit(new GameEvent.SessionEnded(ALICE, "test"));
        return session;
    }

    /**
     * The two who played, account and all.
     * <p>Fixed accounts rather than random ones, because half of what the header is for is
     * answering "was this your game" on a server that keeps replays for the people who played
     * them - and a random id cannot be asked that question afterwards.
     */
    private static List<Replays.Played> twoPlayers() {
        return List.of(
                new Replays.Played("Alice", ALICE_ACCOUNT),
                new Replays.Played("Bob", BOB_ACCOUNT));
    }

    private static final UUID ALICE_ACCOUNT =
            UUID.nameUUIDFromBytes("gathering-replay-alice".getBytes(StandardCharsets.UTF_8));

    private static final UUID BOB_ACCOUNT =
            UUID.nameUUIDFromBytes("gathering-replay-bob".getBytes(StandardCharsets.UTF_8));

    private static SessionSeed seed() {
        return SessionSeed.fromBytes(
                "gathering-replay-test-seed-0123456789".getBytes(StandardCharsets.UTF_8));
    }

    private static List<CardIdentity> deck(int size) {
        List<CardIdentity> cards = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            cards.add(CardIdentity.ofPrinting(UUID.fromString(
                    String.format("00000000-0000-4000-8000-%012d", index))));
        }
        return cards;
    }

    private static Optional<Replays.Record> newest() {
        List<Replays.Record> kept = Replays.kept();
        return kept.isEmpty() ? Optional.empty() : Optional.of(kept.get(0));
    }

    /** Runs a body with replays on, and puts the setting back however it started. */
    private static void withReplaysOn(GameTestHelper helper, Runnable body) {
        var before = ServerSettings.get().modes().replays();
        try {
            if (before != dev.gathering.core.config.GatheringConfig.Replays.PUBLIC) {
                Settings.set("modes.replays", "public");
            }
            body.run();
        } catch (RuntimeException broke) {
            helper.fail("a replay threw: " + broke);
        } finally {
            if (before != dev.gathering.core.config.GatheringConfig.Replays.PUBLIC) {
                Settings.set("modes.replays", before.toString());
            }
        }
    }
}
