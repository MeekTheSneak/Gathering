package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageUris;
import dev.gathering.core.card.Rarity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The collection's search box.
 *
 * <p>Scryfall's syntax, so these are mostly checks that the queries a Magic player already has
 * in their fingers do what that player expects - and that the ones this cannot answer fail
 * softly, as a word to look for, rather than as an error over a typo.
 */
class CardSearchTest {

    private static final CardMetadata BOLT = card(
            "Lightning Bolt", "{R}", 1, "Instant",
            "Lightning Bolt deals 3 damage to any target.",
            Set.of("R"), Set.of("R"), Rarity.COMMON, "lea", "Limited Edition Alpha", "", "");

    private static final CardMetadata BEARS = card(
            "Grizzly Bears", "{1}{G}", 2, "Creature — Bear", "",
            Set.of("G"), Set.of("G"), Rarity.COMMON, "lea", "Limited Edition Alpha", "2", "2");

    private static final CardMetadata SOL_RING = card(
            "Sol Ring", "{1}", 1, "Artifact", "{T}: Add {C}{C}.",
            Set.of(), Set.of(), Rarity.UNCOMMON, "ltc", "Commander Masters", "", "");

    private static final CardMetadata HYDRA = card(
            "Managorger Hydra", "{2}{G}", 3, "Creature — Hydra",
            "Trample. Whenever a player casts a spell, put a +1/+1 counter on Managorger Hydra.",
            Set.of("G"), Set.of("G"), Rarity.RARE, "ori", "Magic Origins", "1", "1");

    private static final CardMetadata ELF = card(
            "Llanowar Elves", "{G}", 1, "Creature — Elf Druid", "{T}: Add {G}.",
            Set.of("G"), Set.of("G"), Rarity.COMMON, "m19", "Core Set 2019", "1", "1");

    private static final CardMetadata TALRAND = card(
            "Talrand, Sky Summoner", "{2}{U}", 3, "Legendary Creature — Merfolk Wizard",
            "Whenever you cast an instant or sorcery spell, create a 2/2 blue Drake.",
            Set.of("U"), Set.of("U"), Rarity.RARE, "m13", "Magic 2013", "2", "2");

    // ------------------------------------------------------------------ fields

    @Test
    @DisplayName("a bare word looks at the name, the type and the set, as it always did")
    void bareWordsAreTheOldBehaviour() {
        assertThat(finds("bolt", BOLT)).isTrue();
        assertThat(finds("creature", BEARS)).isTrue();
        assertThat(finds("alpha", BOLT)).isTrue();
        assertThat(finds("bolt", BEARS)).isFalse();
    }

    @Test
    @DisplayName("t: searches the whole type line, so supertype, type and creature type all work")
    void typeCoversTheWholeLine() {
        assertThat(finds("t:legendary", TALRAND)).isTrue();
        assertThat(finds("t:creature", BEARS)).isTrue();
        assertThat(finds("t:bear", BEARS)).isTrue();
        assertThat(finds("t:elf", ELF)).isTrue();
        assertThat(finds("t:wizard", TALRAND)).isTrue();
        assertThat(finds("t:elf", BEARS)).isFalse();
    }

    @Test
    @DisplayName("o: searches the rules text")
    void oracleSearchesTheText() {
        assertThat(finds("o:trample", HYDRA)).isTrue();
        assertThat(finds("o:damage", BOLT)).isTrue();
        assertThat(finds("o:trample", BOLT)).isFalse();
    }

    @Test
    @DisplayName("a quoted phrase is one term, spaces and all")
    void quotesHoldAPhraseTogether() {
        assertThat(finds("o:\"+1/+1 counter\"", HYDRA)).isTrue();
        assertThat(finds("o:\"any target\"", BOLT)).isTrue();
        // Without the quotes those are two terms and the second one fails.
        assertThat(finds("o:any target", BOLT)).isFalse();
    }

