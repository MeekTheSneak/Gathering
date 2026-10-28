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
        assertThat(Fixtures.card("persistent_petitioners").printedCopyAllowance())
                .hasValue(CardMetadata.ANY_NUMBER);
        assertThat(Fixtures.card("sol_ring").printedCopyAllowance()).isEmpty();
    }

    @Test
    @DisplayName("Seven Dwarves' \"up to seven\" is an allowance of seven, not a violation")
    void upToSevenIsReadFromOracleText() {
        // Matching only the any-number phrase made the deck check report a legal 7x Seven
        // Dwarves deck as too many copies - a rules violation that does not exist, from the
        // one screen whose job is to be right about that.
        CardMetadata dwarves = withOracleText(
                "A deck can have up to seven cards named Seven Dwarves.");
        CardMetadata nazgul = withOracleText(
                "A deck can have up to nine cards named Nazgûl.");

        assertThat(dwarves.printedCopyAllowance()).hasValue(7);
        assertThat(nazgul.printedCopyAllowance()).hasValue(9);
    }

    @Test
    @DisplayName("a spell with a land on its back is not a land - the front face decides")
    void theFrontFaceDecidesWhatACardIs() {
        // Scryfall's card-level type line joins both faces, so Malakir Rebirth reads
        // "Instant // Land". A word search over that made a land of it, and cascade - whose
        // stop rule is "first nonland" - sailed straight past a card it should stop on.
        CardMetadata modalSpellLand = withFaces("Instant // Land",
                new CardFace("Malakir Rebirth", "{B}", "Instant",
                        null, null, null, null, null, null, ImageUris.EMPTY),
                new CardFace("Malakir Mire", null, "Land",
                        null, null, null, null, null, null, ImageUris.EMPTY));

        assertThat(modalSpellLand.isLand()).isFalse();
        assertThat(modalSpellLand.isBasicLand()).isFalse();
    }

    private static CardMetadata withOracleText(String oracleText) {
        return new CardMetadata(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "A Card", "{1}", 1, "Creature", oracleText, null, null, null, "normal",
                "tst", "Test", "1", Rarity.COMMON, false, true, true, false, false,
                java.util.List.of("paper"), null, null, null);
    }

    private static CardMetadata withFaces(String combinedTypeLine, CardFace... faces) {
        return new CardMetadata(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "A Card", null, 1, combinedTypeLine, null, null, null,
                java.util.List.of(faces), "modal_dfc", "tst", "Test", "1", Rarity.COMMON,
                false, true, true, false, false, java.util.List.of("paper"), null, null, null);
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
