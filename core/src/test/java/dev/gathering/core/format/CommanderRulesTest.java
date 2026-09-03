package dev.gathering.core.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What is allowed to sit in a command zone.
 * <p>This had no tests at all, which is how it came to be asking what a card is with its own
 * {@code contains} over the card's whole joined type line. Scryfall writes a double-faced
 * card's types as one string - "Instant // Land" - and a card is its front face everywhere
 * but the battlefield, so a search over the joined line answers for the back as well.
 * {@link CardMetadata} had that written down and reads the front face; this file had the same
 * rule written a second time, without the comment, and got it wrong.
 */
class CommanderRulesTest {

    @Test
    @DisplayName("a legendary creature may lead a commander deck")
    void aLegendaryCreatureLeads() {
        assertThat(CommanderRules.COMMANDER.isEligible(
                simple("Legendary Creature — Human Wizard"), 0)).isTrue();
        assertThat(CommanderRules.COMMANDER.isEligible(
                simple("Legendary Artifact Creature — Golem"), 0)).isTrue();
    }

    @Test
    @DisplayName("and something that is only one half of that does not")
    void halfOfItIsNotEnough() {
        assertThat(CommanderRules.COMMANDER.isEligible(
                simple("Creature — Bear"), 0)).isFalse();
        assertThat(CommanderRules.COMMANDER.isEligible(
                simple("Legendary Enchantment"), 0)).isFalse();
        assertThat(CommanderRules.COMMANDER.isEligible(simple("Land"), 0)).isFalse();
    }

    /**
     * The reason this file exists.
     * <p>A card whose front face is a land and whose back is a legendary creature has a
     * joined line reading "Land // Legendary Creature". Both words are in it, so a
     * {@code contains} over the whole string made it a legal commander - and what a deck
     * leads with is its front face.
     */
    @Test
    @DisplayName("a card is its front face, not the two halves of its type line joined")
    void theBackFaceDoesNotMakeACommander() {
        CardMetadata landWithALegendBehindIt = doubleFaced(
                "Land // Legendary Creature — Spirit",
                "Land",
                "Legendary Creature — Spirit");

        assertThat(CommanderRules.COMMANDER.isEligible(landWithALegendBehindIt, 0)).isFalse();
    }

    @Test
    @DisplayName("and a legend on the front still leads, whatever is on the back")
    void theFrontFaceStillCounts() {
        CardMetadata legendThatTransforms = doubleFaced(
                "Legendary Creature — God // Legendary Enchantment Artifact",
                "Legendary Creature — God",
                "Legendary Enchantment Artifact");

        assertThat(CommanderRules.COMMANDER.isEligible(legendThatTransforms, 0)).isTrue();
    }

    @Test
    @DisplayName("a card that says it can be your commander may, whatever its type line says")
    void sayingSoIsEnough() {
        CardMetadata says = withText("Creature — Human Peasant",
                "Thraben Standard Bearer can be your commander.");

        assertThat(CommanderRules.COMMANDER.isEligible(says, 0)).isTrue();
    }

    @Test
    @DisplayName("Oathbreaker's two slots are different from each other")
    void oathbreakerWantsOneOfEach() {
        CardMetadata walker = simple("Legendary Planeswalker — Teferi");
        CardMetadata spell = simple("Instant");
        CardMetadata creature = simple("Legendary Creature — Human");

        assertThat(CommanderRules.OATHBREAKER.isEligible(walker, 0)).isTrue();
        assertThat(CommanderRules.OATHBREAKER.isEligible(spell, 0)).isFalse();
        assertThat(CommanderRules.OATHBREAKER.isEligible(spell, 1)).isTrue();
        assertThat(CommanderRules.OATHBREAKER.isEligible(simple("Sorcery"), 1)).isTrue();
        assertThat(CommanderRules.OATHBREAKER.isEligible(creature, 1)).isFalse();
    }

    @Test
    @DisplayName("a format with no command zone has nothing eligible for one")
    void noCommandZoneMeansNoCommanders() {
        assertThat(CommanderRules.NONE.isEligible(simple("Legendary Creature — Human"), 0))
                .isFalse();
        assertThat(CommanderRules.NONE.inUse()).isFalse();
        assertThat(CommanderRules.COMMANDER.inUse()).isTrue();
    }

    @Test
    @DisplayName("two commanders are only allowed when both of them say Partner")
    void pairingNeedsBothToSaySo() {
        CardMetadata partner = withText("Legendary Creature — Human",
                "Partner (You can have two commanders if both have partner.)");
        CardMetadata partnerWith = withText("Legendary Creature — Human",
                "Partner with Somebody Else");
        CardMetadata alone = simple("Legendary Creature — Human");

        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(alone))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(partner, partnerWith)))
                .isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(partner, alone))).isFalse();
        assertThat(CommanderRules.OATHBREAKER.allowsPairing(List.of(alone))).isFalse();
        assertThat(CommanderRules.OATHBREAKER.allowsPairing(List.of(alone, alone))).isTrue();
        assertThat(CommanderRules.NONE.allowsPairing(List.of())).isTrue();
        assertThat(CommanderRules.NONE.allowsPairing(List.of(alone))).isFalse();
    }

    @Test
    @DisplayName("what a slot wants is said in words a player can read")
    void everySlotSaysWhatItWants() {
        for (CommanderRules rules : CommanderRules.values()) {
            for (int slot = 0; slot < 2; slot++) {
                assertThat(rules.describeEligibility(slot))
                        .describedAs("%s slot %s", rules, slot)
                        .isNotBlank();
            }
        }
    }

    private static CardMetadata simple(String typeLine) {
        return withText(typeLine, null);
    }

    private static CardMetadata withText(String typeLine, String oracleText) {
        return card(typeLine, oracleText, List.of());
    }

    private static CardMetadata doubleFaced(String joined, String front, String back) {
        return card(joined, null, List.of(face(front), face(back)));
    }

    private static CardFace face(String typeLine) {
        return new CardFace("A Face", null, typeLine, null, null, null, null, null, null, null);
    }

    private static CardMetadata card(String typeLine, String oracleText, List<CardFace> faces) {
        return new CardMetadata(UUID.randomUUID(), UUID.randomUUID(), "A Card", null, 1,
                typeLine, oracleText, null, null, faces,
                faces.size() > 1 ? "modal_dfc" : "normal", "tst", "Test", "1",
                Rarity.COMMON, false, true, true, false, false, List.of("paper"),
                null, null, null);
    }
}
