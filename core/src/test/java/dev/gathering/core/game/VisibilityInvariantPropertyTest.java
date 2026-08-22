package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The invariant, asserted over games nobody wrote down.
 *
 * <p>The example tests cover the situations somebody thought of. This one plays thousands of
 * arbitrary games - drawing, playing, flipping, stealing, exiling, shuffling, conceding in
 * whatever order the generator picks - and after every single action checks that no viewer
 * holds identity they are not entitled to.
 *
 * <p>This is the suite the design brief says must never regress, and it is the one most
 * likely to catch a regression, because the interesting sequences are the ones nobody would
 * write by hand.
 */
class VisibilityInvariantPropertyTest {

    private static final int ACTION_KINDS = 9;

    @Property(tries = 500)
    void noViewerEverHoldsIdentityTheyAreNotEntitledTo(@ForAll("actionScripts") List<Integer> script) {
        GameSession session = GameFixtures.twoPlayerTable(25);

        for (int action : script) {
            perform(session, action);
            assertInvariantHolds(session.state());
        }
    }

    @Property(tries = 500)
    void aSpectatorNeverLearnsMoreThanTheLeastInformedSeat(@ForAll("actionScripts") List<Integer> script) {
        GameSession session = GameFixtures.twoPlayerTable(25);

        for (int action : script) {
            perform(session, action);
        }

        GameState state = session.state();
        long spectatorIdentities = identityCount(VisibilityRules.viewFor(state, Viewer.SPECTATOR));
        for (SeatId seat : state.seats()) {
            assertThat(spectatorIdentities)
                    .as("spectator against %s", seat)
                    .isLessThanOrEqualTo(identityCount(VisibilityRules.viewFor(state, Viewer.seat(seat))));
        }
    }

    @Property(tries = 300)
    void hiddenZonesNeverSendPerCardPayloadsToOutsiders(@ForAll("actionScripts") List<Integer> script) {
        GameSession session = GameFixtures.twoPlayerTable(25);

        for (int action : script) {
            perform(session, action);
        }

        GameState state = session.state();
        for (Map.Entry<Viewer, GameView> entry : VisibilityRules.allViews(state).entrySet()) {
            Viewer viewer = entry.getKey();
            for (SeatId seat : state.seats()) {
                var seatView = entry.getValue().seat(seat);

                assertThat(seatView.zone(Zone.LIBRARY).cards())
                        .as("library payload for %s", viewer)
                        .isEmpty();

                if (!viewer.isSeatedAt(seat)) {
                    assertThat(seatView.zone(Zone.HAND).cards())
                            .as("hand payload for %s looking at %s", viewer, seat)
                            .isEmpty();
                }
            }
        }
    }

    /**
     * Counts and identities must stay consistent: a viewer who cannot see a hand still has to
     * be told how big it is, or the table stops being legible.
     */
    @Property(tries = 300)
    void countsAreAlwaysTruthfulEvenWhenContentsAreNot(@ForAll("actionScripts") List<Integer> script) {
        GameSession session = GameFixtures.twoPlayerTable(25);

        for (int action : script) {
            perform(session, action);
        }

        GameState state = session.state();
        for (GameView view : VisibilityRules.allViews(state).values()) {
            for (SeatId seat : state.seats()) {
                for (Zone zone : Zone.values()) {
                    assertThat(view.seat(seat).zone(zone).count())
                            .as("%s %s count", seat, zone)
                            .isEqualTo(state.count(ZoneRef.of(seat, zone)));
                }
            }
        }
    }

    /**
     * Auto-placement must never put two cards on one square.
     *
     * <p>A player may deliberately stack cards - the mod never says no - but a card the game
     * placed on a player's behalf landing under an existing one would look like a bug and
     * hide a permanent.
     */
    @Property(tries = 300)
    void automaticallyPlacedCardsNeverShareASquare(@ForAll("actionScripts") List<Integer> script) {
        GameSession session = GameFixtures.twoPlayerTable(25);

        for (int action : script) {
            perform(session, action);

            GameState state = session.state();
            for (SeatId seat : state.seats()) {
                List<dev.gathering.core.game.TablePosition> squares = state.contents(seat, Zone.BATTLEFIELD).stream()
                        .map(id -> state.requireCard(id).position())
                        .filter(java.util.Objects::nonNull)
                        .toList();
                assertThat(squares).as("squares on %s's battlefield", seat).doesNotHaveDuplicates();
            }
        }
    }

