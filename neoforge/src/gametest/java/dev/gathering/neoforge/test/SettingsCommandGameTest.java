package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.server.Settings;
import dev.gathering.service.ServerSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Changing a setting without stopping the server.
 * <p>The reason this exists: collection mode could only be turned on by editing a file and
 * restarting, which meant limited play could not be tried at all without a shutdown - and a
 * card shop villager on a server without it has nothing to sell and shakes its head at
 * everybody, which is what the whole feature looks like from the outside.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettingsCommandGameTest {

    private static final String FILE_NAME = "gathering-server.toml";

    /** A setting set is a setting the server is running on, and one the file remembers. */
    @GameTest(template = "empty")
    public static void aSettingChangesTheServerAndTheFile(GameTestHelper helper) {
        boolean before = ServerSettings.get().modes().collectionEnabled();
        try {
            Settings.Changed changed = Settings.set("modes.collection_enabled", "on");
            if (!changed.worked()) {
                helper.fail("turning collecting on failed: " + changed.problem());
                return;
            }
            if (!ServerSettings.get().modes().collectionEnabled()) {
                helper.fail("the setting was written but the server is not running on it");
                return;
            }
            if (!Settings.valueOf("modes.collection_enabled").equals("true")) {
                helper.fail("the command reads back " + Settings.valueOf("modes.collection_enabled"));
                return;
            }
            String written = Files.readString(file(), StandardCharsets.UTF_8);
            if (!written.contains("collection_enabled = true")) {
                helper.fail("the file does not hold the new value, so a restart would lose it");
                return;
            }
            helper.succeed();
        } catch (Exception broke) {
            helper.fail("changing a setting threw: " + broke);
        } finally {
            Settings.set("modes.collection_enabled", before ? "on" : "off");
        }
    }

    /** The words people type, rather than the two the file understands. */
    @GameTest(template = "empty")
    public static void onAndOffMeanTrueAndFalse(GameTestHelper helper) {
        boolean before = ServerSettings.get().modes().importEnabled();
        try {
            Settings.set("modes.import_enabled", "off");
            if (ServerSettings.get().modes().importEnabled()) {
                helper.fail("off did not turn a setting off");
                return;
            }
            Settings.set("modes.import_enabled", "yes");
            if (!ServerSettings.get().modes().importEnabled()) {
                helper.fail("yes did not turn a setting on");
                return;
            }
            helper.succeed();
        } finally {
            Settings.set("modes.import_enabled", before ? "on" : "off");
        }
    }

    /**
     * A value of the wrong shape is refused, not written.
     * <p>The setting command checked that the path had a dot in it and that the value was not
     * blank, and nothing else - it has no idea what any key is meant to hold. The written file
     * is always valid TOML, because ConfigEdit quotes anything it does not recognise; the
     * damage happens one layer further in. GatheringConfig.read asks {@code toml.flag} for a
     * boolean key, and flag throws when it finds a string - so the reload fails and the server
     * drops <em>every</em> setting it has back to the defaults. Import mode, collection mode,
     * ante, loot, all of it, from one mistyped word on an unrelated key. It stays that way at
     * the next restart too, because the file is still on disk. And the command reported
     * success, because writing the file had worked.
     * <p>Checked through a setting deliberately left at a non-default value: a revert to the
     * defaults is only visible on a setting whose default is not what it already says.
     */
    @GameTest(template = "empty")
    public static void aValueOfTheWrongShapeIsRefusedRatherThanWritten(GameTestHelper helper) {
        boolean collectionBefore = ServerSettings.get().modes().collectionEnabled();
        boolean importBefore = ServerSettings.get().modes().importEnabled();
        try {
            // Import defaults to on, so off is the state a revert would undo.
            Settings.set("modes.import_enabled", "off");
            if (ServerSettings.get().modes().importEnabled()) {
                helper.fail("could not put a setting into a non-default state to check against");
                return;
            }

            Settings.Changed changed = Settings.set("modes.collection_enabled", "banana");
            if (changed.worked()) {
                helper.fail("a word was accepted for a setting that holds true or false");
                return;
            }
            // The part that made this serious: the damage was never to the key being edited.
            if (ServerSettings.get().modes().importEnabled()) {
                helper.fail("a refused setting took an unrelated one with it - the whole config"
                        + " reverted to its defaults");
                return;
            }
            helper.succeed();
        } finally {
            Settings.set("modes.import_enabled", importBefore ? "on" : "off");
            Settings.set("modes.collection_enabled", collectionBefore ? "on" : "off");
        }
    }

    /** A name nothing reads is refused rather than written into the file. */
    @GameTest(template = "empty")
    public static void aSettingThatDoesNotExistIsRefused(GameTestHelper helper) {
        Settings.Changed changed = Settings.set("modes.not_a_setting", "true");
        if (changed.worked()) {
            helper.fail("a setting nothing reads was accepted");
            return;
        }
        helper.succeed();
    }

    /** Every name the command offers is one it can also read a value for. */
    @GameTest(template = "empty")
    public static void everySettingOfferedCanBeRead(GameTestHelper helper) {
        for (String name : Settings.names()) {
            if ("?".equals(Settings.valueOf(name))) {
                helper.fail(name + " is offered by the command and has no value to show");
                return;
            }
        }
        if (Settings.names().isEmpty()) {
            helper.fail("the command offers no settings at all");
            return;
        }
        helper.succeed();
    }

    /**
     * Turning collecting on is what makes a shopkeeper have anything to sell.
     * <p>The reported bug was that card shop villagers shake their heads at everybody. They
     * do: a shopkeeper's offers are read off a shelf that is only stocked when collecting is
     * on, so on a default server every one of them has zero trades, which is exactly what
     * vanilla's head-shake means. This checks the switch reaches the shop rather than that
     * the shelf fills, because filling it reads real set data over the network.
     */
    @GameTest(template = "empty")
    public static void turningCollectingOnReachesTheShop(GameTestHelper helper) {
        boolean before = ServerSettings.get().modes().collectionEnabled();
        try {
            Settings.set("modes.collection_enabled", "off");
            if (dev.gathering.server.CardShop.isStocking()) {
                helper.fail("the shop was stocking with collecting turned off");
                return;
            }
            Settings.Changed changed = Settings.set("modes.collection_enabled", "on");
            if (!dev.gathering.server.CardShop.isStocking()) {
                helper.fail("turning collecting on did not put the shop back in business,"
                        + " so its villagers would still have nothing to sell");
                return;
            }
            // And the shelf was actually read again. Without this the test passes on the
            // config alone, while the shop goes on holding whatever it was holding before -
            // which is the half-applied state that made a restart necessary in the first
            // place, and exactly what somebody turning collecting on would then report.
            if (!changed.rewarmed()) {
                helper.fail("the setting changed and nothing downstream was read again");
                return;
            }
            helper.succeed();
        } finally {
            Settings.set("modes.collection_enabled", before ? "on" : "off");
        }
    }

    private static Path file() {
        return Platform.get().configDirectory().resolve(FILE_NAME);
    }
}
