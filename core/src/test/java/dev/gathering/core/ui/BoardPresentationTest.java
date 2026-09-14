package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameFixtures;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A board worked out once, and worked out the same way it was worked out every frame before.
 */
class BoardPresentationTest {

    @Test
    @DisplayName("asking twice about one view works it out once; a new view is worked out again")
    void keptByIdentity() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        onTheBattlefield(session, GameFixtures.ALICE, 3, 4000, 4000);
        BoardPresentation.Memo memo = new BoardPresentation.Memo(2);
        GameView first = viewOf(session);

        BoardPresentation once = memo.of(first);
        assertThat(memo.of(first)).isSameAs(once);

        // An equal board that arrived as a new object is a new board as far as this knows,
        // which is the safe direction: rebuilt for nothing, never stale.
        assertThat(memo.of(viewOf(session))).isNotSameAs(once);
    }

    @Test
    @DisplayName("several views are kept at once, so drawing two tables a frame rebuilds neither")
    void severalAtOnce() {
        GameSession one = GameFixtures.twoPlayerTable(20);
        GameSession two = GameFixtures.twoPlayerTable(20);
        GameView a = viewOf(one);
        GameView b = viewOf(two);
        BoardPresentation.Memo memo = new BoardPresentation.Memo(4);
        BoardPresentation forA = memo.of(a);
        BoardPresentation forB = memo.of(b);
        for (int frame = 0; frame < 10; frame++) {
            assertThat(memo.of(a)).isSameAs(forA);
            assertThat(memo.of(b)).isSameAs(forB);
        }
    }

    @Test
    @DisplayName("piles are the same as asking the stacking rules about the zone directly")
    void pilesMatchTheRules() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        onTheBattlefield(session, GameFixtures.ALICE, 3, 4000, 4000);
        onTheBattlefield(session, GameFixtures.BOB, 2, 7000, 2000);
        GameView view = viewOf(session);

        for (BoardPresentation.Mat mat : BoardPresentation.of(view).mats()) {
            List<CardView> cards = view.seat(mat.seat()).zone(Zone.BATTLEFIELD).cards();
            assertThat(mat.cards()).isEqualTo(cards);
            assertThat(mat.piles().depths())
                    .isEqualTo(TableStacking.depths(BoardPresentation.spotsIn(cards)));
        }
        BoardPresentation.Mat alice = BoardPresentation.of(view).mats().get(0);
        assertThat(alice.piles().pileSize(2)).isEqualTo(3);
    }

    @Test
    @DisplayName("a card attached to another does not make its host a pile")
    void attachedCardsAreNotCountedAtTheirOwnSpot() {
        // Both views ask this now. The board on the block used to count the aura at its own
        // spot - the same spot as its creature - and drew a lone creature as a pile of two.
        GameSession session = GameFixtures.twoPlayerTable(20);
        List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2, 4000, 4000);
        session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

        BoardPresentation.Mat mat = BoardPresentation.of(viewOf(session)).mats().get(0);
        for (int index = 0; index < mat.cards().size(); index++) {
            assertThat(mat.piles().pileSize(index)).as("pile at %d", index).isZero();
            assertThat(mat.piles().depth(index)).as("depth at %d", index).isZero();
        }
        assertThat(mat.attachments()).containsKey(cards.get(0));
    }

    @Test
    @DisplayName("finds exactly the cards the view shows, and no card it hides")
    void findsWhatTheViewShows() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        List<CardInstanceId> mine = onTheBattlefield(session, GameFixtures.ALICE, 2, 4000, 4000);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 3));
        GameView view = viewOf(session);
        BoardPresentation shown = BoardPresentation.of(view);

        for (CardView card : view.allCardViews()) {
            if (card instanceof CardView.Visible visible) {
                assertThat(shown.card(visible.id())).contains(card);
            }
        }
        assertThat(shown.card(mine.get(0))).isPresent();
        // Bob's hand is face down to Alice: no id in the view, so nothing to find it by.
        for (CardInstanceId hidden : session.state().contents(GameFixtures.BOB, Zone.HAND)) {
            assertThat(shown.card(hidden)).isEmpty();
        }
        assertThat(shown.card(null)).isEmpty();
    }

    private static GameView viewOf(GameSession session) {
        return VisibilityRules.viewFor(session.state(), new Viewer.Seated(GameFixtures.ALICE));
    }

    private static List<CardInstanceId> onTheBattlefield(
            GameSession session, SeatId seat, int count, int x, int y) {
        List<CardInstanceId> before = List.copyOf(session.state().contents(seat, Zone.HAND));
        session.submit(new GameEvent.CardsDrawn(seat, seat, count));
        List<CardInstanceId> drawn = new ArrayList<>(session.state().contents(seat, Zone.HAND));
        drawn.removeAll(before);
        for (CardInstanceId card : drawn) {
            session.submit(new GameEvent.CardMoved(
                    seat, card, ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.at(x, y)));
        }
        return drawn;
    }
}
