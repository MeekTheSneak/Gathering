package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientSettings;
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

    /**
     * All of it, in one test, on purpose.
     * <p>Minecraft runs game tests several at a time in a grid. {@code ClientSettings} is one
     * static holder for one player's settings - which is right for a game, where there is one
     * player and one file - so six tests poking at it at once were racing over the same
     * fields, and which of them failed depended on how many other tests happened to be in the
     * run. Giving each a file of its own fixed half of it; the other half is the holder, and
     * the honest way to stop racing over a global is not to race over it.
     * <p>Each check says what it is checking, so a failure still names the thing that broke.
     */
    @GameTest(template = "empty")
    public static void theplayersownsettingsfile(GameTestHelper helper) throws Exception {
        anOldFileKeepsWhatItSaid(helper);
        abrokenfilefallsbackratherthanfailing(helper);
        impossiblevaluesareclamped(helper);
        writingbackkeepswhatthisversiondoesnotknow(helper);
        asimilarlynamedlineisleftalone(helper);
        thesamenameunderanotherheadingisleftalone(helper);
        skippingisnotfinishing(helper);
        helper.succeed();
    }

    /**
     * Runs a check against a settings file of this test's own.
     * <p>Its own, not the real one. These all used to write the player's actual settings file
     * and put it back afterwards, which worked until there were enough of them: Minecraft runs
     * several game tests at once in a grid, so they were racing over one file and which of them
     * failed depended on what else happened to be in the run. A file each removes the race
     * rather than papering over it, and leaves the real settings alone.
     */
    private static void withFile(String contents, GameTestHelper helper, Check check) throws Exception {
        Path where = Files.createTempDirectory("gathering-settings-")
                .resolve("gathering-client.toml");
        try {
            Files.writeString(where, contents, StandardCharsets.UTF_8);
            ClientSettings.fileForTesting(where);
            check.run(where);
        } finally {
            ClientSettings.fileForTesting(null);
            Files.deleteIfExists(where);
            Files.deleteIfExists(where.getParent());
        }
    }

    private interface Check {
        void run(Path where) throws Exception;
    }

    /**
     * A file from before any of these settings existed keeps its one line and grows the rest.
     * <p>The migration, and the thing a player would actually notice: somebody who picked
     * walnut a month ago must still be looking at walnut afterwards.
     */
    private static void anOldFileKeepsWhatItSaid(GameTestHelper helper) throws Exception {
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
    private static void abrokenfilefallsbackratherthanfailing(GameTestHelper helper) throws Exception {
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
    private static void impossiblevaluesareclamped(GameTestHelper helper) throws Exception {
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
    private static void writingbackkeepswhatthisversiondoesnotknow(GameTestHelper helper) throws Exception {
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
    private static void asimilarlynamedlineisleftalone(GameTestHelper helper) throws Exception {
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
    private static void thesamenameunderanotherheadingisleftalone(GameTestHelper helper) throws Exception {
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
    private static void skippingisnotfinishing(GameTestHelper helper) throws Exception {
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
    /**
     * Every value the settings screen offers is a value the settings will actually keep.
     * <p>A screen that steps a row to 50% when the setter's floor is 75% shows 50, stores 75
     * and draws at 75 - a control that lies about what it just did, and one nobody notices
     * because every part of it looks right on its own. The screen derives its steps from the
     * bounds now; this is what stops the two drifting apart again.
     * <p>Reached through the same setters a press uses, because what is being checked is the
     * round trip rather than the arithmetic. The steps live on ClientSettings rather than on
     * the screen precisely so that this test can see them: a screen cannot be loaded here.
     */
    @GameTest(template = "empty")
    public static void everyofferedsettingsurvivesbeingset(GameTestHelper helper) {
        int text = ClientSettings.textScale();
        int control = ClientSettings.controlScale();
        int effects = ClientSettings.effectIntensity();
        int volume = ClientSettings.tableSoundVolume();
        int waiting = ClientSettings.waitingAfterMillis();
        try {
            for (var offered : ClientSettings.offeredSteps().entrySet()) {
                for (int value : offered.getValue()) {
                    int got = switch (offered.getKey()) {
                        case "text_scale" -> {
                            ClientSettings.textScale(value);
                            yield ClientSettings.textScale();
                        }
                        case "control_scale" -> {
                            ClientSettings.controlScale(value);
                            yield ClientSettings.controlScale();
                        }
                        case "effect_intensity" -> {
                            ClientSettings.effectIntensity(value);
                            yield ClientSettings.effectIntensity();
                        }
                        case "sound_volume" -> {
                            ClientSettings.tableSoundVolume(value);
                            yield ClientSettings.tableSoundVolume();
                        }
                        case "waiting_after" -> {
                            ClientSettings.waitingAfterMillis(value);
                            yield ClientSettings.waitingAfterMillis();
                        }
                        default -> value;
                    };
                    if (got != value) {
                        helper.fail("the settings screen offers " + offered.getKey() + " = "
                                + value + ", but setting it leaves " + got);
                        return;
                    }
                }
            }
            helper.succeed();
        } finally {
            ClientSettings.textScale(text);
            ClientSettings.controlScale(control);
            ClientSettings.effectIntensity(effects);
            ClientSettings.tableSoundVolume(volume);
            ClientSettings.waitingAfterMillis(waiting);
        }
    }
}
