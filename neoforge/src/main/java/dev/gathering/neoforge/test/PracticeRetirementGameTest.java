package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.match.MatchRules;
import dev.gathering.item.CardComponent;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.server.PracticeTable;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Taking apart a practice game left behind by the design that replaced it.
 * <p>The guided first game is a local demonstration now and nothing creates one of these any
 * more. Old saves still hold them, and some of those saves were written before the intake
 * guard landed - so a table marked practice can be holding <b>a real deck somebody built</b>,
 * which the old ending would have discarded on the way out. Retiring has to hand that back,
 * hand it back once, and be safe to run again on the next tick, the next load and the next
 * launch.
 * <p>These replace the fixtures that tested the old feature's own lifecycle. They keep the
 * thing worth keeping from them: that a deck a player owns is still there afterwards.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PracticeRetirementGameTest {

    /** A deck with nothing in it but blank stock, so no card service is ever asked anything. */
    private static DeckComponent ownedDeck() {
        return new DeckComponent("Built by hand", "", Optional.empty(),
                List.of(CardComponent.of(PaperStock.BLANK.identity())), List.of(), List.of());
    }

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    private static TableBlockEntity entity(GameTestHelper helper, BlockPos origin) {
        return TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
    }

    /**
     * How many copies of this exact deck the player can actually get at.
     * <p>Inventory and the floor around the table both, because "handed back" means either -
     * a deck goes to whoever put it down wherever they are, and onto the ground beside the
     * table when there is nowhere else for it. What is being counted is that it exists once,
     * not where it ended up.
     */
    private static int copiesOf(GameTestHelper helper, ServerPlayer player, DeckComponent deck,
            BlockPos origin) {
        int found = 0;
        for (ItemStack item : player.getInventory().items) {
            if (DeckItem.deckOf(item).map(deck::equals).orElse(false)) {
                found += item.getCount();
            }
        }
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(origin).inflate(6))) {
            if (DeckItem.deckOf(item.getItem()).map(deck::equals).orElse(false)) {
                found += item.getItem().getCount();
            }
        }
        return found;
    }

    /**
     * Builds the thing an old save can contain: a practice table that is holding a real deck.
     * <p>Put there with {@code holdDeck} rather than by committing one, because committing one
     * is refused now - which is the fix for the original defect, and exactly why the saves that
     * still need repairing are the ones written before it.
     */
    private static void aPracticeTableHoldingARealDeck(
            GameTestHelper helper, ServerPlayer player, BlockPos origin, DeckComponent deck) {
        if (PracticeTable.start(player, origin) != PracticeTable.Outcome.STARTED) {
            throw new AssertionError("the legacy practice fixture did not start");
        }
        SeatId seat = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID())
                .orElseThrow();
        entity(helper, origin).holdDeck(seat, deck, null, player.getUUID());
    }

    /** Retiring hands a real held deck back, exactly once. */
    @GameTest(template = "empty")
    public static void retiringhandsbackarealdeckexactlyonce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        BlockPos origin = table(helper);
        DeckComponent deck = ownedDeck();
        aPracticeTableHoldingARealDeck(helper, player, origin, deck);

        if (copiesOf(helper, player, deck, origin) != 0) {
            helper.fail("the fixture left a copy of the deck lying about before retiring");
            return;
        }

        boolean retired = PracticeTable.retire(helper.getLevel(), origin, entity(helper, origin));

        if (!retired) {
            helper.fail("retiring a practice table reported that there was nothing to retire");
            return;
        }
        int back = copiesOf(helper, player, deck, origin);
        if (back != 1) {
            helper.fail("retiring returned " + back + " copies of an owned deck; expected exactly 1");
            return;
        }
        if (entity(helper, origin).isPractice()) {
            helper.fail("the table is still marked practice after being retired");
            return;
        }
        if (entity(helper, origin).hasSession()) {
            helper.fail("the invented game survived its table being retired");
            return;
        }
        helper.succeed();
    }

    /**
     * Retiring twice is the same as retiring once.
     * <p>It runs from a tick, so it will be reached again on the very next one. A migration
     * that handed the deck back each time would be a duplication bug wearing a repair's
     * clothes, and one that threw would take the table's chunk down with it.
     */
    @GameTest(template = "empty")
    public static void retiringtwicehandsbacknothingextra(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        BlockPos origin = table(helper);
        DeckComponent deck = ownedDeck();
        aPracticeTableHoldingARealDeck(helper, player, origin, deck);

        PracticeTable.retire(helper.getLevel(), origin, entity(helper, origin));
        boolean again = PracticeTable.retire(helper.getLevel(), origin, entity(helper, origin));

        if (again) {
            helper.fail("retiring a second time thought there was still a practice game here");
            return;
        }
        int back = copiesOf(helper, player, deck, origin);
        if (back != 1) {
            helper.fail("after retiring twice there are " + back + " copies of one deck");
            return;
        }
        helper.succeed();
    }

    /**
     * An ordinary game is not touched by any of this.
     * <p>The migration runs from the ticker every table has, so the case that matters most is
     * the one where it must do nothing at all: somebody's real match, with somebody's real
     * deck on it, ticking away while this code runs past it.
     */
    @GameTest(template = "empty")
    public static void anordinarygameisleftalone(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        BlockPos origin = table(helper);
        DeckComponent deck = ownedDeck();

        // Somebody has to be sitting at it, or there is no game to start - which is the
        // difference between a table and a match, and the fixture has to make a real one.
        var seat = dev.gathering.block.TableClusters.at(helper.getLevel(), origin).seats().get(0);
        if (TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player.getUUID())
                != TableSeats.Claim.TAKEN) {
            throw new AssertionError("the ordinary game fixture could not seat anybody");
        }
        if (TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, 1)) != TableSessions.Outcome.STARTED) {
            throw new AssertionError("the ordinary game fixture did not start");
        }
        entity(helper, origin).holdDeck(new SeatId(0), deck, null, player.getUUID());

        boolean retired = PracticeTable.retire(helper.getLevel(), origin, entity(helper, origin));

        if (retired) {
            helper.fail("retiring claimed an ordinary game was a practice one");
            return;
        }
        if (!entity(helper, origin).hasSession()) {
            helper.fail("an ordinary game was ended by the practice migration");
            return;
        }
        if (entity(helper, origin).heldDecks().size() != 1) {
            helper.fail("an ordinary game's held deck was handed back underneath it");
            return;
        }
        if (copiesOf(helper, player, deck, origin) != 0) {
            helper.fail("an ordinary game's deck was turned into an item while it was playing");
            return;
        }
        helper.succeed();
    }

    /**
     * A practice flag with no game left on it is still cleared.
     * <p>This is the table that would otherwise be broken for ever. Every real deck put on it
     * is refused, because the intake guard asks whether the table is practice and it says yes,
     * and nothing would ever have told it otherwise: the old lifecycle only cleared the flag
     * by ending a session, and there is no session here to end.
     */
    @GameTest(template = "empty")
    public static void apracticeflagwithnogameleftisstillcleared(GameTestHelper helper) {
        BlockPos origin = table(helper);
        TableBlockEntity table = entity(helper, origin);
        table.markAsPractice();

        if (table.hasSession()) {
            helper.fail("the fixture was meant to have no game on it");
            return;
        }

        boolean retired = PracticeTable.retire(helper.getLevel(), origin, table);

        if (!retired) {
            helper.fail("a table marked practice with no game on it was not retired");
            return;
        }
        if (entity(helper, origin).isPractice()) {
            helper.fail("the flag survived, so this table refuses every real deck for ever");
            return;
        }
        helper.succeed();
    }

    /**
     * The chair nobody was ever in is empty afterwards.
     * <p>The demonstration seat is held by a name no player can have, so a seat left taken is
     * a chair at a real table that nobody can sit in and nobody can be asked to leave.
     */
    @GameTest(template = "empty")
    public static void thedemonstrationseatisfreedagain(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        BlockPos origin = table(helper);
        if (PracticeTable.start(player, origin) != PracticeTable.Outcome.STARTED) {
            throw new AssertionError("the legacy practice fixture did not start");
        }
        if (TableSeats.seatOf(helper.getLevel(), origin, PracticeTable.demonstrationSeat())
                .isEmpty()) {
            helper.fail("the fixture never seated the demonstration in the first place");
            return;
        }

        PracticeTable.retire(helper.getLevel(), origin, entity(helper, origin));

        if (TableSeats.seatOf(helper.getLevel(), origin, PracticeTable.demonstrationSeat())
                .isPresent()) {
            helper.fail("the demonstration is still sitting at a table that is now an ordinary one");
            return;
        }
        helper.succeed();
    }

    /**
     * The retired payload starts nothing.
     * <p>Taking the button away is not the same as closing the door. An older client still
     * knows how to ask, and asking must now get an answer rather than a practice game.
     */
    @GameTest(template = "empty")
    public static void theretiredpayloadstartsnopracticegame(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 1, 1)));

        PracticeTable.handle(player, new dev.gathering.network.PracticePayload(
                origin, dev.gathering.network.PracticePayload.What.START));

        if (entity(helper, origin).hasSession()) {
            helper.fail("the retired START payload started a game");
            return;
        }
        if (entity(helper, origin).isPractice()) {
            helper.fail("the retired START payload marked the table as practice");
            return;
        }
        helper.succeed();
    }
}
