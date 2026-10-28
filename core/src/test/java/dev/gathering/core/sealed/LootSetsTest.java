package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which sets a server's packs are found from. */
class LootSetsTest {

    private static final List<String> RECENT = List.of("hob", "msh", "sos");

    @Test
    @DisplayName("a seasonal server gets exactly the set it named")
    void namedSetsOnly() {
        assertThat(LootSets.wanted(List.of("blb"), Optional.of("hob"), RECENT))
                .containsExactly("blb");
    }

    @Test
    @DisplayName("an era server gets its block, in the order it wrote it")
    void namedSetsKeepTheirOrder() {
        assertThat(LootSets.wanted(List.of("otj", "blb", "dsk"), Optional.empty(), List.of()))
                .containsExactly("otj", "blb", "dsk");
    }

    @Test
    @DisplayName("a server that said nothing gets whatever is current")
    void currentIsTheDefault() {
        assertThat(LootSets.wanted(List.of("current"), Optional.of("hob"), RECENT))
                .containsExactly("hob");
    }

    @Test
    @DisplayName("a pinned current set is the current set")
    void aPinnedCurrentSetCounts() {
        // The bug this exists for: a server that names its own current set and asks for
        // "current" loot must draw from that set, not from nothing. The two settings are
        // about the same thing and they have to agree.
        assertThat(LootSets.wanted(List.of("current"), Optional.of("blb"), List.of()))
                .containsExactly("blb");
    }

    @Test
    @DisplayName("recent brings the last few, newest first")
    void recentBringsSeveral() {
        assertThat(LootSets.wanted(List.of("recent"), Optional.of("hob"), RECENT))
                .containsExactly("hob", "msh", "sos");
    }

    @Test
    @DisplayName("a set named outright comes first and is not repeated")
    void namedComesFirstAndOnce() {
        assertThat(LootSets.wanted(List.of("msh", "recent"), Optional.of("hob"), RECENT))
                .containsExactly("msh", "hob", "sos");
    }

    @Test
    @DisplayName("both words together is one list, not two")
    void currentAndRecentAgree() {
        assertThat(LootSets.wanted(List.of("current", "recent"), Optional.of("hob"), RECENT))
                .containsExactly("hob", "msh", "sos");
    }

    @Test
    @DisplayName("nothing current and nothing recent is nothing, not a crash")
    void nothingIsNothing() {
        assertThat(LootSets.wanted(List.of("current"), Optional.empty(), List.of())).isEmpty();
        assertThat(LootSets.wanted(List.of(), Optional.of("hob"), RECENT)).isEmpty();
        assertThat(LootSets.wanted(null, Optional.empty(), null)).isEmpty();
        assertThat(LootSets.wanted(List.of("recent"), Optional.of("hob"), null))
                .isEmpty();
    }

    @Test
    @DisplayName("only a config with a word in it needs anybody to go and look")
    void onlyWordsNeedTheList() {
        assertThat(LootSets.needsTheReleaseList(List.of("blb", "dsk"))).isFalse();
        assertThat(LootSets.needsTheReleaseList(List.of("current"))).isTrue();
        assertThat(LootSets.needsTheReleaseList(List.of("recent"))).isTrue();
        assertThat(LootSets.needsTheReleaseList(null)).isFalse();

        assertThat(LootSets.needsMoreThanTheNewest(List.of("current"))).isFalse();
        assertThat(LootSets.needsMoreThanTheNewest(List.of("recent"))).isTrue();
    }

    @Test
    @DisplayName("\"all\" takes every release there is")
    void allTakesEverything() {
        assertThat(LootSets.wanted(List.of("all"), Optional.of("hob"), RECENT))
                .containsExactlyElementsOf(RECENT);
        assertThat(LootSets.wantsEverySet(List.of("all"))).isTrue();
        assertThat(LootSets.needsTheReleaseList(List.of("all"))).isTrue();
        assertThat(LootSets.needsMoreThanTheNewest(List.of("all"))).isTrue();
    }

    @Test
    @DisplayName("\"all\" beside a named set still puts the named one first")
    void allKeepsTheNamedSetFirst() {
        // The order is what a set is picked by walking, so a config that names its own set
        // and then asks for everything must not have that set arrive somewhere in the middle.
        assertThat(LootSets.wanted(List.of("msh", "all"), Optional.empty(), RECENT).get(0))
                .isEqualTo("msh");
    }

    @Test
    @DisplayName("\"all\" and \"recent\" together list each set once")
    void allAndRecentDoNotDouble() {
        List<String> both = LootSets.wanted(List.of("all", "recent"), Optional.empty(), RECENT);
        assertThat(both).doesNotHaveDuplicates().containsExactlyElementsOf(RECENT);
    }
}
