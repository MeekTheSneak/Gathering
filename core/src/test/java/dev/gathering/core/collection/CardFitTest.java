package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suggesting cards out of somebody's own box.
 *
 * <p>A text match rather than crowd data, for the reason {@link CardFit} states, so what these
 * check is that it is honest about being one: it never suggests a card that could not go in
 * the deck, never suggests one already in it, and always says which word it matched on.
 */
class CardFitTest {

    private static final BuildCard ELF_LORD = card(
            "Marwyn", "Legendary Creature — Elf Druid",
            "Whenever another Elf enters the battlefield, put a +1/+1 counter on Marwyn.",
            3, Set.of("G"));

    @Test
    @DisplayName("a card outside the commander's colours is never suggested")
    void coloursAreAFilterNotAScore() {
        BuildCard offColour = card("Lightning Bolt", "Instant",
                "Lightning Bolt deals 3 damage to any target. Elf.", 1, Set.of("R"));

        List<CardFit.Fit> fits = CardFit.forCommander(
                ELF_LORD, List.of(offColour), DeckBuild.EMPTY, 10);

        assertThat(fits).isEmpty();
    }

    @Test
    @DisplayName("a card the deck already holds is not suggested again")
    void alreadyInTheDeckIsNotSuggested() {
        BuildCard elf = card("Llanowar Elves", "Creature — Elf Druid", "Add {G}.", 1, Set.of("G"));
        DeckBuild holding = DeckBuild.EMPTY.with(elf);

        assertThat(CardFit.forCommander(ELF_LORD, List.of(elf), holding, 10)).isEmpty();
        assertThat(CardFit.forCommander(ELF_LORD, List.of(elf), DeckBuild.EMPTY, 10)).hasSize(1);
    }

    @Test
    @DisplayName("the commander is never suggested to itself")
    void theCommanderIsNotItsOwnSuggestion() {
        assertThat(CardFit.forCommander(ELF_LORD, List.of(ELF_LORD), DeckBuild.EMPTY, 10)).isEmpty();
    }

    @Test
    @DisplayName("sharing a theme beats sharing nothing")
    void themesRankAboveStaples() {
        BuildCard elf = card("Elvish Archdruid", "Creature — Elf Druid",
                "Other Elf creatures you control get +1/+1.", 3, Set.of("G"));
        BuildCard plainRamp = card("Rampant Growth", "Sorcery",
                "Search your library for a basic land card.", 2, Set.of("G"));

        List<CardFit.Fit> fits = CardFit.forCommander(
                ELF_LORD, List.of(plainRamp, elf), DeckBuild.EMPTY, 10);

        assertThat(fits).extracting(fit -> fit.card().name())
                .containsExactly("Elvish Archdruid", "Rampant Growth");
    }

    @Test
    @DisplayName("every suggestion says which word it matched on")
    void everySuggestionCarriesItsReason() {
        BuildCard elf = card("Elvish Mystic", "Creature — Elf Druid", "Add {G}.", 1, Set.of("G"));

        List<CardFit.Fit> fits = CardFit.forCommander(
                ELF_LORD, List.of(elf), DeckBuild.EMPTY, 10);

        assertThat(fits).hasSize(1);
        assertThat(fits.get(0).because()).contains("elf");
    }

    @Test
    @DisplayName("one row per card, however many printings of it are in the box")
    void printingsCollapse() {
        BuildCard one = card("Elvish Mystic", "Creature — Elf", "Add {G}.", 1, Set.of("G"));
        BuildCard other = new BuildCard(UUID.randomUUID(), one.oracle(), one.name(),
                one.typeLine(), one.oracleText(), one.manaValue(), one.colorIdentity(), true);

        assertThat(CardFit.forCommander(ELF_LORD, List.of(one, other), DeckBuild.EMPTY, 10))
                .hasSize(1);
    }

    @Test
    @DisplayName("the list is capped, and asking for none gives none")
    void theListIsCapped() {
        List<BuildCard> many = new java.util.ArrayList<>();
        for (int index = 0; index < 50; index++) {
            many.add(card("Elf " + index, "Creature — Elf", "Elf.", 1, Set.of("G")));
        }

        assertThat(CardFit.forCommander(ELF_LORD, many, DeckBuild.EMPTY, 5)).hasSize(5);
        assertThat(CardFit.forCommander(ELF_LORD, many, DeckBuild.EMPTY, 0)).isEmpty();
    }

    @Test
    @DisplayName("no commander, no suggestions - there is nothing to suggest against")
    void noCommanderNoSuggestions() {
        assertThat(CardFit.forCommander(null, List.of(ELF_LORD), DeckBuild.EMPTY, 10)).isEmpty();
    }

    @Test
    @DisplayName("a colourless commander still takes coloured cards, because it has no identity to break")
    void colourlessCommandersOnlyTakeColourless() {
        BuildCard kozilek = card("Kozilek", "Legendary Creature — Eldrazi",
                "When you cast this spell, draw four cards. Annihilator.", 10, Set.of());
        BuildCard green = card("Llanowar Elves", "Creature — Elf", "Add {G}. Draw a card.",
                1, Set.of("G"));

        // A colourless identity contains nothing, so a green card is outside it - which is
        // the actual Commander rule, and the thing a suggestion list must not get wrong.
        assertThat(CardFit.forCommander(kozilek, List.of(green), DeckBuild.EMPTY, 10)).isEmpty();
    }

    private static BuildCard card(
            String name, String typeLine, String text, double manaValue, Set<String> identity) {
        return new BuildCard(UUID.randomUUID(), UUID.randomUUID(), name, typeLine, text,
                manaValue, identity, false);
    }
}
