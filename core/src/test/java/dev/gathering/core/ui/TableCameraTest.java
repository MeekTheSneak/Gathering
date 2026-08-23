package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.gathering.core.game.TablePosition;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Looking at the table.
 *
 * <p>Everything that draws a card and everything that works out what the cursor is pointing at
 * goes through this one transform, so the only property that really matters is that the two
 * directions are inverses. When they are not, cards land next to where you dropped them and
 * clicks miss what they are aimed at - which is exactly the failure the grid removal already
 * had to fix once.
 */
class TableCameraTest {

    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;

    @Nested
    @DisplayName("the transform")
    class TheTransform {

        @Test
        @DisplayName("the point the camera is centred on is drawn in the middle of the screen")
        void theCentreIsTheCentre() {
            TableCamera camera = TableCamera.showingAll(WIDTH, HEIGHT);

            assertThat(camera.toScreenX(camera.centreX(), WIDTH)).isCloseTo(WIDTH / 2.0, within(0.001));
            assertThat(camera.toScreenY(camera.centreY(), HEIGHT)).isCloseTo(HEIGHT / 2.0, within(0.001));
        }

        @Test
        @DisplayName("showing everything shows everything")
        void showingAllFitsTheTable() {
            TableCamera camera = TableCamera.showingAll(WIDTH, HEIGHT);

            // Both far corners are on screen, or the first thing a player sees is a crop.
            assertThat(camera.toScreenX(0, WIDTH)).isGreaterThanOrEqualTo(0);
            assertThat(camera.toScreenY(0, HEIGHT)).isGreaterThanOrEqualTo(0);
            assertThat(camera.toScreenX(TablePosition.SPAN, WIDTH)).isLessThanOrEqualTo(WIDTH);
            assertThat(camera.toScreenY(TablePosition.SPAN, HEIGHT)).isLessThanOrEqualTo(HEIGHT);
        }

        @Test
        @DisplayName("a square on the table is a square on the screen at every zoom")
        void bothAxesScaleTogether() {
            // The camera has one scale for both directions, and it is the reason a card drawn
            // through it keeps its shape - the shape itself is the surface's business, and
            // TableSurfaceTest holds it. What can go wrong here is the two axes drifting.
            TableCamera camera = TableCamera.showingAll(WIDTH, HEIGHT);
            for (int step = 0; step < 12; step++) {
                double across = camera.toScreenX(6000, WIDTH) - camera.toScreenX(5000, WIDTH);
                double down = camera.toScreenY(6000, HEIGHT) - camera.toScreenY(5000, HEIGHT);
                assertThat(Math.abs(across))
                        .describedAs("a thousand units, both ways, at zoom step %s", step)
                        .isCloseTo(Math.abs(down), within(0.001));
                camera = camera.zoomedAt(1.3, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
            }
        }
    }

    @Nested
    @DisplayName("zooming")
    class Zooming {

        @Test
        @DisplayName("whatever is under the cursor stays under it")
        void zoomingIsAnchoredToTheCursor() {
            // The thing every map does and the thing you notice instantly when it is missing:
            // zooming about the screen's middle slides the card away as you lean in on it.
            TableCamera camera = TableCamera.showingAll(WIDTH, HEIGHT);
            double cursorX = 600;
            double cursorY = 120;
            double wasOverX = camera.toTableX(cursorX, WIDTH);
            double wasOverY = camera.toTableY(cursorY, HEIGHT);

            TableCamera zoomed = camera.zoomedAt(2.0, cursorX, cursorY, WIDTH, HEIGHT);

            assertThat(zoomed.toTableX(cursorX, WIDTH)).isCloseTo(wasOverX, within(1.0));
            assertThat(zoomed.toTableY(cursorY, HEIGHT)).isCloseTo(wasOverY, within(1.0));
        }

