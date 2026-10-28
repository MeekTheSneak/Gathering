package dev.gathering.neoforge.test;

import dev.gathering.platform.Platform;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Runs one check against a config file this test wrote, and puts the old one back.
 * <p>Every test here runs on one server against one set of settings held in a static, and
 * they run in an order nobody chose. A test that reads a setting without setting it is a test
 * that passes or fails depending on which test ran before it - so a test about what a server
 * does with collecting switched off switches it off, rather than assuming a test server has
 * it off.
 * <p>The file is put back afterwards whatever happened, and the settings are read again from
 * it, so the next test starts where this one found things.
 */
final class TestConfig {

    /** The file every one of these writes, in the server's own config directory. */
    static final String FILE_NAME = "gathering-server.toml";

    /** @return what went wrong in plain words, or null if the check passed */
    @FunctionalInterface
    interface Check {
        String run();
    }

    /** The same, for a check that needs somebody to run it as. */
    @FunctionalInterface
    interface PlayerCheck {
        String run(net.minecraft.server.level.ServerPlayer player);
    }

    private TestConfig() {
    }

    /**
     * Runs a body with this text as the config, and puts the file back whatever it does.
     * <p>For a test that wants to say {@code helper.fail} and {@code helper.succeed} itself.
     */
    static void run(String text, Runnable body) {
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
            body.run();
        } catch (IOException couldNotWrite) {
            throw new java.io.UncheckedIOException(couldNotWrite);
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

    /** The same, with a mock player made once the settings are in place. */
    static void withPlayer(GameTestHelper helper, String text, PlayerCheck check) {
        with(helper, text, () -> check.run(helper.makeMockServerPlayerInLevel()));
    }

    /**
     * Writes {@code text} as the config, runs the check, and restores what was there.
     *
     * @param text the whole config file, or null to run with no config file at all
     */
    static void with(GameTestHelper helper, String text, Check check) {
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
