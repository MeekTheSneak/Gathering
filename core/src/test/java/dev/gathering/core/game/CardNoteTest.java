package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardNoteTest {

    @Test
    @DisplayName("a note is kept as written")
    void keptAsWritten() {
        assertThat(CardNote.clean("flying until end of turn")).isEqualTo("flying until end of turn");
    }

    @Test
    @DisplayName("nothing written is no note")
    void nothingIsNoNote() {
        assertThat(CardNote.clean(null)).isNull();
        assertThat(CardNote.clean("")).isNull();
        assertThat(CardNote.clean("   ")).isNull();
        assertThat(CardNote.clean("\n\t ")).isNull();
    }

    @Test
    @DisplayName("a note is one line, however it was typed")
    void oneLine() {
        assertThat(CardNote.clean("  flying\nand   vigilance\t ")).isEqualTo("flying and vigilance");
    }

    @Test
    @DisplayName("a formatting code is not writing")
    void formattingIsDropped() {
        assertThat(CardNote.clean("§cred§r note")).isEqualTo("credr note");
    }

    @Test
    @DisplayName("a note stops at the length a card can hold")
    void trimmedToLength() {
        String note = CardNote.clean("x".repeat(CardNote.LONGEST * 3));
        assertThat(note).hasSize(CardNote.LONGEST);
    }

    @Test
    @DisplayName("blank and nothing are the same note")
    void blankIsNothing() {
        assertThat(CardNote.same(null, "  ")).isTrue();
        assertThat(CardNote.same("hi", "hi ")).isTrue();
        assertThat(CardNote.same("hi", "ho")).isFalse();
    }

    @Property
    @Label("a cleaned note is always one line, short enough, and carries no formatting")
    void cleaningIsIdempotentAndSafe(@ForAll String written) {
        String once = CardNote.clean(written);
        if (once == null) {
            return;
        }
        assertThat(once).hasSizeLessThanOrEqualTo(CardNote.LONGEST);
        assertThat(once).doesNotContain("§");
        assertThat(once).doesNotContain("\n").doesNotContain("\t");
        assertThat(once.trim()).isEqualTo(once);
        assertThat(CardNote.clean(once))
                .as("cleaning something already clean must not change it")
                .isEqualTo(once);
    }
}
