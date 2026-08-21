package dev.gathering.core.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.scryfall.ScryfallCardCodec;
import dev.gathering.core.testing.Fixtures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The deck check, against real printings.
 *
 * <p>Everything here runs before a game starts and never again. There is no test in this
 * class for anything happening during play, because nothing in the mod checks anything
 * during play.
 */
class DeckValidatorTest {

    private static final CardMetadata FOREST = Fixtures.card("forest");
    private static final CardMetadata SOL_RING = Fixtures.card("sol_ring");
    private static final CardMetadata BLACK_LOTUS = Fixtures.card("black_lotus");
    private static final CardMetadata PETITIONERS = Fixtures.card("persistent_petitioners");
    private static final CardMetadata HALANA_AND_ALENA = Fixtures.card("halana_and_alena");
    private static final CardMetadata THRASIOS = Fixtures.card("thrasios");
    private static final CardMetadata TEVESH_SZAT = Fixtures.card("tevesh_szat");
    /** Legal everywhere, not basic, not any-number, not restricted: plain four-of filler. */
    private static final CardMetadata DELVER = Fixtures.card("delver_of_secrets");

    @Nested
    @DisplayName("deck size")
    class DeckSize {

        @Test
        void aLegalCommanderDeckPasses() {
            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), pad(99)), FormatPresets.COMMANDER);

            assertThat(result.isLegal()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        void ninetyNineCardsPlusACommanderIsExactlyAHundred() {
            ValidationResult tooSmall = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), pad(98)), FormatPresets.COMMANDER);
            ValidationResult tooLarge = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), pad(100)), FormatPresets.COMMANDER);

            assertThat(codes(tooSmall)).contains("deck_too_small");
            assertThat(codes(tooLarge)).contains("deck_too_large");
        }

        @Test
        @DisplayName("sixty-card formats have a minimum and no maximum")
        void sixtyCardFormatsHaveNoCeiling() {
            assertThat(DeckValidator.validate(sixtyCardDeck(pad(59)), FormatPresets.MODERN).isLegal()).isFalse();
            assertThat(DeckValidator.validate(sixtyCardDeck(pad(250)), FormatPresets.MODERN).isLegal()).isTrue();
        }
    }

    @Nested
    @DisplayName("copy limits")
    class CopyLimits {

        @Test
        void singletonFormatsAllowOneOfEachNonBasic() {
            List<CardMetadata> deck = new ArrayList<>(pad(97));
            deck.add(SOL_RING);
            deck.add(SOL_RING);

            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), deck), FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("too_many_copies");
        }

        @Test
        @DisplayName("basic lands are exempt, which is what makes a singleton deck possible at all")
        void basicLandsAreUnlimited() {
            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), Collections.nCopies(99, FOREST)),
                    FormatPresets.COMMANDER);

            assertThat(codes(result)).doesNotContain("too_many_copies");
        }

        @Test
        @DisplayName("a card whose own text says any number is exempt, read off the card not a list")
        void anyNumberCardsAreExempt() {
            List<CardMetadata> deck = new ArrayList<>(Collections.nCopies(30, PETITIONERS));
            deck.addAll(pad(69));

            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), deck), FormatPresets.COMMANDER);

            assertThat(codes(result)).doesNotContain("too_many_copies");
        }

        @Test
        void sixtyCardFormatsAllowFour() {
            List<CardMetadata> legal = new ArrayList<>(Collections.nCopies(4, DELVER));
            legal.addAll(pad(56));
            List<CardMetadata> illegal = new ArrayList<>(Collections.nCopies(5, DELVER));
            illegal.addAll(pad(56));

            assertThat(codes(DeckValidator.validate(sixtyCardDeck(legal), FormatPresets.VINTAGE)))
                    .doesNotContain("too_many_copies");
            assertThat(codes(DeckValidator.validate(sixtyCardDeck(illegal), FormatPresets.VINTAGE)))
                    .contains("too_many_copies");
        }

        @Test
        @DisplayName("restricted means at most one, which is the whole of Vintage handled for free")
        void restrictedCardsAreLimitedToOne() {
            List<CardMetadata> one = new ArrayList<>(List.of(BLACK_LOTUS));
            one.addAll(pad(59));
            List<CardMetadata> two = new ArrayList<>(List.of(BLACK_LOTUS, BLACK_LOTUS));
            two.addAll(pad(58));

            assertThat(DeckValidator.validate(sixtyCardDeck(one), FormatPresets.VINTAGE).isLegal()).isTrue();
            assertThat(codes(DeckValidator.validate(sixtyCardDeck(two), FormatPresets.VINTAGE)))
                    .contains("too_many_copies");
        }

        @Test
        @DisplayName("copies are counted per card, not per printing")
        void twoPrintingsOfOneCardAreTwoCopies() {
            JsonObject otherPrinting = Fixtures.json("sol_ring");
            otherPrinting.addProperty("id", "00000000-0000-4000-8000-0000000000ff");
            otherPrinting.addProperty("set", "cmr");
            CardMetadata secondPrinting = ScryfallCardCodec.parse(otherPrinting).orElseThrow();

            List<CardMetadata> deck = new ArrayList<>(pad(97));
            deck.add(SOL_RING);
            deck.add(secondPrinting);

            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), deck), FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("too_many_copies");
        }
    }

    @Nested
    @DisplayName("legality")
    class LegalityChecks {

        @Test
        void bannedCardsAreRejected() {
            List<CardMetadata> deck = new ArrayList<>(List.of(BLACK_LOTUS));
            deck.addAll(pad(98));

            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), deck), FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("card_not_legal");
            assertThat(result.errors()).anySatisfy(issue ->
                    assertThat(issue.message()).contains("Black Lotus").contains("banned"));
        }

        @Test
        @DisplayName("a card we have no legality data for is a warning, not a rejection")
        void missingLegalityDataWarnsRatherThanBlocks() {
            JsonObject unknown = Fixtures.json("sol_ring");
            unknown.addProperty("id", "00000000-0000-4000-8000-0000000000ee");
            unknown.remove("legalities");
            CardMetadata card = ScryfallCardCodec.parse(unknown).orElseThrow();

            List<CardMetadata> deck = new ArrayList<>(List.of(card));
            deck.addAll(pad(98));

            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), deck), FormatPresets.COMMANDER);

            assertThat(result.isLegal()).isTrue();
            assertThat(codes(result)).contains("legality_unknown");
        }
    }

    @Nested
    @DisplayName("commanders")
    class Commanders {

        @Test
        @DisplayName("a planeswalker that says it can be your commander is eligible")
        void canBeYourCommanderTextIsEnough() {
            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(TEVESH_SZAT), Collections.nCopies(99, swampish(FOREST))),
                    FormatPresets.COMMANDER);

            assertThat(codes(result)).doesNotContain("commander_ineligible");
        }

        @Test
        void anOrdinaryCardCannotLeadADeck() {
            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(SOL_RING), pad(99)), FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("commander_ineligible");
        }

        @Test
        @DisplayName("two commanders need Partner on both")
        void partnerPairingIsChecked() {
            List<CardMetadata> deck = Collections.nCopies(98, swampish(FOREST));

            ValidationResult paired = DeckValidator.validate(
                    commanderDeck(List.of(THRASIOS, TEVESH_SZAT), deck), FormatPresets.COMMANDER);
            ValidationResult unpaired = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA, THRASIOS), deck), FormatPresets.COMMANDER);

            assertThat(codes(paired)).doesNotContain("commander_pairing");
            assertThat(codes(unpaired)).contains("commander_pairing");
        }

        @Test
        void aCommanderDeckNeedsACommander() {
            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(), pad(100)), FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("commander_count");
        }

        @Test
        @DisplayName("Oathbreaker wants a planeswalker and a signature instant or sorcery")
        void oathbreakerSlotsAreDifferentFromEachOther() {
            CardMetadata signatureSpell = retyped(SOL_RING, "Sorcery");

            ValidationResult right = DeckValidator.validate(
                    new ValidatableDeck("d", pad(58), List.of(TEVESH_SZAT, signatureSpell), List.of()),
                    FormatPresets.OATHBREAKER);
            ValidationResult wrongWayRound = DeckValidator.validate(
                    new ValidatableDeck("d", pad(58), List.of(signatureSpell, TEVESH_SZAT), List.of()),
                    FormatPresets.OATHBREAKER);

            assertThat(codes(right)).doesNotContain("commander_ineligible");
            assertThat(codes(wrongWayRound)).contains("commander_ineligible");
        }
    }

    @Nested
    @DisplayName("colour identity")
    class ColourIdentity {

        @Test
        @DisplayName("a card outside the commander's colours is rejected")
        void containmentIsChecked() {
            List<CardMetadata> deck = new ArrayList<>(List.of(FOREST));
            deck.addAll(Collections.nCopies(98, swampish(FOREST)));

            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(TEVESH_SZAT), deck), FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("colour_identity");
            assertThat(result.errors()).anySatisfy(issue ->
                    assertThat(issue.message()).contains("Forest"));
        }

        @Test
        void insideTheIdentityIsFine() {
            ValidationResult result = DeckValidator.validate(
                    commanderDeck(List.of(HALANA_AND_ALENA), Collections.nCopies(99, FOREST)),
                    FormatPresets.COMMANDER);

            assertThat(codes(result)).doesNotContain("colour_identity");
        }

        @Test
        @DisplayName("sixty-card formats have no commander, so no identity to contain")
        void nonCommanderFormatsSkipTheCheck() {
            ValidationResult result = DeckValidator.validate(sixtyCardDeck(pad(60)), FormatPresets.MODERN);

            assertThat(codes(result)).doesNotContain("colour_identity");
        }
    }

    @Nested
    @DisplayName("sideboards")
    class Sideboards {

        @Test
        void fifteenIsFineAndSixteenIsNot() {
            assertThat(DeckValidator.validate(
                    new ValidatableDeck("d", pad(60), List.of(), pad(15)), FormatPresets.MODERN).isLegal()).isTrue();
            assertThat(codes(DeckValidator.validate(
                    new ValidatableDeck("d", pad(60), List.of(), pad(16)), FormatPresets.MODERN)))
                    .contains("sideboard_too_large");
        }

        @Test
        void commanderHasNoSideboard() {
            ValidationResult result = DeckValidator.validate(
                    new ValidatableDeck("d", pad(99), List.of(HALANA_AND_ALENA), pad(5)),
                    FormatPresets.COMMANDER);

            assertThat(codes(result)).contains("no_sideboard");
        }
    }

    @Test
    @DisplayName("every shipping preset is well formed")
    void shippingPresets() {
        assertThat(FormatPresets.all()).hasSize(8);
        assertThat(FormatPresets.byId("commander")).contains(FormatPresets.COMMANDER);
        assertThat(FormatPresets.byId("COMMANDER")).contains(FormatPresets.COMMANDER);
        assertThat(FormatPresets.byId("brawl")).isEmpty();
        assertThat(FormatPresets.COMMANDER.startingLife()).isEqualTo(40);
        assertThat(FormatPresets.MODERN.startingLife()).isEqualTo(20);
        assertThat(FormatPresets.all()).allSatisfy(preset ->
                assertThat(preset.legalitiesKey()).isNotBlank());
    }

    // ------------------------------------------------------------- fixtures

    private static ValidatableDeck commanderDeck(List<CardMetadata> commanders, List<CardMetadata> mainboard) {
        return new ValidatableDeck("Test deck", mainboard, commanders, List.of());
    }

    private static ValidatableDeck sixtyCardDeck(List<CardMetadata> mainboard) {
        return new ValidatableDeck("Test deck", mainboard, List.of(), List.of());
    }

    /** Filler that never trips a copy limit or a colour check: basic lands, in two colours. */
    private static List<CardMetadata> pad(int count) {
        List<CardMetadata> cards = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cards.add(FOREST);
        }
        return cards;
    }

    /** A colourless stand-in, so a mono-black commander's deck has something legal in it. */
    private static CardMetadata swampish(CardMetadata basic) {
        JsonObject json = Fixtures.json("forest");
        json.addProperty("id", "00000000-0000-4000-8000-0000000000aa");
        json.add("color_identity", new com.google.gson.JsonArray());
        return ScryfallCardCodec.parse(json).orElseThrow();
    }

    private static CardMetadata retyped(CardMetadata card, String typeLine) {
        JsonObject json = Fixtures.json("sol_ring");
        json.addProperty("id", "00000000-0000-4000-8000-0000000000bb");
        json.addProperty("type_line", typeLine);
        return ScryfallCardCodec.parse(json).orElseThrow();
    }

    private static List<String> codes(ValidationResult result) {
        return result.issues().stream().map(ValidationIssue::code).toList();
    }
}
