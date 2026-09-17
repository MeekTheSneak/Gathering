package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A card's whole text in whatever box it is read in.
 * <p>Written against the owner's report: "It needs to not be cut off. No matter what screen it
 * is in." The panel used to stop drawing at the bottom of its box, and nothing said so.
 */
class CardTextFitTest {

    /** The vanilla font, near enough: six across a character, ten down a line with its gap. */
    private static final int CHARACTER = 6;
    private static final int LINE = 10;
    private static final int RULE = 4;

    /** "Card data and images courtesy of Scryfall." */
    private static final int CREDIT = 42;

    /** A card of this many characters of rules text, over this many faces, with a history. */
    private static CardTextFit.Measure card(int rules, int faces, int story) {
        return new CardTextFit.Measure() {
            @Override
            public int[] lines(int wrap, boolean withStory) {
                List<Integer> lines = new ArrayList<>();
                for (int face = 0; face < faces; face++) {
                    if (face > 0) {
                        lines.add(RULE);
                    }
                    paragraph(lines, 24, wrap);
                    paragraph(lines, 30, wrap);
                    lines.add(RULE);
                    paragraph(lines, rules / faces, wrap);
                }
                if (withStory && story > 0) {
                    lines.add(RULE);
                    paragraph(lines, story, wrap);
                }
                return lines.stream().mapToInt(Integer::intValue).toArray();
            }

            @Override
            public int[] credit(int wrap) {
                List<Integer> lines = new ArrayList<>();
                paragraph(lines, CREDIT, wrap);
                return lines.stream().mapToInt(Integer::intValue).toArray();
            }
        };
    }

    private static void paragraph(List<Integer> into, int characters, int wrap) {
        int perLine = Math.max(1, wrap / CHARACTER);
        for (int line = 0; line < (characters + perLine - 1) / perLine; line++) {
            into.add(LINE);
        }
    }

    @Test
    @DisplayName("a short card is drawn at the asked size, history and all")
    void aShortCard() {
        CardTextFit fit = CardTextFit.of(190, 300, 1f, card(120, 1, 80));

        assertThat(fit.isWhole(1f)).isTrue();
        assertThat(fit.withStory()).isTrue();
    }

    @Test
    @DisplayName("the history goes before the rules text shrinks")
    void theHistoryGoesFirst() {
        // Room for the rules at full size, not for the rules and forty chapters of history.
        CardTextFit fit = CardTextFit.of(190, 200, 1f, card(300, 1, 1200));

        assertThat(fit.withStory()).isFalse();
        assertThat(fit.scale()).isEqualTo(1f);
        assertThat(fit.fits()).isTrue();
    }

