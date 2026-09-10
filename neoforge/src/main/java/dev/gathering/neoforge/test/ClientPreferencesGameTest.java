package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientSettings;
import dev.gathering.platform.Platform;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The player's own settings file: read, grown from an older shape, and survived when broken.
 * <p>A game test rather than a unit test because the thing worth checking is the real file on
 * the real disk through the real platform, and because {@code ClientSettings} answers with
 * defaults when no platform is registered - which would make a bare unit test pass without
 * ever reading anything.
 * <p>Each of these writes the file, forgets what was read, and puts back what was there, so
 * running them does not change the settings of whoever is running the tests.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClientPreferencesGameTest {

    private static Path file() {
        return Platform.get().configDirectory().resolve("gathering-client.toml");
    }

    /** Runs a check with the settings file replaced, and puts the real one back afterwards. */
    private static void withFile(String contents, GameTestHelper helper, Check check) throws Exception {
        Path where = file();
        String before = Files.isRegularFile(where)
                ? Files.readString(where, StandardCharsets.UTF_8)
                : null;
        try {
            Files.createDirectories(where.getParent());
            Files.writeString(where, contents, StandardCharsets.UTF_8);
            ClientSettings.forgetForTesting();
            check.run(where);
        } finally {
            ClientSettings.forgetForTesting();
            if (before == null) {
                Files.deleteIfExists(where);
            } else {
                Files.writeString(where, before, StandardCharsets.UTF_8);
            }
            ClientSettings.forgetForTesting();
        }
        helper.succeed();
    }

    private interface Check {
        void run(Path where) throws Exception;
    }

    /**
     * A file from before any of these settings existed keeps its one line and grows the rest.
     * <p>The migration, and the thing a player would actually notice: somebody who picked
     * walnut a month ago must still be looking at walnut afterwards.
     */
    @GameTest(template = "empty")
    public static void anOldFileKeepsWhatItSaid(GameTestHelper helper) throws Exception {
        withFile("""
                # Gathering, as seen from this computer.

                [gui]
                theme = "gathering:walnut"
                """, helper, where -> {
            if (!"gathering:walnut".equals(ClientSettings.themeId())) {
                helper.fail("an old settings file lost its theme: " + ClientSettings.themeId());
                return;
            }
            if (ClientSettings.textScale() != 100 || ClientSettings.reducedMotion()) {
                helper.fail("an old settings file did not take the defaults for what it lacked");
                return;
            }
            String grown = Files.readString(where, StandardCharsets.UTF_8);
            if (!grown.contains("schema = 2")) {
                helper.fail("an old settings file was not grown into the current shape");
                return;
            }
            if (!grown.contains("gathering:walnut")) {
                helper.fail("growing the file threw away the theme it had");
                return;
            }
            if (!grown.contains("# Gathering, as seen from this computer.")) {
                helper.fail("growing the file threw away the comments in it");
            }
        });
    }

    /** A file that will not parse leaves the defaults standing rather than failing a game. */
    @GameTest(template = "empty")
    public static void abrokenfilefallsbackratherthanfailing(GameTestHelper helper) throws Exception {
        withFile("[gui\ntheme = broken \"quotes\n= = =\n", helper, where -> {
            if (!"gathering:basic".equals(ClientSettings.themeId())) {
                helper.fail("a broken settings file did not fall back to the default theme");
                return;
            }
            if (ClientSettings.textScale() != 100 || !ClientSettings.tableSounds()) {
                helper.fail("a broken settings file did not fall back to the defaults");
            }
        });
    }

    /** Values outside the allowed range are pulled into it rather than used as written. */
    @GameTest(template = "empty")
    public static void impossiblevaluesareclamped(GameTestHelper helper) throws Exception {
        withFile("""
                [file]
                schema = 2

                [accessibility]
                text_scale = 100000
                control_scale = -40
                effect_intensity = 900

                [feedback]
                waiting_after_ms = 1
                """, helper, where -> {
            if (ClientSettings.textScale() != 200) {
                helper.fail("a huge text scale was not clamped: " + ClientSettings.textScale());
                return;
            }
            if (ClientSettings.controlScale() != 75) {
                helper.fail("a negative control scale was not clamped: "
                        + ClientSettings.controlScale());
                return;
            }
            if (ClientSettings.effectIntensity() != 100) {
                helper.fail("an effect intensity over 100 was not clamped");
                return;
            }
            if (ClientSettings.waitingAfterMillis() != 100) {
                helper.fail("a one-millisecond waiting notice was not clamped");
            }
        });
    }

    /**
     * A setting written back does not take the player's own lines with it.
     * <p>The file is theirs. A comment they added, and a section this version has never heard
     * of, both have to survive a button press.
     */
    @GameTest(template = "empty")
    public static void writingbackkeepswhatthisversiondoesnotknow(GameTestHelper helper) throws Exception {
        withFile("""
                [file]
                schema = 2

                [gui]
                # my own note about why I picked this
                theme = "gathering:slate"

                [something_from_the_future]
                nobody_here_has_heard_of_this = 7
                """, helper, where -> {
            ClientSettings.textScale(150);
            ClientSettings.flush();
            String after = Files.readString(where, StandardCharsets.UTF_8);
            if (!after.contains("text_scale = 150")) {
                helper.fail("the new setting was not written");
                return;
            }
            if (!after.contains("# my own note about why I picked this")) {
                helper.fail("writing a setting threw away the player's own comment");
                return;
            }
            if (!after.contains("nobody_here_has_heard_of_this = 7")) {
                helper.fail("writing a setting threw away a section this version does not know");
                return;
            }
            if (!after.contains("gathering:slate")) {
                helper.fail("writing a setting changed the theme");
            }
        });
    }

    /**
     * A name that starts with another name's letters is not the same setting.
     * <p>The write is a line-by-line replace, and a plain "starts with" would have rewritten
     * {@code text_scale_locked} while looking for {@code text_scale}. It is written first in
     * this fixture on purpose: with the real setting first, the replace stops at the right
     * line and the bug never shows.
     */
    @GameTest(template = "empty")
    public static void asimilarlynamedlineisleftalone(GameTestHelper helper) throws Exception {
        withFile("""
                [file]
                schema = 2

                [accessibility]
                text_scale_locked = true
                text_scale = 100
                """, helper, where -> {
            ClientSettings.textScale(125);
            ClientSettings.flush();
            String after = Files.readString(where, StandardCharsets.UTF_8);
            if (!after.contains("text_scale = 125")) {
                helper.fail("the setting was not written");
                return;
            }
            if (!after.contains("text_scale_locked = true")) {
                helper.fail("a line whose name merely starts the same was overwritten");
            }
        });
    }

    /**
     * The same name under two headings is two settings.
     * <p>Both sections could hold a {@code text_scale}; only the accessibility one is ours.
     */
    @GameTest(template = "empty")
    public static void thesamenameunderanotherheadingisleftalone(GameTestHelper helper) throws Exception {
        withFile("""
                [file]
                schema = 2

                [somebody_elses]
                text_scale = 42

                [accessibility]
                text_scale = 100
                """, helper, where -> {
            ClientSettings.textScale(175);
            ClientSettings.flush();
            String after = Files.readString(where, StandardCharsets.UTF_8);
            if (!after.contains("text_scale = 42")) {
                helper.fail("a setting of the same name under another heading was overwritten");
                return;
            }
            if (!after.contains("text_scale = 175")) {
                helper.fail("the accessibility text scale was not written");
            }
        });
    }

    /** Skipping the tutorial is recorded as skipping it, never as finishing it. */
    @GameTest(template = "empty")
    public static void skippingisnotfinishing(GameTestHelper helper) throws Exception {
        withFile("[file]\nschema = 2\n", helper, where -> {
            ClientSettings.tutorialOffered(true);
            ClientSettings.tutorialSkipped(true);
            ClientSettings.flush();
            if (ClientSettings.tutorialFinished()) {
                helper.fail("skipping the tutorial was recorded as finishing it");
                return;
            }
            String after = Files.readString(where, StandardCharsets.UTF_8);
            if (!after.contains("skipped = true") || !after.contains("finished = false")) {
                helper.fail("the tutorial state was not written truthfully: " + after);
            }
        });
    }
}
