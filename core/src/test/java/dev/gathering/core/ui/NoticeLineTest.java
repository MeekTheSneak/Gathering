package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A notice said while a screen is open: how long it is up, and where.
 * <p>Reported as "any pop-ups or info that is given to you while in a menu needs to be
 * presented in a way you can see it even with the menu open".
 */
class NoticeLineTest {

    /** The smallest window the game produces, and one of the largest. */
    private static final int[][] WINDOWS = {
        {320, 240}, {427, 240}, {640, 360}, {854, 480}, {1920, 1080}, {3840, 2160},
    };

    private static final int LINE_HEIGHT = 9;

    @Test
    @DisplayName("stands at full brightness, then fades, then is over")
    void theFade() {
        assertThat(NoticeLine.alphaAt(0, 0)).isEqualTo(255);
        assertThat(NoticeLine.alphaAt(0, NoticeLine.STANDS_MILLIS - NoticeLine.FADES_MILLIS))
                .isEqualTo(255);
        assertThat(NoticeLine.alphaAt(0, NoticeLine.STANDS_MILLIS - NoticeLine.FADES_MILLIS / 2))
                .isBetween(NoticeLine.FAINTEST, 254);
        assertThat(NoticeLine.alphaAt(0, NoticeLine.STANDS_MILLIS)).isZero();
        assertThat(NoticeLine.isOver(0, NoticeLine.STANDS_MILLIS)).isTrue();
        assertThat(NoticeLine.isOver(0, NoticeLine.STANDS_MILLIS * 4)).isTrue();
    }

    @Test
    @DisplayName("never draws at the alpha the font reads as no alpha at all")
    void itNeverEndsUpOpaqueAgain() {
        for (long at = 0; at <= NoticeLine.STANDS_MILLIS + 50; at++) {
            int alpha = NoticeLine.alphaAt(0, at);
            assertThat(alpha)
                    .as("at %d ms", at)
                    .satisfiesAnyOf(
                            value -> assertThat(value).isZero(),
                            value -> assertThat(value).isGreaterThanOrEqualTo(NoticeLine.FAINTEST));
        }
    }

    @Test
    @DisplayName("never gets brighter while it is up")
    void itOnlyEverDims() {
        int last = 256;
        for (long at = 0; at <= NoticeLine.STANDS_MILLIS; at += 10) {
            int alpha = NoticeLine.alphaAt(0, at);
            assertThat(alpha).as("at %d ms", at).isLessThanOrEqualTo(last);
            last = alpha;
        }
    }

    @Test
    @DisplayName("shows a notice whose clock has gone backwards rather than swallowing it")
    void aClockThatWentBackwards() {
        assertThat(NoticeLine.alphaAt(1_000, 0)).isEqualTo(255);
    }

    @Test
    @DisplayName("sits inside the window at every size, off the bottom row of buttons")
    void theBoxIsAlwaysOnTheWindow() {
        for (int[] window : WINDOWS) {
            int width = window[0];
            int height = window[1];
            for (int text : new int[] {0, 12, 200, 900, 5_000}) {
                Rect box = NoticeLine.placeIn(width, height, text, LINE_HEIGHT);
                String where = "%dx%d, %dpx of writing".formatted(width, height, text);
                assertThat(box.x()).as(where).isGreaterThanOrEqualTo(0);
                assertThat(box.y()).as(where).isGreaterThanOrEqualTo(0);
                assertThat(box.right()).as(where).isLessThanOrEqualTo(width);
                assertThat(box.bottom()).as(where).isLessThanOrEqualTo(height);
                // Nowhere near the buttons, which live along the bottom of every screen here.
                assertThat(box.bottom()).as(where).isLessThan(height / 2);
                assertThat(box.isEmpty()).as(where).isFalse();
            }
        }
    }

    @Test
    @DisplayName("centers the box across the window")
    void theBoxIsCentered() {
        Rect box = NoticeLine.placeIn(854, 480, 100, LINE_HEIGHT);
        assertThat(box.x() - 0).isEqualTo(854 - box.right());
    }

    @Test
    @DisplayName("has nothing to place on a window with no size")
    void noWindow() {
        assertThat(NoticeLine.placeIn(0, 0, 50, LINE_HEIGHT)).isEqualTo(Rect.NONE);
    }

    @Test
    @DisplayName("leaves room for writing on the smallest window there is")
    void thereIsAlwaysRoomToWrite() {
        assertThat(NoticeLine.roomForWriting(320)).isGreaterThan(200);
    }

