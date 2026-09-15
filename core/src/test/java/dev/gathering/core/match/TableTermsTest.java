package dev.gathering.core.match;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.match.TableTerms.Note;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a table says about itself to somebody sitting down. */
class TableTermsTest {

    @Test
    @DisplayName("Modern best of three and Commander best of one are the usual terms and say nothing")
    void usualTermsSayNothing() {
        TableTerms modern = new TableTerms("modern", 3, 1, false, false, false, 0);
        TableTerms commander = new TableTerms("commander", 1, 1, false, false, false, 0);
        assertThat(modern.usualLength()).isEqualTo(3);
        assertThat(commander.usualLength()).isEqualTo(1);
        assertThat(modern.notes()).isEmpty();
        assertThat(commander.notes()).isEmpty();
        assertThat(modern.unusual()).isFalse();
    }

    @Test
    @DisplayName("for keeps comes first, and marks the table")
    void forKeepsFirst() {
        TableTerms terms = new TableTerms("modern", 1, 1, false, true, false, 0);
        assertThat(terms.notes()).containsExactly(Note.FOR_KEEPS, Note.UNUSUAL_LENGTH);
        assertThat(terms.unusual()).isTrue();
    }

    @Test
    @DisplayName("free play has no format and no usual length to differ from")
    void freePlay() {
        TableTerms terms = new TableTerms("", 1, 1, true, false, false, 0);
        assertThat(terms.format()).isEmpty();
        assertThat(terms.notes()).containsExactly(Note.FREE_PLAY);
        assertThat(terms.unusual()).isTrue();
    }

    @Test
    @DisplayName("an event's length is the event's own, and an event or practice table alone is not unusual")
    void eventsAndPractice() {
        TableTerms event = new TableTerms("modern", 1, 1, false, false, false, 4);
        assertThat(event.notes()).containsExactly(Note.EVENT);
        assertThat(event.unusual()).isFalse();
        TableTerms practice = new TableTerms("commander", 1, 1, false, false, true, 0);
        assertThat(practice.notes()).containsExactly(Note.PRACTICE);
        assertThat(practice.unusual()).isFalse();
    }

    @Test
    @DisplayName("a format this game does not know is not free play, and has no length to call unusual")
    void unknownFormat() {
        TableTerms terms = new TableTerms("not-a-format", 5, 2, false, false, false, 0);
        assertThat(terms.format()).isEmpty();
        assertThat(terms.notes()).isEmpty();
    }
}
