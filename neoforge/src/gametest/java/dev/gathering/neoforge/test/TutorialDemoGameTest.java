package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientNetworking;
import dev.gathering.client.ClientSettings;
import dev.gathering.client.ClientTableActions;
import dev.gathering.client.Tutorial;
import dev.gathering.client.TutorialDemo;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.tutorial.TutorialStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The guided first game reaches no server, and can be started over after it runs out.
 * <p>The teaching board used to be a real session at a real table, and the worst defect this
 * project has had came from that: a real deck put down during practice was taken by the table
 * and discarded when practice ended. The replacement cannot lose property because it cannot
 * hold any - there is no table, no seat and no item anywhere in it.
 * <p>That argument is only worth as much as the isolation it rests on, so the isolation is
 * what these check, at the two places a move can leave this client: the event path every verb
 * goes through, and the payload path the screens use for the few things a server decides. Both
 * are checked with a spy bound in place of the real sender, which is the only way to tell
 * "sent nothing" apart from "sent something nobody was listening for".
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TutorialDemoGameTest {

    /** A table somewhere in an ordinary world, to stand in for a real one. */
    private static final BlockPos A_REAL_TABLE = new BlockPos(12, 64, 30);

    private static final SeatId LEARNER = SeatId.of(0);

    /**
     * Runs something with a spy in place of the client's sender, and hands back what it saw.
     * <p>The real sender is put back afterwards however it ends. A game test server has no
     * client and so usually has none bound at all, which is exactly why a spy is needed: an
     * unbound sender throws, and a throw is not the same evidence as a count of nought.
     */
    private static List<CustomPacketPayload> watchingTheWire(Runnable what) {
        List<CustomPacketPayload> seen = new ArrayList<>();
        ClientNetworking.bindSender(seen::add);
        try {
            what.run();
        } finally {
            ClientNetworking.bindSender(payload -> {
            });
        }
        return seen;
    }

    /** Puts the three remembered flags back, so one test cannot decide another's answer. */
    private static void restoring(Runnable what) {
        boolean offered = ClientSettings.tutorialOffered();
        boolean finished = ClientSettings.tutorialFinished();
        boolean skipped = ClientSettings.tutorialSkipped();
        try {
            what.run();
        } finally {
            Tutorial.clear();
            TutorialDemo.clear();
            ClientSettings.tutorialOffered(offered);
            ClientSettings.tutorialFinished(finished);
            ClientSettings.tutorialSkipped(skipped);
        }
    }

    private static int countIn(GameView board, Zone zone) {
        return board.seat(LEARNER).zone(zone).count();
    }

    private static Optional<CardView.Visible> firstVisibleIn(GameView board, Zone zone) {
        for (CardView card : board.seat(LEARNER).zone(zone).cards()) {
            if (card instanceof CardView.Visible visible) {
                return Optional.of(visible);
            }
        }
        return Optional.empty();
    }

    /**
     * Every move made in the demonstration changes the local board and sends nothing.
     * <p>Both halves matter. A screen that sent nothing because its moves went nowhere would
     * pass the first half of this and be useless, so the board is checked for having actually
     * changed - a card really did leave the library and arrive in the hand - while the wire is
     * checked for having stayed empty.
     */
    @GameTest(template = "empty")
    public static void demonstrationmovesneverreachtheserver(GameTestHelper helper) {
        restoring(() -> {
            TutorialDemo.begin();
            GameView before = TutorialDemo.board().orElseThrow();
            int handBefore = countIn(before, Zone.HAND);
            int libraryBefore = countIn(before, Zone.LIBRARY);

            List<CustomPacketPayload> sent = watchingTheWire(() -> {
                // Through the production path a screen uses, with the demonstration's own
                // position - not by calling TutorialDemo.submit, which would prove nothing
                // about how a real press gets there.
                ClientTableActions.send(TutorialDemo.table(),
                        new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            });

            if (!sent.isEmpty()) {
                helper.fail("a tutorial draw put " + sent.size() + " payload(s) on the wire: "
                        + sent.getFirst().type().id());
                return;
            }
            GameView after = TutorialDemo.board().orElseThrow();
            if (countIn(after, Zone.HAND) != handBefore + 1) {
                helper.fail("the tutorial draw sent nothing, but it also did nothing: hand went "
                        + handBefore + " -> " + countIn(after, Zone.HAND));
                return;
            }
            if (countIn(after, Zone.LIBRARY) != libraryBefore - 1) {
                helper.fail("a card arrived in the hand without leaving the library");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A payload addressed to the demonstration is dropped; the same one addressed to a table
     * is not.
     * <p>The second half is the part that makes this a test rather than a tautology. A guard
     * that dropped everything would pass the first half, and would also break the mod.
     */
    @GameTest(template = "empty")
    public static void payloadsaboutthedemonstrationaredropped(GameTestHelper helper) {
        restoring(() -> {
            List<CustomPacketPayload> toNowhere = watchingTheWire(() ->
                    ClientNetworking.send(
                            new dev.gathering.network.FlipCoinPayload(TutorialDemo.table())));
            if (!toNowhere.isEmpty()) {
                helper.fail("a payload addressed to the demonstration was put on the wire");
                return;
            }
            List<CustomPacketPayload> toATable = watchingTheWire(() ->
                    ClientNetworking.send(new dev.gathering.network.FlipCoinPayload(A_REAL_TABLE)));
            if (toATable.size() != 1) {
                helper.fail("the guard swallowed a payload meant for a real table: "
                        + toATable.size() + " of 1 arrived");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Restart deals a whole new board, including after the library has been drawn empty.
     * <p>The old tutorial restarted the instructions and left the game where it was, so
     * somebody who had drawn their last card was told to draw one. A demonstration is cheap to
     * throw away and build again, and that is what Restart does.
     */
    @GameTest(template = "empty")
    public static void restartdealsagainafterthelibraryranout(GameTestHelper helper) {
        restoring(() -> {
            TutorialDemo.begin();
            int library = countIn(TutorialDemo.board().orElseThrow(), Zone.LIBRARY);
            for (int card = 0; card < library; card++) {
                TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            }
            if (countIn(TutorialDemo.board().orElseThrow(), Zone.LIBRARY) != 0) {
                helper.fail("the demonstration library did not run out when it was drawn out");
                return;
            }

            TutorialDemo.restart();

            GameView fresh = TutorialDemo.board().orElseThrow();
            if (countIn(fresh, Zone.LIBRARY) != library) {
                helper.fail("Restart left " + countIn(fresh, Zone.LIBRARY) + " cards in a library"
                        + " that started with " + library);
                return;
            }
            if (countIn(fresh, Zone.HAND) != 0) {
                helper.fail("Restart kept " + countIn(fresh, Zone.HAND) + " cards in the hand");
                return;
            }
            if (Tutorial.showing().orElse(null) != TutorialStep.DRAW) {
                helper.fail("Restart left the instruction on " + Tutorial.showing().orElse(null));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The first action after Restart is the one that satisfies the new first step.
     * <p>The defect this keeps out was reproduced against the old tutorial by an external
     * review: navigation cleared the baseline and let the next board set it, so the action the
     * player took to satisfy the step was spent establishing what it was measured against, and
     * they had to do it twice. Restart here throws the board away as well, which is a second
     * way to get the same thing wrong.
     */
    @GameTest(template = "empty")
    public static void thefirstdrawafterrestartadvancesthetutorial(GameTestHelper helper) {
        restoring(() -> {
            TutorialDemo.begin();
            TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            TutorialDemo.restart();
            if (Tutorial.showing().orElse(null) != TutorialStep.DRAW) {
                helper.fail("Restart did not put the first instruction back up");
                return;
            }
            TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            TutorialStep now = Tutorial.showing().orElse(null);
            if (now == TutorialStep.DRAW) {
                helper.fail("the first draw after Restart was spent on the baseline it was"
                        + " measured against: still showing DRAW");
                return;
            }
            if (now != TutorialStep.PLAY) {
                helper.fail("one draw after Restart moved the tutorial to " + now);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A real table's board cannot advance the demonstration.
     * <p>Somebody else's game carries on the whole time this is open, and its boards keep
     * arriving. None of them is what the player is being taught on, so none of them may count
     * as having done the thing being asked for.
     */
    @GameTest(template = "empty")
    public static void arealboardcannotadvancethedemonstration(GameTestHelper helper) {
        restoring(() -> {
            TutorialDemo.begin();
            TutorialStep before = Tutorial.showing().orElse(null);

            // The demonstration's own board, arriving as though it were a real table's. It is
            // the strongest form of the case: a board that would satisfy the step if it were
            // the right board, addressed to the wrong place.
            GameView board = TutorialDemo.board().orElseThrow();
            TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            GameView drawn = TutorialDemo.board().orElseThrow();
            Tutorial.restartOn(board);

            Tutorial.sawBoard(A_REAL_TABLE, drawn);

            if (Tutorial.showing().orElse(null) != before) {
                helper.fail("a board from a real table moved the demonstration's instruction from "
                        + before + " to " + Tutorial.showing().orElse(null));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * All six steps can be completed on the local board, in order, by the real verbs.
     * <p>The whole lesson, end to end, so that "the demonstration teaches the controls" is a
     * thing that has been run rather than a thing that was designed. Each step is satisfied by
     * the same event the table screen sends for that verb.
     */
    @GameTest(template = "empty")
    public static void allsixstepscompleteonthelocalboard(GameTestHelper helper) {
        restoring(() -> {
            TutorialDemo.begin();
            if (Tutorial.showing().orElse(null) != TutorialStep.DRAW) {
                helper.fail("the lesson did not open on DRAW");
                return;
            }

            TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            if (Tutorial.showing().orElse(null) != TutorialStep.PLAY) {
                helper.fail("drawing left the lesson on " + Tutorial.showing().orElse(null));
                return;
            }

            CardView.Visible inHand =
                    firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.HAND).orElse(null);
            if (inHand == null) {
                helper.fail("nothing visible in the hand after drawing one card");
                return;
            }
            TutorialDemo.submit(new GameEvent.CardMoved(LEARNER, inHand.id(),
                    ZoneRef.of(LEARNER, Zone.BATTLEFIELD), Placement.BOTTOM));
            if (Tutorial.showing().orElse(null) != TutorialStep.TAP) {
                helper.fail("playing left the lesson on " + Tutorial.showing().orElse(null));
                return;
            }

            CardView.Visible onTable =
                    firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.BATTLEFIELD)
                            .orElse(null);
            if (onTable == null) {
                helper.fail("nothing visible on the battlefield after playing a card");
                return;
            }
            TutorialDemo.submit(new GameEvent.CardTapSet(LEARNER, onTable.id(), true));
            if (Tutorial.showing().orElse(null) != TutorialStep.COUNT) {
                helper.fail("tapping left the lesson on " + Tutorial.showing().orElse(null));
                return;
            }

            TutorialDemo.submit(new GameEvent.CounterChanged(LEARNER, onTable.id(), "+1/+1", 1));
            if (Tutorial.showing().orElse(null) != TutorialStep.READ) {
                helper.fail("adding a counter left the lesson on "
                        + Tutorial.showing().orElse(null));
                return;
            }

            // Reading is the one step nothing about the game records, because nothing about it
            // changes the game. It is this client saying it showed somebody a card.
            Tutorial.readACard();
            if (Tutorial.showing().orElse(null) != TutorialStep.PASS) {
                helper.fail("reading a card left the lesson on " + Tutorial.showing().orElse(null));
                return;
            }

            GameView board = TutorialDemo.board().orElseThrow();
            TutorialDemo.submit(new GameEvent.TurnPassed(LEARNER,
                    board.nextSeatWithABoard(board.turn().activeSeat())));
            if (!Tutorial.progress().orElseThrow().isFinished()) {
                helper.fail("passing the turn did not finish the lesson; it is showing "
                        + Tutorial.showing().orElse(null));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The whole lesson, start to finish, puts nothing at all on the wire.
     * <p>The one that answers the acceptance question directly. The steps above check each
     * move; this checks that a player who sits down, learns the controls and walks away has
     * caused their client to say nothing to the server about any of it.
     */
    @GameTest(template = "empty")
    public static void thewholelessonsendsnothing(GameTestHelper helper) {
        restoring(() -> {
            List<CustomPacketPayload> sent = watchingTheWire(() -> {
                TutorialDemo.begin();
                BlockPos where = TutorialDemo.table();
                ClientTableActions.send(where, new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
                firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.HAND).ifPresent(card ->
                        ClientTableActions.send(where, new GameEvent.CardMoved(LEARNER, card.id(),
                                ZoneRef.of(LEARNER, Zone.BATTLEFIELD), Placement.BOTTOM)));
                firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.BATTLEFIELD)
                        .ifPresent(card -> {
                            ClientTableActions.send(where,
                                    new GameEvent.CardTapSet(LEARNER, card.id(), true));
                            ClientTableActions.send(where,
                                    new GameEvent.CounterChanged(LEARNER, card.id(), "+1/+1", 1));
                        });
                Tutorial.readACard();
                GameView board = TutorialDemo.board().orElseThrow();
                ClientTableActions.send(where, new GameEvent.TurnPassed(LEARNER,
                        board.nextSeatWithABoard(board.turn().activeSeat())));
                TutorialDemo.restart();
            });

            if (!sent.isEmpty()) {
                StringBuilder what = new StringBuilder();
                for (CustomPacketPayload payload : sent) {
                    what.append(' ').append(payload.type().id());
                }
                helper.fail("the guided first game sent " + sent.size() + " payload(s):" + what);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * An interrupted lesson is never written down as a finished one.
     * <p>The flag decides whether this is ever offered again, and the two facts it is built
     * from are not the same fact. Somebody who disconnected halfway through has not learned
     * the controls, and a mod that recorded them as having done so would have quietly decided
     * never to teach them.
     */
    @GameTest(template = "empty")
    public static void aninterruptedlessonisnotafinishedone(GameTestHelper helper) {
        restoring(() -> {
            ClientSettings.tutorialFinished(false);
            ClientSettings.tutorialSkipped(false);
            TutorialDemo.begin();
            TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));

            // The two things a disconnect does to the lesson. Not ClientState.forgetTheServer
            // itself, which is what actually runs them: it also empties holders that reach
            // into LocalPlayer, and a dedicated server cannot load that class at all. So this
            // covers the behavior and tools/statecheck.py covers the wiring - it fails the
            // build if a client holder with a clear() is not named in that method, which is
            // the half a test run on a server genuinely cannot see.
            Tutorial.clear();
            TutorialDemo.clear();

            if (ClientSettings.tutorialFinished()) {
                helper.fail("a lesson abandoned partway through was recorded as finished");
                return;
            }
            if (TutorialDemo.running()) {
                helper.fail("the demonstration survived a disconnect");
                return;
            }
            if (Tutorial.running()) {
                helper.fail("the instructions survived a disconnect");
                return;
            }
            helper.succeed();
        });
    }
    /**
     * The counter editor's own lookups find the lesson's board and seat, and its send lands.
     * <p>The lesson's fourth step is "put a counter on it", and the ordinary route to it is the
     * card menu's Counters row. That screen does not take a board; it looks one up by table
     * position through {@code ClientTableState}, and looks its seat up the same way. While
     * those answered only for real tables, the editor found no seat and sent nothing, and found
     * no board and closed itself on the next tick - so the step could not be completed the way
     * a player reaches it. An audit reproduced it in a real client: "sample +1/+1 counters=0;
     * expected 1".
     * <p>This drives the same seat resolution and the same send rather than the screen, because
     * neither a screen nor {@code ClientTableState} can be loaded on a dedicated server - both
     * reach {@code Screen}. So the lookup's own wiring is checked by the audit's client probe,
     * and what it decides - that a counter aimed at the lesson lands on the lesson and reaches
     * no server - is checked here.
     */
    @GameTest(template = "empty")
    public static void thecountereditorslookupsfindthelesson(GameTestHelper helper) {
        restoring(() -> {
            TutorialDemo.begin();
            BlockPos where = TutorialDemo.table();

            // The seat as a child screen resolves it: off the board's own viewer, which is
            // what ClientTableState.seatAt returns. Asked of the board directly because that
            // class cannot be loaded here - it reaches Screen - so the lookup itself is
            // checked by the audit's client probe and the behaviour it decides is checked here.
            SeatId seat = TutorialDemo.board()
                    .map(GameView::viewer)
                    .filter(dev.gathering.core.game.visibility.Viewer.Seated.class::isInstance)
                    .map(dev.gathering.core.game.visibility.Viewer.Seated.class::cast)
                    .map(dev.gathering.core.game.visibility.Viewer.Seated::seat)
                    .orElse(null);
            if (seat == null) {
                helper.fail("the lesson's board named no seat, so a child screen asking for one"
                        + " would send nothing");
                return;
            }
            if (!seat.equals(LEARNER)) {
                helper.fail("the lesson's seat came back as " + seat + " rather than the learner");
                return;
            }

            // Get a card onto the battlefield, the way the lesson does.
            TutorialDemo.submit(new GameEvent.CardsDrawn(LEARNER, LEARNER, 1));
            CardView.Visible inHand =
                    firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.HAND).orElse(null);
            if (inHand == null) {
                helper.fail("nothing in hand to play");
                return;
            }
            TutorialDemo.submit(new GameEvent.CardMoved(LEARNER, inHand.id(),
                    ZoneRef.of(LEARNER, Zone.BATTLEFIELD), Placement.BOTTOM));
            CardView.Visible onTable =
                    firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.BATTLEFIELD)
                            .orElse(null);
            if (onTable == null) {
                helper.fail("nothing on the battlefield to count");
                return;
            }

            // And exactly what the editor sends, through the production path.
            List<CustomPacketPayload> sent = watchingTheWire(() ->
                    ClientTableActions.send(where,
                            new GameEvent.CounterChanged(seat, onTable.id(), "+1/+1", 1)));

            if (!sent.isEmpty()) {
                helper.fail("the counter editor put " + sent.size() + " payload(s) on the wire"
                        + " from inside the lesson");
                return;
            }
            CardView.Visible after =
                    firstVisibleIn(TutorialDemo.board().orElseThrow(), Zone.BATTLEFIELD)
                            .orElse(null);
            int counters = after == null ? 0 : after.counters().getOrDefault("+1/+1", 0);
            if (counters != 1) {
                helper.fail("the sample card has " + counters + " +1/+1 counters; expected 1");
                return;
            }
            helper.succeed();
        });
    }
}