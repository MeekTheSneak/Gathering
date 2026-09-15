package dev.gathering.core.guide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.guide.GuidePage.Bullet;
import dev.gathering.core.guide.GuidePage.Heading;
import dev.gathering.core.guide.GuidePage.Key;
import dev.gathering.core.guide.GuidePage.Paragraph;
import dev.gathering.core.guide.GuidePage.Step;
import dev.gathering.core.guide.GuidePage.Words;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading a page of the guide. */
class GuidePageTest {

    @Test
    @DisplayName("headings, paragraphs run together, bullets and numbered steps, in order")
    void blocks() {
        GuidePage page = GuidePage.parse("""
                # Your turn

                Draw first,
                then play.

                - Tap a card
                * Untap it
                1. Sit down
                2. Put a deck down
                ## Afterwards
                """);
        assertThat(page.blocks()).containsExactly(
                new Heading(1, List.of(new Words("Your turn", false))),
                new Paragraph(List.of(new Words("Draw first, then play.", false))),
                new Bullet(List.of(new Words("Tap a card", false))),
                new Bullet(List.of(new Words("Untap it", false))),
                new Step(1, List.of(new Words("Sit down", false))),
                new Step(2, List.of(new Words("Put a deck down", false))),
                new Heading(2, List.of(new Words("Afterwards", false))));
    }

    @Test
    @DisplayName("bold runs and keys are their own spans")
    void spans() {
        assertThat(GuidePage.spans("Press {key:draw} to **draw** a card")).containsExactly(
                new Words("Press ", false), new Key("draw"), new Words(" to ", false),
                new Words("draw", true), new Words(" a card", false));
    }

    @Test
    @DisplayName("what it does not understand is kept as the text it was")
    void keepsWhatItDoesNotRead() {
        assertThat(GuidePage.spans("5 ** 3 and {key:not a key} and {key:")).containsExactly(
                new Words("5 ** 3 and {key:not a key} and {key:", false));
        assertThat(GuidePage.parse("#NoSpace").blocks()).containsExactly(
                new Paragraph(List.of(new Words("#NoSpace", false))));
        assertThat(GuidePage.parse("2024. was a year").blocks()).containsExactly(
                new Paragraph(List.of(new Words("2024. was a year", false))));
    }

    @Test
    @DisplayName("an empty or missing page is an empty page, and Windows line ends read the same")
    void emptyAndLineEnds() {
        assertThat(GuidePage.parse(null).blocks()).isEmpty();
        assertThat(GuidePage.parse("\n\n").blocks()).isEmpty();
        assertThat(GuidePage.parse("one\r\ntwo").blocks()).containsExactly(
                new Paragraph(List.of(new Words("one two", false))));
    }
}
