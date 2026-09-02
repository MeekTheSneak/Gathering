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
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.service.CardDataService;
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
import net.minecraft.server.level.ServerPlayer;
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
     * A staked card leaves the deck the table is holding, not only the library.
     *
     * <p>The whole way through, because the two halves are what went wrong: the stake is
     * taken out of the <em>library</em> the game is dealt, and for a long time the table
     * separately kept the deck exactly as it went down, staked card and all. The winner was
     * handed the card out of the pot and the loser got the same card home in their deck.
     * Ante is the one feature whose entire point is that a card really changes owner, so a
     * card that exists twice afterwards is the failure it must not have.
     *
     * <p>Driven through the real commit, not through the helper it calls, because the helper
     * being right and never being called is exactly the shape this bug had.
     */
    @GameTest(template = "tables")
    public static void aStakedCardLeavesTheDeckTheTableHolds(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("no card service, so staking cannot be exercised");
            return;
        }
        CardMetadata bolt = cache(cards, "Held Bolt", "Instant");
        CardMetadata ring = cache(cards, "Held Ring", "Artifact");

        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(0);
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());

        if (dev.gathering.block.TableSessions.start(level, origin,
                dev.gathering.core.match.MatchRules.single(
                        dev.gathering.core.format.FormatPresets.COMMANDER))
                != dev.gathering.block.TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start at the table");
            return;
        }
        TableBlockEntity table = TableBlock.entityAt(level, origin).orElseThrow();
        table.playForKeeps(true);

        List<dev.gathering.item.CardComponent> mainboard = new java.util.ArrayList<>();
        for (int copy = 0; copy < 3; copy++) {
            mainboard.add(dev.gathering.item.CardComponent.of(
                    CardIdentity.ofPrinting(bolt.scryfallId(), false)));
        }
        mainboard.add(dev.gathering.item.CardComponent.of(
                CardIdentity.ofPrinting(ring.scryfallId(), false)));
        dev.gathering.item.DeckComponent went = new dev.gathering.item.DeckComponent(
                "For keeps", "", java.util.Optional.of(player.getUUID()),
                List.copyOf(mainboard), List.of(), List.of());

        TableBlock.putDown(level, origin, player, dev.gathering.item.DeckItem.of(went));

        List<CardIdentity> staked = table.pot().everything();
        if (staked.isEmpty()) {
            helper.fail("a for-keeps deck of four known cards staked nothing, so this"
                    + " checks nothing");
            return;
        }
        dev.gathering.item.DeckComponent held =
                table.heldDecks().values().stream().findFirst().orElse(null);
        if (held == null) {
            helper.fail("the table took a deck and is not holding one");
            return;
        }
        if (held.totalCards() + staked.size() != went.totalCards()) {
            helper.fail("a deck of " + went.totalCards() + " that staked " + staked.size()
                    + " left the table holding " + held.totalCards()
                    + "; the staked cards exist in two places");
            return;
        }
        // One copy each, not every copy: a deck running three Bolts that staked one comes
        // back running two.
        for (CardIdentity one : staked) {
            long before = went.entries().stream()
                    .filter(entry -> entry.toIdentity().equals(one)).count();
            long after = held.entries().stream()
                    .filter(entry -> entry.toIdentity().equals(one)).count();
            if (after != before - 1) {
                helper.fail("staking one of " + before + " copies left " + after
                        + " in the deck the table holds");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Breaking the table does not take the pot out of the world with it.
     *
     * <p>The pot lives on the block entity and nowhere else. The break path hands back every
     * deck the table was holding - and for a long time handed back nothing else, so a table
     * broken with a stake in it deleted every staked card. A staked card is one somebody
     * agreed to risk against another player; losing it to a pickaxe is the one way ante must
     * not be able to lose it.
     *
     * <p>Nobody won, so every card goes back to whoever put it in - onto the ground here,
     * because the seats have been vacated by then and there is nobody to hand it to.
     */
    @GameTest(template = "tables")
    public static void breakingTheTableGivesTheStakeBackRatherThanEatingIt(
            GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.stake(new SeatId(0), List.of(card(BOLT), card(RING)));

        ServerPlayer breaker = helper.makeMockServerPlayerInLevel();
        breaker.setPos(origin.getCenter());
        breaker.gameMode.destroyBlock(origin);

        int found = cardsAround(helper, origin);
        if (found != 2) {
            helper.fail("a table broken with two cards staked left " + found
                    + " of them in the world");
            return;
        }
        helper.succeed();
    }

    /**
     * And a machine breaking it does not eat the pot either.
     *
     * <p>The by-hand break and the machine break reach the block through different vanilla
     * methods, and only one of them is what a player does. A modded breaker or a piston-fed
     * quarry pointed at a table is the case nobody is watching, which is exactly the case
     * where cards quietly stop existing.
     */
    @GameTest(template = "tables")
    public static void aMachineBreakingTheTableDoesNotEatThePotEither(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlock.entityAt(helper.getLevel(), origin).orElseThrow()
                .stake(new SeatId(0), List.of(card(BOLT), card(RING)));

        // No entity, which is what a machine breaking a block looks like from here.
        helper.getLevel().destroyBlock(origin, false, null);

        int found = cardsAround(helper, origin);
        if (found != 2) {
            helper.fail("a table a machine broke with two cards staked left " + found
                    + " of them in the world");
            return;
        }
        helper.succeed();
    }

    /** And the same table's held deck, which goes the same way for the same reason. */
    @GameTest(template = "tables")
    public static void aMachineBreakingTheTableDoesNotEatAHeldDeckEither(
            GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlock.entityAt(helper.getLevel(), origin).orElseThrow().holdDeck(
                new SeatId(0),
                new dev.gathering.item.DeckComponent("Left behind", "",
                        java.util.Optional.empty(),
                        List.of(dev.gathering.item.CardComponent.of(card(BOLT))),
                        List.of(), List.of()),
                null, null);

        helper.getLevel().destroyBlock(origin, false, null);

        boolean back = !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(origin).inflate(8.0)).stream()
                .filter(item -> dev.gathering.item.DeckItem.deckOf(item.getItem()).isPresent())
                .toList().isEmpty();
        if (!back) {
            helper.fail("a table a machine broke ate the deck it was holding");
            return;
        }
        helper.succeed();
    }

    /** Staking nothing leaves the deck exactly as it was. */
    @GameTest(template = "empty")
    public static void stakingNothingChangesNothing(GameTestHelper helper) {
        dev.gathering.item.DeckComponent went = new dev.gathering.item.DeckComponent(
                "Friendly", "", java.util.Optional.empty(),
                List.of(dev.gathering.item.CardComponent.of(card(BOLT))), List.of(), List.of());

        if (dev.gathering.server.Staking.heldAfter(went, List.of()) != went) {
            helper.fail("a deck that staked nothing was rebuilt anyway");
            return;
        }
        helper.succeed();
    }

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

    /**
     * The exclusion list decides which card leaves, and nothing is created or lost.
     *
     * <p>Stocked into the cache first, because the rule protects any card it cannot check -
     * which is right, and which makes a test against a cold cache prove nothing at all. With
     * both cards known, a deck of basics and spells under the shipped "basic lands" exclusion
     * must stake a spell, every time.
     */
    @GameTest(template = "empty")
    public static void whatIsStakedIsWhatTheServerAllowsAndTheDeckIsIntact(
            GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("no card service, so staking cannot be exercised");
            return;
        }
        CardMetadata forest = cache(cards, "Ante Forest", "Basic Land - Forest");
        CardMetadata bolt = cache(cards, "Ante Bolt", "Instant");

        List<CardIdentity> deck = new java.util.ArrayList<>();
        for (int copy = 0; copy < 20; copy++) {
            deck.add(CardIdentity.ofPrinting(forest.scryfallId(), false));
        }
        for (int copy = 0; copy < 4; copy++) {
            deck.add(CardIdentity.ofPrinting(bolt.scryfallId(), false));
        }

        for (int run = 0; run < 12; run++) {
            dev.gathering.server.Staking.Stake stake =
                    dev.gathering.server.Staking.from(deck, helper.getLevel().getRandom());

            if (stake.isEmpty()) {
                helper.fail("a deck with four legal cards in it staked nothing");
                return;
            }
            for (CardIdentity staked : stake.staked()) {
                if (staked.printing().orElseThrow().equals(forest.scryfallId())) {
                    helper.fail("a basic land went into the pot on a server that protects them");
                    return;
                }
            }
            List<CardIdentity> after = new java.util.ArrayList<>(stake.staked());
            after.addAll(stake.library());
            if (after.size() != deck.size()) {
                helper.fail("a deck of " + deck.size() + " came back as " + after.size());
                return;
            }
            List<CardIdentity> wanted = new java.util.ArrayList<>(deck);
            for (CardIdentity card : after) {
                if (!wanted.remove(card)) {
                    helper.fail("staking produced a card the deck never had");
                    return;
                }
            }
            if (!wanted.isEmpty()) {
                helper.fail("staking lost " + wanted.size() + " card(s)");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A card the cache cannot answer for never goes in the pot.
     *
     * <p>The safe default, and the one that matters after a restart: a server that cannot
     * check what it is about to take stakes nothing rather than taking something it promised
     * to protect.
     */
    @GameTest(template = "empty")
    public static void aCardTheServerCannotCheckIsNeverStaked(GameTestHelper helper) {
        List<CardIdentity> unknown = List.of(card(BOLT), card(RING), card(BOLT));
        dev.gathering.server.Staking.Stake stake =
                dev.gathering.server.Staking.from(unknown, helper.getLevel().getRandom());
        if (!stake.isEmpty()) {
            helper.fail("a card the server knows nothing about was put in the pot");
            return;
        }
        if (stake.library().size() != unknown.size()) {
            helper.fail("a deck that staked nothing came back the wrong size");
            return;
        }
        helper.succeed();
    }

    /**
     * Staking one copy of a card takes one copy, not all of them.
     *
     * <p>Removing by value takes every match, which for a one-card ante out of a deck with
     * four copies is a player losing four cards. Checked by hand rather than trusted, because
     * the list method that does it wrong reads exactly like the one that does it right.
     */
    @GameTest(template = "empty")
    public static void stakingOneCopyTakesOneCopy(GameTestHelper helper) {
        List<CardIdentity> deck = List.of(card(BOLT), card(BOLT), card(BOLT), card(BOLT));
        List<CardIdentity> left = new java.util.ArrayList<>(deck);
        // What Staking does with what it drew, on its own: one removal per staked card.
        left.remove(card(BOLT));
        if (left.size() != 3) {
            helper.fail("taking one of four copies left " + left.size());
            return;
        }
        helper.succeed();
    }

    /** A table not playing for keeps says so, and a fresh one never is. */
    @GameTest(template = "empty")
    public static void aTableIsNotPlayingForKeepsUntilItSaysSo(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        if (table.playingForKeeps()) {
            helper.fail("a table nobody has asked was already playing for keeps");
            return;
        }
        table.playForKeeps(true);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag written = table.saveWithoutMetadata(registries);
        table.playForKeeps(false);
        table.loadWithComponents(written, registries);
        if (!table.playingForKeeps()) {
            helper.fail("a table forgot it was playing for keeps when it was saved");
            return;
        }
        // And ending the game forgets it, so the next one has to agree for itself.
        table.endSession();
        if (table.playingForKeeps()) {
            helper.fail("a table was still playing for keeps after the game ended");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ bits

    /** A card the server knows about, so the exclusion rule has something to read. */
    private static CardMetadata cache(CardDataService cards, String name, String typeLine) {
        CardMetadata card = new CardMetadata(
                UUID.nameUUIDFromBytes(("gathering-ante:" + name).getBytes()),
                UUID.nameUUIDFromBytes(("gathering-ante-oracle:" + name).getBytes()),
                name, "", 0, typeLine, "", java.util.Set.of(), java.util.Set.of("G"),
                List.of(), "normal", "tst", "Test", "1", Rarity.COMMON,
                false, false, true, false, false, List.of("paper"),
                java.util.Map.of(), java.util.Map.of(), "");
        cards.store().store(card, null);
        return card;
    }

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
