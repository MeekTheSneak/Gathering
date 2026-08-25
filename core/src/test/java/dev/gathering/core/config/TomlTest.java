package dev.gathering.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The corner of TOML this mod's one config file lives in. */
class TomlTest {

    @Test
    @DisplayName("sections, comments and the four kinds of value")
    void readsAConfigFile() throws Exception {
        Toml toml = Toml.read("""
                # the whole file
                [modes]
                import_enabled = true          # a comment after a value
                collection_enabled = false

                [collection]
                sealed_price_item = "minecraft:diamond"
                stall_rotation_hours = 4
                pack_loot_sources = ["fishing", "structures"]
                """);

        assertThat(toml.flag("modes.import_enabled", false)).isTrue();
        assertThat(toml.flag("modes.collection_enabled", true)).isFalse();
        assertThat(toml.string("collection.sealed_price_item", "")).isEqualTo("minecraft:diamond");
        assertThat(toml.number("collection.stall_rotation_hours", 0)).isEqualTo(4);
        assertThat(toml.strings("collection.pack_loot_sources", List.of()))
                .containsExactly("fishing", "structures");
    }

    @Test
    @DisplayName("a setting the file does not mention keeps its default")
    void absentSettingsKeepTheirDefaults() throws Exception {
        Toml toml = Toml.read("[modes]\nimport_enabled = false\n");

        assertThat(toml.has("modes.import_enabled")).isTrue();
        assertThat(toml.has("modes.collection_enabled")).isFalse();
        assertThat(toml.flag("modes.collection_enabled", true)).isTrue();
        assertThat(toml.number("table.max_tables_loaded", 16)).isEqualTo(16);
        assertThat(toml.string("collection.current_set", "auto")).isEqualTo("auto");
        assertThat(toml.strings("import.formats", List.of("commander")))
                .containsExactly("commander");
    }

