package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.service.Rewards;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Reward files load as one snapshot, and a bad one changes nothing.
 * <p>The unit tests hold the contract to account - what a field may contain, what is refused,
 * which fields an error names. These cover the part that touches a folder: that a reload is
 * all-or-nothing, that a typo in one file does not empty the shelf, and that a reward for a
 * mod nobody installed loads quietly rather than failing.
 * <p>Every test puts the folder back the way it found it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RewardsGameTest {

    private static Path folder() {
        return Platform.get().configDirectory().resolve(Rewards.DIRECTORY);
    }

    private static void write(String named, String json) throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve(named), json, StandardCharsets.UTF_8);
    }

    /** Takes the folder away again, so one test's files are not the next one's. */
    private static void tidyUp() {
        Path where = folder();
        if (!Files.isDirectory(where)) {
            Rewards.clear();
            return;
        }
        try (Stream<Path> listing = Files.walk(where)) {
            listing.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A file left behind is not worth failing a test over.
                }
            });
        } catch (IOException ignored) {
            // Same.
        }
        Rewards.clear();
    }

    private static String rewardJson(String id, String requiredMods) {
        return "{\"id\": \"" + id + "\", \"set\": \"j25\", \"product\": \"default\","
                + " \"count\": 2" + requiredMods + "}";
    }

    /** A well-formed file loads and can be found by name. */
    @GameTest(template = "empty")
    public static void arewardfileloads(GameTestHelper helper) {
        try {
            tidyUp();
            write("boss.json", rewardJson("boss_drop", ""));
            Rewards.reload();

            if (!Rewards.problems().isEmpty()) {
                helper.fail("a sound reward file was complained about: " + Rewards.problems());
                return;
            }
            var found = Rewards.find("boss_drop").orElse(null);
            if (found == null) {
                helper.fail("a sound reward did not load: " + Rewards.all().keySet());
                return;
            }
            if (found.count() != 2 || !found.set().equals("j25")) {
                helper.fail("the reward loaded with the wrong contents: " + found);
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("could not write a reward file: " + couldNotWrite.getMessage());
        } finally {
            tidyUp();
        }
    }

    /**
     * A folder that will not read leaves the previous snapshot exactly as it was.
     * <p>The property the whole design is built around. An empty set of rewards because
     * somebody left a trailing comma in one file turns a typo into a server where nothing is
     * granted and nobody knows why.
     */
    @GameTest(template = "empty")
    public static void abadreloadkeepsthelastgoodone(GameTestHelper helper) {
        try {
            tidyUp();
            write("good.json", rewardJson("still_here", ""));
            Rewards.reload();
            if (Rewards.find("still_here").isEmpty()) {
                helper.fail("the fixture did not load in the first place");
                return;
            }

            // A file somebody was halfway through editing.
            write("broken.json", "{\"id\": \"oops\", \"set\": ");
            Rewards.reload();

            if (Rewards.find("still_here").isEmpty()) {
                helper.fail("one broken file emptied the shelf; the good reward is gone");
                return;
            }
            if (Rewards.problems().isEmpty()) {
                helper.fail("a broken file was accepted without a word");
                return;
            }
            boolean namesTheFile = Rewards.problems().stream()
                    .anyMatch(problem -> problem.contains("broken.json"));
            if (!namesTheFile) {
                helper.fail("the complaint did not say which file: " + Rewards.problems());
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("could not write a reward file: " + couldNotWrite.getMessage());
        } finally {
            tidyUp();
        }
    }

    /**
     * A reward needing a mod nobody has loads, and is simply not granted.
     * <p>Not an error. One pack ships rewards for four boss mods and expects two to be
     * installed; refusing to load would punish exactly the packs being careful about it.
     */
    @GameTest(template = "empty")
    public static void arewardforamissingmodloadsquietly(GameTestHelper helper) {
        try {
            tidyUp();
            write("needs.json", rewardJson("for_a_mod_nobody_has",
                    ", \"required_mods\": [\"definitely_not_installed\"]"));
            Rewards.reload();

            if (!Rewards.problems().isEmpty()) {
                helper.fail("a reward for an absent mod was treated as an error: "
                        + Rewards.problems());
                return;
            }
            if (!Rewards.all().containsKey("for_a_mod_nobody_has")) {
                helper.fail("a reward for an absent mod did not load at all");
                return;
            }
            if (Rewards.find("for_a_mod_nobody_has").isPresent()) {
                helper.fail("a reward was granted for a mod that is not installed");
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("could not write a reward file: " + couldNotWrite.getMessage());
        } finally {
            tidyUp();
        }
    }

    /**
     * A field that is out of bounds is refused, with the file and the field named.
     * <p>A pack author reading "something is wrong" has to find it themselves.
     */
    @GameTest(template = "empty")
    public static void abadfieldisrefusedbyname(GameTestHelper helper) {
        try {
            tidyUp();
            write("toomany.json",
                    "{\"id\": \"greedy\", \"set\": \"j25\", \"product\": \"default\","
                            + " \"count\": 9999}");
            write("address.json",
                    "{\"id\": \"sneaky\", \"set\": \"http://example.test/x\","
                            + " \"product\": \"default\"}");
            Rewards.reload();

            if (Rewards.all().containsKey("greedy") || Rewards.all().containsKey("sneaky")) {
                helper.fail("a reward outside its bounds was loaded anyway");
                return;
            }
            boolean saysCount = Rewards.problems().stream()
                    .anyMatch(problem -> problem.contains("toomany.json")
                            && problem.contains("count"));
            boolean saysSet = Rewards.problems().stream()
                    .anyMatch(problem -> problem.contains("address.json")
                            && problem.contains("set"));
            if (!saysCount || !saysSet) {
                helper.fail("the complaints did not name the file and the field: "
                        + Rewards.problems());
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("could not write a reward file: " + couldNotWrite.getMessage());
        } finally {
            tidyUp();
        }
    }

    /** No folder at all is the ordinary case, not a failure. */
    @GameTest(template = "empty")
    public static void nofolderisfine(GameTestHelper helper) {
        tidyUp();
        Rewards.reload();
        if (!Rewards.problems().isEmpty()) {
            helper.fail("a server with no reward folder complained: " + Rewards.problems());
            return;
        }
        if (!Rewards.all().isEmpty()) {
            helper.fail("rewards appeared from a folder that does not exist");
            return;
        }
        helper.succeed();
    }
}
