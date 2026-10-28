package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.GameFixtures;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the picture push offers a viewer, over a table with real and custom cards on it.
 * <p>The safety half - only printings the view already revealed - is asserted by the
 * visibility suite. This checks the other half: the set it hands the sender has to be one the
 * sender can actually use.
 */
class ArtToSendTest {

    @Test
    @DisplayName("a custom card on the table does not put a null among the printings")
    void aCustomCardIsPassedOverNotSentAsNull() {
        // A custom card has no Scryfall printing, so its identity's scryfallId is null. That
        // null used to ride along into the wanted set, and the send remembers what it sent
        // in a ConcurrentHashMap-backed set - which refuses nulls. One face-up custom card
        // blew up the push on the server thread, and every picture at the table with it.
        GameSession session = GameSession.create(
                List.of(GameFixtures.ALICE), 40, GameFixtures.FIXED_SEED,
                dev.gathering.core.game.UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(GameFixtures.ALICE,
                new dev.gathering.core.game.PlayerRef(UUID.randomUUID(), "Alice")));
        session.submit(new GameEvent.DeckLoaded(GameFixtures.ALICE, List.of(
                GameFixtures.card(1),
                CardIdentity.ofCustom("my-own-dragon", false)), List.of()));
        // Both onto the battlefield face up, where the view names them.
        for (var card : session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)
                .stream().toList()) {
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.TOP));
        }

        GameView view = VisibilityRules.viewFor(session.state(), Viewer.SPECTATOR);
        Set<UUID> wanted = ArtToSend.wanted(view, Set.of());

        assertThat(wanted).doesNotContainNull();
        assertThat(wanted).containsExactly(GameFixtures.card(1).scryfallId());
    }
}