    @Test
    @DisplayName("a dotted setting name means what the section would have meant")
    void dottedKeysAreThePathTheySpell() throws Exception {
        Toml toml = Toml.read("modes.import_enabled = false\n");
        assertThat(toml.has("modes.import_enabled")).isTrue();
        assertThat(toml.flag("modes.import_enabled", true)).isFalse();

        // And under a section, the two join up rather than one replacing the other.
        Toml nested = Toml.read("[collection]\nfoo.bar = 1\n");
        assertThat(nested.number("collection.foo.bar", 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("a setting nobody asked about is reported rather than dropped")
    void unknownSettingsAreReported() throws Exception {
        Toml toml = Toml.read("""
                [modes]
                import_enabled = true
                improt_enabled = true
                """);

        assertThat(toml.unknownKeys(List.of("modes.import_enabled")))
                .containsExactly("modes.improt_enabled");
        assertThat(toml.unknownKeys(List.of("modes.import_enabled", "modes.improt_enabled")))
                .isEmpty();
    }

    @Test
    @DisplayName("a hash inside quotes is part of the value")
    void aHashInsideQuotesIsNotAComment() throws Exception {
        Toml toml = Toml.read("""
                [collection]
                sealed_price_item = "minecraft:diamond # the good one"  # this one is a comment
                """);
        assertThat(toml.string("collection.sealed_price_item", ""))
                .isEqualTo("minecraft:diamond # the good one");
    }

    @Test
    @DisplayName("a comma inside quotes does not split a list")
    void aCommaInsideQuotesDoesNotSplitAList() throws Exception {
        Toml toml = Toml.read("""
                [import]
                formats = ["one, really", "two"]
                """);
        assertThat(toml.strings("import.formats", List.of()))
                .containsExactly("one, really", "two");
    }

    @Test
    @DisplayName("an escape inside a string comes out as the character it names")
    void escapesAreUnwrapped() throws Exception {
        Toml toml = Toml.read("a = \"say \\\"hi\\\"\\nthen \\\\ and\\ta tab\"\n");
        assertThat(toml.string("a", "")).isEqualTo("say \"hi\"\nthen \\ and\ta tab");
    }

    @Test
    @DisplayName("an empty list is a list with nothing in it")
    void anEmptyListIsAllowed() throws Exception {
        assertThat(Toml.read("a = []\n").strings("a", List.of("something"))).isEmpty();
    }

    @Test
    @DisplayName("a value read as the wrong kind says which setting")
    void theWrongKindOfValueSaysWhich() throws Exception {
        Toml toml = Toml.read("[modes]\nimport_enabled = 4\ncount = true\nname = \"x\"\n");

        assertThatThrownBy(() -> toml.flag("modes.import_enabled", false))
                .isInstanceOf(TomlException.class)
                .hasMessageContaining("modes.import_enabled")
                .hasMessageContaining("true or false");
        assertThatThrownBy(() -> toml.number("modes.count", 0))
                .isInstanceOf(TomlException.class)
                .hasMessageContaining("whole number");
        assertThatThrownBy(() -> toml.string("modes.count", ""))
                .isInstanceOf(TomlException.class)
                .hasMessageContaining("in quotes");
        assertThatThrownBy(() -> toml.strings("modes.name", List.of()))
                .isInstanceOf(TomlException.class)
                .hasMessageContaining("should be a list");
    }

    @Test
    @DisplayName("every kind of malformed line says which line it is on")
    void malformedLinesSayWhere() {
        assertMalformed("[modes]\nimport_enabled\n", 2, "neither a section nor a setting");
        assertMalformed("[modes\nx = 1\n", 1, "never closes it");
        assertMalformed("[]\n", 1, "section with no name");
        assertMalformed("[a b!]\n", 1, "not a section name");
        assertMalformed("a b! = 1\n", 1, "not a setting name");
        assertMalformed(".a = 1\n", 1, "not a setting name");
        assertMalformed("a. = 1\n", 1, "not a setting name");
        assertMalformed("a..b = 1\n", 1, "not a setting name");
        assertMalformed("a =\n", 1, "has no value");
        assertMalformed("a = yes\n", 1, "which is not true, false");
        assertMalformed("a = \"unclosed\n", 1, "never closes it");
        assertMalformed("a = [\"one\", two]\n", 1, "needs quotes round it");
        assertMalformed("a = [\"one\"\n", 1, "never closes it");
        assertMalformed("a = \"\\q\"\n", 1, "means nothing here");
        assertMalformed("a = 1\na = 2\n", 2, "set twice");
        assertMalformed("a = 99999999999999\n", 0, "bigger than this setting can be");
    }

    @Test
    @DisplayName("a big number is refused when it is read, not silently wrapped")
    void aNumberTooBigIsRefused() throws Exception {
        Toml toml = Toml.read("a = 9999999999\n");
        assertThatThrownBy(() -> toml.number("a", 0))
                .isInstanceOf(TomlException.class)
                .hasMessageContaining("bigger than this setting can be");
    }

    @Test
    @DisplayName("an empty file is every setting at its default")
    void anEmptyFileIsAllDefaults() throws Exception {
        for (Toml toml : List.of(Toml.empty(), Toml.read(""), Toml.read("# only a comment\n"))) {
            assertThat(toml.flag("modes.import_enabled", true)).isTrue();
            assertThat(toml.unknownKeys(List.of())).isEmpty();
        }
    }

    private static void assertMalformed(String text, int line, String says) {
        if (line == 0) {
            // Read fine; the trouble only shows when the value is asked for.
            assertThatThrownBy(() -> Toml.read(text).number("a", 0))
                    .as(text)
                    .isInstanceOf(TomlException.class)
                    .hasMessageContaining(says);
            return;
        }
        assertThatThrownBy(() -> Toml.read(text))
                .as(text)
                .isInstanceOf(TomlException.class)
                .hasMessageContaining("line " + line)
                .hasMessageContaining(says);
    }
}
