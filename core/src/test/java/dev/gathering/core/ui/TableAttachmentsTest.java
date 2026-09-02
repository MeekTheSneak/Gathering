package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where auras and equipment are drawn.
 * <p>The point of drawing them at all is that a stacked aura is an aura nobody can see, so the
 * two things worth guarding are that an attachment never lands on top of its host and that two
 * attachments never land on top of each other.
 */
class TableAttachmentsTest {

    private static final Rect HOST = new Rect(200, 100, 40, 56);
    private static final Rect SURFACE = new Rect(0, 0, 400, 300);

    @Test
    @DisplayName("an attachment is smaller than the card it is on")
    void attachmentsAreSmall() {
        Rect slot = TableAttachments.slot(HOST, 0);

        assertThat(slot.width()).isLessThan(HOST.width());
        assertThat(slot.height()).isLessThan(HOST.height());
    }

    @Test
    @DisplayName("an attachment is never drawn on top of its host")
    void attachmentsDoNotHideTheHost() {
        // The whole reason this exists rather than letting people stack cards by hand.
        for (int index = 0; index < 6; index++) {
            assertThat(TableAttachments.slot(HOST, index).overlaps(HOST))
                    .describedAs("attachment %s covers its host", index)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("two attachments are visibly two")
    void attachmentsDoNotHideEachOther() {
        // They overlap, the way a real stack of equipment does. What matters is the strip of
        // each one that still shows past the card drawn over it.
        Rect first = TableAttachments.slot(HOST, 0);
        Rect second = TableAttachments.slot(HOST, 1);

        assertThat(second.y() - first.y()).isEqualTo(TableAttachments.visibleStrip(HOST));
        assertThat(TableAttachments.visibleStrip(HOST)).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("the first one stays put as more arrive")
    void theFanGrowsDownwards() {
        // A fan that grows upwards renumbers itself every time somebody equips something, and
        // the card you were about to click moves out from under the cursor.
        Rect first = TableAttachments.slot(HOST, 0);

        assertThat(TableAttachments.slot(HOST, 3).y()).isGreaterThan(first.y());
        assertThat(TableAttachments.slot(HOST, 0)).isEqualTo(first);
    }

    @Test
    @DisplayName("a card against the left edge fans to the right instead")
    void theFanFlipsRatherThanLeavingTheTable() {
        Rect nearTheEdge = new Rect(4, 100, 40, 56);

        assertThat(TableAttachments.fansLeft(nearTheEdge, SURFACE)).isFalse();
        assertThat(TableAttachments.fansLeft(HOST, SURFACE)).isTrue();

        Rect flipped = TableAttachments.slotOnTheRight(nearTheEdge, 0);
        assertThat(flipped.x()).isGreaterThanOrEqualTo(nearTheEdge.right());
        assertThat(flipped.overlaps(nearTheEdge)).isFalse();
    }

    @Property(tries = 2000)
    void everyAttachmentShowsAStripOfItself(
            @ForAll @IntRange(min = 26, max = 74) int height,
            @ForAll @IntRange(min = 1, max = 8) int index) {
        // A fan where any two land in the same place is a fan that has quietly lost a card.
        Rect host = new Rect(300, 200, Math.round(height * 488f / 680f), height);

        int gap = TableAttachments.slot(host, index).y() - TableAttachments.slot(host, index - 1).y();

        assertThat(gap)
                .describedAs("strip showing between attachments %s and %s", index - 1, index)
                .isGreaterThanOrEqualTo(5);
    }

    @Property(tries = 2000)
    void aFanNeverCoversItsHostAtAnyCardSize(
            @ForAll @IntRange(min = 26, max = 74) int height,
            @ForAll @IntRange(min = 0, max = 8) int index) {
        Rect host = new Rect(300, 200, Math.round(height * 488f / 680f), height);

        assertThat(TableAttachments.slot(host, index).overlaps(host)).isFalse();
        assertThat(TableAttachments.slotOnTheRight(host, index).overlaps(host)).isFalse();
    }
}
