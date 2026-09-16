package dev.gathering.core.tutorial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the lesson has seen what it asked for.
 * <p>The case that matters is the learner doing two things at once, because that is what learners do.
 */
class TutorialEvidenceTest {

    @Test
    @DisplayName("one card gaining is enough, however many others lose")
    void oneGainIsEnough() {
        // Tapped one, untapped another: the total is unchanged and the step is still done.
        assertThat(TutorialEvidence.anyRose(
                Map.of("a", 1, "b", 0), Map.of("a", 0, "b", 1))).isTrue();
    }

    @Test
    @DisplayName("nothing gaining is not enough, however much moves about")
    void losingIsNotDoing() {
        assertThat(TutorialEvidence.anyRose(Map.of("a", 1, "b", 1), Map.of("a", 0, "b", 0))).isFalse();
        assertThat(TutorialEvidence.anyRose(Map.of("a", 1), Map.of("a", 1))).isFalse();
        assertThat(TutorialEvidence.anyRose(Map.of(), Map.of())).isFalse();
    }

    @Test
    @DisplayName("a card that was not there before can satisfy it too")
    void somethingNewCounts() {
        // Played a card and tapped it in one go, or made a token that came in tapped.
        assertThat(TutorialEvidence.anyRose(Map.of(), Map.of("fresh", 1))).isTrue();
        // But one that arrives doing nothing does not.
        assertThat(TutorialEvidence.anyRose(Map.of(), Map.of("fresh", 0))).isFalse();
    }

    @Test
    @DisplayName("nothing at all on the board is never evidence")
    void anEmptyBoardSaysNothing() {
        assertThat(TutorialEvidence.anyRose(Map.of("a", 0), null)).isFalse();
        assertThat(TutorialEvidence.anyRose(null, Map.of())).isFalse();
        // And a first reading with nothing to compare against still counts a gain.
        assertThat(TutorialEvidence.anyRose(null, Map.of("a", 2))).isTrue();
    }
}
