package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.server.Achievements;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The advancements load, and the code that grants them can.
 * <p>Both halves fail silently otherwise, which is the reason this is worth a test. An
 * advancement file with a mistyped trigger is dropped by the loader with a line in the log
 * nobody reads, and {@link Achievements#award} on a name that is not there does nothing at
 * all - deliberately, so a data pack may remove one. Between the two, an advancement that
 * never appears looks exactly like an advancement nobody has earned yet.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdvancementGameTest {

    /** Every one the code can grant. A name here that no file matches is a dead grant. */
    private static final List<String> ALL = List.of(
            Achievements.ROOT,
            Achievements.FIRST_PACK,
            Achievements.FIRST_MYTHIC,
            Achievements.FIRST_DRAFT,
            Achievements.SET_COMPLETE,
            Achievements.FIRST_DECK,
            Achievements.FIRST_GAME,
            Achievements.FIRST_TRADE);

    private AdvancementGameTest() {
    }

    /** Every file parsed, and every parent in it points at one that did. */
    @GameTest(template = "empty")
    public static void theTreeLoads(GameTestHelper helper) {
        for (String name : ALL) {
            AdvancementHolder holder = holderOf(helper, name);
            if (holder == null) {
                helper.fail("gathering:" + name + " is not loaded, so nobody can ever earn it");
                return;
            }
            if (holder.value().display().isEmpty()) {
                helper.fail("gathering:" + name + " has no display, so it is invisible");
                return;
            }
            // A parent that did not load leaves the child hanging off nothing, and the whole
            // branch under it disappears from the screen without an error anywhere.
            ResourceLocation parent = holder.value().parent().orElse(null);
            if (parent != null && helper.getLevel().getServer().getAdvancements().get(parent) == null) {
                helper.fail("gathering:" + name + " hangs off " + parent + ", which is not loaded");
                return;
            }
        }
        if (holderOf(helper, Achievements.ROOT).value().parent().isPresent()) {
            helper.fail("the root has a parent, so the mod's tab has no root at all");
            return;
        }
        helper.succeed();
    }

    /** Granting one grants it, and granting it twice is not a second announcement. */
    @GameTest(template = "empty")
    public static void grantingOneSticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AdvancementHolder pack = holderOf(helper, Achievements.FIRST_PACK);
        if (pack == null) {
            helper.fail("the pack advancement is not loaded");
            return;
        }
        if (player.getAdvancements().getOrStartProgress(pack).isDone()) {
            helper.fail("a fresh player already has an advancement they have not earned");
            return;
        }
        Achievements.award(player, Achievements.FIRST_PACK);
        if (!player.getAdvancements().getOrStartProgress(pack).isDone()) {
            helper.fail("awarding an advancement left it unearned");
            return;
        }
        // Twice, because every grant in this mod runs on a path a player can take again -
        // opening a second booster, finishing a second game - and the guard against a second
        // toast is in award rather than in each of them.
        Achievements.award(player, Achievements.FIRST_PACK);
        if (!player.getAdvancements().getOrStartProgress(pack).isDone()) {
            helper.fail("awarding an advancement twice took it away again");
            return;
        }
        helper.succeed();
    }

    /**
     * A name nothing matches is silence, not a crash.
     * <p>Which is the contract a data pack removing one relies on.
     */
    @GameTest(template = "empty")
    public static void anUnknownNameIsHarmless(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            Achievements.award(player, "no_such_advancement");
            helper.succeed();
        } catch (RuntimeException threw) {
            helper.fail("granting an advancement that is not there threw: " + threw);
        }
    }

    private static AdvancementHolder holderOf(GameTestHelper helper, String name) {
        return helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, name));
    }
}
