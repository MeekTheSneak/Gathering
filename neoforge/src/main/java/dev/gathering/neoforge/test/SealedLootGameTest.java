package dev.gathering.neoforge.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.gathering.Gathering;
import dev.gathering.core.sealed.LootSource;
import dev.gathering.item.GatheringContent;
import dev.gathering.loot.PackLootEntry;
import dev.gathering.registry.GatheringLoot;
import dev.gathering.server.SealedLoot;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sealed product turning up in the world.
 *
 * <p>Two things are worth checking in a running server rather than on paper. The first is
 * that rolling one of Minecraft's own chests still works with the mod installed: a loot
 * modifier runs inside loot generation, and one that throws takes every chest in the world
 * with it. The second is that the two files that wire the modifier up actually shipped -
 * they are generated, and a modifier whose files are missing is not an error anywhere, it is
 * simply a feature that never happens.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedLootGameTest {

    private static final ResourceLocation DUNGEON =
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon");

    /**
     * A vanilla chest still rolls, and does not quietly gain a pack.
     *
     * <p>Collecting is off in a test server, and off means off however the loot tables are
     * written - a pack here would mean the switch in the config file is not the switch.
     */
    @GameTest(template = "empty")
    public static void aChestRollsAndHasNoPackInIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, DUNGEON));
        LootParams params = new LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);

        for (int roll = 0; roll < 64; roll++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(GatheringContent.PACK.get())) {
                    helper.fail("A pack came out of a dungeon chest with collecting switched off");
                    return;
                }
            }
        }
        helper.succeed();
    }

    /** The modifier's own two files, which nothing complains about the absence of. */
    @GameTest(template = "empty")
    public static void theLootModifierIsActuallyInstalled(GameTestHelper helper) {
        String list = read(helper, ResourceLocation.fromNamespaceAndPath(
                "neoforge", "loot_modifiers/global_loot_modifiers.json"));
        String modifier = read(helper, ResourceLocation.fromNamespaceAndPath(
                Gathering.MOD_ID, "loot_modifiers/" + GatheringLoot.SEALED_PRODUCT_ID + ".json"));
        if (list == null || modifier == null) {
            helper.fail("The generated loot modifier files are not in the jar; run runData");
            return;
        }
        String id = Gathering.id(GatheringLoot.SEALED_PRODUCT_ID).toString();
        if (!list.contains(id)) {
            helper.fail("global_loot_modifiers.json does not list " + id);
            return;
        }
        if (!modifier.contains(id)) {
            helper.fail(GatheringLoot.SEALED_PRODUCT_ID + ".json does not name " + id);
            return;
        }
        helper.succeed();
    }

    /**
     * The entry type a data pack would write, both registered and fussy about its source.
     *
     * <p>Fabric puts packs in chests with this entry rather than with a modifier, so a
     * codec that does not round trip is that whole loader's loot silently not loading.
     */
    @GameTest(template = "empty")
    public static void theEntryTypeRoundTripsAndRefusesNonsense(GameTestHelper helper) {
        if (!BuiltInRegistries.LOOT_POOL_ENTRY_TYPE.containsKey(
                Gathering.id(GatheringLoot.SEALED_PRODUCT_ID))) {
            helper.fail("gathering:sealed_product is not a registered loot pool entry type");
            return;
        }

        JsonElement written = JsonParser.parseString(
                "{\"source\": \"" + LootSource.STRUCTURES.configName() + "\"}");
        if (PackLootEntry.CODEC.codec().parse(JsonOps.INSTANCE, written).result().isEmpty()) {
            helper.fail("A sealed_product entry naming a real source would not parse");
            return;
        }

        JsonElement nonsense = JsonParser.parseString("{\"source\": \"trading\"}");
        if (PackLootEntry.CODEC.codec().parse(JsonOps.INSTANCE, nonsense).result().isPresent()) {
            helper.fail("A sealed_product entry naming a source nobody has parsed anyway");
            return;
        }

        // And a data pack may say its own chest is a find worth making, which is what
        // decides whether a collector booster is plausible out of it.
        JsonElement rich = JsonParser.parseString(
                "{\"source\": \"structures\", \"richness\": \"rich\"}");
        var parsed = PackLootEntry.CODEC.codec().parse(JsonOps.INSTANCE, rich).result();
        if (parsed.isEmpty()) {
            helper.fail("A sealed_product entry saying its chest is rich would not parse");
            return;
        }
        var backOut = PackLootEntry.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, parsed.get()).result().orElse(null);
        if (backOut == null || !backOut.toString().contains("rich")) {
            helper.fail("How good the chest is did not survive a round trip: " + backOut);
            return;
        }
        helper.succeed();
    }

    /**
     * A server that is not collecting does not go and ask which set is current.
     *
     * <p>The answer is already there before anything asks for it, which is what "did not
     * make a request" looks like from here. Worth pinning: every play-only server in the
     * world would otherwise fetch a megabyte at every start for a number nobody reads.
     */
    @GameTest(template = "empty")
    public static void aPlayOnlyServerNeverAsksWhichSetIsCurrent(GameTestHelper helper) {
        var known = dev.gathering.server.CurrentSet.whenKnown();
        if (!known.isDone()) {
            helper.fail("A server with collecting off went looking for the current set");
            return;
        }
        if (known.join().isPresent()) {
            helper.fail("A server with collecting off has a current set: " + known.join().get());
            return;
        }
        helper.succeed();
    }

    /** Nothing is available on a server that has not been told a set, so nothing drops. */
    @GameTest(template = "empty")
    public static void nothingDropsBeforeASetIsKnown(GameTestHelper helper) {
        for (LootSource source : LootSource.values()) {
            if (SealedLoot.rollFrom(source, dev.gathering.core.sealed.LootRichness.PLAIN,
                    helper.getLevel().getRandom()).isPresent()) {
                helper.fail("A pack dropped from " + source.configName()
                        + " before any set had been read");
                return;
            }
        }
        if (SealedLoot.rollFor("minecraft:entities/zombie", helper.getLevel().getRandom())
                .isPresent()) {
            helper.fail("A pack dropped out of a table this mod has nothing to do with");
            return;
        }
        helper.succeed();
    }

    /**
     * The entry inside a real table, rolled by the real loot machinery.
     *
     * <p>This is the whole of Fabric's mechanism bar the one line that registers the hook,
     * so it is worth rolling here where there is a server to roll it on: an entry that
     * throws, or one the pool refuses to build, is that loader's chests broken.
     */
    @GameTest(template = "empty")
    public static void theEntryRollsInsideARealTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .add(PackLootEntry.forTable(
                                LootSource.STRUCTURES, dev.gathering.core.sealed.LootRichness.RICH)))
                .build();
        LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);

        for (int roll = 0; roll < 64; roll++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(GatheringContent.PACK.get())) {
                    helper.fail("A pack came out of a sealed_product entry with collecting off");
                    return;
                }
                helper.fail("A sealed_product entry produced " + stack + ", which is not nothing");
                return;
            }
        }
        helper.succeed();
    }

    private static String read(GameTestHelper helper, ResourceLocation where) {
        List<Resource> stack = helper.getLevel().getServer().getResourceManager()
                .getResourceStack(where);
        StringBuilder all = new StringBuilder();
        for (Resource resource : stack) {
            try (Reader reader = resource.openAsReader()) {
                reader.transferTo(new java.io.Writer() {
                    @Override
                    public void write(char[] buffer, int off, int len) {
                        all.append(buffer, off, len);
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                });
            } catch (IOException unreadable) {
                return null;
            }
        }
        return all.isEmpty() ? null : all.toString();
    }
}
