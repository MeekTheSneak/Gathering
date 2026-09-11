package dev.gathering.core.reward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What a pack author may write down, and what is refused with the field named. */
class RewardDefinitionTest {

    private static RewardDefinition sound() {
        return new RewardDefinition("boss_drop", "j25", "default", Optional.of("W"), 2, List.of());
    }

    @Nested
    @DisplayName("a sound definition")
    class Sound {

        @Test
        @DisplayName("has nothing wrong with it")
        void noProblems() {
            assertThat(sound().problems()).isEmpty();
            assertThat(sound().isSound()).isTrue();
        }

        @Test
        @DisplayName("may leave the color out")
        void colorIsOptional() {
            assertThat(new RewardDefinition("x", "j25", "default", Optional.empty(), 1, List.of())
                    .problems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("a definition with an address in it")
    class Addresses {

        @Test
        @DisplayName("is refused wherever the address is")
        void refusedEverywhere() {
            // The whole point of the bound. Nothing here would fetch one - but a field that
            // silently accepts a URL today is a field somebody wires to a fetch in two years.
            for (String nasty : List.of(
                    "http://example.test/pack.json",
                    "//example.test",
                    "../../etc/passwd",
                    "C:\\Windows\\System32")) {
                assertThat(new RewardDefinition(nasty, "j25", "default", Optional.empty(), 1,
                        List.of()).problems())
                        .as("id = %s", nasty)
                        .isNotEmpty();
                assertThat(new RewardDefinition("x", nasty, "default", Optional.empty(), 1,
                        List.of()).problems())
                        .as("set = %s", nasty)
                        .isNotEmpty();
                assertThat(new RewardDefinition("x", "j25", nasty, Optional.empty(), 1,
                        List.of()).problems())
                        .as("product = %s", nasty)
                        .isNotEmpty();
                assertThat(new RewardDefinition("x", "j25", "default", Optional.empty(), 1,
                        List.of(nasty)).problems())
                        .as("required_mods = %s", nasty)
                        .isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("a problem")
    class Problems {

        @Test
        @DisplayName("names the field it is about")
        void namesItsField() {
            // A pack author reading "something is wrong" has to find it themselves; one
            // reading "count: is 0" does not.
            List<RewardDefinition.Problem> problems =
                    new RewardDefinition("x", "j25", "default", Optional.empty(), 0, List.of())
                            .problems();
            assertThat(problems).isNotEmpty();
            assertThat(problems.getFirst().field()).isEqualTo("count");
            assertThat(problems.getFirst().toString()).contains("count");
        }

        @Test
        @DisplayName("is reported for every field at once")
        void allOfThemAtOnce() {
            // Somebody fixing one problem per reload gives up on the third.
            List<RewardDefinition.Problem> problems =
                    new RewardDefinition("", "", "", Optional.of("Q"), 999, List.of()).problems();
            assertThat(problems.stream().map(RewardDefinition.Problem::field))
                    .contains("id", "set", "product", "color", "count");
        }

        @Test
        @DisplayName("catches a count outside the bound rather than clamping it")
        void countIsBounded() {
            assertThat(new RewardDefinition("x", "j25", "default", Optional.empty(),
                    RewardDefinition.MOST_AT_ONCE + 1, List.of()).problems()).isNotEmpty();
            assertThat(new RewardDefinition("x", "j25", "default", Optional.empty(),
                    RewardDefinition.MOST_AT_ONCE, List.of()).problems()).isEmpty();
        }

        @Test
        @DisplayName("catches a name longer than the bound")
        void namesAreBounded() {
            String tooLong = "a".repeat(RewardDefinition.LONGEST_NAME + 1);
            assertThat(new RewardDefinition("x", tooLong, "default", Optional.empty(), 1,
                    List.of()).problems()).isNotEmpty();
        }

        @Test
        @DisplayName("catches a color that is not one")
        void colorsAreTheFive() {
            for (String colour : List.of("W", "U", "B", "R", "G", "g")) {
                assertThat(new RewardDefinition("x", "j25", "default", Optional.of(colour), 1,
                        List.of()).problems()).as("%s", colour).isEmpty();
            }
            for (String notAColour : List.of("Q", "WU", "", "1")) {
                assertThat(new RewardDefinition("x", "j25", "default", Optional.of(notAColour), 1,
                        List.of()).problems()).as("%s", notAColour).isNotEmpty();
            }
        }

        @Test
        @DisplayName("catches a definition depending on more mods than anybody would")
        void requiredModsAreBounded() {
            List<String> many = new java.util.ArrayList<>();
            for (int at = 0; at <= RewardDefinition.MOST_REQUIRED_MODS; at++) {
                many.add("mod" + at);
            }
            assertThat(new RewardDefinition("x", "j25", "default", Optional.empty(), 1, many)
                    .problems()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("required mods")
    class RequiredMods {

        @Test
        @DisplayName("let a reward through when they are all installed")
        void allPresent() {
            RewardDefinition needs = new RewardDefinition("x", "j25", "default",
                    Optional.empty(), 1, List.of("cataclysm", "create"));
            assertThat(needs.appliesWith(Set.of("cataclysm", "create", "jei")::contains)).isTrue();
        }

        @Test
        @DisplayName("hold it back when one is missing, without it being an error")
        void oneMissing() {
            // A pack shipping rewards for four boss mods expects people to install two.
            // Refusing to load would punish exactly the packs being careful about it.
            RewardDefinition needs = new RewardDefinition("x", "j25", "default",
                    Optional.empty(), 1, List.of("cataclysm", "create"));
            assertThat(needs.isSound()).isTrue();
            assertThat(needs.appliesWith(Set.of("cataclysm")::contains)).isFalse();
        }

        @Test
        @DisplayName("are not required at all when none are named")
        void noneNamed() {
            assertThat(sound().appliesWith(mod -> false)).isTrue();
        }
    }
}