        @Test
        @DisplayName("zoom stops rather than running away in either direction")
        void zoomIsBounded() {
            TableCamera camera = TableCamera.showingAll(WIDTH, HEIGHT);
            for (int step = 0; step < 60; step++) {
                camera = camera.zoomedAt(1.5, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
            }
            assertThat(camera.isAtClosest()).isTrue();
            assertThat(camera.referenceCardPixels()).isLessThanOrEqualTo(300);

            for (int step = 0; step < 120; step++) {
                camera = camera.zoomedAt(0.6, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
            }
            assertThat(camera.isAtFurthest()).isTrue();
            assertThat(camera.referenceCardPixels()).isGreaterThanOrEqualTo(20);
        }
    }

    @Nested
    @DisplayName("panning")
    class Panning {

        @Test
        @DisplayName("dragging moves the table with the cursor, not against it")
        void panFollowsTheHand() {
            // Grab the felt and pull right: the table comes right. The inverse of this is the
            // single most common way a pan feels wrong.
            TableCamera camera = new TableCamera(5000, 5000, 0.05);
            double wasAtCentre = camera.toTableX(WIDTH / 2.0, WIDTH);

            TableCamera panned = camera.pannedBy(100, 0);

            assertThat(panned.toTableX(WIDTH / 2.0, WIDTH)).isLessThan(wasAtCentre);
        }

        @Test
        @DisplayName("you cannot pan the table off the screen and lose it")
        void panningStaysOverTheTable() {
            TableCamera camera = new TableCamera(5000, 5000, 0.05);

            TableCamera far = camera.pannedBy(-100000, -100000);

            assertThat(far.centreX()).isBetween(0.0, (double) TablePosition.SPAN);
            assertThat(far.centreY()).isBetween(0.0, (double) TablePosition.SPAN);
        }
    }

    @Property(tries = 4000)
    void screenAndTableAgreeInBothDirections(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int centre,
            @ForAll @DoubleRange(min = 0.01, max = 0.4) double scale,
            @ForAll @IntRange(min = -4000, max = 4000) int screenX) {
        // The property the whole screen rests on. A card is drawn by going one way and clicked
        // by going the other, so a camera where those disagree is a table you cannot use.
        TableCamera camera = new TableCamera(centre, centre, scale);

        double table = camera.toTableX(screenX, width);
        double andBack = camera.toScreenX(table, width);

        assertThat(andBack).isCloseTo(screenX, within(0.001));
    }

    @Property(tries = 4000)
    void aCardIsAlwaysDrawnWhereItsPositionSays(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down,
            @ForAll @DoubleRange(min = 0.01, max = 0.5) double scale) {
        TableCamera camera = new TableCamera(5000, 5000, scale);
        TablePosition position = TablePosition.of(across, down);

        double screenX = camera.toScreenX(position.x(), width);
        double screenY = camera.toScreenY(position.y(), height);
        TablePosition found = TablePosition.clamped(
                (int) Math.round(camera.toTableX(screenX, width)),
                (int) Math.round(camera.toTableY(screenY, height)));

        // Within a pixel and a half's worth of table units. Both directions round to a whole
        // number, so the error is bounded by the pixel - and the tolerance has to be worked out
        // from the camera's own scale rather than the one asked for, because the camera clamps
        // it. Reading the requested value here is what made this fail the first time: at a
        // zoom the camera had refused, a pixel was worth thirty times what the test allowed.
        double tolerance = 1.0 + 1.5 / camera.scale();
        // Positions at the very edge come back clamped rather than a hair outside, so what is
        // asserted is that the round trip lands within a pixel of where it started *or* on the
        // edge it was already against.
        assertThat((double) found.x())
                .satisfiesAnyOf(
                        back -> assertThat(back).isCloseTo(across, within(tolerance)),
                        back -> assertThat(back).isIn(0.0, (double) TablePosition.SPAN));
        assertThat((double) found.y())
                .satisfiesAnyOf(
                        back -> assertThat(back).isCloseTo(down, within(tolerance)),
                        back -> assertThat(back).isIn(0.0, (double) TablePosition.SPAN));
    }

    @Property(tries = 3000)
    void panningNeverChangesHowBigThingsAre(
            @ForAll @IntRange(min = -3000, max = 3000) int pixelsX,
            @ForAll @IntRange(min = -3000, max = 3000) int pixelsY) {
        TableCamera camera = new TableCamera(5000, 5000, 0.06);

        TableCamera panned = camera.pannedBy(pixelsX, pixelsY);

        assertThat(panned.scale()).isEqualTo(camera.scale());
        assertThat(panned.referenceCardPixels()).isEqualTo(camera.referenceCardPixels());
    }
}
