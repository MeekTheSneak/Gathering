package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
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
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The board as it crosses the wire.
 *
 * <p>Two things have to hold, and the second is the one that matters. A view has to survive
 * the round trip unchanged, or clients draw a board nobody is playing on. And it has to
 * survive it <em>without gaining anything</em>: a codec that turned a count-only zone into a
 * list, or an anonymous card into a visible one, would hand a client exactly the secret the
 * visibility rules had just taken away, and it would look like a working game.
 */
class ViewCodecTest {

    @Property(tries = 500)
    void everyViewSurvivesTheWire(@ForAll("games") GameSession session) throws IOException {
        for (Viewer viewer : viewers(session)) {
            GameView view = VisibilityRules.viewFor(session.state(), viewer);

            assertThat(ViewCodec.read(ViewCodec.write(view))).isEqualTo(view);
        }
    }

    @Property(tries = 500)
    void nothingGainsIdentityOnTheWay(@ForAll("games") GameSession session) throws IOException {
        for (Viewer viewer : viewers(session)) {
            GameView view = VisibilityRules.viewFor(session.state(), viewer);

            GameView arrived = ViewCodec.read(ViewCodec.write(view));

            assertThat(identitiesIn(arrived))
                    .describedAs("a card became knowable in transit for %s", viewer)
                    .isEqualTo(identitiesIn(view));
        }
    }

    @Property(tries = 500)
    void aCountedZoneStaysACountedZone(@ForAll("games") GameSession session) throws IOException {
        // An opponent's hand is a number. A codec that wrote an empty card list instead would
        // make it indistinguishable from a zone that is genuinely empty, and the obvious next
        // step is to "fix" that by sending redacted entries.
        for (Viewer viewer : viewers(session)) {
            GameView view = VisibilityRules.viewFor(session.state(), viewer);
            GameView arrived = ViewCodec.read(ViewCodec.write(view));

            for (var seat : view.seats()) {
                for (var entry : seat.zones().entrySet()) {
                    assertThat(arrived.seat(seat.seat()).zone(entry.getKey()).isCountOnly())
                            .describedAs("%s of seat %s changed shape", entry.getKey(), seat.seat())
                            .isEqualTo(entry.getValue().isCountOnly());
                }
            }
        }
    }

    @Test
    @DisplayName("a face-down card arrives as a marker and nothing else")
    void faceDownCardsStayAnonymous() throws IOException {
        GameSession session = GameFixtures.twoPlayerTable(20);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 3));
        CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));

        GameView opponent = VisibilityRules.viewFor(session.state(), new Viewer.Seated(GameFixtures.BOB));
        GameView arrived = ViewCodec.read(ViewCodec.write(opponent));

        List<CardView> battlefield = arrived.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards();
        assertThat(battlefield).isNotEmpty();
        assertThat(battlefield).allSatisfy(view ->
                assertThat(view).isInstanceOf(CardView.Anonymous.class));
    }

    private static List<Viewer> viewers(GameSession session) {
        return List.of(
                new Viewer.Seated(GameFixtures.ALICE),
                new Viewer.Seated(GameFixtures.BOB),
                new Viewer.Spectator());
    }

    /** Every card identity this view lets its holder know, as strings. */
    private static Set<String> identitiesIn(GameView view) {
        return view.allCardViews().stream()
                .filter(CardView::carriesIdentity)
                .map(CardView.Visible.class::cast)
                .map(visible -> visible.id() + "=" + visible.identity())
                .collect(Collectors.toSet());
    }

    /** Short scripted games, so the views under test are boards somebody could reach. */
    @Provide
    Arbitrary<GameSession> games() {
        return Arbitraries.integers().between(0, 9).list().ofMinSize(1).ofMaxSize(18)
                .map(script -> {
                    GameSession session = GameFixtures.twoPlayerTable(20);
                    script.forEach(action -> perform(session, action));
                    return session;
                });
    }

    private static void perform(GameSession session, int action) {
        SeatId actor = session.state().seats().get(action % session.state().seats().size());
        switch (action) {
            case 0 -> session.submit(new GameEvent.CardsDrawn(actor, actor, 1));
            case 1 -> session.submit(new GameEvent.LibraryShuffled(actor, actor));
            case 2 -> moveFromHand(session, actor, Zone.BATTLEFIELD);
            case 3 -> moveFromHand(session, actor, Zone.GRAVEYARD);
            case 4 -> moveFromHand(session, actor, Zone.EXILE);
            case 5 -> flip(session, actor);
            // Both of these put state on a card that crosses the wire, and one of them
            // deliberately survives a card going face down - so a codec that dropped either
            // has to fail here rather than in somebody's game.
            case 6 -> onABattlefieldCard(session, actor,
                    card -> new GameEvent.CardNoted(actor, card, "written on"));
            case 7 -> onABattlefieldCard(session, actor,
                    card -> new GameEvent.CardTurnedOver(actor, card, true));
            case 8 -> onABattlefieldCard(session, actor,
                    card -> new GameEvent.CardStrengthSet(actor, card, "6/6"));
            default -> session.submit(new GameEvent.LifeChanged(actor, actor, -1));
        }
    }

    private static void moveFromHand(GameSession session, SeatId seat, Zone destination) {
        List<CardInstanceId> hand = session.state().contents(seat, Zone.HAND);
        if (!hand.isEmpty()) {
            session.submit(new GameEvent.CardMoved(
                    seat, hand.get(0), ZoneRef.of(seat, destination), Placement.BOTTOM));
        }
    }

    private static void onABattlefieldCard(
            GameSession session, SeatId seat,
            java.util.function.Function<CardInstanceId, GameEvent> what) {
        List<CardInstanceId> onTheTable = session.state().contents(seat, Zone.BATTLEFIELD);
        if (!onTheTable.isEmpty()) {
            session.submit(what.apply(onTheTable.get(0)));
        }
    }

    private static void flip(GameSession session, SeatId seat) {
        for (CardInstanceId id : session.state().contents(seat, Zone.BATTLEFIELD)) {
            if (session.state().requireCard(id).facing() != Facing.FACE_DOWN) {
                session.submit(new GameEvent.CardFacingSet(seat, id, Facing.FACE_DOWN));
                return;
            }
        }
    }
}
