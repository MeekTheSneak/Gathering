package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seated view, laid out at every window size anybody could have.
 *
 * <p>This is the screen a game is played in, so the failure this guards against is not
 * cosmetic: a hand drawn under the action bar is cards you cannot pick up, and a surface that
 * draws cards somewhere other than where the cursor says they are is a table you cannot play
 * on.
 */
class TableScreenLayoutTest {

    @Property(tries = 3000)
    void nothingLeavesTheScreen(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);

        for (Rect rect : all(layout)) {
            if (rect.isEmpty()) {
                continue;
            }
            assertThat(rect.x()).isGreaterThanOrEqualTo(0);
            assertThat(rect.y()).isGreaterThanOrEqualTo(0);
            assertThat(rect.right()).isLessThanOrEqualTo(width);
            assertThat(rect.bottom()).isLessThanOrEqualTo(height);
        }
    }

    @Property(tries = 3000)
    void theBandsNeverOverlap(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        // A hand drawn under the action bar is cards you cannot pick up.
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);
        Rect[] bands = {layout.opponents(), layout.surface(), layout.hand(), layout.actions()};

        for (int first = 0; first < bands.length; first++) {
            for (int second = first + 1; second < bands.length; second++) {
                assertThat(bands[first].overlaps(bands[second]))
                        .describedAs("band %s overlaps band %s at %sx%s", first, second, width, height)
                        .isFalse();
            }
        }
        assertThat(layout.zones().overlaps(layout.surface())).isFalse();
    }

    @Property(tries = 3000)
    void thereIsAlwaysSomewhereToPutACard(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);

        assertThat(layout.cardWidth()).isPositive();
        assertThat(layout.cardHeight()).isPositive();
        assertThat(layout.surface().isEmpty()).isFalse();
        assertThat(layout.hand().isEmpty()).isFalse();
    }

    @Property(tries = 3000)
    void everyCardIsDrawnFullyOnTheSurface(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down) {
        // Cards may overlap each other as much as a player likes. What they may not do is
        // hang off the table, because half a card drawn over the hand band is a card you
        // cannot tell apart from one in your grip.
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);

        Rect drawn = layout.cardAt(TablePosition.of(across, down));

        assertThat(drawn.x()).isGreaterThanOrEqualTo(layout.surface().x());
        assertThat(drawn.y()).isGreaterThanOrEqualTo(layout.surface().y());
        assertThat(drawn.right()).isLessThanOrEqualTo(layout.surface().right());
        assertThat(drawn.bottom()).isLessThanOrEqualTo(layout.surface().bottom());
    }

    @Property(tries = 3000)
    void whereACardIsDrawnIsWhereTheCursorFindsIt(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down) {
        // Drawing a card at a position and working out what position the cursor is over are
        // two pieces of arithmetic that have to agree, or you drop cards next to where you
        // aimed. They agree to within the pixel they are both rounded to.
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);
        TablePosition position = TablePosition.of(across, down);

        Rect drawn = layout.cardAt(position);
        TablePosition found = layout.positionFor(drawn.x(), drawn.y());

        assertThat(layout.cardAt(found)).isEqualTo(drawn);
    }

    @Property(tries = 3000)
    void aDropKeepsTheGripWhereItWasGrabbed(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down) {
        // Picking a card up by its corner and dropping it should leave it where the cursor
        // is, not offset by wherever you happened to grab it.
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);
        Rect drawn = layout.cardAt(TablePosition.of(across, down));
        int grabX = layout.cardWidth() / 3;
        int grabY = layout.cardHeight() / 3;

        TablePosition dropped =
                layout.positionForDrop(drawn.x() + grabX, drawn.y() + grabY, grabX, grabY);

        assertThat(layout.cardAt(dropped)).isEqualTo(drawn);
    }

    @Property(tries = 3000)
    void aDropPastTheEdgeLandsAgainstTheEdgeRatherThanThrowing(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);

        assertThat(layout.cardAt(layout.positionFor(-9000, -9000)))
                .isEqualTo(new Rect(layout.surface().x(), layout.surface().y(),
                        layout.cardWidth(), layout.cardHeight()));
        assertThat(layout.cardAt(layout.positionFor(width * 4, height * 4)).right())
                .isLessThanOrEqualTo(layout.surface().right());
    }

    @Property(tries = 3000)
    void everyPileIsInsideTheColumnAndClearOfTheOthers(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        // A pile drawn over another one is two piles you cannot tell apart, and a pile drawn
        // outside its column is one sitting on the table where a card should go.
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);

        List<Rect> drawn = TableScreenLayout.PILES.stream()
                .map(layout::pile)
                .filter(rect -> !rect.isEmpty())
                .toList();

        for (int first = 0; first < drawn.size(); first++) {
            assertThat(layout.zones().isEmpty()).isFalse();
            assertThat(drawn.get(first).x()).isGreaterThanOrEqualTo(layout.zones().x());
            assertThat(drawn.get(first).right()).isLessThanOrEqualTo(layout.zones().right());
            assertThat(drawn.get(first).y()).isGreaterThanOrEqualTo(layout.zones().y());
            assertThat(drawn.get(first).bottom()).isLessThanOrEqualTo(layout.zones().bottom());
            for (int second = first + 1; second < drawn.size(); second++) {
                assertThat(drawn.get(first).overlaps(drawn.get(second)))
                        .describedAs("piles %s and %s overlap at %sx%s", first, second, width, height)
                        .isFalse();
            }
        }
    }

    @Property(tries = 3000)
    void everyPileDrawnCanBeClicked(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // Drawing a pile and finding the pile under the cursor are the same two pieces of
        // arithmetic that have to agree as anywhere else on this screen.
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);

        for (Zone zone : TableScreenLayout.PILES) {
            Rect pile = layout.pile(zone);
            if (pile.isEmpty()) {
                continue;
            }
            assertThat(layout.pileAt(pile.x() + pile.width() / 2, pile.y() + pile.height() / 2))
                    .describedAs("%s pile is not clickable at %sx%s", zone, width, height)
                    .isEqualTo(zone);
        }
    }

    @Property(tries = 3000)
    void nothingOnTheTableIsEverMistakenForAPile(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down) {
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);
        Rect card = layout.cardAt(TablePosition.of(across, down));

        assertThat(layout.pileAt(card.x() + card.width() / 2, card.y() + card.height() / 2)).isNull();
    }

    @Test
    @DisplayName("the smallest window Minecraft allows still gives you a table and a hand")
    void theSmallestWindowIsStillPlayable() {
        TableScreenLayout layout = TableScreenLayout.of(320, 240, 3);

        assertThat(layout.surface().width()).isGreaterThan(layout.cardWidth());
        assertThat(layout.hand().height()).isGreaterThan(20);
    }

    private static Rect[] all(TableScreenLayout layout) {
        return new Rect[] {
            layout.opponents(), layout.surface(), layout.zones(), layout.hand(), layout.actions(),
        };
    }
}
