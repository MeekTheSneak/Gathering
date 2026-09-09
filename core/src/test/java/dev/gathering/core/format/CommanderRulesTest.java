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
        CardMetadata alone = simple("Legendary Creature — Human");

        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(alone))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(partner, partner))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(partner, alone))).isFalse();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(alone, alone))).isFalse();
        assertThat(CommanderRules.OATHBREAKER.allowsPairing(List.of(alone))).isFalse();
        assertThat(CommanderRules.OATHBREAKER.allowsPairing(List.of(alone, alone))).isTrue();
        assertThat(CommanderRules.NONE.allowsPairing(List.of())).isTrue();
        assertThat(CommanderRules.NONE.allowsPairing(List.of(alone))).isFalse();
    }

    /**
     * Each printed way of leading a deck with two cards, and what it actually pairs with.
     * <p>These were one rule - "the word Partner appears somewhere" - and an audit reproduced
     * what that accepts: two cards whose Partner-with clauses name different people, which no
     * table would allow and which the mod's own deck check called legal.
     */
    @Test
    @DisplayName("each pairing mechanic pairs with what it names and nothing else")
    void eachMechanicPairsWithItsOwn() {
        CardMetadata thrasios = pairing("Thrasios, Triton Hero", "Partner");
        CardMetadata tymna = pairing("Tymna the Weaver", "Partner");
        CardMetadata hanna = pairing("Hanna, Ship's Navigator",
                "Partner with Sidar Jabari (When this creature enters, target opponent may put"
                        + " Sidar Jabari into their hand from their library.)");
        CardMetadata sidar = pairing("Sidar Jabari", "Partner with Hanna, Ship's Navigator");
        CardMetadata wrongHalf = pairing("Somebody Else", "Partner with A Third Person");

        // A named pair goes together, and neither half goes with anybody else.
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(hanna, sidar))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(sidar, hanna))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(hanna, wrongHalf))).isFalse();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(hanna, thrasios))).isFalse();

        // The bare keyword pairs with the bare keyword.
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(thrasios, tymna))).isTrue();
    }

    @Test
    @DisplayName("Friends forever, Backgrounds and the Doctor pair with their own kind")
    void theOtherThreeMechanics() {
        CardMetadata friend = pairing("Amy Pond", "Friends forever (You can have two commanders"
                + " if both have friends forever.)");
        CardMetadata otherFriend = pairing("Rory Williams", "Friends forever");
        CardMetadata chooser = pairing("Wilson, Refined Grizzly", "Choose a Background");
        CardMetadata background = named("Criminal Past", "Legendary Enchantment — Background",
                "Commander creatures you own have deathtouch.");
        CardMetadata companion = pairing("Rose Tyler", "Doctor's companion (You can have two"
                + " commanders if the other is the Doctor.)");
        CardMetadata doctor = named("The Tenth Doctor", "Legendary Creature — Time Lord Doctor", null);
        CardMetadata partner = pairing("Thrasios, Triton Hero", "Partner");

        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(friend, otherFriend))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(friend, partner))).isFalse();

        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(chooser, background))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(background, background))).isFalse();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(chooser, partner))).isFalse();

        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(companion, doctor))).isTrue();
        assertThat(CommanderRules.COMMANDER.allowsPairing(List.of(companion, partner))).isFalse();
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

    /** A legendary creature with a name and a pairing line, which is what these are about. */
    private static CardMetadata pairing(String name, String oracleText) {
        return named(name, "Legendary Creature — Human", oracleText);
    }

    private static CardMetadata named(String name, String typeLine, String oracleText) {
        return new CardMetadata(UUID.randomUUID(), UUID.randomUUID(), name, null, 1,
                typeLine, oracleText, null, null, List.of(),
                "normal", "tst", "Test", "1",
                Rarity.COMMON, false, true, true, false, false, List.of("paper"),
                null, null, null);
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
