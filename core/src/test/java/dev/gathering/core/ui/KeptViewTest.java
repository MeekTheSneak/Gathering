package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The view put back when a screen closes.
 * <p>Reported as "where you were looking before you open a pack, open a menu, use a block,
 * etc, needs to be kept the same after you exit that menu".
 */
class KeptViewTest {

    @Test
    @DisplayName("gives back the view the screen opened over")
    void openAndClose() {
        KeptView kept = new KeptView();
        kept.opened(Facing.of(90f, -20f));
        assertThat(kept.isKeeping()).isTrue();
        assertThat(kept.closed()).contains(Facing.of(90f, -20f));
        assertThat(kept.isKeeping()).isFalse();
    }

    @Test
    @DisplayName("keeps the outermost view, not the one a detour opened over")
    void detoursKeepTheFirstView() {
        KeptView kept = new KeptView();
        kept.opened(Facing.of(90f, 0f));
        kept.opened(Facing.of(-45f, 30f));
        kept.opened(Facing.of(0f, 0f));
        assertThat(kept.closed()).contains(Facing.of(90f, 0f));
    }

    @Test
    @DisplayName("gives a view back once, and has nothing to give on the way out of nothing")
    void onlyOnce() {
        KeptView kept = new KeptView();
        kept.opened(Facing.of(12f, 3f));
        assertThat(kept.closed()).isPresent();
        assertThat(kept.closed()).isEmpty();
    }

    @Test
    @DisplayName("drops a view rather than putting it back over something it did not open")
    void forgotten() {
        KeptView kept = new KeptView();
        kept.opened(Facing.of(12f, 3f));
        kept.forget();
        assertThat(kept.isKeeping()).isFalse();
        assertThat(kept.closed()).isEmpty();
    }

    @Test
    @DisplayName("writes the same direction down the same way however it arrives")
    void oneWayOfWritingAView() {
        assertThat(Facing.of(350f, 0f)).isEqualTo(Facing.of(-10f, 0f));
        assertThat(Facing.of(-370f, 0f)).isEqualTo(Facing.of(-10f, 0f));
        assertThat(Facing.of(0f, 140f)).isEqualTo(Facing.of(0f, Facing.FURTHEST_DOWN));
        assertThat(Facing.of(Float.NaN, Float.NaN).yaw()).isZero();
        assertThat(Facing.turnBetween(179f, -177f)).isEqualTo(4f);
        assertThat(Facing.turnBetween(-177f, 179f)).isEqualTo(-4f);
    }
}
