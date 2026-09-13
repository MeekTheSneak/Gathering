package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The card shop, as a building a village can put up.
 * <p>Structure files are the one part of a mod that fails silently and completely. A block name
 * with a typo in it is read as air; a property that block does not have is dropped and the
 * default used. Either way the file loads, the village builds, and the shop has a hole in it
 * where the counter should be - with nothing in any log to say so.
 * <p>So every block state in every one of the five buildings is read here the way the game reads
 * it, and anything that came back as air or lost a property fails the run.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardShopBuildingGameTest {

    private static final List<String> VILLAGES =
            List.of("plains", "desert", "savanna", "snowy", "taiga");

    @GameTest(template = "empty")
    public static void everyBlockInEveryShopIsARealBlock(GameTestHelper helper) {
        HolderGetter<Block> blocks = helper.getLevel().holderLookup(Registries.BLOCK);
        List<String> wrong = new ArrayList<>();
        for (String village : VILLAGES) {
            CompoundTag file = read(helper, village);
            if (file == null) {
                wrong.add(village + ": no building shipped");
                continue;
            }
            var palette = file.getList("palette", Tag.TAG_COMPOUND);
            if (palette.isEmpty()) {
                wrong.add(village + ": a building made of nothing");
                continue;
            }
            for (int entry = 0; entry < palette.size(); entry++) {
                CompoundTag written = palette.getCompound(entry);
                String name = written.getString("Name");
                BlockState state = NbtUtils.readBlockState(blocks, written);
                if (state.is(Blocks.AIR) && !name.equals("minecraft:air")) {
                    wrong.add(village + ": '" + name + "' is not a block");
                    continue;
                }
                CompoundTag properties = written.getCompound("Properties");
                for (String held : properties.getAllKeys()) {
                    Property<?> property = state.getBlock().getStateDefinition().getProperty(held);
                    if (property == null) {
                        wrong.add(village + ": " + name + " has no '" + held + "'");
                    } else if (!value(state, property).equals(properties.getString(held))) {
                        wrong.add(village + ": " + name + " cannot be '" + held + "="
                                + properties.getString(held) + "'");
                    }
                }
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail(String.join("; ", wrong));
            return;
        }
        helper.succeed();
    }

    /**
     * A shop has a counter, a chest and two tables in it.
     * <p>Cheap to break by accident: the layout is generated, and a generator that stopped
     * emitting the counter would still produce a perfectly valid house.
     */
    @GameTest(template = "empty")
    public static void everyShopIsActuallyAShop(GameTestHelper helper) {
        for (String village : VILLAGES) {
            CompoundTag file = read(helper, village);
            if (file == null) {
                helper.fail("No " + village + " card shop shipped");
                return;
            }
            int counters = 0;
            int chests = 0;
            int tableCorners = 0;
            var palette = file.getList("palette", Tag.TAG_COMPOUND);
            var placed = file.getList("blocks", Tag.TAG_COMPOUND);
            for (int block = 0; block < placed.size(); block++) {
                String name = palette.getCompound(placed.getCompound(block).getInt("state"))
                        .getString("Name");
                if (name.equals("gathering:shop_counter")) {
                    counters++;
                } else if (name.equals("minecraft:chest")) {
                    chests++;
                } else if (name.equals("gathering:table")) {
                    tableCorners++;
                }
            }
            if (counters < 3 || chests != 1 || tableCorners != 8) {
                helper.fail(village + " is not a card shop: " + counters + " counter(s), "
                        + chests + " chest(s), " + tableCorners + " table corner(s)");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A table turns with the building it is in.
     * <p>Villages place their buildings at any of four rotations. A table whose quarters did
     * not turn with it would come out of the ground as four north-west corners on top of each
     * other - four tables in the space of one, none of them whole.
     */
    @GameTest(template = "empty")
    public static void aTableSurvivesBeingTurned(GameTestHelper helper) {
        for (Rotation rotation : Rotation.values()) {
            List<TablePart> landed = new ArrayList<>();
            for (TablePart part : TablePart.values()) {
                BlockState turned = dev.gathering.item.GatheringContent.TABLE.get()
                        .defaultBlockState()
                        .setValue(TableBlock.PART, part)
                        .rotate(rotation);
                landed.add(turned.getValue(TableBlock.PART));
            }
            if (landed.stream().distinct().count() != TablePart.values().length) {
                helper.fail("Turned " + rotation + ", a table has " + landed
                        + " - the same quarter twice");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The five village styles all resolve to a real house pool and a real shop.
     * <p>The pool files are data, and a data file with a typo in an element type does not stop
     * a server - it stops that one pool from existing, quietly, and the villages go up without
     * a card shop in them.
     */
    @GameTest(template = "empty")
    public static void everyVillageHasSomewhereToPutOne(GameTestHelper helper) {
        var found = dev.gathering.village.LocalGameStore.found(
                helper.getLevel().getServer());
        if (found.size() != VILLAGES.size()) {
            helper.fail("Only " + found.size() + " of " + VILLAGES.size()
                    + " villages can build a card shop");
            return;
        }
        for (var shop : found) {
            if (shop.buildings().isEmpty()) {
                helper.fail("The " + shop.village() + " card shop pool holds no building");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The chest behind the counter rolls, and does not throw.
     * <p>Its pool entry is the one with no source on it, which means "always a pack" rather
     * than "one chest in eight" - a shop's stock is not a lucky find. That path is only ever
     * taken by this one table, so without this nothing would notice it was broken until
     * somebody opened the chest in a village and found nothing in it.
     * <p>Nothing comes out here because collecting is off on a test server, which is the same
     * thing every other loot test in this mod checks. What is being checked is that a table
     * with a sourceless entry in it rolls at all.
     */
    @GameTest(template = "empty")
    public static void theShopsOwnChestRolls(GameTestHelper helper) {
        // Switched off here rather than assumed. These tests share one server with
        // tests that switch collecting on, so a test that reads a setting it did not
        // set passes or fails by the order the tests happened to run in.
        TestConfig.run("[modes]\ncollection_enabled = false\n", () -> {
            var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.LOOT_TABLE,
                            Gathering.id("chests/card_shop")));
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(
                    helper.getLevel())
                    .withParameter(
                            net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                            net.minecraft.world.phys.Vec3.atCenterOf(
                                    helper.absolutePos(net.minecraft.core.BlockPos.ZERO)))
                    .create(net.minecraft.world.level.storage.loot.parameters
                            .LootContextParamSets.CHEST);

            boolean anything = false;
            for (int roll = 0; roll < 32; roll++) {
                for (var stack : table.getRandomItems(params)) {
                    anything = true;
                    if (stack.is(dev.gathering.item.GatheringContent.PACK.get())) {
                        helper.fail("A pack came out of a shop's chest with collecting switched off");
                        return;
                    }
                }
            }
            if (!anything) {
                helper.fail("The shop's chest is empty however many times it is rolled, so the "
                        + "loot table is not in the jar or holds nothing");
                return;
            }
            helper.succeed();
        });
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static CompoundTag read(GameTestHelper helper, String village) {
        ResourceLocation where = Gathering.id(
                "structure/village/" + village + "_card_shop.nbt");
        Resource found = helper.getLevel().getServer().getResourceManager()
                .getResource(where).orElse(null);
        if (found == null) {
            return null;
        }
        try (InputStream open = found.open()) {
            return NbtIo.readCompressed(open, NbtAccounter.unlimitedHeap());
        } catch (Exception couldNotRead) {
            return null;
        }
    }
}
