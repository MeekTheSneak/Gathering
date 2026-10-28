package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The badge behind a card's numbers holds them, at every card size.
 * <p>The reported fault was a badge stopping at the card's edge while the numbers carried on
 * past both sides of it, so the property worth having is the one that says that cannot
 * happen - rather than a handful of sizes somebody thought to try.
 */
class StrengthBadgeTest {

    private static final int LINE = 9;

    @Property
    @Label("a badge always holds the numbers it was worked out for")
    void itAlwaysHoldsItsNumbers(
            @ForAll @IntRange(min = 1, max = 400) int textWidth,
            @ForAll @IntRange(min = 1, max = 300) int room) {
        StrengthBadge.Fit fit = StrengthBadge.of(textWidth, LINE, room);
        assertThat(StrengthBadge.holdsItsNumbers(fit, textWidth, LINE)).isTrue();
    }

    @Test
    @DisplayName("numbers that fit are drawn full size")
    void whatFitsIsNotSquashed() {
        // "3/3" on an ordinary card: nothing to solve, so nothing is changed.
        StrengthBadge.Fit fit = StrengthBadge.of(15, LINE, 60);
        assertThat(fit.scale()).isEqualTo(1f);
        assertThat(fit.width()).isEqualTo(15 + StrengthBadge.PADDING * 2);
    }

    @Test
    @DisplayName("numbers too wide for the card are made to fit rather than overrun it")
    void whatDoesNotFitIsFitted() {
        // A three-digit loyalty on a card narrower than it.
        int wide = 21;
        int room = 20;
        StrengthBadge.Fit fit = StrengthBadge.of(wide, LINE, room);
        assertThat(fit.scale()).isLessThan(1f);
        assertThat(fit.width()).isLessThanOrEqualTo(room);
        assertThat(StrengthBadge.holdsItsNumbers(fit, wide, LINE)).isTrue();
    }

    @Property
    @Label("a badge only sticks out past the card when squeezing further would be unreadable")
    void itOnlyOverhangsWhenItHasTo(
            @ForAll @IntRange(min = 1, max = 400) int textWidth,
            @ForAll @IntRange(min = StrengthBadge.MINIMUM_ROOM, max = 300) int room) {
        StrengthBadge.Fit fit = StrengthBadge.of(textWidth, LINE, room);
        if (fit.width() > room) {
            assertThat(fit.scale()).isEqualTo(StrengthBadge.SMALLEST);
        }
        assertThat(fit.scale()).isBetween(StrengthBadge.SMALLEST, 1f);
    }

    @Test
    @DisplayName("a card with no room for a badge is told so rather than given a wide one")
    void aCardTooNarrowGetsNoBadge() {
        assertThat(StrengthBadge.fitsOn(StrengthBadge.MINIMUM_ROOM)).isTrue();
        assertThat(StrengthBadge.fitsOn(StrengthBadge.MINIMUM_ROOM - 1)).isFalse();
        assertThat(StrengthBadge.fitsOn(1)).isFalse();
    }

    @Test
    @DisplayName("and the check itself refuses the way this used to be sized")
    void theCheckRefusesTheOldSizing() {
        // What it did before: the numbers drawn at full size, and the badge clamped to the
        // card. Without this, holdsItsNumbers only ever restates how of() computed its own
        // width, and a guard that cannot fail is not a guard.
        int wide = 21;
        int room = 14;
        StrengthBadge.Fit clamped = new StrengthBadge.Fit(
                Math.min(room, wide + StrengthBadge.PADDING * 2), LINE + 1, 1f, room / 2, 1);
        assertThat(StrengthBadge.holdsItsNumbers(clamped, wide, LINE)).isFalse();
    }

    @Test
    @DisplayName("a card too narrow to read numbers on gets a badge that sticks out instead")
    void unreadableIsWorseThanOverhanging() {
        // Squeezing past the floor would make the numbers unreadable, which is worse than a
        // badge wider than the card - and both are better than numbers outside their box.
        StrengthBadge.Fit fit = StrengthBadge.of(60, LINE, 8);
        assertThat(fit.scale()).isEqualTo(StrengthBadge.SMALLEST);
        assertThat(StrengthBadge.holdsItsNumbers(fit, 60, LINE)).isTrue();
    }

    @Test
    @DisplayName("the numbers sit inside the badge top to bottom, not against an edge")
    void theNumbersAreCentered() {
        StrengthBadge.Fit fit = StrengthBadge.of(15, LINE, 60);
        assertThat(fit.textY()).isGreaterThanOrEqualTo(0);
        assertThat(fit.textY() + LINE).isLessThanOrEqualTo(fit.height());
    }
}