    @Test
    @DisplayName("makes room for the frame drawn round it")
    void theBoxIsNeverSmallerThanItsOwnFrame() {
        // A box sized to one line of text is about seventeen pixels tall, and the panel behind it is
        // painted with an eight pixel border - sixteen in four of the looks. Below what its border
        // needs, the game squashes the whole picture into the box, which is what the owner saw.
        Rect tight = NoticeLine.placeIn(400, 300, 60, 9);
        assertThat(tight.height()).isLessThan(24);

        Rect roomy = NoticeLine.placeIn(400, 300, 60, 9, 24, 24);

        assertThat(roomy.height()).isGreaterThanOrEqualTo(24);
        assertThat(roomy.width()).isGreaterThanOrEqualTo(24);
        // And still centered, and still inside the window.
        assertThat(roomy.x()).isGreaterThanOrEqualTo(0);
        assertThat(roomy.x() + roomy.width()).isLessThanOrEqualTo(400);
    }

    @Test
    @DisplayName("never grows past the window to make that room")
    void aframeBiggerThanTheWindowStillFits() {
        Rect box = NoticeLine.placeIn(40, 20, 10, 9, 200, 200);

        assertThat(box.width()).isLessThanOrEqualTo(40);
        assertThat(box.height()).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("starts its writing as far in as the look's own frame does")
    void athickerFrameGetsAWiderBox() {
        // Four pixels was chosen against the panel the mod paints itself, whose drawn frame is three
        // thick. The looks built around a frame somebody drew are about twice that, and the words
        // sat on the border - which the owner reported of this notice on Ember.
        Rect thin = NoticeLine.placeIn(400, 300, 60, 9, 0, 0, 4);
        Rect thick = NoticeLine.placeIn(400, 300, 60, 9, 0, 0, 10);

        assertThat(thick.width()).isGreaterThan(thin.width());
        assertThat(thick.height()).isGreaterThan(thin.height());
        // And the writing gets less of the window, because more of it is frame.
        assertThat(NoticeLine.roomForWriting(400, 10))
                .isLessThan(NoticeLine.roomForWriting(400, 4));
    }

    /**
     * The line the table says when the turn comes round sits just under the strip along the top, and so does the far
     * seat's hand once the whole table is framed: the fan was drawn across the words. Moved under the hand, it covers
     * none of it.
     */
    @Test
    @DisplayName("a line over the board moves clear of what is drawn there")
    void aLineMovesClearOfAHand() {
        Rect line = new Rect(180, 22, 60, 9);
        java.util.List<Rect> hand = java.util.List.of(new Rect(200, 18, 12, 17), new Rect(208, 19, 12, 17));

        Rect clear = NoticeLine.clearOf(line, hand, 220, 2);

        for (Rect card : hand) {
            assertThat(clear.overlaps(card)).as("%s over %s", clear, card).isFalse();
        }
        assertThat(clear.y()).isEqualTo(19 + 17 + 2);
        assertThat(clear.x()).isEqualTo(line.x());
    }

    @Test
    @DisplayName("a line nothing is drawn under stays where it was asked to be")
    void aClearLineStays() {
        Rect line = new Rect(180, 22, 60, 9);

        assertThat(NoticeLine.clearOf(line, java.util.List.of(new Rect(0, 100, 50, 50)), 220, 2)).isEqualTo(line);
        assertThat(NoticeLine.clearOf(line, java.util.List.of(), 220, 2)).isEqualTo(line);
    }

    /** Each move is under something the line covered, so a stack of things is cleared one under another. */
    @Test
    @DisplayName("a line moved under one thing and onto another moves again")
    void aLineClearsAStack() {
        Rect line = new Rect(100, 20, 40, 9);
        // The second is clear of the line as asked, and in the way once it has moved under the first.
        java.util.List<Rect> drawn = java.util.List.of(new Rect(90, 15, 30, 10), new Rect(110, 30, 30, 10));

        Rect clear = NoticeLine.clearOf(line, drawn, 220, 1);

        assertThat(drawn).noneMatch(clear::overlaps);
        assertThat(clear.y()).isEqualTo(41);
    }

    @Test
    @DisplayName("with nowhere clear above the floor, the line stays where it was asked to be")
    void nowhereClearKeepsTheLine() {
        Rect line = new Rect(100, 20, 40, 9);

        assertThat(NoticeLine.clearOf(line, java.util.List.of(new Rect(0, 0, 400, 300)), 220, 2)).isEqualTo(line);
    }
}
