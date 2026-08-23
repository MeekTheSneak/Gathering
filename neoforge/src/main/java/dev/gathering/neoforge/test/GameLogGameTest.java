package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Phase;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.event.LogEntry;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The log, as sentences.
 *
 * <p>Runs in a world because it needs the real language file: the failure this catches is a
 * log line whose translation key nobody added, or one whose arguments are read in a different
 * order than the event supplies them. Both are invisible in a unit test - the key is a string
 * either way - and both look like the log is broken to the person reading it.
 *
 * <p>The second one is the reason every log string uses indexed placeholders. "Chris drew
 * Chris cards for 3" is what an unindexed string produces the moment an event's argument order
 * is not the sentence's word order, which for English is most of them.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameLogGameTest {

    private static final SeatId ALICE = new SeatId(0);
    private static final SeatId BOB = new SeatId(1);
    private static final CardInstanceId A_CARD = new CardInstanceId(0);

    @GameTest(template = "empty")
    public static void everyKindOfLineHasAStringAndReadsAsEnglish(GameTestHelper helper) {
        GameSession session = playedGame();
        GameView board = VisibilityRules.viewFor(
                session.state(), Viewer.seat(ALICE), session.log());

        if (board.log().isEmpty()) {
            helper.fail("A game was played and the log is empty");
            return;
        }
        for (LogEntry entry : board.log()) {
            Component line = dev.gathering.client.GameLogText.render(board, entry);
            String text = line.getString();

            if (!Language.getInstance().has(entry.key())) {
                helper.fail("No language string for log line " + entry.key());
                return;
            }
            if (text.contains("%")) {
                helper.fail("Unfilled placeholder in " + entry.key() + ": \"" + text + "\"");
                return;
            }
            if (text.isBlank()) {
                helper.fail("Log line " + entry.key() + " renders as nothing");
                return;
            }
            // Every number the event carried has to appear in the sentence. This is the check
            // that catches a string reading its arguments in the wrong order: "Chris drew
            // Chris cards for 3" fills every placeholder and drops nothing, so only asking
            // whether the counts survived finds it.
            for (var arg : entry.args()) {
                if (arg instanceof dev.gathering.core.game.event.LogArg.Amount amount
                        && !text.contains(Integer.toString(amount.value()))) {
                    helper.fail("Log line " + entry.key() + " lost the number " + amount.value()
                            + ": \"" + text + "\"");
                    return;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theLogNeverNamesACardInSomebodysHand(GameTestHelper helper) {
        // The whole security design of the log, checked end to end rather than at the type
        // level: a card moved out of a hand is described before it arrives anywhere public.
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 20, SessionSeed.random(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE, new PlayerRef(new UUID(0L, 1L), "Alice")));
        session.submit(new GameEvent.DeckLoaded(
                ALICE, List.of(CardIdentity.ofPrinting(UUID.fromString(
                        "5805f64c-dd88-4e94-8f0a-a01dae67e3ba"))), List.of()));
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        session.submit(new GameEvent.CardMoved(
                ALICE, A_CARD, ZoneRef.of(ALICE, Zone.LIBRARY), Placement.BOTTOM));

        GameView board = VisibilityRules.viewFor(session.state(), Viewer.seat(BOB), session.log());
        String moved = board.log().stream()
                // Any of the three: which end of a pile a card went to picks the wording, and
                // this one goes to the bottom of a library.
                .filter(entry -> entry.key().startsWith("log.gathering.card_moved"))
                .map(entry -> dev.gathering.client.GameLogText.render(board, entry).getString())
                .findFirst()
                .orElse("");

        if (moved.isBlank()) {
            helper.fail("The move never reached the log");
            return;
        }
        if (moved.contains("Sol Ring")) {
            helper.fail("The log named a card that went from a hand into a library: " + moved);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewindingStrikesALineOutRatherThanLosingIt(GameTestHelper helper) {
        GameSession session = playedGame();
        int before = session.log().size();

        session.undo(ALICE, 1, List.of(ALICE, BOB));

        if (session.log().size() != before) {
            helper.fail("Undo shortened the log from " + before + " to " + session.log().size());
            return;
        }
        if (session.log().stream().noneMatch(LogEntry::undone)) {
            helper.fail("Undo left no line marked as rewound");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------- fixtures

    /** A short game that exercises as many kinds of line as can be reached in sequence. */
    private static GameSession playedGame() {
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 20, SessionSeed.random(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE, new PlayerRef(new UUID(0L, 1L), "Alice")));
        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(new UUID(0L, 2L), "Bob")));
        session.submit(new GameEvent.DeckLoaded(ALICE, deck(), List.of()));
        session.submit(new GameEvent.LibraryShuffled(ALICE, ALICE));
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 3));

        CardInstanceId first = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardMoved(
                ALICE, first, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        session.submit(new GameEvent.CardTapSet(ALICE, first, true));
        session.submit(new GameEvent.CardRotated(ALICE, first, 30));
        session.submit(new GameEvent.CardFacingSet(ALICE, first, Facing.FACE_DOWN));
        session.submit(new GameEvent.CardFacingSet(ALICE, first, Facing.FACE_UP));
        session.submit(new GameEvent.CounterChanged(ALICE, first, "+1/+1", 2));
        session.submit(new GameEvent.SeatCounterChanged(ALICE, ALICE, "poison", 1));
        session.submit(new GameEvent.SeatUntappedAll(ALICE, ALICE));
        session.submit(new GameEvent.LibraryLooked(ALICE, ALICE, 2));
        session.submit(new GameEvent.LibraryClosed(ALICE));
        session.submit(new GameEvent.LibraryMilled(ALICE, ALICE, 1));
        session.submit(new GameEvent.LibraryRevealed(ALICE, ALICE, 1));
        session.submit(new GameEvent.LibraryRevealed(ALICE, ALICE, 0));
        session.submit(new GameEvent.LibrarySearched(ALICE, ALICE));
        session.submit(new GameEvent.LifeChanged(ALICE, BOB, -3));
        session.submit(new GameEvent.CommanderDamageChanged(ALICE, BOB, ALICE, 4));
        session.submit(new GameEvent.CommanderTaxChanged(ALICE, ALICE, first, 2));
        session.submit(new GameEvent.TokenCreated(ALICE, ALICE, deck().get(0), 2));
        session.submit(new GameEvent.TokenCopyCreated(ALICE, first, ALICE));
        session.submit(new GameEvent.CardPinged(ALICE, first));
        session.submit(new GameEvent.PhaseSet(ALICE, Phase.PRECOMBAT_MAIN));
        session.submit(new GameEvent.TurnPassed(ALICE, BOB));
        session.submit(new GameEvent.Mulliganed(BOB, BOB, 6));

        CardInstanceId second = session.state().contents(ALICE, Zone.BATTLEFIELD).get(1);
        session.submit(new GameEvent.CardAttached(ALICE, second, first));
        session.submit(new GameEvent.CardAttached(ALICE, second, null));
        session.submit(new GameEvent.TokenRemoved(ALICE, second));
        session.submit(new GameEvent.SeatReleased(BOB));
        return session;
    }

    private static List<CardIdentity> deck() {
        UUID solRing = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
        return List.of(
                CardIdentity.ofPrinting(solRing), CardIdentity.ofPrinting(solRing),
                CardIdentity.ofPrinting(solRing), CardIdentity.ofPrinting(solRing),
                CardIdentity.ofPrinting(solRing), CardIdentity.ofPrinting(solRing));
    }
}
