package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a replay's frame costs, which is the number the whole replay design rests on.
 *
 * <p>A frame is built on the server, because the client is never sent the log. Folding the
 * whole game for each one measured at seventy-one milliseconds on a four-thousand-event game
 * - and at four frames a second that is a watcher costing a server most of its time. Half of
 * it was the log and the board being folded separately over the same events, which is now one
 * walk; the rest is why {@link GameSession#extendWith} exists, and why playback steps a frame
 * forward rather than rebuilding it.
 *
 * <p>So there are two numbers here and they are not the same budget. Opening a replay folds
 * it once and can afford to; every frame after that is one event and must be nothing.
 */
class ReplayFoldScaleTest {

    /** A long game. Four players over an hour do not come near this. */
    private static final int EVENTS = 4_000;

    /** What opening a replay may cost. Paid once, when somebody picks a game off the list. */
    private static final long OPENING_MILLIS = 250L;

    /**
     * And what one step of playback may cost.
     *
     * <p>Two milliseconds for one event, which is a hundred times the room it needs. What
     * this is guarding is the shape rather than the number: a change that made a frame cost a
     * fold again would land here rather than in a bug report about a server stuttering
     * whenever anybody watched a game back.
     */
    private static final long STEP_MILLIS = 2L;

    @Test
    @DisplayName("opening a four-thousand-event replay is a one-off, and each step after it is free")
    void steppingIsCheaperThanFolding() {
        GameSession played = GameFixtures.twoPlayerTable(60);
        for (int event = 0; event < EVENTS; event++) {
            // Life, because it is the cheapest event that still touches a seat and so
            // measures the fold rather than the event.
            played.submit(new GameEvent.LifeChanged(
                    GameFixtures.ALICE, GameFixtures.ALICE, event % 2 == 0 ? 1 : -1));
        }
        List<SessionRecord> records = played.records();
        assertThat(records).hasSizeGreaterThan(EVENTS);

        // Once to warm the classes, then the one that is measured.
        rebuild(records);
        long began = System.nanoTime();
        GameSession folded = rebuild(records);
        long opening = (System.nanoTime() - began) / 1_000_000L;

        assertThat(folded.state().seatStates().get(GameFixtures.ALICE).life())
                .isEqualTo(played.state().seatStates().get(GameFixtures.ALICE).life());
        assertThat(opening)
                .describedAs("opening a %s-event replay took %sms", records.size(), opening)
                .isLessThanOrEqualTo(OPENING_MILLIS);

        // And now the case playback is actually in: a game already open, one step to go.
        GameSession stepping = rebuild(records.subList(0, records.size() - 1));
        began = System.nanoTime();
        stepping.extendWith(records.subList(records.size() - 1, records.size()));
        long step = (System.nanoTime() - began) / 1_000_000L;

        assertThat(stepping.state()).isEqualTo(folded.state());
        assertThat(step)
                .describedAs("one step of a %s-event replay took %sms", records.size(), step)
                .isLessThanOrEqualTo(STEP_MILLIS);
    }

    private static GameSession rebuild(List<SessionRecord> records) {
        return GameSession.restore(
                List.of(GameFixtures.ALICE, GameFixtures.BOB), 40,
                GameFixtures.FIXED_SEED, UndoMode.shippedDefault(), records);
    }
}
