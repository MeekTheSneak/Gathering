package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a hopper, a pipe or a comparator makes of a collection block.
 *
 * <p>Nothing, and that is the answer these pin down. A collection is not a container of item
 * stacks - it is a tally of counts with a permission list attached, and the permission list
 * is the reason automation stays out: {@code mayAdd} and {@code mayTake} name players, and a
 * hopper is not a player. A pipe feeding a shared collection would be every one of those
 * rights bypassed by a block somebody placed next to it.
 *
 * <p>So the block exposes no {@link Container}, registers no item-handler capability, and
 * answers no comparator. That is easy to say and easy to lose - a later change adding a
 * capability for some other reason would open all of it silently, with nothing failing - so
 * it is checked here rather than remembered.
 *
 * <p>The other half is that none of it may <em>crash</em>. A hopper pointed at a block that
 * is not a container, and a pipe asking a block for a capability it does not have, are things
 * every player will do by accident; both have to be a quiet no rather than a stack trace, and
 * these run the real hopper for long enough to prove it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionAutomationGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");

    /** A hopper's cooldown is eight ticks, so this is several chances to have moved. */
    private static final int LONG_ENOUGH = 40;

    /**
     * A hopper pointed into a collection cannot push cards into it.
     *
     * <p>And keeps its own cards rather than losing them: a container that swallows what it
     * will not accept is worse than one that refuses.
     */
    @GameTest(template = "tables", timeoutTicks = 120)
    public static void aHopperCannotFeedACollection(GameTestHelper helper) {
        BlockPos box = collection(helper);
        BlockPos above = box.above();
        helper.getLevel().setBlock(above,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        Container hopper = hopperAt(helper, above);
        if (hopper == null) {
            helper.fail("the hopper did not come with a container of its own");
            return;
        }
        // One per slot, because a card is a stack of one - two cards with different
        // histories could never share a slot, so the item does not stack at all and a hopper
        // handed a stack of four keeps one of them.
        for (int slot = 0; slot < 3; slot++) {
            hopper.setItem(slot, oneCard());
        }

        int before = boxAt(helper, box).cards().total();
        helper.runAfterDelay(LONG_ENOUGH, () -> {
            int left = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                left += hopper.getItem(slot).getCount();
            }
            if (left != 3) {
                helper.fail("a hopper over a collection lost " + (3 - left)
                        + " card(s) into a block that is not a container");
                return;
            }
            if (boxAt(helper, box).cards().total() != before) {
                helper.fail("a hopper pushed cards into a collection, which bypasses every"
                        + " right the collection has");
                return;
            }
            helper.succeed();
        });
    }

    /** And a hopper under one cannot pull them out. */
    @GameTest(template = "tables", timeoutTicks = 120)
    public static void aHopperCannotDrainACollection(GameTestHelper helper) {
        BlockPos box = collection(helper);
        CollectionBlockEntity collection = boxAt(helper, box);
        collection.put(CardIdentity.ofPrinting(SOL_RING, false), 12);

        BlockPos under = box.below();
        helper.getLevel().setBlock(under,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH), 3);
        Container hopper = hopperAt(helper, under);
        if (hopper == null) {
            helper.fail("the hopper did not come with a container of its own");
            return;
        }

        helper.runAfterDelay(LONG_ENOUGH, () -> {
            if (!hopper.isEmpty()) {
                helper.fail("a hopper under a collection pulled " + hopper.getItem(0)
                        + " out of it");
                return;
            }
            if (boxAt(helper, box).cards().total() != 12) {
                helper.fail("a collection under a hopper lost cards, and it holds "
                        + boxAt(helper, box).cards().total() + " rather than 12");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A modded pipe gets nothing back, from any side, and does not throw asking.
     *
     * <p>This is the exact call every NeoForge item pipe makes. It is asked from all six
     * sides and with no side at all, because a pipe that asked from below and got an
     * inventory would be reading a stranger's binder through the floor.
     */
    @GameTest(template = "tables")
    public static void anItemPipeFindsNoInventory(GameTestHelper helper) {
        BlockPos box = collection(helper);
        boxAt(helper, box).put(CardIdentity.ofPrinting(SOL_RING, false), 3);

        for (Direction side : Direction.values()) {
            if (helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, box, side) != null) {
                helper.fail("a collection hands out an item handler on its " + side
                        + " side, so any pipe can take cards out of it");
                return;
            }
        }
        if (helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, box, null) != null) {
            helper.fail("a collection hands out a sideless item handler, so any pipe can take"
                    + " cards out of it");
            return;
        }
        helper.succeed();
    }

    /**
     * Nothing the mod places is a container, so nothing can be piped into or out of any of it.
     *
     * <p>The blanket version of the checks above, so a block added later is covered by this
     * without anybody remembering to come back. A {@link Container} is what vanilla's hopper
     * looks for and it is the one interface that would open all of this at once.
     */
    @GameTest(template = "tables")
    public static void noBlockOfThisModIsAContainer(GameTestHelper helper) {
        BlockPos box = collection(helper);
        BlockPos table = helper.absolutePos(new BlockPos(4, 1, 1));
        helper.getLevel().setBlock(table, GatheringContent.TABLE.get().defaultBlockState(), 3);

        for (BlockPos where : List.of(box, table)) {
            BlockEntity entity = helper.getLevel().getBlockEntity(where);
            if (entity instanceof Container) {
                helper.fail(entity.getClass().getSimpleName() + " is a Container, so a hopper"
                        + " can push into it and pull out of it with nobody's permission");
                return;
            }
            // A comparator reading a collection would be a count of somebody's cards on a
            // redstone line, which is a different feature and not this one by accident.
            if (helper.getLevel().getBlockState(where).hasAnalogOutputSignal()) {
                helper.fail(helper.getLevel().getBlockState(where).getBlock()
                        + " answers a comparator");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A deck item sitting in a hopper over a collection stays a deck.
     *
     * <p>The case the sweep gesture and the dissolve gesture are both careful about, arrived
     * at the other way: a deck near a collection must never be quietly taken apart, and a
     * hopper is the one thing that could do it without anybody pressing anything.
     */
    @GameTest(template = "tables", timeoutTicks = 120)
    public static void aDeckInAHopperIsNotDissolved(GameTestHelper helper) {
        BlockPos box = collection(helper);
        BlockPos above = box.above();
        helper.getLevel().setBlock(above,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        Container hopper = hopperAt(helper, above);
        if (hopper == null) {
            helper.fail("the hopper did not come with a container of its own");
            return;
        }
        DeckComponent deck = new DeckComponent(
                "Piped", "", Optional.empty(),
                List.of(CardComponent.of(CardIdentity.ofPrinting(SOL_RING, false))),
                List.of(), List.of());
        hopper.setItem(0, DeckItem.of(deck));

        helper.runAfterDelay(LONG_ENOUGH, () -> {
            if (DeckItem.deckOf(hopper.getItem(0)).map(DeckComponent::totalCards).orElse(0) != 1) {
                helper.fail("a deck in a hopper over a collection was taken apart");
                return;
            }
            if (boxAt(helper, box).cards().total() != 0) {
                helper.fail("a deck in a hopper poured itself into the collection below");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A collection broken by something that is not a player keeps its cards.
     *
     * <p>Every block-breaker any other mod ships ends up in {@code Level#destroyBlock}, which
     * reads the block entity and hands it to the loot context - so the drop is built by this
     * block's own {@code getDrops} and the item carries the collection with it. The path a
     * player takes has an owner check in front of it and this one has nobody to check, which
     * is the same answer a chest gives: what breaks it decides, and the contents survive.
     *
     * <p>Worth pinning because the failure is silent and total. A {@code getDrops} that
     * missed the block entity would drop an empty collection block and ten thousand cards
     * would be gone with no error anywhere.
     */
    @GameTest(template = "tables")
    public static void amoddedBreakerCannotVoidACollection(GameTestHelper helper) {
        BlockPos box = collection(helper);
        boxAt(helper, box).put(CardIdentity.ofPrinting(SOL_RING, false), 7);

        // No entity, which is what a machine breaking a block looks like from here.
        helper.getLevel().destroyBlock(box, true, null);

        List<net.minecraft.world.entity.item.ItemEntity> dropped =
                helper.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(box).inflate(2.0));
        for (net.minecraft.world.entity.item.ItemEntity entity : dropped) {
            ItemStack stack = entity.getItem();
            if (!stack.is(GatheringContent.COLLECTION_ITEM.get())) {
                continue;
            }
            net.minecraft.world.item.component.CustomData saved =
                    stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (saved == null) {
                helper.fail("a collection broken by a machine dropped a block with nothing"
                        + " inside it - every card in it is gone");
                return;
            }
            if (saved.copyTag().getList("Cards", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()) {
                helper.fail("a collection broken by a machine dropped a block whose card list"
                        + " is empty");
                return;
            }
            helper.succeed();
            return;
        }
        helper.fail("a collection broken by a machine dropped no collection block at all");
    }

    // ------------------------------------------------------------------ bits

    private static ItemStack oneCard() {
        return CardItem.of(CardComponent.of(CardIdentity.ofPrinting(SOL_RING, false)));
    }

    private static Container hopperAt(GameTestHelper helper, BlockPos where) {
        return helper.getLevel().getBlockEntity(where) instanceof Container container
                ? container
                : null;
    }

    private static CollectionBlockEntity boxAt(GameTestHelper helper, BlockPos where) {
        return (CollectionBlockEntity) helper.getLevel().getBlockEntity(where);
    }

    /**
     * A collection with a block of room above and below it, inside the plot.
     *
     * <p>Off the floor on purpose: these tests put a hopper on each side of it, and a
     * template's own height is a wall like its width - a hopper written above the top of the
     * plot is a block in nobody's structure that no run cleans up after.
     */
    private static BlockPos collection(GameTestHelper helper) {
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(
                where, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        return where;
    }
}
