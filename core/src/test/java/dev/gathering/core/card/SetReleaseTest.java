package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which set a server that has not chosen one is running. */
class SetReleaseTest {

    private static SetRelease set(String code, String type, String released) {
        return new SetRelease(code, code.toUpperCase(java.util.Locale.ROOT), type, released,
                false, 300);
    }

    @Test
    @DisplayName("the current set is the newest premier set that has actually come out")
    void theNewestOutIsCurrent() {
        List<SetRelease> sets = List.of(
                set("trk", "expansion", "2026-11-13"),
                set("hob", "expansion", "2026-08-14"),
                set("msh", "expansion", "2026-06-26"));

        assertThat(SetRelease.current(sets, "2026-08-25"))
                .map(SetRelease::code).contains("hob");
    }

    @Test
    @DisplayName("a set announced for December is not what August is selling")
    void futureSetsAreNotCurrent() {
        // Scryfall lists sets months before release, and lists them first.
        List<SetRelease> sets = List.of(
                set("trk", "expansion", "2026-11-13"),
                set("hob", "expansion", "2026-08-14"));

        // The day before its release date it is not out, and neither is anything else here.
        assertThat(SetRelease.current(sets, "2026-08-13")).isEmpty();
        assertThat(SetRelease.current(sets, "2026-08-14"))
                .map(SetRelease::code).contains("hob");
        assertThat(SetRelease.current(sets, "2026-11-13"))
                .map(SetRelease::code).contains("trk");
    }

    @Test
    @DisplayName("only the kinds of set that get boosters printed for them")
    void onlyPremierSetsCount() {
        // Every one of these came out after the expansion and none of them is what anybody
        // means by "the current set": a commander deck, a masters reprint set, a token sheet,
        // a promo pack, and an Arena-only set that does not exist on paper.
        List<SetRelease> sets = List.of(
                set("trc", "commander", "2026-11-13"),
                set("ttrk", "token", "2026-11-13"),
                set("sds", "masterpiece", "2026-11-13"),
                set("mh4", "draft_innovation", "2026-10-01"),
                set("pfoo", "promo", "2026-09-30"),
                new SetRelease("y26", "Alchemy", "alchemy", "2026-09-01", true, 30),
                set("hob", "expansion", "2026-08-14"));

        assertThat(SetRelease.current(sets, "2026-12-01"))
                .map(SetRelease::code).contains("hob");
    }

    @Test
    @DisplayName("a core set is as premier as an expansion")
    void coreSetsCount() {
        List<SetRelease> sets = List.of(
                set("m21", "core", "2020-07-03"),
                set("iko", "expansion", "2020-04-24"));

        assertThat(SetRelease.current(sets, "2020-08-01"))
                .map(SetRelease::code).contains("m21");
    }

    @Test
    @DisplayName("two sets on the same day settle the same way every time")
    void tiesDoNotWander() {
        List<SetRelease> one = List.of(
                set("bbb", "expansion", "2026-08-14"), set("aaa", "expansion", "2026-08-14"));
        List<SetRelease> other = List.of(
                set("aaa", "expansion", "2026-08-14"), set("bbb", "expansion", "2026-08-14"));

        assertThat(SetRelease.current(one, "2026-09-01"))
                .isEqualTo(SetRelease.current(other, "2026-09-01"));
    }

    @Test
    @DisplayName("nothing to pick from is an answer, not a crash")
    void anEmptyListIsEmpty() {
        assertThat(SetRelease.current(List.of(), "2026-08-25")).isEmpty();
        assertThat(SetRelease.current(null, "2026-08-25")).isEmpty();
        assertThat(SetRelease.current(List.of(set("hob", "expansion", "")), "2026-08-25"))
                .isEmpty();
    }

    @Test
    @DisplayName("a set code Scryfall would not write is not one this will use")
    void theCodeStillHasToBeACode() {
        // It goes into a URL and a file path the moment it is chosen, so it goes through the
        // same rule everything else does rather than being trusted for arriving from Scryfall.
        List<SetRelease> sets = List.of(
                set("../../etc", "expansion", "2026-08-20"),
                set("hob", "expansion", "2026-08-14"));

        assertThat(SetRelease.current(sets, "2026-08-25"))
                .map(SetRelease::code).contains("hob");
    }
}
