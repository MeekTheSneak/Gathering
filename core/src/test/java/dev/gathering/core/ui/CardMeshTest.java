package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shape of a card, as arithmetic.
 * <p>Everything drawn on a card - the printed face, and the shine on a foil - is drawn from
 * the points this emits and no others. So "can the shine leave the card" is not a question
 * about clipping, angles or scissors. It is the question of whether any point here is outside
 * the card's outline, which is exactly what the first property below asks, at every card shape
 * and every mesh density.
 */
class CardMeshTest {

    /** One card's worth of quads, as a flat list of points. */
    private static List<float[]> pointsOf(float aspect, int columns, int rows, int arc) {
        List<float[]> points = new ArrayList<>();
        CardMesh.walk(aspect, columns, rows, arc, (u1, v1, u2, v2, u3, v3, u4, v4) -> {
            points.add(new float[] {u1, v1});
            points.add(new float[] {u2, v2});
            points.add(new float[] {u3, v3});
            points.add(new float[] {u4, v4});
        });
        return points;
    }

    @Property
    @Label("no point of the surface is ever outside the card")
    void nothingLeavesTheCard(
            @ForAll @FloatRange(min = 0.4f, max = 1.6f) float aspect,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 30) int columns,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 40) int rows) {
        for (float[] point : pointsOf(aspect, columns, rows, 8)) {
            assertThat(CardMesh.holds(point[0], point[1], aspect, 1.0e-4f))
                    .describedAs("a point at %s,%s is off the card", point[0], point[1])
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the corners are actually cut, not merely described as cut")
    void theCornersAreGone() {
        // The very corner of the bounding box is off a real card, and a mesh that still
        // covered it would be a rounded card that is not rounded.
        assertThat(CardMesh.holds(0f, 0f, 0.716f, 0f)).isFalse();
        assertThat(CardMesh.holds(1f, 0f, 0.716f, 0f)).isFalse();
        assertThat(CardMesh.holds(0f, 1f, 0.716f, 0f)).isFalse();
        assertThat(CardMesh.holds(1f, 1f, 0.716f, 0f)).isFalse();
        // And the middle of every edge is on it, which is what stops "rounded" meaning "oval".
        assertThat(CardMesh.holds(0f, 0.5f, 0.716f, 0f)).isTrue();
        assertThat(CardMesh.holds(0.5f, 0f, 0.716f, 0f)).isTrue();
    }

    @Test
    @DisplayName("the corner cut is a circle rather than an oval")
    void theCutIsCircular() {
        // Across the width and down the height by the same number of pixels, which for a card
        // taller than it is wide means two different fractions.
        float aspect = 0.716f;
        assertThat(CardMesh.cornerAcross()).isEqualTo(CardMesh.CORNER);
        assertThat(CardMesh.cornerDown(aspect)).isEqualTo(CardMesh.CORNER * aspect);
    }

    @Property
    @Label("the quads cover the card and no more of it than there is")
    void theSurfaceIsCovered(@ForAll @FloatRange(min = 0.5f, max = 1.0f) float aspect) {
        double covered = 0;
        List<float[]> points = pointsOf(aspect, 24, 32, 12);
        for (int at = 0; at < points.size(); at += 4) {
            covered += Math.abs(triangle(points, at, at + 1, at + 2))
                    + Math.abs(triangle(points, at, at + 2, at + 3));
        }
        // Under by a hair, because a curve drawn in twelve straight pieces cuts the corners
        // slightly inside the arc. Never over: over would mean covering card that is not there.
        assertThat(covered).isLessThanOrEqualTo(CardMesh.area(aspect) + 1.0e-6);
        assertThat(covered).isGreaterThan(CardMesh.area(aspect) * 0.995);
    }

    private static double triangle(List<float[]> points, int a, int b, int c) {
        float[] first = points.get(a);
        float[] second = points.get(b);
        float[] third = points.get(c);
        return 0.5 * ((second[0] - first[0]) * (third[1] - first[1])
                - (second[1] - first[1]) * (third[0] - first[0]));
    }

    @Property
    @Label("every quad is wound the same way, so none of them faces away")
    void everyQuadFacesTheSameWay(@ForAll @FloatRange(min = 0.5f, max = 1.0f) float aspect) {
        List<float[]> points = pointsOf(aspect, 12, 16, 10);
        for (int at = 0; at < points.size(); at += 4) {
            double signed = triangle(points, at, at + 1, at + 2);
            if (Math.abs(signed) < 1.0e-9) {
                continue;
            }
            assertThat(signed)
                    .describedAs("a quad at point %s is wound the other way and would be culled", at)
                    .isNegative();
        }
    }
}
