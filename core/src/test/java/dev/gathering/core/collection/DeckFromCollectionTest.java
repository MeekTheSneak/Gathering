package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.decklist.DeckSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Building a deck out of a collection, from a list")
class DeckFromCollectionTest {

    private static final UUID BOLT_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOLT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID RING = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID FOREST = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static final Map<CardIdentity, String> NAMES = new LinkedHashMap<>();

    private static CardIdentity card(UUID printing, boolean foil, String name) {
        CardIdentity identity = CardIdentity.ofPrinting(printing, foil);
        NAMES.put(identity, name);
        return identity;
    }

    private static final CardIdentity PLAIN_BOLT = card(BOLT_A, false, "Lightning Bolt");
    private static final CardIdentity FOIL_BOLT = card(BOLT_A, true, "Lightning Bolt");
    private static final CardIdentity OTHER_BOLT = card(BOLT_B, false, "Lightning Bolt");
    private static final CardIdentity SOL_RING = card(RING, false, "Sol Ring");
    private static final CardIdentity A_FOREST = card(FOREST, false, "Forest");

    private static final DeckFromCollection.Naming NAMING = NAMES::get;

    @Test
    @DisplayName("a list is answered with whichever printing is in the box")
    void anyPrintingWillDo() {
        // The whole point. A list off a deck site names printings almost nobody owns.
        CardTally holding = CardTally.builder().add(OTHER_BOLT, 4).build();

        var built = DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("Lightning Bolt", 4, DeckSection.MAINBOARD)),
                holding, NAMING);

        assertThat(built.isComplete()).isTrue();
        assertThat(built.lines().get(0).cards()).containsOnly(OTHER_BOLT).hasSize(4);
        assertThat(built.taking().of(OTHER_BOLT)).isEqualTo(4);
    }

    @Test
    @DisplayName("the plain copy goes in the deck before the foil")
    void plainBeforeFoil() {
        // Sleeving somebody's only foil when there was an ordinary one in the same box is the
        // kind of thing a player notices afterwards and cannot undo.
        CardTally holding = CardTally.builder().add(FOIL_BOLT, 2).add(PLAIN_BOLT, 2).build();

        var built = DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("Lightning Bolt", 3, DeckSection.MAINBOARD)),
                holding, NAMING);

        assertThat(built.taking().of(PLAIN_BOLT)).isEqualTo(2);
        assertThat(built.taking().of(FOIL_BOLT)).isEqualTo(1);
        assertThat(built.lines().get(0).cards().subList(0, 2)).containsOnly(PLAIN_BOLT);
    }

    @Test
    @DisplayName("what the box is short of is said, not silently dropped")
    void shortIsSaid() {
        CardTally holding = CardTally.builder().add(PLAIN_BOLT, 1).build();

        var built = DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("Lightning Bolt", 4, DeckSection.MAINBOARD),
                        DeckFromCollection.Wanted.of("Sol Ring", 1, DeckSection.MAINBOARD)),
                holding, NAMING);

        assertThat(built.isComplete()).isFalse();
        assertThat(built.shortBy()).isEqualTo(4);
        assertThat(built.missing()).containsExactly(
                new DeckFromCollection.Missing("Lightning Bolt", DeckSection.MAINBOARD, 3),
                new DeckFromCollection.Missing("Sol Ring", DeckSection.MAINBOARD, 1));
        assertThat(built.lines().get(0).cards()).containsExactly(PLAIN_BOLT);
    }

    @Test
    @DisplayName("two lines cannot both take the same copy")
    void theBoxIsConsumedAsItGoes() {
        // A mainboard and a sideboard asking for the same card is the ordinary case.
        CardTally holding = CardTally.builder().add(PLAIN_BOLT, 3).build();

        var built = DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("Lightning Bolt", 2, DeckSection.MAINBOARD),
                        DeckFromCollection.Wanted.of("Lightning Bolt", 2, DeckSection.SIDEBOARD)),
                holding, NAMING);

        assertThat(built.taking().of(PLAIN_BOLT))
                .as("three in the box is three taken, however many lines asked")
                .isEqualTo(3);
        assertThat(built.lines().get(0).cards()).hasSize(2);
        assertThat(built.lines().get(1).cards()).hasSize(1);
        assertThat(built.missing()).containsExactly(
                new DeckFromCollection.Missing("Lightning Bolt", DeckSection.SIDEBOARD, 1));
    }

    @Test
    @DisplayName("nothing free is taken out of the box")
    void basicsAreNotTaken() {
        // Basics are given away everywhere in this mod. Taking somebody's Forests when Forests
        // cost nothing would be charging for something that is not for sale.
        CardTally holding = CardTally.builder().add(A_FOREST, 40).build();

        var built = DeckFromCollection.from(
                List.of(new DeckFromCollection.Wanted(
                        "Forest", 37, DeckSection.MAINBOARD, true)),
                holding, NAMING);

        assertThat(built.isComplete()).isTrue();
        assertThat(built.taking().isEmpty()).isTrue();
        assertThat(built.lines().get(0).free()).isEqualTo(37);
        assertThat(built.lines().get(0).cards()).isEmpty();
        assertThat(built.size()).isEqualTo(37);
    }

    @Test
    @DisplayName("a free card nobody owns is still not short")
    void freeCardsNeedNoBox() {
        var built = DeckFromCollection.from(
                List.of(new DeckFromCollection.Wanted(
                        "Island", 24, DeckSection.MAINBOARD, true)),
                CardTally.EMPTY, NAMING);

        assertThat(built.isComplete()).isTrue();
        assertThat(built.size()).isEqualTo(24);
    }

    @Test
    @DisplayName("the same box and the same list build the same deck")
    void buildingIsRepeatable() {
        CardTally holding = CardTally.builder()
                .add(FOIL_BOLT, 2).add(OTHER_BOLT, 2).add(PLAIN_BOLT, 2).add(SOL_RING, 1).build();
        List<DeckFromCollection.Wanted> list = List.of(
                DeckFromCollection.Wanted.of("Lightning Bolt", 5, DeckSection.MAINBOARD),
                DeckFromCollection.Wanted.of("Sol Ring", 1, DeckSection.MAINBOARD));

        var once = DeckFromCollection.from(list, holding, NAMING);
        for (int again = 0; again < 10; again++) {
            assertThat(DeckFromCollection.from(list, holding, NAMING)).isEqualTo(once);
        }
    }

    @Test
    @DisplayName("a printing the collection cannot name is one no line can ask for")
    void anUnnameablePrintingIsSkipped() {
        CardTally holding = CardTally.builder().add(PLAIN_BOLT, 4).build();

        var built = DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("Lightning Bolt", 4, DeckSection.MAINBOARD)),
                holding, card -> null);

        assertThat(built.taking().isEmpty()).isTrue();
        assertThat(built.shortBy()).isEqualTo(4);
    }

    @Test
    @DisplayName("nothing asked for is nothing built")
    void nothingIsNothing() {
        assertThat(DeckFromCollection.from(List.of(), CardTally.EMPTY, NAMING))
                .isEqualTo(DeckFromCollection.Building.NOTHING);
        assertThat(DeckFromCollection.from(null, CardTally.EMPTY, NAMING).size()).isZero();
        assertThat(DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("", 4, DeckSection.MAINBOARD)),
                CardTally.EMPTY, NAMING).lines()).isEmpty();
        assertThat(DeckFromCollection.from(
                List.of(DeckFromCollection.Wanted.of("Sol Ring", 0, DeckSection.MAINBOARD)),
                CardTally.EMPTY, NAMING).lines()).isEmpty();
    }

    @Test
    @DisplayName("a line off somebody's clipboard cannot ask for two billion Islands")
    void oneLineIsBounded() {
        var line = DeckFromCollection.Wanted.of("Island", Integer.MAX_VALUE, DeckSection.MAINBOARD);

        assertThat(line.howMany()).isEqualTo(DeckFromCollection.MOST_PER_LINE);
    }

    @Property
    @net.jqwik.api.Label("never takes more of anything than the box holds, and adds up")
    void takingIsBoundedAndAddsUp(
            @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 8) Integer> asked,
            @ForAll @IntRange(min = 0, max = 6) int inTheBox) {
        CardTally holding = inTheBox == 0
                ? CardTally.EMPTY
                : CardTally.builder().add(PLAIN_BOLT, inTheBox).build();
        List<DeckFromCollection.Wanted> list = new ArrayList<>();
        for (int howMany : asked) {
            list.add(DeckFromCollection.Wanted.of("Lightning Bolt", howMany, DeckSection.MAINBOARD));
        }

        var built = DeckFromCollection.from(list, holding, NAMING);

        assertThat(built.taking().of(PLAIN_BOLT))
                .as("never more than is there")
                .isLessThanOrEqualTo(inTheBox);
        int wanted = asked.stream().mapToInt(Integer::intValue).sum();
        assertThat(built.size() + built.shortBy())
                .as("every card asked for is either in the deck or reported short")
                .isEqualTo(wanted);
        assertThat(built.taking().total())
                .as("what is taken is what is in the deck")
                .isEqualTo(built.size());
    }
}
