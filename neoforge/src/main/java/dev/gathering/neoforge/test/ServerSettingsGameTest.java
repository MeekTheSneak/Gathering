package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.server.DecklistImport;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server config file, from the disk it lives on to the answer a player gets.
 *
 * <p>Through the real file rather than by handing the settings object a value: the thing that
 * would actually break is the path between somebody editing a file and the server behaving
 * differently, and half of that path is the file.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerSettingsGameTest {

    private static final String FILE_NAME = "gathering-server.toml";

    @GameTest(template = "empty")
    public static void aServerWithNoFileGetsOneWithEverythingInIt(GameTestHelper helper) {
        withConfig(helper, null, () -> {
            Path file = Platform.get().configDirectory().resolve(FILE_NAME);
            if (!Files.isRegularFile(file)) {
                return "Starting without a config file did not write one";
            }
            if (!ServerSettings.get().modes().importEnabled()) {
                return "A fresh server came up with importing off";
            }
            if (ServerSettings.get().modes().collectionEnabled()) {
                return "A fresh server came up with collection on";
            }
            if (DecklistImport.whyNot(false) != null) {
                return "A fresh server refused an ordinary player an import: "
                        + DecklistImport.whyNot(false);
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void importTurnedOffInTheFileRefusesEverybody(GameTestHelper helper) {
        withConfig(helper, "[modes]\nimport_enabled = false\n", () -> {
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
        withConfig(helper, "[import]\nallow_all_players = false\n", () -> {
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
        withConfig(helper, "[modes\nimport_enabled = false\n", () -> {
            if (!ServerSettings.get().modes().importEnabled()) {
                return "A broken config file took importing down with it";
            }
            return null;
        });
    }

    // ------------------------------------------------------------------- bits

    /** What a check found wrong, or null if it found nothing. */
    @FunctionalInterface
    private interface Check {
        String run();
    }

    /**
     * Runs a check with this text in the config file, and puts the file back afterwards.
     *
     * <p>A null text means no file at all, which is what a server's first start looks like.
     */
    private static void withConfig(GameTestHelper helper, String text, Check check) {
        Path file = Platform.get().configDirectory().resolve(FILE_NAME);
        String before = null;
        try {
            if (Files.isRegularFile(file)) {
                before = Files.readString(file, StandardCharsets.UTF_8);
            }
            Files.createDirectories(file.getParent());
            if (text == null) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, text, StandardCharsets.UTF_8);
            }
            ServerSettings.load(Platform.get());

            String wrong = check.run();
            if (wrong != null) {
                helper.fail(wrong);
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("The config file could not be written: " + couldNotWrite);
        } finally {
            try {
                if (before == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.writeString(file, before, StandardCharsets.UTF_8);
                }
            } catch (IOException couldNotRestore) {
                // Nothing useful to do here; the next start writes a fresh one.
            }
            ServerSettings.load(Platform.get());
        }
    }
}
