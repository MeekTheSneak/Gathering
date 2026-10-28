package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.CollectionKeyPayload;
import dev.gathering.network.CollectionLockPayload;
import dev.gathering.server.CollectionKeys;
import dev.gathering.server.CollectionView;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The lock on a collection, and who holds a key to it.
 * <p>The owner asked for this (2026-09-16): view-only, locked outright, and people let in by name. What
 * is checked here is the part that only exists in a world - that a locked collection answers nothing at
 * all to somebody who has not been let in, that only its owner can change that, and that the lock is
 * still there after the block has been saved and read back.
 * <p>Reading is asked through {@link CollectionView#pageFor}, which is the production path every page a
 * screen ever shows comes down: a test that called the rights object directly would prove the rights
 * object and nothing about the cabinet.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionKeysGameTest {

    private static final UUID BOLT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @GameTest(template = "tables")
    public static void anOpenCollectionIsOpenToAnybody(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));

        if (pageFor(helper, stranger, at) == null) {
            helper.fail("a stranger could not read an open collection");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aLockedCollectionAnswersAStrangerNothing(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()).openedToLook(false));

        if (pageFor(helper, stranger, at) != null) {
            helper.fail("a stranger read a page of a locked collection");
            return;
        }
        if (pageFor(helper, owner, at) == null) {
            helper.fail("the owner could not read their own locked collection");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void somebodyLetInMayLook(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID())
                .openedToLook(false)
                .allowingLook(friend.getUUID()));

        if (pageFor(helper, friend, at) == null) {
            helper.fail("somebody let in could not look");
            return;
        }
        helper.succeed();
    }

    /** And somebody allowed to take from it can see what they are taking, without a second right. */
    @GameTest(template = "tables")
    public static void somebodyAllowedToTakeMayLook(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID())
                .openedToLook(false)
                .allowingTake(friend.getUUID()));

        if (pageFor(helper, friend, at) == null) {
            helper.fail("somebody allowed to take from a locked collection could not see into it");
            return;
        }
        helper.succeed();
    }

    /**
     * Only its owner holds the keys.
     * <p>On an <b>open</b> collection on purpose. A locked one refuses a stranger anyway - the lock is
     * checked before anything else, so a test on a locked cabinet passes whether or not anybody ever
     * checks who owns it, which is exactly what a first draft of this proved. Open, standing right in
     * front of it, with every other check satisfied, the only thing between a stranger and the keys is
     * that the collection is not theirs.
     */
    @GameTest(template = "tables")
    public static void aStrangerCannotChangeSomebodyElsesKeys(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));
        standAt(helper, stranger, at);

        CollectionKeys.lock(stranger, new CollectionLockPayload(helper.absolutePos(at), false, false, false));
        if (!collection.rights().open()) {
            helper.fail("a stranger locked somebody else's collection");
            return;
        }
        // Nor cut themselves a key to take everything out of it.
        CollectionKeys.set(stranger, helper.absolutePos(at), stranger.getUUID(), true, true, true);
        if (collection.rights().mayTake(stranger.getUUID())
                || collection.rights().mayAdd(stranger.getUUID())
                || !collection.rights().everybodyLetIn().isEmpty()) {
            helper.fail("a stranger cut themselves a key to somebody else's collection: "
                    + collection.rights());
            return;
        }
        helper.succeed();
    }

    /** And a locked one refuses them before it even gets that far. */
    @GameTest(template = "tables")
    public static void aStrangerCannotUnlockALockedOne(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()).openedToLook(false));
        standAt(helper, stranger, at);

        CollectionKeys.lock(stranger, new CollectionLockPayload(helper.absolutePos(at), true, false, false));
        if (collection.rights().open()) {
            helper.fail("a stranger unlocked somebody else's collection");
            return;
        }
        helper.succeed();
    }

    /** And the owner can, through the same payloads the screen sends. */
    @GameTest(template = "tables")
    public static void theOwnerLocksItAndLetsSomebodyIn(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));
        standAt(helper, owner, at);
        standAt(helper, friend, at);

        CollectionKeys.lock(owner, new CollectionLockPayload(helper.absolutePos(at), false, false, false));
        if (collection.rights().open()) {
            helper.fail("the owner locked their collection and it stayed open");
            return;
        }
        CollectionKeys.set(owner, helper.absolutePos(at), friend.getUUID(), true, true, false);
        if (!collection.rights().mayLook(friend.getUUID())
                || !collection.rights().mayTake(friend.getUUID())
                || collection.rights().mayAdd(friend.getUUID())) {
            helper.fail("the three rights did not land as they were sent: " + collection.rights());
            return;
        }
        // Every right off is off every list, which is what the row's cross sends.
        CollectionKeys.set(owner, helper.absolutePos(at), friend.getUUID(), false, false, false);
        if (!collection.rights().everybodyLetIn().isEmpty()) {
            helper.fail("shutting somebody out left them on a list: " + collection.rights());
            return;
        }
        helper.succeed();
    }

    /** A cabinet locked before the server stopped is locked when it starts again. */
    @GameTest(template = "tables")
    public static void theLockSurvivesBeingSavedAndReadBack(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID())
                .openedToLook(false)
                .allowingLook(friend.getUUID()));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 1);

        CompoundTag saved = collection.saveWithFullMetadata(helper.getLevel().registryAccess());
        CollectionBlockEntity again = place(helper, new BlockPos(3, 1, 1));
        again.loadWithComponents(saved, helper.getLevel().registryAccess());

        if (again.rights().open()) {
            helper.fail("a locked collection came back open");
            return;
        }
        if (!again.rights().mayLook(friend.getUUID())) {
            helper.fail("somebody let in was forgotten by the save");
            return;
        }
        if (again.rights().mayLook(helper.makeMockServerPlayerInLevel().getUUID())) {
            helper.fail("a stranger could look into a locked collection that had been read back");
            return;
        }
        helper.succeed();
    }

    /** A collection saved before there was a lock loads open, which is what it was. */
    @GameTest(template = "tables")
    public static void anOlderCollectionLoadsOpen(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()).openedToLook(false));

        CompoundTag saved = collection.saveWithFullMetadata(helper.getLevel().registryAccess());
        // The tag an older world has: everything but the key that says it is shut.
        saved.remove("Closed");
        CollectionBlockEntity again = place(helper, new BlockPos(3, 1, 1));
        again.loadWithComponents(saved, helper.getLevel().registryAccess());

        if (!again.rights().open()) {
            helper.fail("a collection saved before the lock existed came back locked");
            return;
        }
        helper.succeed();
    }

    /**
     * Stands a player at the block, which every path into a collection asks about before anything else.
     */
    private static void standAt(GameTestHelper helper, ServerPlayer player, BlockPos at) {
        BlockPos where = helper.absolutePos(at);
        player.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5);
    }

    private static dev.gathering.network.CollectionPagePayload pageFor(
            GameTestHelper helper, ServerPlayer player, BlockPos at) {
        BlockPos where = helper.absolutePos(at);
        player.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5);
        return CollectionView.pageFor(player, where,
                dev.gathering.network.CollectionQuery.EVERYTHING, false, 0, 8, false, -1);
    }

    private static CollectionBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at))
                instanceof CollectionBlockEntity collection) {
            return collection;
        }
        throw new IllegalStateException("A collection block was placed without its block entity");
    }

    /**
     * A collection can be handed to somebody else, which it never could be.
     * <p>Whoever put it down owned it for ever, and because the owner travels in the item, a cabinet
     * whose owner had stopped playing was locked to everybody with no way back - and the lock on looking
     * made that worse rather than better.
     */
    @GameTest(template = "tables")
    public static void acollectionCanBeHandedOver(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID())
                .openedToLook(false)
                .allowingTake(friend.getUUID()));
        standAt(helper, owner, at);
        standAt(helper, friend, at);

        dev.gathering.server.CollectionKeys.handOverTo(owner, helper.absolutePos(at), friend.getUUID());

        if (!collection.rights().isOwner(friend.getUUID())) {
            helper.fail("a collection handed over still belongs to whoever put it down");
            return;
        }
        // The new owner's rights are no longer a list entry, and the old owner keeps nothing.
        if (!collection.rights().everybodyLetIn().isEmpty()) {
            helper.fail("the new owner is still on a list: " + collection.rights());
            return;
        }
        if (collection.rights().mayLook(owner.getUUID())) {
            helper.fail("the old owner can still look into a locked collection they gave away");
            return;
        }
        helper.succeed();
    }

    /** And only its owner may hand it anywhere. */
    @GameTest(template = "tables")
    public static void astrangerCannotHandItToThemselves(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));
        standAt(helper, stranger, at);

        dev.gathering.server.CollectionKeys.handOverTo(
                stranger, helper.absolutePos(at), stranger.getUUID());

        if (!collection.rights().isOwner(owner.getUUID())) {
            helper.fail("a stranger gave themselves somebody else's collection");
            return;
        }
        helper.succeed();
    }
}
