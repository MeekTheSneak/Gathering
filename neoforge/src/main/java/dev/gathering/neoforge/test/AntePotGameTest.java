package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.ante.AntePot;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The pot, in a running world.
 *
 * <p>Where the arithmetic lives is {@link AntePot} and it has its own tests. What cannot be
 * checked there is the part that costs somebody a card: whether the pot survives being
 * written to disk, whether a settled pot can be settled twice, and whether the cards actually
 * arrive anywhere when a game ends.
 *
 * <p>Every test here counts the cards afterwards. A pot that paid out and a pot that was
 * handed back should both leave exactly what went in.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AntePotGameTest {

    private static final UUID BOLT = UUID.fromString("11111111-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID RING = UUID.fromString("22222222-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    /**
     * A pot survives being written down and read back.
     *
     * <p>The whole reason escrow is on the block rather than inside the game. Losing this to a
     * restart is losing cards people agreed to play for and never got a chance to win back.
     */
    @GameTest(template = "empty")
    public static void aPotSurvivesBeingSavedAndLoaded(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.stake(new SeatId(0), List.of(card(BOLT), card(BOLT)));
        table.stake(new SeatId(1), List.of(card(RING)));

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag written = table.saveWithoutMetadata(registries);
        table.releasePot();
        if (!table.pot().isEmpty()) {
            helper.fail("releasing the pot did not empty it");
            return;
        }
        table.loadWithComponents(written, registries);

        AntePot read = table.pot();
        if (read.size() != 3) {
            helper.fail("a pot of three cards came back from disk with " + read.size());
            return;
        }
        if (read.stakeOf(new SeatId(0)).size() != 2
                || read.stakeOf(new SeatId(1)).size() != 1) {
            helper.fail("a pot came back from disk with the stakes on the wrong seats");
            return;
        }
        helper.succeed();
    }

    /**
     * Settling twice pays out once.
     *
     * <p>The pot is emptied by the release, before a card is handed anywhere, precisely so
     * that a second call finds nothing. A card existing in two places is the one arithmetic
     * mistake this feature must not make.
     */
    @GameTest(template = "empty")
    public static void aPotSettledTwicePaysOutOnce(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.stake(new SeatId(0), List.of(card(BOLT), card(RING)));

        TableSessions.settlePot(helper.getLevel(), origin, table, null);
        TableSessions.settlePot(helper.getLevel(), origin, table, null);

        int loose = cardsAround(helper, origin);
        if (loose != 2) {
            helper.fail("settling a two-card pot twice left " + loose + " cards on the floor");
            return;
        }
        helper.succeed();
    }

    /** A pot nobody won comes back, card for card. */
    @GameTest(template = "empty")
    public static void aPotNobodyWonGoesBackToItsOwners(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.stake(new SeatId(0), List.of(card(BOLT)));
        table.stake(new SeatId(1), List.of(card(RING), card(RING)));

        TableSessions.settlePot(helper.getLevel(), origin, table, null);

        if (cardsAround(helper, origin) != 3) {
            helper.fail("a three-card pot handed back " + cardsAround(helper, origin)
                    + " cards");
            return;
        }
        if (!table.pot().isEmpty()) {
            helper.fail("the table is still holding a pot it has handed back");
            return;
        }
        helper.succeed();
    }

    /** A pot with a winner is the same cards, all going one way. */
    @GameTest(template = "empty")
    public static void aWonPotIsTheSameCardsGoingOneWay(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.stake(new SeatId(0), List.of(card(BOLT)));
        table.stake(new SeatId(1), List.of(card(RING), card(RING)));

        TableSessions.settlePot(helper.getLevel(), origin, table, new SeatId(1));

        if (cardsAround(helper, origin) != 3) {
            helper.fail("a three-card pot paid out " + cardsAround(helper, origin) + " cards");
            return;
        }
        helper.succeed();
    }

    /** No pot is no work, and certainly not an empty payout that drops nothing anywhere. */
    @GameTest(template = "empty")
    public static void aTableWithNoPotSettlesNothing(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        TableSessions.settlePot(helper.getLevel(), origin, table, new SeatId(0));
        if (cardsAround(helper, origin) != 0) {
            helper.fail("a table with no pot produced cards from nowhere");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ bits

    private static CardIdentity card(UUID printing) {
        return CardIdentity.ofPrinting(printing, false);
    }

    /** Every card item lying on the ground near the table, counted by copies. */
    private static int cardsAround(GameTestHelper helper, BlockPos origin) {
        int found = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(origin).inflate(8.0))) {
            ItemStack stack = item.getItem();
            if (CardItem.cardOf(stack).isPresent()) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static BlockPos seatedTable(GameTestHelper helper, int players) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        List<SeatAnchor> anchors = TableClusters.at(level, origin).seats();
        for (int index = 0; index < Math.min(players, anchors.size()); index++) {
            SeatAnchor anchor = anchors.get(index);
            TableSeats.take(level, origin, anchor.cell(), anchor.side(),
                    new UUID(13L, index));
        }
        return origin;
    }
}
