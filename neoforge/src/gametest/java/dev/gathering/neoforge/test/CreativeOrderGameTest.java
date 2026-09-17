package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.Registered;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The order the creative menu offers things in.
 * <p>Not cosmetic. The mod registers seventy-odd items, most of them the same six pieces of
 * furniture in eleven woods and three stones, and the only way anybody finds the one they want
 * is that the family is together. The order was written out by hand, twice, once per loader,
 * with every plain block at the front and all the wooden ones in a block at the end - so the
 * dark oak table sat eleven rows away from the spruce one and read as the real table with a
 * pile of recolors after it.
 * <p>What that order is now is {@link GatheringContent#creativeItems()}, and these are the
 * three things about it that a hand-written list kept getting wrong: nothing is missing,
 * nothing is twice, and every family is whole with its plain member in its own wood's place.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeOrderGameTest {

    /**
     * Every item the mod registered is offered, exactly once.
     * <p>Against the registry rather than against a list written here, because a list written
     * here is the same hand-written list with the same failure in it.
     */
    @GameTest(template = "empty")
    public static void everythingIsOfferedExactlyOnce(GameTestHelper helper) {
        List<Registered<Item>> offered = GatheringContent.creativeItems();

        Set<String> seen = new LinkedHashSet<>();
        List<String> twice = new ArrayList<>();
        for (Registered<Item> item : offered) {
            if (!seen.add(item.entryName())) {
                twice.add(item.entryName());
            }
        }
        if (!twice.isEmpty()) {
            helper.fail("The creative menu offers these more than once: " + twice);
            return;
        }

        List<String> missing = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (id.getNamespace().equals(Gathering.MOD_ID) && !seen.contains(id.getPath())) {
                missing.add(id.getPath());
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("Registered but not in the creative menu: " + missing);
            return;
        }
        helper.succeed();
    }

    /**
     * Each family sits together, with the plain one in its own wood's place.
     * <p>"There should be no default variant of anything": {@code table} is the dark oak table
     * and {@code chair} is the oak chair, so each belongs among its woods rather than in front
     * of them.
     */
    @GameTest(template = "empty")
    public static void everyFamilyIsWholeAndInWoodOrder(GameTestHelper helper) {
        List<String> offered = new ArrayList<>();
        GatheringContent.creativeItems().forEach(item -> offered.add(item.entryName()));

        for (GatheringContent.Woodwork kind : GatheringContent.Woodwork.values()) {
            List<String> wanted = new ArrayList<>();
            for (String wood : GatheringContent.WOODS) {
                wanted.add(kind.idFor(wood));
            }
            int at = java.util.Collections.indexOfSubList(offered, wanted);
            if (at < 0) {
                helper.fail("The " + kind + " family is not together in wood order. Wanted "
                        + wanted + " in " + offered);
                return;
            }
            // And the plain one is genuinely inside that run rather than somewhere else too.
            String plain = kind.idFor(kind.plainWood());
            if (offered.indexOf(plain) < at || offered.indexOf(plain) >= at + wanted.size()) {
                helper.fail("The plain " + plain + " sits apart from its own woods");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The stone variants follow their own family rather than forming one of their own.
     * <p>A cobblestone table is a table. It was next to the wooden table, which was right, and
     * then eleven wooden tables were added somewhere else entirely, which was not.
     */
    @GameTest(template = "empty")
    public static void stoneSitsWithItsOwnKind(GameTestHelper helper) {
        List<String> offered = new ArrayList<>();
        GatheringContent.creativeItems().forEach(item -> offered.add(item.entryName()));

        for (GatheringContent.Woodwork kind : GatheringContent.Woodwork.values()) {
            List<String> family = new ArrayList<>();
            GatheringContent.itemsOf(kind).forEach(item -> family.add(item.entryName()));
            if (java.util.Collections.indexOfSubList(offered, family) < 0) {
                helper.fail("The whole " + kind + " family, stone and all, is not one run of "
                        + "the menu: wanted " + family);
                return;
            }
        }

        // And the stone ones really are on the end of their own family rather than a family
        // of their own somewhere else.
        List<String> tables = new ArrayList<>();
        GatheringContent.itemsOf(GatheringContent.Woodwork.TABLE)
                .forEach(item -> tables.add(item.entryName()));
        if (!tables.subList(tables.size() - 3, tables.size()).equals(List.of(
                "cobblestone_table", "blackstone_table", "crying_obsidian_table"))) {
            helper.fail("The stone tables are not on the end of the tables: " + tables);
            return;
        }
        List<String> chairs = new ArrayList<>();
        GatheringContent.itemsOf(GatheringContent.Woodwork.CHAIR)
                .forEach(item -> chairs.add(item.entryName()));
        if (!chairs.subList(chairs.size() - 3, chairs.size()).equals(List.of(
                "cobblestone_chair", "blackstone_chair", "crying_obsidian_chair"))) {
            helper.fail("The stone chairs are not on the end of the chairs: " + chairs);
            return;
        }
        helper.succeed();
    }
}
