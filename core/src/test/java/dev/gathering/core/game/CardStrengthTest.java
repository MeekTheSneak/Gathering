package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Power and toughness written over the printed ones")
class CardStrengthTest {

    @Test
    @DisplayName("what you type is what it says")
    void itKeepsTheNumbers() {
        assertThat(CardStrength.clean("4/5")).isEqualTo("4/5");
        assertThat(CardStrength.clean("10/10")).isEqualTo("10/10");
        assertThat(CardStrength.clean("*/*")).isEqualTo("*/*");
        assertThat(CardStrength.clean("+2/+2")).isEqualTo("+2/+2");
        assertThat(CardStrength.clean("-1/-1")).isEqualTo("-1/-1");
    }

    @Test
    @DisplayName("somebody typing it with spaces meant it without them")
    void spacesAreDropped() {
        // Refusing "6 / 6" would be a screen arguing with a player about spacing, which is
        // not what the box is for.
        assertThat(CardStrength.clean("6 / 6")).isEqualTo("6/6");
        assertThat(CardStrength.clean("  3/3  ")).isEqualTo("3/3");
    }

    @Test
    @DisplayName("letters are not power and toughness")
    void wordsAreNotNumbers() {
        // A card with "flying" in the corner box is a note in the wrong place. The pen has
        // its own field for that, and it is drawn somewhere it fits.
        assertThat(CardStrength.clean("flying")).isNull();
        assertThat(CardStrength.clean("2/2 flying")).isEqualTo("2/2");
    }

    @Test
    @DisplayName("nothing typed is a card showing what it was printed as")
    void emptyIsNoOverride() {
        assertThat(CardStrength.clean(null)).isNull();
        assertThat(CardStrength.clean("")).isNull();
        assertThat(CardStrength.clean("   ")).isNull();
        assertThat(CardStrength.clean("hello")).isNull();
    }

    @Test
    @DisplayName("a formatting code cannot get in, because none of it is a number")
    void formattingCannotGetIn() {
        // The note's cleaner drops the escape by name; this one never had to, because the
        // only characters it keeps are the ones power and toughness are made of.
        assertThat(CardStrength.clean("§c4/4")).isEqualTo("4/4");
        assertThat(CardStrength.clean("§c§l")).isNull();
    }

    @Test
    @DisplayName("the corner of a card is not somewhere to write an essay")
    void thereIsACeiling() {
        String typed = "1234567890123456789";

        assertThat(CardStrength.clean(typed)).hasSize(CardStrength.LONGEST);
    }

    @Test
    @DisplayName("cleaning something twice does not change it again")
    void cleaningIsStable() {
        for (String typed : new String[] {"4/5", "6 / 6", "flying", "", "*/1+*", null}) {
            String once = CardStrength.clean(typed);
            assertThat(CardStrength.clean(once))
                    .describedAs("cleaning \"%s\" twice", typed)
                    .isEqualTo(once);
        }
    }

    @Test
    @DisplayName("two ways of typing the same thing are the same thing")
    void sameIsAboutWhatItMeans() {
        assertThat(CardStrength.same("6/6", "6 / 6")).isTrue();
        assertThat(CardStrength.same(null, "")).isTrue();
        assertThat(CardStrength.same("hello", null)).isTrue();
        assertThat(CardStrength.same("6/6", "7/7")).isFalse();
    }

    @Test
    @DisplayName("a card remembers what was written on it, and forgets it again")
    void aCardCarriesIt() {
        CardInstance card = CardInstance.faceUp(
                new CardInstanceId(1),
                dev.gathering.core.card.CardIdentity.ofPrinting(java.util.UUID.randomUUID()),
                new SeatId(0));

        assertThat(card.writtenStrength()).isEmpty();
        assertThat(card.withStrength("6/6").writtenStrength()).contains("6/6");
        assertThat(card.withStrength("6/6").withStrength("").writtenStrength()).isEmpty();
        // Writing the same thing again is the same card, so undo has nothing to walk back.
        CardInstance written = card.withStrength("6/6");
        assertThat(written.withStrength("6 / 6")).isSameAs(written);
    }
}
