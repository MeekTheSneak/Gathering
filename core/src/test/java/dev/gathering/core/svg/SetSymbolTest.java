package dev.gathering.core.svg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading a set symbol and drawing it as a silhouette.
 *
 * <p>The symbols here are made up. Their shapes are not: every construction below appears in
 * the real ones - a hole that needs a winding rule, a path a group has switched off, a
 * viewBox three times wider than it is tall - and the mod redistributes nobody's artwork.
 */
class SetSymbolTest {

    private static final int SIZE = 32;

    @Test
    @DisplayName("a square comes out solid, with nothing outside it")
    void aSquareIsFilled() throws Exception {
        byte[] mask = SetSymbol.read(svg("0 0 10 10",
                "<path d='M2 2 L8 2 L8 8 L2 8 Z'/>")).mask(SIZE);

        assertThat(alphaAt(mask, SIZE / 2, SIZE / 2)).isEqualTo(255);
        assertThat(alphaAt(mask, 1, 1)).isZero();
        assertThat(alphaAt(mask, SIZE - 2, SIZE - 2)).isZero();
    }

    @Test
    @DisplayName("a hole is a hole under both winding rules that say so")
    void holesAreHoles() throws Exception {
        // An outer ring clockwise and an inner ring the same way round. Nonzero fills the
        // middle back in; even-odd leaves the hole. Alpha's symbol is exactly this: an A cut
        // out of a stack of cards.
        String rings = "<path d='M0 0 L10 0 L10 10 L0 10 Z M3 3 L7 3 L7 7 L3 7 Z'/>";

        byte[] nonzero = SetSymbol.read(svg("0 0 10 10", rings)).mask(SIZE);
        byte[] evenOdd = SetSymbol.read(
                svg("0 0 10 10", "<path fill-rule='evenodd' d='M0 0 L10 0 L10 10 L0 10 Z "
                        + "M3 3 L7 3 L7 7 L3 7 Z'/>")).mask(SIZE);

        assertThat(alphaAt(nonzero, SIZE / 2, SIZE / 2)).isEqualTo(255);
        assertThat(alphaAt(evenOdd, SIZE / 2, SIZE / 2)).isZero();
        // And the ring itself is there in both.
        assertThat(alphaAt(nonzero, 2, SIZE / 2)).isEqualTo(255);
        assertThat(alphaAt(evenOdd, 2, SIZE / 2)).isEqualTo(255);
    }

    @Test
    @DisplayName("a shape a group has switched off is not part of the silhouette")
    void anUnfilledPathIsNotDrawn() throws Exception {
        // Real symbols do this: War of the Spark carries a path under a group with no fill,
        // which a renderer draws nothing for. Filling it would put a slab over the symbol.
        SetSymbol symbol = SetSymbol.read(svg("0 0 10 10",
                "<g fill='none'><path d='M0 0 L10 0 L10 10 L0 10 Z'/>"
                        + "<g fill='#000'><path d='M4 4 L6 4 L6 6 L4 6 Z'/></g></g>"));

        assertThat(symbol.outlines()).hasSize(1);
        byte[] mask = symbol.mask(SIZE);
        assertThat(alphaAt(mask, SIZE / 2, SIZE / 2)).isEqualTo(255);
        assertThat(alphaAt(mask, 1, 1)).isZero();
    }

    @Test
    @DisplayName("a wide symbol keeps its shape and sits in the middle")
    void aWideSymbolIsNotStretched() throws Exception {
        byte[] mask = SetSymbol.read(svg("0 0 30 10",
                "<path d='M0 0 L30 0 L30 10 L0 10 Z'/>")).mask(SIZE);

        // A third as tall as it is wide, centred: the top and bottom thirds are empty and the
        // middle is full across.
        assertThat(alphaAt(mask, SIZE / 2, 1)).isZero();
        assertThat(alphaAt(mask, SIZE / 2, SIZE - 2)).isZero();
        assertThat(alphaAt(mask, 1, SIZE / 2)).isEqualTo(255);
        assertThat(alphaAt(mask, SIZE - 2, SIZE / 2)).isEqualTo(255);
    }

