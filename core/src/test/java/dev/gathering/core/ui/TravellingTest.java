package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.Test;

class TravellingTest {

    private static final Rect FROM = new Rect(0, 0, 20, 28);
    private static final Rect TO = new Rect(200, 100, 40, 56);

    @Test
    void itStartsWhereItStartedAndEndsWhereItWasGoing() {
        assertThat(Travelling.between(FROM, TO, 0f)).isEqualTo(FROM);
        assertThat(Travelling.between(FROM, TO, 1f)).isEqualTo(TO);
    }

    @Test
    void halfWayIsHalfWay() {
        Rect middle = Travelling.between(FROM, TO, 0.5f);
        assertThat(middle.x()).isEqualTo(100);
        assertThat(middle.y()).isEqualTo(50);
        assertThat(middle.width()).isEqualTo(30);
    }

    /** A card that grows or shrinks to nothing part way across is a card that vanished. */
    @Property
    void aCardInFlightIsAlwaysACard(@ForAll @FloatRange(min = 0f, max = 1f) float at) {
        Rect where = Travelling.between(FROM, TO, at);
        assertThat(where.width()).isGreaterThan(0);
        assertThat(where.height()).isGreaterThan(0);
    }

    @Property
    void itNeverGoesBackwards(@ForAll @FloatRange(min = 0f, max = 1f) float at) {
        Rect where = Travelling.between(FROM, TO, at);
        assertThat(where.x()).isBetween(FROM.x(), TO.x());
        assertThat(where.y()).isBetween(FROM.y(), TO.y());
    }

    @Property
    void pastTheEndIsTheEndAndBeforeTheStartIsTheStart(
            @ForAll @FloatRange(min = -4f, max = 5f) float at) {
        assertThat(Travelling.eased(at)).isBetween(0f, 1f);
    }

    @Test
    void aJourneyToNowhereLeavesTheCardWhereItWas() {
        assertThat(Travelling.between(FROM, Rect.NONE, 0.5f)).isEqualTo(FROM);
        assertThat(Travelling.between(Rect.NONE, TO, 0.5f)).isEqualTo(TO);
    }
}