    @Test
    @DisplayName("mana value compares, and so do power and toughness")
    void numbersCompare() {
        assertThat(finds("mv:1", BOLT)).isTrue();
        assertThat(finds("mv<=2", BEARS)).isTrue();
        assertThat(finds("mv>2", BEARS)).isFalse();
        assertThat(finds("cmc>=3", HYDRA)).isTrue();
        assertThat(finds("pow>=2", BEARS)).isTrue();
        assertThat(finds("tou<2", ELF)).isTrue();
        // A card with no power at all answers no question about power.
        assertThat(finds("pow>=0", BOLT)).isFalse();
    }

    @Test
    @DisplayName("colours read as at least, exactly, or within")
    void coloursReadThreeWays() {
        assertThat(finds("c:r", BOLT)).isTrue();
        assertThat(finds("c:g", BOLT)).isFalse();
        assertThat(finds("c=r", BOLT)).isTrue();
        // Within: every card whose colours fit inside green, which includes the colourless.
        assertThat(finds("c<=g", BEARS)).isTrue();
        assertThat(finds("c<=g", SOL_RING)).isTrue();
        assertThat(finds("c<=g", BOLT)).isFalse();
    }

    @Test
    @DisplayName("colours can be named as words and as guilds")
    void coloursHaveNames() {
        assertThat(finds("c:red", BOLT)).isTrue();
        assertThat(finds("c:green", ELF)).isTrue();
        assertThat(finds("id<=gruul", BOLT)).isTrue();
        assertThat(finds("id<=gruul", ELF)).isTrue();
        assertThat(finds("id<=gruul", TALRAND)).isFalse();
    }

    @Test
    @DisplayName("colourless is the absence of colour, not a colour")
    void colourlessIsItsOwnQuestion() {
        assertThat(finds("c:c", SOL_RING)).isTrue();
        assertThat(finds("c:colorless", SOL_RING)).isTrue();
        assertThat(finds("c:c", BOLT)).isFalse();
    }

    @Test
    @DisplayName("colour identity is asked separately from colour")
    void identityIsItsOwnField() {
        assertThat(finds("id:g", ELF)).isTrue();
        assertThat(finds("id:u", TALRAND)).isTrue();
        assertThat(finds("id:u", ELF)).isFalse();
    }

    @Test
    @DisplayName("rarity is ordered as well as named")
    void rarityCompares() {
        assertThat(finds("r:rare", HYDRA)).isTrue();
        assertThat(finds("r:common", BOLT)).isTrue();
        assertThat(finds("r>=rare", HYDRA)).isTrue();
        assertThat(finds("r>=rare", BOLT)).isFalse();
        assertThat(finds("r<=uncommon", SOL_RING)).isTrue();
    }

    @Test
    @DisplayName("sets match by code or by name")
    void setsMatchEitherWay() {
        assertThat(finds("s:lea", BOLT)).isTrue();
        assertThat(finds("e:ori", HYDRA)).isTrue();
        assertThat(finds("set:origins", HYDRA)).isTrue();
        assertThat(finds("s:lea", HYDRA)).isFalse();
    }

    @Test
    @DisplayName("count asks how many copies are in the box")
    void countAsksTheCollection() {
        assertThat(CardSearch.matches(BOLT, 4, CardSearch.parse("count>=4"))).isTrue();
        assertThat(CardSearch.matches(BOLT, 1, CardSearch.parse("count>=4"))).isFalse();
        assertThat(CardSearch.matches(BOLT, 1, CardSearch.parse("have:1"))).isTrue();
    }

    @Test
    @DisplayName("is: answers the yes-or-no questions")
    void isAnswersTheShorthands() {
        assertThat(finds("is:creature", BEARS)).isTrue();
        assertThat(finds("is:permanent", SOL_RING)).isTrue();
        assertThat(finds("is:permanent", BOLT)).isFalse();
        assertThat(finds("is:spell", BOLT)).isTrue();
        assertThat(finds("is:legendary", TALRAND)).isTrue();
        assertThat(finds("is:vanilla", BEARS)).isTrue();
        assertThat(finds("is:vanilla", BOLT)).isFalse();
    }

    // ------------------------------------------------------------ combinations

