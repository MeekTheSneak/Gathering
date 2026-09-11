package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientCardFlights;
import dev.gathering.client.ClientSettings;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Asking for less motion actually stops the motion.
 * <p>{@code tools/prefcheck.py} proves every preference is read by production code, which is
 * the thing eight of them were not. What it cannot prove is that reading it does the right
 * thing, and most of these settings are about pixels - a sheen, a text size, a row height -
 * which a dedicated server cannot draw and so cannot check.
 * <p>This one can be checked, because the flights a card makes across the felt are decided
 * before anything is drawn: a board arrives, it is compared with the one before it, and
 * whatever changed places goes in the air. So "reduced motion means nothing goes in the air"
 * is a statement about a list, and a list can be counted.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReducedMotionGameTest {

    private static final SeatId SEAT = SeatId.of(0);

    /** Somewhere no real table is, so this cannot disturb another test's board. */
    private static final BlockPos NOWHERE_IN_PARTICULAR = new BlockPos(0, Integer.MIN_VALUE, 7);

    /** A little game with a few cards in a library, built where a server can build it. */
    private static GameSession aGameWithCardsInIt() {
        GameSession game = GameSession.create(
                List.of(SEAT), 20, SessionSeed.random(), UndoMode.shippedDefault());
        List<dev.gathering.core.card.CardIdentity> library = new ArrayList<>();
        for (int card = 0; card < 6; card++) {
            library.add(PaperStock.BLANK.identity());
        }
        game.submit(new GameEvent.DeckLoaded(SEAT, library, List.of(), null));
        return game;
    }

    private static GameView boardOf(GameSession game) {
        return VisibilityRules.viewFor(game.state(), new Viewer.Seated(SEAT));
    }

    /**
     * Moves one card somewhere visible, which is what a flight is made of.
     *
     * @return whether there was a card to move
     */
    private static boolean moveACard(GameSession game) {
        GameView board = boardOf(game);
        for (CardView card : board.seat(SEAT).zone(Zone.HAND).cards()) {
            if (card instanceof CardView.Visible visible) {
                game.submit(new GameEvent.CardMoved(SEAT, visible.id(),
                        ZoneRef.of(SEAT, Zone.BATTLEFIELD), Placement.BOTTOM));
                return true;
            }
        }
        return false;
    }

    /**
     * With motion on, a card that changes places is drawn crossing the felt; with it off, it
     * is simply where it went.
     * <p>Both halves in one test on purpose. A check that only proved the setting suppresses
     * flights would pass just as happily against a build where flights never happened at all,
     * which is the failure mode worth guarding: the setting has to be the reason.
     */
    @GameTest(template = "empty")
    public static void reducedmotionstopscardsflyingandnothingelsedoes(GameTestHelper helper) {
        boolean wanted = ClientSettings.reducedMotion();
        ClientCardFlights.clear();
        try {
            // --- motion on: a move goes in the air ---
            ClientSettings.reducedMotion(false);
            GameSession game = aGameWithCardsInIt();
            game.submit(new GameEvent.CardsDrawn(SEAT, SEAT, 3));
            long now = 1_000L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(game), now);
            if (!moveACard(game)) {
                helper.fail("the fixture had no card in hand to move");
                return;
            }
            now += 50L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(game), now);
            if (ClientCardFlights.at(NOWHERE_IN_PARTICULAR, now).isEmpty()) {
                helper.fail("with motion on, a card that changed places was not drawn moving, so"
                        + " the other half of this test would prove nothing");
                return;
            }

            // --- motion off: the same move goes nowhere ---
            ClientCardFlights.clear();
            ClientSettings.reducedMotion(true);
            GameSession quiet = aGameWithCardsInIt();
            quiet.submit(new GameEvent.CardsDrawn(SEAT, SEAT, 3));
            now += 1_000L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(quiet), now);
            if (!moveACard(quiet)) {
                helper.fail("the second fixture had no card in hand to move");
                return;
            }
            now += 50L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(quiet), now);
            if (!ClientCardFlights.at(NOWHERE_IN_PARTICULAR, now).isEmpty()) {
                helper.fail("reduced motion still put "
                        + ClientCardFlights.at(NOWHERE_IN_PARTICULAR, now).size()
                        + " card(s) in the air");
                return;
            }
            helper.succeed();
        } finally {
            ClientSettings.reducedMotion(wanted);
            ClientCardFlights.clear();
        }
    }

    /**
     * Turning it on mid-game lands whatever was already in the air.
     * <p>Otherwise the cards crossing the felt at the moment somebody ticks the box would hang
     * there until something else happened to the table - which is more motion, not less, and
     * exactly the sort of thing nobody thinks to check.
     */
    @GameTest(template = "empty")
    public static void turningitonlandswhatwasalreadyflying(GameTestHelper helper) {
        boolean wanted = ClientSettings.reducedMotion();
        ClientCardFlights.clear();
        try {
            ClientSettings.reducedMotion(false);
            GameSession game = aGameWithCardsInIt();
            game.submit(new GameEvent.CardsDrawn(SEAT, SEAT, 3));
            long now = 5_000L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(game), now);
            moveACard(game);
            now += 50L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(game), now);
            if (ClientCardFlights.at(NOWHERE_IN_PARTICULAR, now).isEmpty()) {
                helper.fail("the fixture put nothing in the air to begin with");
                return;
            }

            // The box is ticked, and the next board is the first thing that notices.
            ClientSettings.reducedMotion(true);
            moveACard(game);
            now += 50L;
            ClientCardFlights.arrived(NOWHERE_IN_PARTICULAR, boardOf(game), now);

            if (!ClientCardFlights.at(NOWHERE_IN_PARTICULAR, now).isEmpty()) {
                helper.fail("cards already crossing the felt kept crossing it after motion was"
                        + " turned off");
                return;
            }
            helper.succeed();
        } finally {
            ClientSettings.reducedMotion(wanted);
            ClientCardFlights.clear();
        }
    }
}
