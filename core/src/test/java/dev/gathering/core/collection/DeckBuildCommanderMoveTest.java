package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Moving a card into the command zone moves one card.
 * <p>{@link DeckBuild#moved} took the card out of the pile it came from and then handed it to
 * {@link DeckBuild#led}, which took a copy out again. A build holding two copies of one printing lost
 * one of them the moment the player moved the other into the command zone, which is the deck builder's
 * own "Move to commanders" row menu entry - and a sideboard copy promoted ate the deck's copy instead.
 */
@DisplayName("Moving a card into the command zone")
class DeckBuildCommanderMoveTest {

    @Test
    @DisplayName("moving one copy to the command zone must not eat the other copy")
    void movingToTheCommandZoneEatsASecondCopy() {
        BuildCard bolt = new BuildCard(UUID.randomUUID(), UUID.randomUUID(), "Lightning Bolt",
                "Instant", "", 1, Set.of(), false);
        DeckBuild two = DeckBuild.EMPTY.with(bolt).with(bolt);
        assertThat(two.printingsOf(bolt.printing())).isEqualTo(2);

        DeckBuild led = two.moved(bolt, DeckBuild.Pile.MAINBOARD, DeckBuild.Pile.COMMANDERS);

        // One in the command zone, one left in the deck: three cards were never picked.
        assertThat(led.commander()).contains(bolt);
        assertThat(led.total()).isEqualTo(2);
        assertThat(led.printingsOf(bolt.printing())).isEqualTo(1);
    }

    @Test
    @DisplayName("moving a sideboard copy to the command zone must not eat the deck's copy")
    void movingASideboardCopyEatsTheDecksCopy() {
        BuildCard sol = new BuildCard(UUID.randomUUID(), UUID.randomUUID(), "Sol Ring",
                "Artifact", "", 1, Set.of(), false);
        DeckBuild both = DeckBuild.EMPTY.with(sol).aside(sol);

        DeckBuild led = both.moved(sol, DeckBuild.Pile.SIDEBOARD, DeckBuild.Pile.COMMANDERS);

        assertThat(led.commander()).contains(sol);
        assertThat(led.cards()).hasSize(1);
        assertThat(led.total()).isEqualTo(2);
    }
}