    @Test
    @DisplayName("terms are all and, which is the only rule that needs no explaining")
    void everyTermMustHold() {
        assertThat(finds("t:creature c:g mv<=2", ELF)).isTrue();
        assertThat(finds("t:creature c:g mv<=2", HYDRA)).isFalse();
        assertThat(finds("t:creature c:g mv<=2", BOLT)).isFalse();
    }

    @Test
    @DisplayName("a leading minus takes cards out")
    void minusNegates() {
        assertThat(finds("-t:creature", BOLT)).isTrue();
        assertThat(finds("-t:creature", BEARS)).isFalse();
        assertThat(finds("c:g -t:creature", ELF)).isFalse();
    }

    // ------------------------------------------------------------- failing soft

    @Test
    @DisplayName("a field nobody named is a word to look for, not an error")
    void unknownFieldsFallBackToWords() {
        // "sol:ring" is not a field, so the whole thing is looked for as text - and Sol Ring
        // is not called "sol:ring", so this finds nothing rather than throwing.
        assertThat(CardSearch.parse("sol:ring")).singleElement()
                .extracting(CardSearch.Term::field).isEqualTo(CardSearch.Field.ANY);
        assertThat(finds("zzz:zzz", BOLT)).isFalse();
    }

    @Test
    @DisplayName("nothing typed matches everything, and nothing throws on rubbish")
    void nothingIsNotAFilter() {
        assertThat(CardSearch.parse("")).isEmpty();
        assertThat(CardSearch.parse(null)).isEmpty();
        assertThat(finds("", BOLT)).isTrue();
        assertThat(finds("   ", BOLT)).isTrue();
        // Half-typed terms are the ordinary state of a box somebody is typing into.
        assertThat(finds("t:", BOLT)).isTrue();
        assertThat(finds("mv>", BOLT)).isFalse();
        assertThat(finds(":::", BOLT)).isFalse();
        assertThat(finds("\"unclosed", BOLT)).isFalse();
    }

    @Test
    @DisplayName("a card nothing is known about answers no filter and is not claimed to be anything")
    void unknownCardsMatchNothing() {
        assertThat(CardSearch.matches(null, 1, CardSearch.parse("c:r"))).isFalse();
        assertThat(CardSearch.matches(null, 1, CardSearch.parse(""))).isTrue();
    }

    @Test
    @DisplayName("both halves of a double-faced card are searched")
    void bothFacesAreSearched() {
        CardMetadata delver = new CardMetadata(
                UUID.randomUUID(), UUID.randomUUID(), "Delver of Secrets", "{U}", 1,
                "Creature — Human Wizard", "At the beginning of your upkeep, look at the top card.",
                Set.of("U"), Set.of("U"),
                List.of(
                        new CardFace("Delver of Secrets", "{U}", "Creature — Human Wizard",
                                "At the beginning of your upkeep, look at the top card.",
                                "1", "1", "", "", "", ImageUris.EMPTY),
                        new CardFace("Insectile Aberration", "", "Creature — Human Insect",
                                "Flying.", "3", "2", "", "", "", ImageUris.EMPTY)),
                "transform", "isd", "Innistrad", "51", Rarity.COMMON,
                false, true, true, false, false, List.of(), Map.of(), Map.of(), "");

        assertThat(finds("o:flying", delver)).isTrue();
        assertThat(finds("t:insect", delver)).isTrue();
        assertThat(finds("is:transform", delver)).isTrue();
    }

    private static boolean finds(String typed, CardMetadata about) {
        return CardSearch.matches(about, 1, CardSearch.parse(typed));
    }

    private static CardMetadata card(
            String name, String manaCost, double cmc, String typeLine, String oracleText,
            Set<String> colors, Set<String> identity, Rarity rarity,
            String setCode, String setName, String power, String toughness) {
        return new CardMetadata(
                UUID.randomUUID(), UUID.randomUUID(), name, manaCost, cmc, typeLine, oracleText,
                colors, identity,
                List.of(new CardFace(name, manaCost, typeLine, oracleText, power, toughness,
                        "", "", "", ImageUris.EMPTY)),
                "normal", setCode, setName, "1", rarity,
                false, true, true, false, false, List.of(), Map.of(), Map.of(), "");
    }
}
