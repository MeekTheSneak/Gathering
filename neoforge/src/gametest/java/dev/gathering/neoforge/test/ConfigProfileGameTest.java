package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.config.ConfigProfile;
import dev.gathering.platform.Platform;
import dev.gathering.service.ConfigProfiles;
import dev.gathering.service.ServerSettings;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A settings profile goes on, and comes back off exactly.
 * <p>The unit tests prove a profile names real keys and sets values the config will take.
 * What they cannot reach is the file: whether applying one really changes what the server is
 * running on, and whether restoring really puts back what was there - including the parts the
 * profile knows nothing about.
 * <p>Every test here restores the file it found, whatever happens, because the file it finds
 * is the development server's own.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigProfileGameTest {

    /**
     * Applying a profile changes what the server is running on, and restoring undoes it.
     * <p>Read off the live config rather than off the file, because what matters is what the
     * server is actually using: a file that changed and a server that did not is the state
     * ServerSettings.set exists to prevent, and this is the test that says so.
     */
    @GameTest(template = "empty")
    public static void aprofileappliesandrestoresexactly(GameTestHelper helper) {
        Platform platform = Platform.get();
        String found = ConfigProfiles.currentText(platform);
        try {
            // Somewhere known to start from, so this does not depend on what the file said.
            ConfigProfiles.apply(platform, ConfigProfile.IMPORTED);
            boolean collectingBefore = ServerSettings.get().modes().collectionEnabled();
            if (collectingBefore) {
                helper.fail("the imported profile left collecting on, so it did not apply");
                return;
            }

            ConfigProfiles.Applied applied = ConfigProfiles.apply(platform, ConfigProfile.CASUAL);
            if (!applied.clean()) {
                helper.fail("the casual profile was refused: " + applied.refused());
                return;
            }
            if (!ServerSettings.get().modes().collectionEnabled()) {
                helper.fail("the casual profile did not turn collecting on");
                return;
            }

            String problem = ConfigProfiles.restore(platform, applied.restoreTo());
            if (problem != null) {
                helper.fail("restoring failed: " + problem);
                return;
            }
            if (ServerSettings.get().modes().collectionEnabled()) {
                helper.fail("restoring did not put the previous settings back");
                return;
            }
            helper.succeed();
        } finally {
            ConfigProfiles.restore(platform, found);
        }
    }

    /**
     * Restoring puts back what the profile never touched, not just what it did.
     * <p>An operator's own comment, or a key no profile mentions, has to survive being
     * profiled and unprofiled. Restoring a list of settings would lose both; restoring the
     * file does not.
     */
    @GameTest(template = "empty")
    public static void restoringkeepswhattheprofileneverknewabout(GameTestHelper helper) {
        Platform platform = Platform.get();
        String found = ConfigProfiles.currentText(platform);
        try {
            String mine = "# a line somebody wrote by hand\n" + found;
            String problem = ConfigProfiles.restore(platform, mine);
            if (problem != null) {
                helper.fail("could not put the fixture in place: " + problem);
                return;
            }

            ConfigProfiles.Applied applied = ConfigProfiles.apply(platform, ConfigProfile.LONG_RUN);
            ConfigProfiles.restore(platform, applied.restoreTo());

            String after = ConfigProfiles.currentText(platform);
            if (!after.contains("a line somebody wrote by hand")) {
                helper.fail("restoring lost a comment the profile never mentioned");
                return;
            }
            if (!after.equals(mine)) {
                helper.fail("restoring did not put the file back exactly as it was");
                return;
            }
            helper.succeed();
        } finally {
            ConfigProfiles.restore(platform, found);
        }
    }

    /**
     * Nothing that is not readable is ever written over the config.
     * <p>The one file a server needs in order to start. What is being restored was a working
     * file a moment ago, so this should never fire - which is not a reason to write it back
     * unchecked.
     */
    @GameTest(template = "empty")
    public static void nonsenseisneverrestoredoverthesettings(GameTestHelper helper) {
        Platform platform = Platform.get();
        String found = ConfigProfiles.currentText(platform);
        try {
            if (ConfigProfiles.restore(platform, "[this is not = toml at all") == null) {
                helper.fail("unreadable settings were written over the config file");
                return;
            }
            if (!ConfigProfiles.currentText(platform).equals(found)) {
                helper.fail("a refused restore changed the file anyway");
                return;
            }
            if (ConfigProfiles.restore(platform, null) == null) {
                helper.fail("restoring nothing at all reported success");
                return;
            }
            helper.succeed();
        } finally {
            ConfigProfiles.restore(platform, found);
        }
    }

    /**
     * Every profile applies with nothing refused.
     * <p>The unit tests check the values against the parser; this checks them against the
     * setting path a command actually uses, which does more - it re-reads the whole file after
     * each edit and refuses anything that would be quietly ignored.
     */
    @GameTest(template = "empty")
    public static void everyprofileappliescleanly(GameTestHelper helper) {
        Platform platform = Platform.get();
        String found = ConfigProfiles.currentText(platform);
        try {
            for (ConfigProfile profile : ConfigProfile.all()) {
                ConfigProfiles.Applied applied = ConfigProfiles.apply(platform, profile);
                if (!applied.clean()) {
                    helper.fail("the " + profile.id() + " profile was refused: "
                            + applied.refused());
                    return;
                }
            }
            helper.succeed();
        } finally {
            ConfigProfiles.restore(platform, found);
        }
    }
}
