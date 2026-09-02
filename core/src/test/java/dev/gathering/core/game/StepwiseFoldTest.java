package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stepping a replay forward gives the same board as folding it from the start.
 * <p>The whole reason {@code extendWith} exists is that it is cheaper, and the whole risk of
 * it is that cheaper and different are hard to tell apart by looking: a board a step at a
 * time and a board in one go would have to be compared to notice, and nothing on screen would
 * say which was wrong. So they are compared here, at every step, including across an undo -
 * which is the one place the two walks could plausibly part company.
 */
class StepwiseFoldTest {

    @Test
    @DisplayName("a board stepped to is the board folded to, at every step")
    void everyStepAgrees() {
        GameSession played = GameFixtures.twoPlayerTable(20);
        played.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));
        played.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.BOB, -3));
        played.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 2));
        // An undo, so the stored log carries a record that is no longer standing and a record
        // that is not an event at all.
        played.setUndoMode(UndoMode.FREE);
        played.undo(GameFixtures.BOB, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB));
        played.submit(new GameEvent.LifeChanged(GameFixtures.BOB, GameFixtures.ALICE, -1));

        List<SessionRecord> records = played.records();
        assertThat(records).hasSizeGreaterThan(5);

        GameSession stepping = rebuild(records.subList(0, 0));
        for (int step = 1; step <= records.size(); step++) {
            stepping.extendWith(records.subList(step - 1, step));
            GameSession whole = rebuild(records.subList(0, step));

            assertThat(stepping.state())
                    .describedAs("the board at step %s", step)
                    .isEqualTo(whole.state());
            assertThat(stepping.log())
                    .describedAs("the log at step %s", step)
                    .isEqualTo(whole.log());
        }
    }

    private static GameSession rebuild(List<SessionRecord> records) {
        return GameSession.restore(
                List.of(GameFixtures.ALICE, GameFixtures.BOB), 40,
                GameFixtures.FIXED_SEED, UndoMode.shippedDefault(), records);
    }
}
