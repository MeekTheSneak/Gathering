package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Behavior the pre-game validator will lean on, checked against real printings. */
class CardMetadataTest {

    @Test
    @DisplayName("restricted imposes a one-copy ceiling, which is how Vintage needs no Vintage code")
    void restrictedMeansOneCopy() {
        CardMetadata solRing = Fixtures.card("sol_ring");

        assertThat(solRing.legalityIn("vintage")).isEqualTo(Legality.RESTRICTED);
        assertThat(solRing.legalityIn("vintage").copyCeiling()).isEqualTo(1);
        assertThat(solRing.legalityIn("vintage").playable()).isTrue();
        assertThat(solRing.legalityIn("commander")).isEqualTo(Legality.LEGAL);
        assertThat(solRing.legalityIn("commander").copyCeiling()).isEqualTo(-1);
    }

    @Test
    @DisplayName("the any-number exception is read off printed text, not a hardcoded list")
    void anyNumberIsReadFromOracleText() {
        assertThat(Fixtures.card("persistent_petitioners").allowsAnyNumber()).isTrue();
        assertThat(Fixtures.card("sol_ring").allowsAnyNumber()).isFalse();
    }

    @Test
    void doubleFacedCardsKeepBothFaces() {
        CardMetadata delver = Fixtures.card("delver_of_secrets");

        assertThat(delver.isDoubleFaced()).isTrue();
        assertThat(delver.faces()).hasSize(2);
        assertThat(delver.faces().get(0).name()).isEqualTo("Delver of Secrets");
        assertThat(delver.faces().get(1).name()).isEqualTo("Insectile Aberration");
        assertThat(delver.faces().get(0).imageUris().bestFor(ImageTier.NORMAL)).isPresent();
        assertThat(delver.faces().get(1).imageUris().bestFor(ImageTier.NORMAL)).isPresent();
    }

    @Test
    void singleFacedCardsStillHaveOneFace() {
        CardMetadata solRing = Fixtures.card("sol_ring");

        assertThat(solRing.isDoubleFaced()).isFalse();
        assertThat(solRing.frontFace()).isPresent();
        assertThat(solRing.images().bestFor(ImageTier.SMALL)).isPresent();
        assertThat(solRing.images().bestFor(ImageTier.NORMAL)).isPresent();
    }

    @Test
    void colorIdentityIsAvailableForTheCommanderCheck() {
        assertThat(Fixtures.card("halana_and_alena").colorIdentity()).containsExactlyInAnyOrder("G", "R");
        assertThat(Fixtures.card("sol_ring").colorIdentity()).isEmpty();
    }

    @Test
    void paperExistenceIsWhatTheCatalogFiltersOn() {
        assertThat(Fixtures.card("sol_ring").existsInPaper()).isTrue();
    }
}
