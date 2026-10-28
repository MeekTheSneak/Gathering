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
 * <p>Where the arithmetic lives is {@link AntePot} and it has its own tests. What cannot be
 * checked there is the part that costs somebody a card: whether the pot survives being
 * written to disk, whether a settled pot can be settled twice, and whether the cards actually
 * arrive anywhere when a game ends.
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
     * <p>The whole way through, because the two halves are what went wrong: the stake is
     * taken out of the <em>library</em> the game is dealt, and for a long time the table
     * separately kept the deck exactly as it went down, staked card and all. The winner was
     * handed the card out of the pot and the loser got the same card home in their deck.
     * Ante is the one feature whose entire point is that a card really changes owner, so a
     * card that exists twice afterwards is the failure it must not have.
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
        // The player said yes: a stake is drawn only from somebody who did.
        table.playForKeeps(true, java.util.Set.of(player.getUUID()));

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

    /** A loaner deck cannot be played for keeps: its staked cards would be paid out as real ones. */
    @GameTest(template = "tables")
    public static void aLoanerIsNotPlayedForKeeps(GameTestHelper helper) {
        TableFixture fixture = seatedWithAGame(helper, true);
        TableBlock.putDown(fixture.level(), fixture.origin(), fixture.player(),
                dev.gathering.item.DeckItem.of(fourCards(fixture.player()).lent()));
        if (!fixture.table().pot().isEmpty() || !fixture.table().heldDecks().isEmpty()) {
            helper.fail("a loaner deck went down at a table playing for keeps");
            return;
        }
        helper.succeed();
    }

    /**
     * A seat whose library has run out still has its deck in the table's keeping, and a second
     * deck put down there used to replace it there - the first deck gone for good.
     */
    @GameTest(template = "tables")
    public static void asecondDeckDoesNotReplaceTheOneTheTableHolds(GameTestHelper helper) {
        TableFixture fixture = seatedWithAGame(helper, false);
        dev.gathering.item.DeckComponent first = fourCards(fixture.player());
        TableBlock.putDown(fixture.level(), fixture.origin(), fixture.player(), dev.gathering.item.DeckItem.of(first));
        var session = dev.gathering.block.TableSessions.sessionAt(fixture.level(), fixture.origin()).orElseThrow();
        session.submit(new dev.gathering.core.game.event.GameEvent.LibraryExiled(new SeatId(0), new SeatId(0), 60));
        dev.gathering.item.DeckComponent second = new dev.gathering.item.DeckComponent(
                "Second", "", java.util.Optional.of(fixture.player().getUUID()),
                List.of(dev.gathering.item.CardComponent.of(card(BOLT))), List.of(), List.of());
        TableBlock.putDown(fixture.level(), fixture.origin(), fixture.player(), dev.gathering.item.DeckItem.of(second));
        dev.gathering.item.DeckComponent held = fixture.table().heldDecks().get(new SeatId(0));
        if (held == null || !held.name().equals(first.name())) {
            helper.fail("the table is now holding " + (held == null ? "nothing" : held.name())
                    + ", not the first deck put down at that seat");
            return;
        }
        helper.succeed();
    }

    private record TableFixture(ServerLevel level, BlockPos origin, ServerPlayer player, TableBlockEntity table) {
    }

    private static TableFixture seatedWithAGame(GameTestHelper helper, boolean forKeeps) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState().setValue(TableBlock.PART, part), 3);
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(0);
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());
        dev.gathering.block.TableSessions.start(level, origin,
                dev.gathering.core.match.MatchRules.single(dev.gathering.core.format.FormatPresets.COMMANDER));
        TableBlockEntity table = TableBlock.entityAt(level, origin).orElseThrow();
        table.playForKeeps(forKeeps, java.util.Set.of(player.getUUID()));
        return new TableFixture(level, origin, player, table);
    }

    private static dev.gathering.item.DeckComponent fourCards(ServerPlayer owner) {
        return new dev.gathering.item.DeckComponent("First", "", java.util.Optional.of(owner.getUUID()),
                List.of(dev.gathering.item.CardComponent.of(card(RING)), dev.gathering.item.CardComponent.of(card(RING)),
                        dev.gathering.item.CardComponent.of(card(RING)), dev.gathering.item.CardComponent.of(card(RING))),
                List.of(), List.of());
    }

    /**
     * Breaking the table does not take the pot out of the world with it.
     * <p>The pot lives on the block entity and nowhere else. The break path hands back every
     * deck the table was holding - and for a long time handed back nothing else, so a table
     * broken with a stake in it deleted every staked card. A staked card is one somebody
     * agreed to risk against another player; losing it to a pickaxe is the one way ante must
     * not be able to lose it.
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

    /**
     * A pot handed back goes to the people who filled it, not to the chairs.
     * <p>A seat says where a card was staked from; it does not say whose it was. Somebody who
     * stakes, stands up, and is replaced before the game is voided had their card handed to
     * whoever sat down after them - a card changing owner because of where a person happened
     * to be standing, in the one feature whose entire point is that a card changes owner
     * deliberately. The held decks fixed exactly this by recording who put each one down;
     * the pot is the same fact, kept in the same place.
     */
    @GameTest(template = "tables")
    public static void aPotGoesBackToWhoStakedItAndNotToTheChair(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();

        ServerPlayer staker = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        staker.setPos(origin.getCenter());
        stranger.setPos(origin.getCenter());

        table.stake(new SeatId(0), List.of(card(RING)), staker.getUUID());

        // The staker walks away and somebody else takes the chair.
        List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
        TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(),
                stranger.getUUID());

        // Nobody won, so the pot goes back to whoever put each card in.
        TableSessions.settlePot(helper.getLevel(), origin, table, null);

        if (countIn(stranger) != 0) {
            helper.fail("the person who sat down in a vacated chair was handed somebody"
                    + " else's staked card");
            return;
        }
        if (countIn(staker) != 1) {
            helper.fail("the card went back to the chair rather than to the person who"
                    + " staked it: the staker has " + countIn(staker));
            return;
        }
        helper.succeed();
    }

    /**
     * A seat holds one person's stake, so a pot handed back gives each card to the person it came from.
     * <p>A pot is paid out by seat, to whoever staked from it. A staker who left the table mid-match
     * leaves their stake on the seat, and the next person to put a deck down there had theirs added to
     * it - with the seat's staker written over by the newcomer, so a drawn match, a table broken or a
     * game ended by hand gave the first staker's card to the second.
     * <p>Both doors: the pot itself, and the deck boundary a deck is put down through, for a player who
     * did agree to play for keeps and sat down at the seat somebody else had staked from.
     */
    @GameTest(template = "tables")
    public static void aSeatHoldsOnePersonsStakeAndAHandedBackPotGoesToEach(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("no card service, so staking cannot be exercised");
            return;
        }
        UUID known = cache(cards, "Second Ring", "Artifact").scryfallId();
        BlockPos origin = seatedTable(helper, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        first.setPos(origin.getCenter());
        second.setPos(origin.getCenter());
        List<SeatAnchor> anchors = TableClusters.at(level, origin).seats();
        TableSeats.take(level, origin, anchors.get(1).cell(), anchors.get(1).side(), first.getUUID());
        if (TableSessions.start(level, origin, dev.gathering.core.match.MatchRules.single(
                dev.gathering.core.format.FormatPresets.COMMANDER)) != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start at the table");
            return;
        }
        TableBlockEntity table = TableBlock.entityAt(level, origin).orElseThrow();
        // Both said yes, so neither is refused for not having been asked.
        table.playForKeeps(true, java.util.Set.of(first.getUUID(), second.getUUID()));
        SeatId seat = new SeatId(1);

        // The first staker's card is on seat 1, and they have left it.
        if (!table.stake(seat, List.of(card(RING)), first.getUUID())) {
            helper.fail("the first stake at an empty seat was refused, so this checks nothing");
            return;
        }
        TableSeats.leave(level, origin, first.getUUID());
        TableSeats.take(level, origin, anchors.get(1).cell(), anchors.get(1).side(), second.getUUID());

        // The second, who also agreed, puts a deck of cards the server can stake down at that seat.
        ItemStack theirs = dev.gathering.item.DeckItem.of(new dev.gathering.item.DeckComponent(
                "Second", "", java.util.Optional.of(second.getUUID()),
                List.of(dev.gathering.item.CardComponent.of(card(known)),
                        dev.gathering.item.CardComponent.of(card(known)),
                        dev.gathering.item.CardComponent.of(card(known)),
                        dev.gathering.item.CardComponent.of(card(known))),
                List.of(), List.of()));
        TableBlock.putDown(level, origin, second, theirs);
        // And the pot itself takes no second person's cards on that seat, whoever asks.
        boolean added = table.stake(seat, List.of(card(BOLT)), second.getUUID());

        if (added || table.pot().stakeOf(seat).size() != 1
                || !table.stakerOf(seat).filter(first.getUUID()::equals).isPresent()) {
            helper.fail("a second person's stake went onto a seat holding the first one's: the seat holds "
                    + table.pot().stakeOf(seat).size() + " card(s), staked by "
                    + (table.stakerOf(seat).filter(first.getUUID()::equals).isPresent() ? "the first" : "the second"));
            return;
        }
        if (theirs.getCount() != 1) {
            helper.fail("a deck refused for keeps at a seat holding another player's stake was taken anyway");
            return;
        }

        // Nobody won, so each card goes back to the person who put it in.
        TableSessions.settlePot(level, origin, table, null);
        if (countIn(first) != 1 || countIn(second) != 0) {
            helper.fail("a pot handed back gave the first staker " + countIn(first) + " card(s) and the second "
                    + countIn(second) + ", not one and none");
            return;
        }
        helper.succeed();
    }

    /**
     * A pot for somebody who is not here is kept for them, not dropped on the table for whoever
     * ended the game to pick up. A security review reproduced exactly that: an absent player's
     * stake, and their deck, left on the table.
     */
    @GameTest(template = "tables")
    public static void aPotForSomebodyAwayIsKeptForThemNotLeftOnTheTable(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        // Logged out: off the server, not only out of this level - a player in another level is online,
        // and is handed their cards there (see aPotWonInAnotherDimensionIsHandedOverWithItsStory).
        ServerPlayer away = helper.makeMockServerPlayerInLevel();
        TestPlayers.leaving(helper, away);
        dev.gathering.server.Owed.forget(away.getUUID());
        try {
            table.stake(new SeatId(0), List.of(card(RING)), away.getUUID());
            TableSessions.settlePot(helper.getLevel(), origin, table, null);
            var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(origin).inflate(3));
            if (!dropped.isEmpty()) {
                helper.fail("an absent player's staked card was left on the table");
                return;
            }
            if (dev.gathering.server.Owed.waitingFor(away.getUUID()) != 1) {
                helper.fail("an absent player's staked card was not kept for them");
                return;
            }
            helper.succeed();
        } finally {
            dev.gathering.server.Owed.forget(away.getUUID());
        }
    }

    /**
     * A pot won by a player who is online in another dimension is handed to them there, now, with the
     * story of who it was won from.
     * <p>The winner was looked up in the table's own level only, so one who had gone through a portal was
     * taken for one who had logged out: the cards were written down for their next login, bare, and the
     * "won from" chapter a card gets for being won was never written at all.
     */
    @GameTest(template = "tables")
    public static void aPotWonInAnotherDimensionIsHandedOverWithItsStory(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        ServerPlayer loser = TestPlayers.joining(helper, UUID.randomUUID(), "Loser");
        ServerPlayer winner = helper.makeMockServerPlayerInLevel();
        loser.setPos(origin.getCenter());
        dev.gathering.server.Owed.forget(winner.getUUID());
        try {
            List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(),
                    loser.getUUID());
            TableSeats.take(helper.getLevel(), origin, anchors.get(1).cell(), anchors.get(1).side(),
                    winner.getUUID());
            table.stake(new SeatId(0), List.of(card(BOLT)), loser.getUUID());
            table.stake(new SeatId(1), List.of(card(RING)), winner.getUUID());
            winner.teleportTo(helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER),
                    0.5, 100.0, 0.5, 0.0F, 0.0F);
            if (winner.level() == helper.getLevel()) {
                helper.fail("the winner never left the table's dimension, so this checked nothing");
                return;
            }

            TableSessions.settlePot(helper.getLevel(), origin, table, new SeatId(1));

            if (dev.gathering.server.Owed.waitingFor(winner.getUUID()) != 0) {
                helper.fail("a pot won by a player online in another dimension was kept for their next login");
                return;
            }
            if (countIn(winner) != 2) {
                helper.fail("the winner of a two-card pot, in another dimension, is holding " + countIn(winner));
                return;
            }
            if (!wonFrom(winner, BOLT, "Loser")) {
                helper.fail("the card won off the loser reached a winner in another dimension without the story"
                        + " of who it was won from");
                return;
            }
            helper.succeed();
        } finally {
            dev.gathering.server.Owed.forget(winner.getUUID());
            TestPlayers.leaving(helper, winner);
            TestPlayers.leaving(helper, loser);
        }
    }

    /**
     * A card won off somebody who has since logged out does not name whoever sat down in their chair.
     * <p>The person a card was won from was looked up in the table's level, and when the staker was not
     * found there the chair was asked instead - the right answer for a table saved before stakes knew their
     * staker, and the wrong one for a staker who had gone home: the card's history named the stranger who
     * took the seat, which is worse than naming nobody.
     */
    @GameTest(template = "tables")
    public static void aCardWonFromSomebodyWhoLeftDoesNotNameTheirChair(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        ServerPlayer loser = TestPlayers.joining(helper, UUID.randomUUID(), "Loser");
        ServerPlayer squatter = TestPlayers.joining(helper, UUID.randomUUID(), "Squatter");
        ServerPlayer winner = helper.makeMockServerPlayerInLevel();
        winner.setPos(origin.getCenter());
        try {
            List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(),
                    loser.getUUID());
            TableSeats.take(helper.getLevel(), origin, anchors.get(1).cell(), anchors.get(1).side(),
                    winner.getUUID());
            table.stake(new SeatId(0), List.of(card(BOLT)), loser.getUUID());
            table.stake(new SeatId(1), List.of(card(RING)), winner.getUUID());
            // The loser goes home, and somebody else sits down in their chair.
            TableSeats.leave(helper.getLevel(), origin, loser.getUUID());
            TestPlayers.leaving(helper, loser);
            TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(),
                    squatter.getUUID());

            TableSessions.settlePot(helper.getLevel(), origin, table, new SeatId(1));

            if (countIn(winner) != 2) {
                helper.fail("the winner of a two-card pot is holding " + countIn(winner));
                return;
            }
            if (wonFrom(winner, BOLT, "Squatter")) {
                helper.fail("a card won off a player who had logged out says it was won from whoever took their chair");
                return;
            }
            helper.succeed();
        } finally {
            TestPlayers.leaving(helper, winner);
            TestPlayers.leaving(helper, squatter);
        }
    }

    /** Whether this player holds the card of this printing, with a "won" chapter naming this person. */
    private static boolean wonFrom(ServerPlayer player, UUID printing, String from) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            boolean thatCard = CardItem.cardOf(stack)
                    .flatMap(card -> card.faceUp().toIdentity().printing())
                    .filter(printing::equals)
                    .isPresent();
            if (thatCard) {
                return dev.gathering.server.CardStories.storyOf(stack).chapters().stream()
                        .anyMatch(chapter -> chapter.how() == dev.gathering.core.story.HowItCame.WON
                                && chapter.from().equals(from));
            }
        }
        return false;
    }

    /** Card items this player is carrying. */
    private static int countIn(ServerPlayer player) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (CardItem.cardOf(stack).isPresent()) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * A staked copy leaves no history behind it for another copy to take.
     * <p>The deck the table holds lost the staked card and kept its history, so the next copy of that
     * printing taken out of it - or poured into a box - came out remembering a pull or a win that happened
     * to a card now in somebody else's hands. Plain copies go first, as they leave a collection, so a deck
     * with a plain copy to spare keeps the history on the copy it still holds; a deck without one gives the
     * history up with the card.
     */
    @GameTest(template = "tables")
    public static void aStakedCopyLeavesNoHistoryBehindForAnother(GameTestHelper helper) {
        dev.gathering.item.CardComponent bolt = dev.gathering.item.CardComponent.of(card(BOLT));
        dev.gathering.core.story.CardStory won = dev.gathering.core.story.CardStory.begunWith(
                new dev.gathering.core.story.CardStory.Chapter(
                        dev.gathering.core.story.HowItCame.WON, "Ana", "Ben", "", "2026-09-09"));
        dev.gathering.core.story.CardStory pulled = dev.gathering.core.story.CardStory.begunWith(
                new dev.gathering.core.story.CardStory.Chapter(
                        dev.gathering.core.story.HowItCame.PULLED, "Ana", "", "DMU", "2026-09-10"));

        // Two copies and one history, one staked: the plain one went, the history stays with the copy left.
        dev.gathering.item.DeckComponent spare = new dev.gathering.item.DeckComponent("Spare", "",
                java.util.Optional.empty(), List.of(bolt, bolt), List.of(), List.of()).keeping(bolt, won);
        dev.gathering.item.DeckComponent afterSpare = dev.gathering.server.Staking.heldAfter(spare, List.of(card(BOLT)));
        if (afterSpare.entries().size() != 1 || !afterSpare.storyOf(bolt).filter(won::equals).isPresent()) {
            helper.fail("a deck staking a plain copy of a card it holds two of kept " + afterSpare.stories()
                    + " for " + afterSpare.entries().size() + " copy(s)");
            return;
        }
        // One copy with a history, staked: there is no copy left for it to belong to.
        dev.gathering.item.DeckComponent only = new dev.gathering.item.DeckComponent("Only", "",
                java.util.Optional.empty(), List.of(bolt, dev.gathering.item.CardComponent.of(card(RING))),
                List.of(), List.of()).keeping(bolt, won);
        dev.gathering.item.DeckComponent afterOnly = dev.gathering.server.Staking.heldAfter(only, List.of(card(BOLT)));
        if (!afterOnly.stories().isEmpty()) {
            helper.fail("a deck whose only copy of a card was staked still keeps its history for a copy it does not"
                    + " hold: " + afterOnly.stories());
            return;
        }

        // And through the real deck boundary: two storied copies of one known card, one of them staked.
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("no card service, so staking cannot be exercised");
            return;
        }
        dev.gathering.item.CardComponent known = dev.gathering.item.CardComponent.of(CardIdentity.ofPrinting(
                cache(cards, "Storied Ring", "Artifact").scryfallId(), false));
        TableFixture fixture = seatedWithAGame(helper, true);
        TableBlock.putDown(fixture.level(), fixture.origin(), fixture.player(), dev.gathering.item.DeckItem.of(
                new dev.gathering.item.DeckComponent("Storied", "", java.util.Optional.of(fixture.player().getUUID()),
                        List.of(known, known), List.of(), List.of()).keeping(known, won).keeping(known, pulled)));
        if (fixture.table().pot().isEmpty()) {
            helper.fail("a for-keeps deck of two known cards staked nothing, so this checks nothing");
            return;
        }
        dev.gathering.item.DeckComponent held = fixture.table().heldDecks().get(new SeatId(0));
        if (held == null || held.entries().size() != 1 || held.stories().size() != 1) {
            helper.fail("a deck of two storied copies that staked one is held keeping "
                    + (held == null ? "nothing" : held.stories().size() + " histories for " + held.entries().size()
                    + " copy(s)"));
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

    /**
     * A pot with a winner is the same cards, all going one way - to the winner.
     * <p>Checked by looking in the two players' hands, which is the whole point and is what
     * this used to leave out. It counted the cards lying on the floor and found three, and
     * three is what falls on the floor whichever way the pot goes: a payout to the wrong seat
     * drops three, a payout split back to both owners drops three, and a table with nobody
     * sitting at it drops three no matter what. The test passed on all of them.
     * <p>So the seats hold real players now. The winner should be holding all three and the
     * loser none, and nothing should be on the floor at all.
     */
    @GameTest(template = "tables")
    public static void aWonPotIsTheSameCardsGoingOneWay(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();

        ServerPlayer loser = helper.makeMockServerPlayerInLevel();
        ServerPlayer winner = helper.makeMockServerPlayerInLevel();
        loser.setPos(origin.getCenter());
        winner.setPos(origin.getCenter());
        List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
        TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(),
                loser.getUUID());
        TableSeats.take(helper.getLevel(), origin, anchors.get(1).cell(), anchors.get(1).side(),
                winner.getUUID());

        table.stake(new SeatId(0), List.of(card(BOLT)), loser.getUUID());
        table.stake(new SeatId(1), List.of(card(RING), card(RING)), winner.getUUID());

        TableSessions.settlePot(helper.getLevel(), origin, table, new SeatId(1));

        if (countIn(winner) != 3) {
            helper.fail("the winner of a three-card pot is holding " + countIn(winner));
            return;
        }
        if (countIn(loser) != 0) {
            helper.fail("the loser kept " + countIn(loser) + " card(s) of a pot they lost");
            return;
        }
        if (cardsAround(helper, origin) != 0) {
            helper.fail(cardsAround(helper, origin) + " card(s) of a paid-out pot ended up on"
                    + " the floor beside two players who could have been handed them");
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
        UUID agreed = new UUID(14L, 1L);
        UUID neverAsked = new UUID(14L, 2L);
        table.playForKeeps(true, java.util.Set.of(agreed));

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag written = table.saveWithoutMetadata(registries);
        table.playForKeeps(false, java.util.Set.of());
        table.loadWithComponents(written, registries);
        if (!table.playingForKeeps()) {
            helper.fail("a table forgot it was playing for keeps when it was saved");
            return;
        }
        // And who said yes, which is who a stake may be drawn from after a restart.
        if (!table.agreedToPlayForKeeps(agreed) || table.agreedToPlayForKeeps(neverAsked)) {
            helper.fail("a table read back off the disk has the wrong people agreeing to play for keeps: "
                    + table.agreedToPlayForKeeps(agreed) + " for the one who agreed, "
                    + table.agreedToPlayForKeeps(neverAsked) + " for the one never asked");
            return;
        }
        // And ending the game forgets it, so the next one has to agree for itself.
        table.endSession();
        if (table.playingForKeeps() || table.agreedToPlayForKeeps(agreed)) {
            helper.fail("a table was still playing for keeps after the game ended");
            return;
        }
        helper.succeed();
    }

    /**
     * A seat nobody staked from, played by somebody who never said yes, does not take the pot - and one
     * played by somebody who did, wherever they are, does.
     * <p>Somebody who was never asked cannot put a deck down, but can still hold a seat - one claimed while
     * they were away when the question went round - and a seat holding nothing is still a player standing.
     * The pot follows the match, and a seat with no staker was paid through the chair, so every card the
     * others had staked against each other went to the one player at the table who had agreed to nothing and
     * risked nothing. Handed back instead, and the table told why.
     * <p>And the same seat, played by somebody who did agree - whose deck staked nothing, as one the server
     * could not check does - still takes it: read from the game, not the chair, so a winner in another
     * dimension is handed it there and one who has logged out has it kept for them.
     */
    @GameTest(template = "tables")
    public static void aSeatThatNeitherStakedNorAgreedDoesNotTakeThePot(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 0);
        ServerLevel level = helper.getLevel();
        TableBlockEntity table = TableBlock.entityAt(level, origin).orElseThrow();
        ServerPlayer staker = TestPlayers.joining(helper, UUID.randomUUID(), "Staker");
        ServerPlayer other = TestPlayers.joining(helper, UUID.randomUUID(), "Other");
        staker.setPos(origin.getCenter());
        other.setPos(origin.getCenter());
        dev.gathering.server.Owed.forget(other.getUUID());
        boolean otherLeft = false;
        try {
            List<SeatAnchor> anchors = TableClusters.at(level, origin).seats();
            TableSeats.take(level, origin, anchors.get(0).cell(), anchors.get(0).side(), staker.getUUID());
            TableSeats.take(level, origin, anchors.get(1).cell(), anchors.get(1).side(), other.getUUID());
            if (TableSessions.start(level, origin, dev.gathering.core.match.MatchRules.single(
                    dev.gathering.core.format.FormatPresets.COMMANDER)) != TableSessions.Outcome.STARTED) {
                helper.fail("a game would not start at the table");
                return;
            }
            List<String> told = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            UUID stakerId = staker.getUUID();
            dev.gathering.server.Notices.watchForTesting((to, line) -> {
                if (stakerId.equals(to) && line.getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents said) {
                    told.add(said.getKey());
                }
            });

            // Only the staker said yes, and the other seat is the one left standing.
            table.playForKeeps(true, java.util.Set.of(staker.getUUID()));
            table.stake(new SeatId(0), List.of(card(RING)), staker.getUUID());
            TableSessions.settlePot(level, origin, table, new SeatId(1));
            if (countIn(other) != 0) {
                helper.fail("a player who never agreed and staked nothing took " + countIn(other)
                        + " card(s) of the pot by being the last one standing");
                return;
            }
            if (countIn(staker) != 1) {
                helper.fail("a pot nobody it was played for won did not go back to the person who staked it: they hold "
                        + countIn(staker));
                return;
            }
            if (!told.contains("message.gathering.ante_pot_back")) {
                helper.fail("a pot went back although a seat took the match, and the table was not told why: " + told);
                return;
            }

            // The same seat, played by somebody who said yes and could stake nothing, in another dimension.
            table.playForKeeps(true, java.util.Set.of(staker.getUUID(), other.getUUID()));
            table.stake(new SeatId(0), List.of(card(BOLT)), staker.getUUID());
            other.teleportTo(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER),
                    0.5, 100.0, 0.5, 0.0F, 0.0F);
            if (other.level() == level) {
                helper.fail("the winner never left the table's dimension, so this checks nothing");
                return;
            }
            TableSessions.settlePot(level, origin, table, new SeatId(1));
            if (countIn(other) != 1) {
                helper.fail("a player who agreed, whose deck could stake nothing and who is in another dimension did"
                        + " not take the pot their seat won: they hold " + countIn(other));
                return;
            }

            // And logged out: kept for them.
            table.stake(new SeatId(0), List.of(card(RING)), staker.getUUID());
            TestPlayers.leaving(helper, other);
            otherLeft = true;
            TableSessions.settlePot(level, origin, table, new SeatId(1));
            if (dev.gathering.server.Owed.waitingFor(other.getUUID()) != 1) {
                helper.fail("a pot won by a player who agreed and has logged out was not kept for them: "
                        + dev.gathering.server.Owed.waitingFor(other.getUUID()) + " waiting");
                return;
            }
            helper.succeed();
        } finally {
            dev.gathering.server.Owed.forget(other.getUUID());
            if (!otherLeft) {
                TestPlayers.leaving(helper, other);
            }
            TestPlayers.leaving(helper, staker);
        }
    }

    /**
     * The same rule the whole way through a match ending: a concession settles it, the game is ended in its
     * own log, and only then is the pot paid - to the player the game says is at the seat that took it.
     * <p>Through {@code TableMatch.settleIfFinished}, the one place a won match pays out, so the seat's
     * player is read from a game that has already been marked over; and once more with that player never
     * having agreed, so the pot goes back.
     */
    @GameTest(template = "tables")
    public static void aMatchWonByAnAgreedSeatThatStakedNothingPaysItsPlayer(GameTestHelper helper) {
        // A table of its own each time, so nothing the first left on its seats is read by the second.
        BlockPos[] tables = {TestTables.place(helper, 1, 2, 1), TestTables.place(helper, 5, 2, 1)};
        for (int round = 0; round < tables.length; round++) {
            boolean agreed = round == 0;
            BlockPos origin = tables[round];
            ServerLevel level = helper.getLevel();
            TableBlockEntity table = TableBlock.entityAt(level, origin).orElseThrow();
            ServerPlayer staker = helper.makeMockServerPlayerInLevel();
            ServerPlayer winner = helper.makeMockServerPlayerInLevel();
            staker.setPos(origin.getCenter());
            winner.setPos(origin.getCenter());
            List<SeatAnchor> anchors = TableClusters.at(level, origin).seats();
            TableSeats.take(level, origin, anchors.get(0).cell(), anchors.get(0).side(), staker.getUUID());
            TableSeats.take(level, origin, anchors.get(1).cell(), anchors.get(1).side(), winner.getUUID());
            if (TableSessions.start(level, origin, dev.gathering.core.match.MatchRules.single(
                    dev.gathering.core.format.FormatPresets.COMMANDER)) != TableSessions.Outcome.STARTED) {
                helper.fail("a game would not start at the table");
                return;
            }
            table.playForKeeps(true, agreed ? java.util.Set.of(staker.getUUID(), winner.getUUID())
                    : java.util.Set.of(staker.getUUID()));
            table.stake(new SeatId(0), List.of(card(RING)), staker.getUUID());

            var session = TableSessions.sessionAt(level, origin).orElseThrow();
            session.submit(new dev.gathering.core.game.event.GameEvent.Conceded(new SeatId(0)));
            if (dev.gathering.server.TableMatch.settleIfFinished(level, origin, session.state()) == null) {
                helper.fail("a conceded game settled nothing, so this checks nothing");
                return;
            }
            int toWinner = agreed ? 1 : 0;
            if (countIn(winner) != toWinner || countIn(staker) != 1 - toWinner) {
                helper.fail("a match won by a seat that staked nothing, whose player " + (agreed ? "agreed" : "never agreed")
                        + ", left the winner holding " + countIn(winner) + " and the staker " + countIn(staker));
                return;
            }
            if (!table.pot().isEmpty() || table.hasSession()) {
                helper.fail("the match ended still holding a pot or a game");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A table saved before a yes was written down by whose it was reads as nobody having said it.
     * <p>The safe way round. Such a table is still for keeps, so the stakes already in its pot stay
     * in play; what it cannot say is who agreed, and staking a deck put down there now on the strength
     * of a question its player may never have seen is the failure the question exists to prevent.
     */
    @GameTest(template = "empty")
    public static void aTableSavedBeforeAYesWasWrittenDownStakesNobodyNew(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 2);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        UUID agreed = new UUID(15L, 1L);
        table.playForKeeps(true, java.util.Set.of(agreed));
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag written = table.saveWithoutMetadata(registries);
        if (!written.contains("for_keeps_agreed")) {
            helper.fail("a table playing for keeps did not write down who agreed, so this checks nothing");
            return;
        }
        // As a table written before the list existed.
        written.remove("for_keeps_agreed");
        table.loadWithComponents(written, registries);
        if (!table.playingForKeeps()) {
            helper.fail("an older table playing for keeps forgot it was when read back");
            return;
        }
        if (table.agreedToPlayForKeeps(agreed)) {
            helper.fail("an older table that never wrote down who agreed reads as somebody having agreed");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ bits

    /** A card the server knows about, so the exclusion rule has something to read. */
    static CardMetadata cache(CardDataService cards, String name, String typeLine) {
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