    @Test
    @DisplayName("two shapes that meet leave no pale line down the join")
    void abuttingShapesHaveNoSeam() throws Exception {
        // Two halves meeting on a boundary that falls between pixels. Each covers half of the
        // pixels the join runs through, and a symbol drawn in several paths - which the real
        // ones are - would otherwise have a ghost line down it.
        byte[] mask = SetSymbol.read(svg("0 0 10 10",
                "<path d='M0 0 L5.5 0 L5.5 10 L0 10 Z'/>"
                        + "<path d='M5.5 0 L10 0 L10 10 L5.5 10 Z'/>")).mask(SIZE);

        for (int x = 1; x < SIZE - 1; x++) {
            assertThat(alphaAt(mask, x, SIZE / 2)).as("at " + x).isEqualTo(255);
        }
    }

    @Test
    @DisplayName("a symbol drawn from curves comes out round rather than faceted")
    void curvesAreSmooth() throws Exception {
        // A circle written the way an SVG writes one: two arcs.
        byte[] mask = SetSymbol.read(svg("0 0 10 10",
                "<path d='M1 5 A4 4 0 1 0 9 5 A4 4 0 1 0 1 5 Z'/>")).mask(64);

        assertThat(alphaAt(mask, 32, 32, 64)).isEqualTo(255);
        // The corners of the square are outside a circle inscribed in it.
        assertThat(alphaAt(mask, 4, 4, 64)).isZero();
        assertThat(alphaAt(mask, 59, 59, 64)).isZero();
        // And the edge is soft rather than a staircase.
        int somewhereBetween = 0;
        for (int x = 0; x < 64; x++) {
            int alpha = alphaAt(mask, x, 32, 64);
            if (alpha > 10 && alpha < 245) {
                somewhereBetween++;
            }
        }
        assertThat(somewhereBetween).as("a hard edge everywhere means no smoothing").isPositive();
    }

    @Test
    @DisplayName("a document that reaches outside itself is refused")
    void externalEntitiesAreRefused() {
        // The one thing a file off the network must never be able to do.
        String attack = "<?xml version='1.0'?><!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"
                + "<svg viewBox='0 0 10 10'><path d='M0 0 L1 0 L1 1 Z'/></svg>";

        assertThatThrownBy(() -> SetSymbol.read(attack))
                .isInstanceOf(SvgException.class)
                .hasMessageContaining("not readable XML");
    }

    @Test
    @DisplayName("anything this cannot draw says so rather than drawing part of it")
    void unknownShapesAreRefused() {
        assertThatThrownBy(() -> SetSymbol.read(svg("0 0 10 10", "<circle cx='5' cy='5' r='4'/>")))
                .isInstanceOf(SvgException.class)
                .hasMessageContaining("circle");
        assertThatThrownBy(() -> SetSymbol.read("<svg><path d='M0 0 L1 1 Z'/></svg>"))
                .isInstanceOf(SvgException.class)
                .hasMessageContaining("viewBox");
        assertThatThrownBy(() -> SetSymbol.read("not xml at all"))
                .isInstanceOf(SvgException.class);
        assertThatThrownBy(() -> SetSymbol.read(""))
                .isInstanceOf(SvgException.class);
        assertThatThrownBy(() -> SetSymbol.read("<notsvg viewBox='0 0 1 1'/>"))
                .isInstanceOf(SvgException.class)
                .hasMessageContaining("does not begin with an <svg>");
    }

    @Test
    @DisplayName("a symbol with nothing in it draws nothing rather than failing")
    void anEmptySymbolIsBlank() throws Exception {
        SetSymbol symbol = SetSymbol.read(svg("0 0 10 10", "<title>Nothing</title>"));

        assertThat(symbol.isEmpty()).isTrue();
        assertThat(symbol.mask(SIZE)).containsOnly((byte) 0);
    }

    // ------------------------------------------------------------------- bits

    private static String svg(String viewBox, String body) {
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='" + viewBox + "'>"
                + body + "</svg>";
    }

    private static int alphaAt(byte[] mask, int x, int y) {
        return alphaAt(mask, x, y, SIZE);
    }

    private static int alphaAt(byte[] mask, int x, int y, int size) {
        return mask[y * size + x] & 0xFF;
    }
}
