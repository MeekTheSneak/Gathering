package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.DraftPods;
import dev.gathering.block.PodSignup;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.Owed;
import dev.gathering.server.PodSignups;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Signing up for a draft or sealed event at real tables, with real packs.
 * <p>What these follow is the packs. A pack the table takes is somebody's, and every way out
 * of a signup - withdrawing, standing up, the host calling it off, a restart, the table being
 * broken - has to end with it back with that person and nowhere else. Counted, not assumed:
 * each test checks where the pack is afterwards, not only that nothing threw.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodSignupGameTest {

    private static ItemStack packs(String set, int count) {
        ItemStack stack = PackItem.of(new PackComponent(set, "draft"));
        stack.setCount(count);
        return stack;
    }

    private static int packsIn(ServerPlayer player, String set) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            PackComponent about = stack.get(dev.gathering.registry.GatheringComponents.PACK.get());
            if (about != null && about.setCode().equalsIgnoreCase(set)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ServerPlayer seated(GameTestHelper helper, BlockPos origin, TableCell cell, Side side) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Survival, as a player at a table is. A mock starts in creative.
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(origin.getX() + 1.0, origin.getY(), origin.getZ() + 1.0);
        TableSeats.take(helper.getLevel(), origin, cell, side, player.getUUID());
        return player;
    }

    private static TableBlockEntity tableAt(GameTestHelper helper, BlockPos origin) {
        return TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
    }

    /** Putting a stack in takes what is owed and no more; the rest stays in the hand. */
    @GameTest(template = "tables")
    public static void astackGoesInUpToWhatIsOwed(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer alex = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSignups.Created created = PodSignups.create(helper.getLevel(), origin, alex.getUUID(),
                PodSettings.usual(PodSettings.Kind.DRAFT));
        if (created != PodSignups.Created.OPEN) {
            helper.fail("a signup would not open: " + created);
            return;
        }
        ItemStack hand = packs("m21", 5);
        int in = PodSignups.putIn(alex, origin, hand);
        if (in != 3 || hand.getCount() != 2) {
            helper.fail("put in " + in + " and left " + hand.getCount() + ", not 3 and 2");
            return;
        }
        PodSignup signup = tableAt(helper, origin).signup().orElseThrow();
        if (signup.heldFor(alex.getUUID()).size() != 3) {
            helper.fail("the table holds " + signup.heldFor(alex.getUUID()).size() + " of Alex's packs");
            return;
        }
        helper.succeed();
    }

    /** A pack the event cannot use is never taken out of the hand. */
    @GameTest(template = "tables")
    public static void apackOfTheWrongSetStaysInTheHand(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer alex = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSignups.create(helper.getLevel(), origin, alex.getUUID(), new PodSettings(
                PodSettings.Kind.SEALED, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.oneSet("znr"), 6, 0, PodSettings.CardsGo.PLAYERS_KEEP));
        ItemStack hand = packs("m21", 2);
        if (PodSignups.putIn(alex, origin, hand) != 0 || hand.getCount() != 2) {
            helper.fail("a pack of the wrong set was taken: " + hand.getCount() + " left");
            return;
        }
        if (!tableAt(helper, origin).signup().orElseThrow().held().isEmpty()) {
            helper.fail("the table is holding a pack it refused");
            return;
        }
        helper.succeed();
    }

    /** Standing up hands a player's own packs back; a sponsoring host's stay for everybody. */
    @GameTest(template = "tables")
    public static void standingUpHandsYourPacksBack(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer alex = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSignups.create(helper.getLevel(), origin, alex.getUUID(), PodSettings.usual(PodSettings.Kind.SEALED));
        PodSignups.putIn(alex, origin, packs("m21", 4));
        int before = packsIn(alex, "m21");

        TableSeats.leave(helper.getLevel(), origin, alex.getUUID());
        PodSignups.seatReleased(helper.getLevel(), origin, alex.getUUID());

        if (packsIn(alex, "m21") != before + 4) {
            helper.fail("standing up handed back " + (packsIn(alex, "m21") - before) + " of 4 packs");
            return;
        }
        if (!tableAt(helper, origin).signup().orElseThrow().heldFor(alex.getUUID()).isEmpty()) {
            helper.fail("the table still holds packs it handed back");
            return;
        }

        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.SOUTH);
        TableBlockEntity table = tableAt(helper, origin);
        PodSignups.handBackEverything(helper.getLevel(), origin, table, null);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), new PodSettings(
                PodSettings.Kind.SEALED, PodSettings.Source.SPONSORED, PodSettings.SetRule.ANY,
                1, 0, PodSettings.CardsGo.TO_SPONSOR));
        if (PodSignups.putIn(host, origin, packs("m21", 1)) != 1) {
            helper.fail("the sponsor could not put a pack in");
            return;
        }
        PodSignups.seatReleased(helper.getLevel(), origin, host.getUUID());
        if (tableAt(helper, origin).signup().orElseThrow().heldFor(host.getUUID()).size() != 1) {
            helper.fail("a sponsor standing up took the event's packs away");
            return;
        }
        helper.succeed();
    }

    /** Only the host calls an event off, and calling it off hands every pack back. */
    @GameTest(template = "tables")
    public static void callingItOffHandsEveryPackBack(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        ServerPlayer guest = seated(helper, origin, new TableCell(0, 0), Side.SOUTH);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), PodSettings.usual(PodSettings.Kind.DRAFT));
        PodSignups.putIn(host, origin, packs("m21", 3));
        PodSignups.putIn(guest, origin, packs("m21", 2));
        int hostBefore = packsIn(host, "m21");
        int guestBefore = packsIn(guest, "m21");

        if (PodSignups.cancel(helper.getLevel(), origin, guest)) {
            helper.fail("a guest called the whole event off");
            return;
        }
        if (!PodSignups.cancel(helper.getLevel(), origin, host)) {
            helper.fail("the host could not call the event off");
            return;
        }
        if (packsIn(host, "m21") != hostBefore + 3 || packsIn(guest, "m21") != guestBefore + 2) {
            helper.fail("calling it off handed back " + (packsIn(host, "m21") - hostBefore) + " and "
                    + (packsIn(guest, "m21") - guestBefore) + ", not 3 and 2");
            return;
        }
        if (tableAt(helper, origin).hasSignup()) {
            helper.fail("the signup is still open after being called off");
            return;
        }
        helper.succeed();
    }

    /** A pack held for somebody who is not online is owed to them, not lost or given away. */
    @GameTest(template = "tables")
    public static void apackForSomebodyAwayIsOwedToThem(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        UUID away = UUID.fromString("00000000-0000-4000-8000-0000000c0ffe");
        Owed.forget(away);
        TableBlockEntity table = tableAt(helper, origin);
        table.setSignup(PodSignup.open(host.getUUID(), PodSettings.usual(PodSettings.Kind.SEALED))
                .with(new PodSignup.Held(away, packs("m21", 1))));

        PodSignups.handBackEverything(helper.getLevel(), origin, table, null);

        try {
            if (Owed.waitingFor(away) != 1) {
                helper.fail("the absent player is owed " + Owed.waitingFor(away) + " things, not their pack");
                return;
            }
            if (packsIn(host, "m21") != 0) {
                helper.fail("an absent player's pack went to the host");
                return;
            }
        } finally {
            Owed.forget(away);
        }
        helper.succeed();
    }

    /** A restart keeps every held pack, who it belongs to, and what the host decided. */
    @GameTest(template = "tables")
    public static void thesignupsurvivesarestart(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSettings settings = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.perPack(List.of("znr", "znr", "khm")), 3, 1,
                PodSettings.CardsGo.TO_CONTRIBUTORS);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), settings);
        PodSignups.putIn(host, origin, packs("znr", 2));

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = tableAt(helper, origin).saveWithoutMetadata(registries);
        TableBlockEntity reloaded = new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, registries);

        PodSignup back = reloaded.signup().orElse(null);
        if (back == null) {
            helper.fail("the signup did not survive a save");
            return;
        }
        if (!back.settings().equals(settings) || !back.host().equals(host.getUUID())) {
            helper.fail("the signup came back as " + back.settings() + " hosted by " + back.host());
            return;
        }
        if (back.heldFor(host.getUUID()).size() != 2
                || !back.held().get(0).ref().setCode().equals("znr")) {
            helper.fail("the held packs came back as " + back.held());
            return;
        }
        if (reloaded.signupIsToBeHandedBack()) {
            helper.fail("a good signup was marked to be handed back");
            return;
        }
        helper.succeed();
    }

    /** Settings that no longer make sense are not kept, but the packs under them are returned. */
    @GameTest(template = "tables")
    public static void asignupThatWillNotRestoreHandsItsPacksBack(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), PodSettings.usual(PodSettings.Kind.SEALED));
        PodSignups.putIn(host, origin, packs("m21", 2));
        int before = packsIn(host, "m21");

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = tableAt(helper, origin).saveWithoutMetadata(registries);
        saved.getCompound("pod_signup").putString("kind", "SOMETHING_ELSE");
        TableBlockEntity table = tableAt(helper, origin);
        table.loadWithComponents(saved, registries);
        if (!table.signupIsToBeHandedBack() || table.signup().orElseThrow().held().size() != 2) {
            helper.fail("a signup with unreadable settings was not kept to hand back");
            return;
        }
        TableBlockEntity.serverTick(helper.getLevel(), origin, helper.getLevel().getBlockState(origin), table);
        if (table.hasSignup() || packsIn(host, "m21") != before + 2) {
            helper.fail("the packs were not handed back: " + (packsIn(host, "m21") - before) + " of 2");
            return;
        }
        helper.succeed();
    }

    /** Breaking the table hands every held pack back. */
    @GameTest(template = "tables")
    public static void breakingTheTableHandsThePacksBack(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        clearItems(helper, origin);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), PodSettings.usual(PodSettings.Kind.SEALED));
        PodSignups.putIn(host, origin, packs("m21", 3));
        int before = packsIn(host, "m21");
        TableSeats.leave(helper.getLevel(), origin, host.getUUID());
        // Left the seat without the hook, as a player who logged out in their chair would.
        helper.getLevel().destroyBlock(origin, false);

        int back = packsIn(host, "m21") - before + packsOnTheFloor(helper, origin);
        if (back != 3) {
            helper.fail("breaking the table handed back " + back + " of 3 packs");
            return;
        }
        helper.succeed();
    }

    /** Nothing else starts on tables an event is being signed up at. */
    @GameTest(template = "tables")
    public static void nothingElseStartsWhileSigningUp(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), PodSettings.usual(PodSettings.Kind.DRAFT));

        TableSessions.Outcome game = TableSessions.start(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER));
        if (game != TableSessions.Outcome.EVENT_HERE) {
            helper.fail("a game started over a signup: " + game);
            return;
        }
        List<CardIdentity> cube = new java.util.ArrayList<>();
        for (int index = 0; index < 200; index++) {
            cube.add(CardIdentity.ofPrinting(new UUID(3L, index)));
        }
        DraftPods.Outcome draft = DraftPods.start(helper.getLevel(), origin, cube, true);
        if (draft != DraftPods.Outcome.SIGNING_UP) {
            helper.fail("a cube draft started over a signup: " + draft);
            return;
        }
        if (PodSignups.create(helper.getLevel(), origin, host.getUUID(),
                PodSettings.usual(PodSettings.Kind.SEALED)) != PodSignups.Created.BUSY) {
            helper.fail("a second signup opened over the first");
            return;
        }
        helper.succeed();
    }

    private static int packsOnTheFloor(GameTestHelper helper, BlockPos origin) {
        int count = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6.0d))) {
            if (item.getItem().has(dev.gathering.registry.GatheringComponents.PACK.get())) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }

    private static void clearItems(GameTestHelper helper, BlockPos origin) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8.0d))
                .forEach(ItemEntity::discard);
    }

    private static BlockPos twoTables(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        return origin;
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
