package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Searching a pile by typing. */
class PileSearchTest {

    private static final List<String> ELF = List.of(
            "Llanowar Elves", "Creature — Elf Druid", "{T}: Add {G}.");

    @Test
    @DisplayName("a blank search is everything")
    void blank() {
        assertThat(PileSearch.words("   ")).isEmpty();
        assertThat(PileSearch.matches(PileSearch.words(null), ELF)).isTrue();
    }

    @Test
    @DisplayName("every word has to be somewhere on the card, in any order and any case")
    void everyWord() {
        assertThat(PileSearch.matches(PileSearch.words("druid LLANOWAR"), ELF)).isTrue();
        assertThat(PileSearch.matches(PileSearch.words("elf add"), ELF)).isTrue();
        assertThat(PileSearch.matches(PileSearch.words("elf goblin"), ELF)).isFalse();
    }

    @Test
    @DisplayName("part of a word finds it, so a search can be typed half way")
    void partOfAWord() {
        assertThat(PileSearch.matches(PileSearch.words("llan"), ELF)).isTrue();
    }

    @Test
    @DisplayName("a card nothing is known about matches only a blank search")
    void unknown() {
        assertThat(PileSearch.matches(PileSearch.words("elf"), List.of())).isFalse();
        assertThat(PileSearch.matches(PileSearch.words(""), List.of())).isTrue();
    }
}
