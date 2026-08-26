package dev.gathering.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigEditTest {

    private static final String FILE = String.join("\n",
            "# Gathering server settings.",
            "",
            "[modes]",
            "# What this server is for.",
            "import_enabled = true",
            "collection_enabled = false",
            "",
            "[collection]",
            "current_set = \"auto\"",
            "");

    @Test
    void aSettingIsChangedWhereItAlreadyIs() {
        ConfigEdit.Edited edited = ConfigEdit.set(FILE, "modes.collection_enabled", "true");
        assertThat(edited.worked()).isTrue();
        assertThat(edited.text()).contains("collection_enabled = true");
        assertThat(edited.text()).doesNotContain("collection_enabled = false");
    }

    @Test
    void everythingElseInTheFileSurvives() {
        // A server owner's comments are the reason this edits text rather than writing the
        // file out again from a parsed model.
        String after = ConfigEdit.set(FILE, "modes.collection_enabled", "true").text();
        assertThat(after).contains("# Gathering server settings.");
        assertThat(after).contains("# What this server is for.");
        assertThat(after).contains("import_enabled = true");
        assertThat(after).contains("current_set = \"auto\"");
        assertThat(after.lines().count()).isEqualTo(FILE.lines().count());
    }

    @Test
    void aKeyItsSectionDoesNotHaveYetIsAddedToThatSection() {
        String after = ConfigEdit.set(FILE, "modes.something_new", "3").text();
        assertThat(after).contains("[modes]");
        int modes = after.indexOf("[modes]");
        int added = after.indexOf("something_new = 3");
        int collection = after.indexOf("[collection]");
        assertThat(added).isGreaterThan(modes).isLessThan(collection);
    }

    @Test
    void aSectionTheFileDoesNotHaveIsAddedAtTheEnd() {
        String after = ConfigEdit.set(FILE, "ante.enabled", "true").text();
        assertThat(after).contains("[ante]");
        assertThat(after.indexOf("[ante]")).isGreaterThan(after.indexOf("[collection]"));
        assertThat(after).contains("enabled = true");
    }

    @Test
    void aKeyInTheLastSectionLandsInsideIt() {
        String after = ConfigEdit.set(FILE, "collection.loot_sets", "[\"dom\"]").text();
        assertThat(after.indexOf("loot_sets")).isGreaterThan(after.indexOf("[collection]"));
        // And not left dangling after the trailing blank line.
        assertThat(after.strip()).endsWith("loot_sets = [\"dom\"]");
    }

    @Test
    void aKeyInAnotherSectionWithTheSameNameIsNotTheOneChanged() {
        String file = String.join("\n",
                "[modes]", "enabled = false", "", "[ante]", "enabled = false", "");
        String after = ConfigEdit.set(file, "ante.enabled", "true").text();
        assertThat(after).contains("[modes]\nenabled = false");
        assertThat(after).contains("[ante]\nenabled = true");
    }

    @Test
    void indentationIsKept() {
        String file = "[modes]\n    collection_enabled = false\n";
        assertThat(ConfigEdit.set(file, "modes.collection_enabled", "true").text())
                .contains("    collection_enabled = true");
    }

    @Test
    void aNameThatIsNotSectionDotKeyIsRefusedRatherThanGuessedAt() {
        assertThat(ConfigEdit.set(FILE, "collection_enabled", "true").worked()).isFalse();
        assertThat(ConfigEdit.set(FILE, "modes.collection_enabled", "  ").worked()).isFalse();
    }

    @Test
    void whatSomebodyTypesBecomesWhatTomlWants() {
        assertThat(ConfigEdit.asToml("true")).contains("true");
        assertThat(ConfigEdit.asToml("TRUE")).contains("true");
        assertThat(ConfigEdit.asToml("12")).contains("12");
        assertThat(ConfigEdit.asToml("-3")).contains("-3");
        assertThat(ConfigEdit.asToml("auto")).contains("\"auto\"");
        assertThat(ConfigEdit.asToml("basic lands, foils")).contains("[\"basic lands\", \"foils\"]");
        assertThat(ConfigEdit.asToml("[\"dom\"]")).contains("[\"dom\"]");
        assertThat(ConfigEdit.asToml("  ")).isEmpty();
    }

    @Test
    void aValueWithAQuoteInItSurvivesBeingWritten() {
        String value = ConfigEdit.asToml("say \"hello\"").orElseThrow();
        String after = ConfigEdit.set(FILE, "collection.current_set", value).text();
        // And reading it back gives what was typed, which is the only thing that matters.
        assertThat(after).contains("current_set = \"say \\\"hello\\\"\"");
    }

    /** The round trip that matters: what is written can be read back by the real parser. */
    @Test
    void whatIsWrittenIsReadBackAsWhatWasMeant() throws Exception {
        String after = ConfigEdit.set(FILE, "modes.collection_enabled", "true").text();
        Toml read = Toml.read(after);
        assertThat(read.flag("modes.collection_enabled", false)).isTrue();
        assertThat(read.flag("modes.import_enabled", false)).isTrue();
        assertThat(read.string("collection.current_set", "")).isEqualTo("auto");
    }
}
