package dev.gathering.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The three ways people actually run this, as settings rather than as modes. */
class ConfigProfileTest {

    @Nested
    @DisplayName("every profile")
    class EveryProfile {

        @Test
        @DisplayName("only names settings that exist")
        void namesOnlyRealKeys() {
            // The failure this prevents is the quietest one there is: a profile naming a key
            // that was renamed sets nothing, reports success, and leaves the operator with a
            // server that is not what they asked for and no sign of why.
            for (ConfigProfile profile : ConfigProfile.all()) {
                assertThat(GatheringConfig.knownKeys())
                        .as("keys named by the %s profile", profile.id())
                        .containsAll(profile.settings().keySet());
            }
        }

        @Test
        @DisplayName("sets values the config will actually take")
        void valuesAreAccepted() {
            // Parsing is not the same as taking: a value of the wrong shape for its key parses
            // as TOML and is then ignored with a note. A profile that produced notes would be
            // a profile that half applied.
            for (ConfigProfile profile : ConfigProfile.all()) {
                StringBuilder file = new StringBuilder();
                Map<String, List<String>> bySection = new java.util.LinkedHashMap<>();
                profile.settings().forEach((path, value) -> {
                    int dot = path.indexOf('.');
                    bySection.computeIfAbsent(path.substring(0, dot), key -> new java.util.ArrayList<>())
                            .add(path.substring(dot + 1) + " = " + quoted(value));
                });
                bySection.forEach((section, lines) -> {
                    file.append('[').append(section).append("]\n");
                    lines.forEach(line -> file.append(line).append('\n'));
                });
                try {
                    GatheringConfig read = GatheringConfig.read(Toml.read(file.toString()));
                    assertThat(read.notes())
                            .as("notes from the %s profile", profile.id())
                            .isEmpty();
                } catch (TomlException broken) {
                    throw new AssertionError(
                            "the " + profile.id() + " profile does not parse: " + broken.getMessage(),
                            broken);
                }
            }
        }

        @Test
        @DisplayName("has an id that can be typed back")
        void idsRoundTrip() {
            for (ConfigProfile profile : ConfigProfile.all()) {
                assertThat(ConfigProfile.byId(profile.id())).contains(profile);
                assertThat(ConfigProfile.byId(profile.id().toUpperCase(java.util.Locale.ROOT)))
                        .contains(profile);
            }
        }

        @Test
        @DisplayName("is told apart from the others")
        void idsAreDistinct() {
            assertThat(ConfigProfile.all().stream().map(ConfigProfile::id).distinct())
                    .hasSize(ConfigProfile.all().size());
        }

        @Test
        @DisplayName("actually changes something")
        void isNotEmpty() {
            for (ConfigProfile profile : ConfigProfile.all()) {
                assertThat(profile.settings()).as("%s", profile.id()).isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("the diff")
    class TheDiff {

        @Test
        @DisplayName("lists every setting the profile is about, changed or not")
        void listsEverything() {
            // An operator reading it should see the whole of what the profile means, not only
            // the part that happens not to match today.
            ConfigProfile profile = ConfigProfile.CASUAL;
            List<ConfigProfile.Change> diff = profile.diff(path -> "something else");
            assertThat(diff).hasSize(profile.settings().size());
        }

        @Test
        @DisplayName("says which ones would actually change")
        void marksWhatMatters() {
            ConfigProfile profile = ConfigProfile.CASUAL;
            List<ConfigProfile.Change> unchanged =
                    profile.diff(path -> profile.settings().get(path));
            assertThat(unchanged).isNotEmpty();
            assertThat(unchanged.stream().filter(ConfigProfile.Change::matters)).isEmpty();

            List<ConfigProfile.Change> allDifferent = profile.diff(path -> "nothing like it");
            assertThat(allDifferent.stream().filter(ConfigProfile.Change::matters))
                    .hasSize(profile.settings().size());
        }

        @Test
        @DisplayName("copes with a server that cannot answer")
        void unknownCurrentValues() {
            assertThat(ConfigProfile.CASUAL.diff(null)).isNotEmpty();
            assertThat(ConfigProfile.CASUAL.diff(path -> null)).isNotEmpty();
        }
    }

    /** A value as it would be written in the file: bare for a literal, quoted for a word. */
    private static String quoted(String value) {
        boolean alreadyLiteral = "true".equals(value) || "false".equals(value)
                || value.matches("-?\\d+") || value.startsWith("[");
        return alreadyLiteral ? value : "\"" + value + "\"";
    }
}
