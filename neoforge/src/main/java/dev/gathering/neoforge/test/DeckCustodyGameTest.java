package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.SeatId;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What happens to a deck once it has been put down on a table.
 *
 * <p>The table is a deckbox for the length of a match: the library and the commanders go into
 * the game, the sideboard cannot (it is not in play), and the item itself is consumed. Without
 * somewhere to keep the whole thing, committing a deck destroyed a quarter of it and ending
 * the game destroyed the rest - so these check the deck comes back, comes back whole, and
 * comes back even when nobody is there to hand it to.
 *
 * <p>This is the kind of loss that is invisible until it has already happened to somebody's
 * hundred-card deck, which is why it is tested in a world rather than reasoned about.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckCustodyGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
    private static final UUID BOLT = UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a");

    @GameTest(template = "empty")
    public static void aDeckHandedBackIsTheWholeDeck(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DeckComponent deck = deck();

        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(0), deck, null, null);

        TableSessions.returnDecks(helper.getLevel(), origin, table);

        DeckComponent returned = deckInInventory(player)
                .or(() -> deckOnTheFloor(helper, origin))
                .orElse(null);
        if (returned == null) {
            helper.fail("The table kept the deck");
            return;
        }
        // The sideboard is the half that has nowhere else to be: it never entered the session,
        // so if the table does not hand it back, nothing does.
        if (returned.entries().size() != deck.entries().size()) {
            helper.fail("Got back " + returned.entries().size() + " mainboard cards, not "
                    + deck.entries().size());
        }
        if (returned.sideboard().size() != deck.sideboard().size()) {
            helper.fail("Got back " + returned.sideboard().size() + " sideboard cards, not "
                    + deck.sideboard().size());
        }
        if (!returned.name().equals(deck.name())) {
            helper.fail("The deck came back called \"" + returned.name() + "\"");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckWithNobodyToTakeItLandsOnTheTable(GameTestHelper helper) {
        // Everybody walked away mid-match. The deck is still theirs.
        BlockPos origin = place(helper, 1, 2, 1);
        clearItems(helper, origin);

        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(0), deck(), null, null);

        TableSessions.returnDecks(helper.getLevel(), origin, table);

        if (deckOnTheFloor(helper, origin).isEmpty()) {
            helper.fail("A deck with nobody to give it to went nowhere at all");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingTheTableGivesTheDecksBack(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        clearItems(helper, origin);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null, null);

        helper.getLevel().destroyBlock(TablePart.SOUTH_EAST.offsetFrom(origin), false);

        if (deckOnTheFloor(helper, origin).isEmpty()) {
            helper.fail("Breaking the table ate the deck inside it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aHeldDeckSurvivesBeingWrittenDownAndReadBack(GameTestHelper helper) {
        // A server restart mid-match must not eat four decks.
        BlockPos origin = place(helper, 1, 2, 1);
        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(2), deck(), null, null);

        var registries = helper.getLevel().registryAccess();
        var saved = table.saveWithoutMetadata(registries);

        TableBlockEntity reloaded = new TableBlockEntity(
                origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, registries);

        DeckComponent back = reloaded.deckOf(new SeatId(2)).orElse(null);
        if (back == null) {
            helper.fail("The held deck did not survive the save");
            return;
        }
        if (back.sideboard().size() != deck().sideboard().size()) {
            helper.fail("The sideboard did not survive the save");
        }
        if (reloaded.deckOf(new SeatId(0)).isPresent()) {
            helper.fail("A seat that never put a deck down got one back");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------- fixtures

    /**
     * A drafted deck comes back from a match still knowing what it was drafted from.
     *
     * <p>The table takes the whole deck for the length of a match and hands back a new stack
     * afterwards, so anything living on the old stack is gone unless the table kept it. Which
     * means a drafted deck used to come back from its first game with no pool on it, and the
     * limited check it had been playing under quietly stopped applying from the second game
     * onwards - the sort of failure nobody notices until somebody wins with a card they
     * never opened.
     */
    @GameTest(template = "empty")
    public static void aDraftedDeckKeepsItsPoolThroughAMatch(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        clearItems(helper, origin);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DraftedPool pool = new DraftedPool(
                List.of(card(SOL_RING), card(SOL_RING), card(BOLT)), "a pod");

        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(0), deck(), pool, null);

        if (!table.poolOf(new SeatId(0)).equals(java.util.Optional.of(pool))) {
            helper.fail("the table did not keep the pool it was handed");
            return;
        }

        // Through a save and load first, because a match outlives a restart.
        net.minecraft.nbt.CompoundTag saved =
                table.saveWithFullMetadata(helper.getLevel().registryAccess());
        TableBlockEntity reloaded =
                new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        if (!reloaded.poolOf(new SeatId(0)).equals(java.util.Optional.of(pool))) {
            helper.fail("a pool did not survive a save and load: " + reloaded.poolOf(new SeatId(0)));
            return;
        }

        TableSessions.returnDecks(helper.getLevel(), origin, table);

        ItemStack back = deckStackInInventory(player)
                .or(() -> deckStackOnTheFloor(helper, origin))
                .orElse(null);
        if (back == null) {
            helper.fail("no deck came back from the table at all");
            return;
        }
        DraftedPool returned = back.get(dev.gathering.registry.GatheringComponents.POOL.get());
        if (returned == null) {
            helper.fail("a drafted deck came back with no pool on it");
            return;
        }
        if (!returned.equals(pool)) {
            helper.fail("the pool that came back is not the one that went in");
            return;
        }
        helper.succeed();
    }

    /** An ordinary imported deck has no pool, and must not acquire one. */
    @GameTest(template = "empty")
    public static void anOrdinaryDeckComesBackWithNoPool(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        clearItems(helper, origin);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(0), deck(), null, null);

        TableSessions.returnDecks(helper.getLevel(), origin, table);

        ItemStack back = deckStackInInventory(player)
                .or(() -> deckStackOnTheFloor(helper, origin))
                .orElse(null);
        if (back == null) {
            helper.fail("no deck came back from the table at all");
            return;
        }
        if (back.get(dev.gathering.registry.GatheringComponents.POOL.get()) != null) {
            helper.fail("an imported deck came back claiming to have been drafted");
        }
        helper.succeed();
    }

    private static DeckComponent deck() {
        return new DeckComponent(
                "Custody Test", "", Optional.empty(),
                List.of(card(SOL_RING), card(SOL_RING), card(BOLT)),
                List.of(card(SOL_RING)),
                List.of(card(BOLT), card(BOLT)));
    }

    private static CardComponent card(UUID printing) {
        return CardComponent.of(CardIdentity.ofPrinting(printing));
    }

    private static TableBlockEntity tableAt(GameTestHelper helper, BlockPos origin) {
        return TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
    }

    /** The deck stack itself, rather than what is inside it, so its other components show. */
    private static Optional<ItemStack> deckStackInInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (DeckItem.deckOf(stack).isPresent()) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> deckStackOnTheFloor(GameTestHelper helper, BlockPos origin) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6.0d))
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> DeckItem.deckOf(stack).isPresent())
                .findFirst();
    }

    private static Optional<DeckComponent> deckInInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            Optional<DeckComponent> deck = DeckItem.deckOf(player.getInventory().getItem(slot));
            if (deck.isPresent()) {
                return deck;
            }
        }
        return Optional.empty();
    }

    /**
     * Any deck lying near the table.
     *
     * <p>Wide net on purpose: where exactly a dropped item lands is not the thing under test,
     * and pinning it to a block would fail on a physics change rather than on a bug.
     */
    private static Optional<DeckComponent> deckOnTheFloor(GameTestHelper helper, BlockPos origin) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6.0d))
                .stream()
                .map(ItemEntity::getItem)
                .map(DeckItem::deckOf)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Clears anything already lying about.
     *
     * <p>The world these run in is on disk and survives between runs, so a previous run's
     * dropped deck would let a broken one of these pass. Learned the hard way.
     */
    private static void clearItems(GameTestHelper helper, BlockPos origin) {
        helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8.0d))
                .forEach(ItemEntity::discard);
    }

    /**
     * A deck goes back to whoever put it down, not to whoever is in the chair.
     *
     * <p>It used to be handed to the seat's current occupant. A player who stood up mid-match
     * had their deck dropped on the floor, and whoever took the vacated chair had somebody
     * else's collection put into their inventory - cards changing hands because of where a
     * person happened to be standing.
     */
    @GameTest(template = "empty")
    public static void aDeckGoesBackToWhoeverPutItDown(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer squatter = helper.makeMockServerPlayerInLevel();

        // The owner puts a deck down at seat 0, then walks away and somebody else sits there.
        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(0), deck(), null, owner.getUUID());
        SeatAnchor seat = TableClusters.at(helper.getLevel(), origin).seats().get(0);
        TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), squatter.getUUID());

        TableSessions.returnDecks(helper.getLevel(), origin, table);

        if (deckInInventory(squatter).isPresent()) {
            helper.fail("somebody else's deck was handed to whoever took the chair");
            return;
        }
        if (deckInInventory(owner).isEmpty()) {
            helper.fail("the deck did not go back to the player who put it down");
            return;
        }
        helper.succeed();
    }

    /**
     * Leaving the table hands your deck back without ending anybody's match.
     *
     * <p>Reported: "cant get deck back from table". A deck came back only when the whole match
     * ended, and ending a match is a thing the rest of the table is in the middle of.
     */
    @GameTest(template = "empty")
    public static void leavingTheTableHandsTheDeckBack(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();

        TableBlockEntity table = tableAt(helper, origin);
        table.holdDeck(new SeatId(1), deck(), null, owner.getUUID());
        // Somebody else's deck is on the same table and must stay there.
        table.holdDeck(new SeatId(0), deck(), null, new UUID(77L, 1L));

        TableSessions.returnDeckTo(helper.getLevel(), origin, new SeatId(1));

        if (deckInInventory(owner).isEmpty()) {
            helper.fail("leaving the table did not hand the deck back");
            return;
        }
        if (table.deckOf(new SeatId(0)).isEmpty()) {
            helper.fail("one player leaving took another player's deck off the table");
            return;
        }
        if (table.deckOf(new SeatId(1)).isPresent()) {
            helper.fail("the table is still holding a deck it handed back");
            return;
        }
        helper.succeed();
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(
                    part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    /**
     * A table holds and hands back its decks in seat order.
     *
     * <p>Not tidiness. Putting held decks back down between games of a set writes a line in
     * the session log for each, so this decides the log's own order - and the log is the
     * record everybody is supposed to be able to check afterwards. Held in a map ordered by a
     * hash salted once per launch, the same match would have read differently every time the
     * server started.
     */
    @GameTest(template = "empty")
    public static void decksAreHeldAndHandedBackInSeatOrder(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        clearItems(helper, origin);
        TableBlockEntity table = tableAt(helper, origin);

        // Put down out of order and with enough seats that a hash order is near certain to
        // differ from the one they went in.
        int[] order = {5, 1, 7, 3, 6, 0, 4, 2};
        List<SeatId> expected = new java.util.ArrayList<>();
        for (int seat : order) {
            table.holdDeck(new SeatId(seat), deck(), null, null);
            expected.add(new SeatId(seat));
        }
        expected.sort(java.util.Comparator.comparingInt(SeatId::index));

        List<SeatId> held = new java.util.ArrayList<>(table.heldDecks().keySet());
        held.sort(java.util.Comparator.comparingInt(SeatId::index));
        if (!held.equals(expected)) {
            helper.fail("The table is not holding every deck it was given: " + held);
            return;
        }
        // The order they are walked in is the order they were put in, which is what the log
        // ends up recording.
        List<SeatId> walked = new java.util.ArrayList<>(table.heldDecks().keySet());
        List<SeatId> asGiven = new java.util.ArrayList<>();
        for (int seat : order) {
            asGiven.add(new SeatId(seat));
        }
        if (!walked.equals(asGiven)) {
            helper.fail("Held decks came back in " + walked + ", not the order they went in");
            return;
        }
        List<SeatId> handedBack = new java.util.ArrayList<>(table.releaseDecks().keySet());
        if (!handedBack.equals(asGiven)) {
            helper.fail("Decks were handed back in " + handedBack + ", not the order held");
        }
        helper.succeed();
    }
}