    /** Every card on a surface has a square, and every card in a pile has none. */
    @Property(tries = 300)
    void positionsExistExactlyWhereTheyShould(@ForAll("actionScripts") List<Integer> script) {
        GameSession session = GameFixtures.twoPlayerTable(25);

        for (int action : script) {
            perform(session, action);
        }

        GameState state = session.state();
        for (SeatId seat : state.seats()) {
            for (Zone zone : Zone.values()) {
                for (CardInstanceId id : state.contents(seat, zone)) {
                    assertThat(state.requireCard(id).square().isPresent())
                            .as("%s in %s", id, zone)
                            .isEqualTo(zone.isSurface());
                }
            }
        }
    }

    @Provide
    Arbitrary<List<Integer>> actionScripts() {
        return Arbitraries.integers().between(0, ACTION_KINDS - 1).list().ofMinSize(1).ofMaxSize(40);
    }

    /**
     * Interprets one number as a verb against the current board.
     *
     * <p>Actions that do not apply right now are skipped rather than forced, so a script is
     * always a plausible game rather than a pile of rejections.
     */
    private static void perform(GameSession session, int action) {
        GameState state = session.state();
        SeatId actor = state.seats().get(action % state.seats().size());
        SeatId other = state.seatAfter(actor);

        switch (action) {
            case 0 -> session.submit(new GameEvent.CardsDrawn(actor, actor, 1));
            case 1 -> session.submit(new GameEvent.LibraryShuffled(actor, actor));
            case 2 -> moveFromHand(session, actor, Zone.BATTLEFIELD);
            case 3 -> moveFromHand(session, actor, Zone.GRAVEYARD);
            case 4 -> moveFromHand(session, actor, Zone.EXILE);
            case 5 -> flipSomething(session, actor, Facing.FACE_DOWN);
            case 6 -> flipSomething(session, actor, Facing.FACE_UP);
            // Stealing: the card stays its owner's, and lands on somebody else's side.
            case 7 -> stealSomething(session, other, actor);
            case 8 -> session.submit(new GameEvent.LifeChanged(actor, actor, -1));
            default -> throw new IllegalStateException("Unhandled action " + action);
        }
    }

    private static void moveFromHand(GameSession session, SeatId seat, Zone destination) {
        List<CardInstanceId> hand = session.state().contents(seat, Zone.HAND);
        if (hand.isEmpty()) {
            return;
        }
        session.submit(new GameEvent.CardMoved(
                seat, hand.get(0), ZoneRef.of(seat, destination), Placement.BOTTOM));
    }

    private static void flipSomething(GameSession session, SeatId seat, Facing facing) {
        for (Zone zone : List.of(Zone.BATTLEFIELD, Zone.EXILE)) {
            for (CardInstanceId id : session.state().contents(seat, zone)) {
                if (session.state().requireCard(id).facing() != facing) {
                    session.submit(new GameEvent.CardFacingSet(seat, id, facing));
                    return;
                }
            }
        }
    }

    private static void stealSomething(GameSession session, SeatId thief, SeatId victim) {
        List<CardInstanceId> battlefield = session.state().contents(victim, Zone.BATTLEFIELD);
        if (battlefield.isEmpty()) {
            return;
        }
        session.submit(new GameEvent.CardMoved(
                thief, battlefield.get(0), ZoneRef.of(thief, Zone.BATTLEFIELD), Placement.BOTTOM));
    }

    private static void assertInvariantHolds(GameState state) {
        for (Map.Entry<Viewer, GameView> entry : VisibilityRules.allViews(state).entrySet()) {
            Viewer viewer = entry.getKey();
            for (CardView card : entry.getValue().allCardViews()) {
                if (card instanceof CardView.Visible visible) {
                    ZoneRef location = state.locationOf(visible.id()).orElseThrow();
                    CardInstance instance = state.requireCard(visible.id());

                    boolean entitled = switch (location.zone()) {
                        case LIBRARY -> false;
                        case HAND -> viewer.isSeatedAt(location.seat());
                        default -> !instance.isFaceDown() || viewer.isSeatedAt(instance.owner());
                    };

                    assertThat(entitled)
                            .as("%s received identity for %s in %s", viewer, visible.id(), location)
                            .isTrue();
                }
            }
        }
    }

    private static long identityCount(GameView view) {
        return view.allCardViews().stream().filter(CardView::carriesIdentity).count();
    }
}
