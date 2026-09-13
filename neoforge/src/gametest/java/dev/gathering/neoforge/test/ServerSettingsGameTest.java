package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.server.DecklistImport;
import dev.gathering.service.ServerSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server config file, from the disk it lives on to the answer a player gets.
 * <p>Through the real file rather than by handing the settings object a value: the thing that
 * would actually break is the path between somebody editing a file and the server behaving
 * differently, and half of that path is the file.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerSettingsGameTest {

    @GameTest(template = "empty")
    public static void aServerWithNoFileGetsOneWithEverythingInIt(GameTestHelper helper) {
        TestConfig.with(helper, null, () -> {
            Path file = Platform.get().configDirectory().resolve(TestConfig.FILE_NAME);
            if (!Files.isRegularFile(file)) {
                return "Starting without a config file did not write one";
            }
            if (!ServerSettings.get().modes().importEnabled()) {
                return "A fresh server came up with importing off";
            }
            if (!ServerSettings.get().modes().collectionEnabled()) {
                return "A fresh server came up with collection off";
            }
            // The shipped shape, in a real server rather than in a config unit test: cards
            // are found and opened, and importing a decklist is the operator's tool - a card
            // conjured out of a list beside a card opened out of a pack makes the pack
            // pointless.
            if (DecklistImport.whyNot(false) == null) {
                return "A fresh server let an ordinary player import";
            }
            if (DecklistImport.whyNot(true) != null) {
                return "A fresh server refused an operator an import: "
                        + DecklistImport.whyNot(true);
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void importTurnedOffInTheFileRefusesEverybody(GameTestHelper helper) {
        TestConfig.with(helper, "[modes]\nimport_enabled = false\n", () -> {
            if (DecklistImport.whyNot(false) == null || DecklistImport.whyNot(true) == null) {
                return "Importing was turned off in the file and somebody was still allowed";
            }
            if (!DecklistImport.whyNot(true).contains("turned off")) {
                return "The refusal did not say why: " + DecklistImport.whyNot(true);
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void importNarrowedToOperatorsStillLetsOperatorsIn(GameTestHelper helper) {
        TestConfig.with(helper, "[import]\nallow_all_players = false\n", () -> {
            if (DecklistImport.whyNot(false) == null) {
                return "An ordinary player was let in on an operators-only server";
            }
            if (DecklistImport.whyNot(true) != null) {
                return "An operator was refused on an operators-only server: "
                        + DecklistImport.whyNot(true);
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void aFileThatCannotBeReadLeavesTheServerOnTheDefaults(GameTestHelper helper) {
        TestConfig.with(helper, "[modes\nimport_enabled = false\n", () -> {
            if (!ServerSettings.get().modes().importEnabled()) {
                return "A broken config file took importing down with it";
            }
            return null;
        });
    }

    // ------------------------------------------------------------------- bits

}
