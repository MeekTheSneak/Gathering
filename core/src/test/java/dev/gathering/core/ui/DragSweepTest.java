package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DragSweepTest {

    /** Slots holding cards; a click empties one, as putting its card in the deck does. */
    private final Set<Integer> cards = new HashSet<>(Set.of(1, 2, 4, 5));
    private final List<Integer> clicked = new ArrayList<>();

    private DragSweep.Step move(DragSweep sweep, int slot) {
        DragSweep.Step step = sweep.movedTo(slot, cards::contains);
        for (int click : step.clicks()) {
            clicked.add(click);
            cards.remove(click);
        }
        return step;
    }

    @Test
    @DisplayName("a sweep from a card over a row takes every card it passes, the first included, each once")
    void everyCardPassedGoesIn() {
        DragSweep sweep = DragSweep.armedOn(1);
        assertThat(move(sweep, 2).standDown()).isTrue();
        move(sweep, 3);
        move(sweep, 4);
        move(sweep, 4);
        move(sweep, 5);
        move(sweep, 4);

        assertThat(clicked).containsExactly(1, 2, 4, 5);
        assertThat(cards).isEmpty();
    }

    @Test
    @DisplayName("once sweeping, an empty slot passed over is the sweep's, so nothing drops the deck into it")
    void emptySlotsAreTheSweeps() {
        DragSweep sweep = DragSweep.armedOn(1);
        move(sweep, 2);
        DragSweep.Step overEmpty = move(sweep, 3);

        assertThat(overEmpty.clicks()).isEmpty();
        assertThat(overEmpty.ours()).isTrue();
    }

    @Test
    @DisplayName("a drag over empty slots is not a sweep until it reaches a card")
    void notASweepUntilACard() {
        DragSweep sweep = DragSweep.armedOn(0);
        assertThat(move(sweep, 3).ours()).isFalse();
        assertThat(sweep.sweeping()).isFalse();
        DragSweep.Step onACard = move(sweep, 4);

        assertThat(onACard.ours()).isTrue();
        assertThat(clicked).containsExactly(4);
    }

    @Test
    @DisplayName("a press that never moves off its slot is an ordinary click, left to the screen")
    void aStillPressIsNotASweep() {
        DragSweep sweep = DragSweep.armedOn(1);
        assertThat(move(sweep, 1).ours()).isFalse();
        assertThat(move(sweep, DragSweep.NONE).ours()).isFalse();
        assertThat(clicked).isEmpty();
    }
}
