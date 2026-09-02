package dev.gathering.fabric.test;

import com.mojang.serialization.JsonOps;
import dev.gathering.Gathering;
import dev.gathering.core.sealed.ArchiveDrops;
import dev.gathering.core.sealed.LootSource;
import dev.gathering.registry.GatheringLoot;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The mod's loot pool really is added to the right tables on this loader.
 * <p>The one piece of the mod each loader does completely differently. NeoForge appends with a
 * global loot modifier that runs for every table there is; Fabric edits each table as it loads,
 * through {@code LootTableEvents.MODIFY}, and so has to decide for itself which tables to touch.
 * Deciding wrongly is silent: the chest opens, it has loot in it, and the pack that should have
 * been there simply is not.
 * <p>Both mistakes this has actually made are checked here. The hook asked only whether a table
 * was a pack source, so the archive pack could not drop from the four boss fights it was written
 * for; and it asked {@code isBuiltin()}, so a chest any data pack had edited quietly stopped
 * giving packs on this loader while still giving them on the other.
 * <p><b>The pool is looked for, not rolled.</b> What comes out of the pool needs a set list and
 * card data, which a headless server has no network to fetch - so a test that rolled the table
 * and waited for a pack would fail on a working hook and pass on a broken one the moment
 * somebody warmed a cache. The table is encoded through its own codec instead and read for the
 * mod's entry type, which is exactly the thing the hook puts there and nothing else does.
 */
public class FabricLootGameTest implements FabricGameTest {

    /** A chest the mod calls a pack source, and one of the bosses the archive drops from. */
    private static final String CHEST = "minecraft:chests/simple_dungeon";
    private static final String BOSS = "minecraft:entities/ender_dragon";

    /** A table the mod has no business in: not a chest, not a boss, not fishing. */
    private static final String NOT_OURS = "minecraft:blocks/dirt";

    @GameTest(template = EMPTY_STRUCTURE)
    public void aPackSourceChestCarriesTheModsPool(GameTestHelper helper) {
        if (LootSource.of(CHEST).isEmpty()) {
            helper.fail(CHEST + " is no longer a pack source, so this test checks nothing");
            return;
        }
        if (!carriesOurPool(helper, CHEST)) {
            helper.fail("The loot hook added nothing to " + CHEST);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aBossCarriesTheArchivePool(GameTestHelper helper) {
        if (ArchiveDrops.of(BOSS).isEmpty()) {
            helper.fail(BOSS + " no longer drops the archive, so this test checks nothing");
            return;
        }
        // A boss is not a pack source and never will be. Asking only about the first meant the
        // rarest thing in the mod could not drop from the fights it was written for, on this
        // loader alone - NeoForge's modifier runs for every table and never had to ask.
        if (!carriesOurPool(helper, BOSS)) {
            helper.fail("The loot hook added nothing to " + BOSS + ", so the archive cannot "
                    + "drop from a boss on this loader");
            return;
        }
        helper.succeed();
    }

    /**
     * And the other half of the rule, which a hook that touched everything would fail: a table
     * the mod has nothing to do with is left exactly as the game shipped it.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnrelatedTableIsLeftAlone(GameTestHelper helper) {
        if (carriesOurPool(helper, NOT_OURS)) {
            helper.fail("The loot hook added a pool to " + NOT_OURS);
            return;
        }
        helper.succeed();
    }

    /** Whether this table carries the mod's own loot entry, read off the table's own codec. */
    private static boolean carriesOurPool(GameTestHelper helper, String tableId) {
        LootTable table = helper.getLevel().getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(
                        Registries.LOOT_TABLE, ResourceLocation.parse(tableId)));
        // Registry-aware ops, not plain JSON. A dungeon chest holds enchanted books, and an
        // enchantment is a registry reference the plain ops cannot resolve - so encoding it
        // fails, the result is empty, and a table carrying the pool reads as one that does not.
        // The dragon's table has no such reference, which is why only one of these went wrong.
        var ops = net.minecraft.resources.RegistryOps.create(
                JsonOps.INSTANCE, helper.getLevel().registryAccess());
        String written = LootTable.DIRECT_CODEC
                .encodeStart(ops, table)
                .result()
                .map(Object::toString)
                .orElse("");
        return written.contains(
                Gathering.id(GatheringLoot.SEALED_PRODUCT_ID).toString());
    }
}