    @Test
    @DisplayName("a wordy card in a small box shrinks rather than losing its end")
    void aWordyCardShrinks() {
        CardTextFit fit = CardTextFit.of(190, 200, 1f, card(900, 2, 0));

        assertThat(fit.fits()).isTrue();
        assertThat(fit.scale()).isLessThan(1f).isGreaterThanOrEqualTo(CardTextFit.FLOOR);
        assertThat(fit.height()).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("past the floor, a wide short box takes a second column")
    void aSecondColumn() {
        CardTextFit fit = CardTextFit.of(700, 90, 1f, card(1400, 2, 0));

        assertThat(fit.fits()).isTrue();
        assertThat(fit.columns()).isGreaterThan(1);
        assertThat(fit.scale()).isGreaterThanOrEqualTo(CardTextFit.FLOOR);
    }

    @Test
    @DisplayName("text turned up is honored where it fits")
    void largerText() {
        CardTextFit fit = CardTextFit.of(400, 400, 1.5f, card(200, 1, 0));

        assertThat(fit.scale()).isEqualTo(1.5f);
        assertThat(fit.isWhole(1.5f)).isTrue();
    }

    @Test
    @DisplayName("a box with no room says so rather than claiming to fit")
    void noRoom() {
        CardTextFit fit = CardTextFit.of(40, 20, 1f, card(900, 1, 0));

        assertThat(fit.fits()).isFalse();
        assertThat(fit.scale()).isEqualTo(CardTextFit.FLOOR);
    }

    @Test
    @DisplayName("columns fill one before the next, and refuse what does not go in")
    void theFlow() {
        assertThat(CardTextFit.flow(new int[] {10, 10, 10, 10}, 20, 2)).containsExactly(0, 2);
        assertThat(CardTextFit.flow(new int[] {10, 10, 10}, 20, 1)).isEmpty();
        assertThat(CardTextFit.flow(new int[] {30}, 20, 3)).isEmpty();
        assertThat(CardTextFit.flow(new int[0], 20, 1)).containsExactly(0);
    }

    @Property
    @Label("any card within bounds is all there: inside the box, at the floor or above, or in columns")
    void neverCutOff(
            @ForAll @IntRange(min = 0, max = 1200) int rules,
            @ForAll @IntRange(min = 1, max = 2) int faces,
            @ForAll @IntRange(min = 0, max = 600) int story,
            @ForAll @IntRange(min = 200, max = 1200) int width,
            @ForAll @IntRange(min = 220, max = 800) int height,
            @ForAll @FloatRange(min = 0.6f, max = 2.0f) float asked) {
        CardTextFit.Measure measure = card(rules, faces, story);
        CardTextFit fit = CardTextFit.of(width, height, asked, measure);

        assertThat(fit.fits()).describedAs("some of the card's text was left out").isTrue();
        assertThat(fit.height()).isLessThanOrEqualTo(height);
        assertThat(fit.scale() >= CardTextFit.FLOOR || fit.columns() > 1).isTrue();
        assertThat(fit.scale()).isLessThanOrEqualTo(TextScale.sane(asked));
        if (fit.scale() < TextScale.sane(asked)) {
            assertThat(fit.withStory())
                    .describedAs("the rules text shrank while the history kept its room")
                    .isFalse();
        }
        assertDrawnInside(fit, measure, width, height);
    }

    @Property
    @Label("whatever the box, a fit that says it fits is inside it")
    void aFitIsInside(
            @ForAll @IntRange(min = 0, max = 4000) int rules,
            @ForAll @IntRange(min = 1, max = 2) int faces,
            @ForAll @IntRange(min = 0, max = 2000) int story,
            @ForAll @IntRange(min = 1, max = 2000) int width,
            @ForAll @IntRange(min = 1, max = 1200) int height,
            @ForAll @FloatRange(min = 0.1f, max = 3.0f) float asked) {
        CardTextFit.Measure measure = card(rules, faces, story);
        CardTextFit fit = CardTextFit.of(width, height, asked, measure);

        assertThat(fit.scale()).isGreaterThanOrEqualTo(CardTextFit.FLOOR);
        assertThat(fit.height()).isLessThanOrEqualTo(height);
        if (fit.fits()) {
            assertDrawnInside(fit, measure, width, height);
        }
    }

    /** Draws it the way the panel does - flowed, scaled, credit pinned under - and measures. */
    private static void assertDrawnInside(
            CardTextFit fit, CardTextFit.Measure measure, int width, int height) {
        int[] lines = measure.lines(fit.columnWrap(), fit.withStory());
        int[] starts = CardTextFit.flow(lines, fit.capacity(), fit.columns());
        assertThat(starts).isNotEmpty();
        assertThat(starts.length).isLessThanOrEqualTo(fit.columns());

        int credit = 0;
        for (int line : measure.credit(fit.creditWrap())) {
            credit += line;
        }
        int textRoom = height - CardTextFit.CREDIT_GAP - CardTextFit.scaled(credit, fit.scale());
        for (int column = 0; column < starts.length; column++) {
            int end = column + 1 < starts.length ? starts[column + 1] : lines.length;
            int sum = 0;
            for (int line = starts[column]; line < end; line++) {
                sum += lines[line];
            }
            // To a thousandth of a pixel: 0.6f is 0.6000000238 as a double, and twenty lines of
            // it are that much over twelve without being a pixel of anything.
            assertThat(sum * (double) fit.scale()).isLessThanOrEqualTo(textRoom + 0.001);
        }
        int across = fit.columns() * (int) Math.ceil(fit.columnWrap() * (double) fit.scale())
                + CardTextFit.COLUMN_GAP * (fit.columns() - 1);
        assertThat(across).isLessThanOrEqualTo(width + fit.columns());
    }
}
