package dev.gathering.fabric.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Everything the mod adds is really registered on this loader, and bound to the same handle.
 * <p>Registration is written twice, once per loader, against a {@code Registered} handle that
 * :common reaches everything through. A handle that is registered but never bound throws the
 * first time anything asks for it, and a handle bound to the wrong entry is worse - it works,
 * quietly, as the wrong block. Neither shows at boot, which is all the smoke test proves.
 */
public class FabricRegistrationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyBlockIsRegisteredAndBound(GameTestHelper helper) {
        check(helper, GatheringContent.TABLE_ID, GatheringContent.TABLE.get());
        check(helper, GatheringContent.COBBLESTONE_TABLE_ID,
                GatheringContent.COBBLESTONE_TABLE.get());
        check(helper, GatheringContent.BLACKSTONE_TABLE_ID,
                GatheringContent.BLACKSTONE_TABLE.get());
        check(helper, GatheringContent.CRYING_OBSIDIAN_TABLE_ID,
                GatheringContent.CRYING_OBSIDIAN_TABLE.get());
        check(helper, GatheringContent.COLLECTION_ID, GatheringContent.COLLECTION.get());
        check(helper, GatheringContent.SHOP_COUNTER_ID, GatheringContent.SHOP_COUNTER.get());
        check(helper, GatheringContent.SCOREKEEPERS_DESK_ID, GatheringContent.SCOREKEEPERS_DESK.get());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyItemIsRegisteredAndBound(GameTestHelper helper) {
        check(helper, GatheringContent.CARD_ID, GatheringContent.CARD.get());
        check(helper, GatheringContent.DECK_ID, GatheringContent.DECK.get());
        check(helper, GatheringContent.PACK_ID, GatheringContent.PACK.get());
        check(helper, GatheringContent.SEALED_ID, GatheringContent.SEALED.get());
        check(helper, GatheringContent.COLLECTION_ID, GatheringContent.COLLECTION_ITEM.get());
        check(helper, GatheringContent.SCOREKEEPERS_DESK_ID, GatheringContent.SCOREKEEPERS_DESK_ITEM.get());
        helper.succeed();
    }

    /** The desk's block entity attaches to the desk, which is how it keeps its tournament and its label. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void theDeskBlockEntityAcceptsTheDesk(GameTestHelper helper) {
        if (!GatheringContent.SCOREKEEPERS_DESK_ENTITY.get().isValid(GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState())) {
            helper.fail("The desk's block entity will not attach to the desk");
            return;
        }
        helper.succeed();
    }

    /**
     * A joining client is asked for its protocol number: the receiver for its answer is registered,
     * and only the server's own number is let in. The connection itself is not something an in-world
     * test has; this is the registration and the rule it applies.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void aClientOnAnotherProtocolIsTurnedAway(GameTestHelper helper) {
        if (!net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking.getGlobalReceivers()
                .contains(dev.gathering.fabric.ProtocolCheck.TYPE.id())) {
            helper.fail("Nothing on this server receives a joining client's protocol number");
            return;
        }
        int ours = dev.gathering.network.GatheringProtocol.VERSION;
        if (!dev.gathering.fabric.ProtocolCheck.accepts(ours) || dev.gathering.fabric.ProtocolCheck.accepts(ours - 1)
                || dev.gathering.fabric.ProtocolCheck.accepts(ours + 1)) {
            helper.fail("The protocol check does not let in exactly this server's number");
            return;
        }
        helper.succeed();
    }

    /**
     * The block entity, which is the one registration that cannot be checked by looking it up:
     * a type bound to the wrong block builds fine and refuses to attach at runtime.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void theTableBlockEntityAcceptsItsBlocks(GameTestHelper helper) {
        var type = GatheringContent.TABLE_ENTITY.get();
        for (Block block : new Block[] {
                GatheringContent.TABLE.get(), GatheringContent.COBBLESTONE_TABLE.get(),
                GatheringContent.BLACKSTONE_TABLE.get(),
                GatheringContent.CRYING_OBSIDIAN_TABLE.get()}) {
            if (!type.isValid(block.defaultBlockState())) {
                helper.fail("The table block entity will not attach to "
                        + BuiltInRegistries.BLOCK.getKey(block));
                return;
            }
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, String id, Block block) {
        ResourceLocation wanted = ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, id);
        if (!BuiltInRegistries.BLOCK.getKey(block).equals(wanted)) {
            helper.fail("The handle for " + id + " is bound to "
                    + BuiltInRegistries.BLOCK.getKey(block));
        }
    }

    private static void check(GameTestHelper helper, String id, Item item) {
        ResourceLocation wanted = ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, id);
        if (!BuiltInRegistries.ITEM.getKey(item).equals(wanted)) {
            helper.fail("The handle for " + id + " is bound to "
                    + BuiltInRegistries.ITEM.getKey(item));
        }
    }
}
