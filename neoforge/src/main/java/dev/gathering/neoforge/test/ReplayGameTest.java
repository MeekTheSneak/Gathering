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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Watching a finished game back.
 *
 * <p>These run against the real disk, because that is where the risk is: a replay is a file
 * written by one process and read by another, and every fault worth catching - a header that
 * does not read back, a step that folds to the wrong board, a client naming a file it was
 * never offered - lives in that gap rather than in the pure core.
 *
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
            if (!Replays.keep(session, 40, List.of("Alice", "Bob"))) {
                helper.fail("a finished game was not kept");
                return;
            }
            Replays.Record kept = newest().orElse(null);
            if (kept == null) {
                helper.fail("the shelf is empty right after a game was put on it");
                return;
            }
            if (!kept.players().contains("Alice") || !kept.players().contains("Bob")) {
                helper.fail("the replay does not say who played it: " + kept.players());
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
     *
     * <p>And shows them <em>only</em> here. The same board asked for during play sends a count
     * and no cards, which is what {@code HistorianTest} pins in the core; what this adds is
     * that the file, the fold and the wire in between do not quietly lose it.
     */
    @GameTest(template = "empty")
    public static void aHistorianSeesWhatThePlayersHid(GameTestHelper helper) {
        withReplaysOn(helper, () -> {
            Replays.keep(aFinishedGame(), 40, List.of("Alice", "Bob"));
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
            Replays.keep(aFinishedGame(), 40, List.of("Alice", "Bob"));
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
     *
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
        boolean before = ServerSettings.get().modes().replaysEnabled();
        try {
            Settings.set("modes.replays_enabled", "off");
            if (Replays.keep(aFinishedGame(), 40, List.of("Alice", "Bob"))) {
                helper.fail("a game was kept on a server that keeps none");
                return;
            }
            helper.succeed();
        } finally {
            Settings.set("modes.replays_enabled", before ? "on" : "off");
        }
    }

    // ------------------------------------------------------------------ setup

    /**
     * Two seats, two decks, three cards drawn, and a game that is over.
     *
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
        boolean before = ServerSettings.get().modes().replaysEnabled();
        try {
            if (!before) {
                Settings.set("modes.replays_enabled", "on");
            }
            body.run();
        } catch (RuntimeException broke) {
            helper.fail("a replay threw: " + broke);
        } finally {
            if (!before) {
                Settings.set("modes.replays_enabled", "off");
            }
        }
    }
}
