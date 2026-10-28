package dev.gathering.server.events;

import dev.gathering.Gathering;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.PrizeOffer;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.CreateEventPayload;
import dev.gathering.network.EventActionPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a host can do with tournaments in the world: run more than one of them from more than one
 * desk, add a table by standing at it rather than sitting down, and put prizes up before the
 * tournament exists.
 * <p>Each of these was reported by the owner as not possible. What is checked here is the part with
 * blocks, players and property in it; the rules the screens follow are checked in the pure module.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EventHostingGameTest {

    private EventHostingGameTest() {
    }

    /**
     * One host, two desks, two tournaments. What a tournament is one of is a desk: the second desk
     * hosts its own rather than being refused, or quietly adopting the first.
     */
    @GameTest(template = "empty")
    public static void twoDesksRunTwoTournamentsForOneHost(GameTestHelper helper) {
        BlockPos first = placeDesk(helper, 1, 2, 1);
        BlockPos second = placeDesk(helper, 3, 2, 3);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState friday = null;
        EventState saturday = null;
        try {
            friday = create(host, first, "Friday Night");
            if (friday == null) {
                helper.fail("hosting at the first desk created no tournament");
                return;
            }
            saturday = create(host, second, "Saturday");
            if (saturday == null) {
                helper.fail("a host already running one was refused a second tournament at another desk");
                return;
            }
            if (!runs(helper, first, friday) || !runs(helper, second, saturday)) {
                helper.fail("the desks run " + deskOf(helper, first).event() + " and " + deskOf(helper, second).event()
                        + ", for tournaments " + friday.tournament.id() + " and " + saturday.tournament.id());
                return;
            }
            if (!first.equals(friday.registrationPoint) || !second.equals(saturday.registrationPoint)) {
                helper.fail("signing up happens at " + friday.registrationPoint + " and " + saturday.registrationPoint);
                return;
            }
        } finally {
            remove(friday);
            remove(saturday);
        }
        helper.succeed();
    }

    /**
     * A free desk put down beside a tournament that already has one offers hosting rather than taking
     * the first over. Moving it here is still possible, and still takes the second use that every
     * other way of taking a desk takes.
     */
    @GameTest(template = "empty")
    public static void afreeDeskOffersHostingRatherThanMovingATournamentWithADesk(GameTestHelper helper) {
        BlockPos first = placeDesk(helper, 1, 2, 1);
        BlockPos second = placeDesk(helper, 3, 2, 3);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState friday = null;
        try {
            friday = create(host, first, "Friday Night");
            if (friday == null) {
                helper.fail("hosting at the first desk created no tournament");
                return;
            }
            use(helper, host, second);
            if (deskOf(helper, second).event().isPresent() || !first.equals(friday.registrationPoint)) {
                helper.fail("one use of a free desk moved a tournament that already had one: "
                        + deskOf(helper, second).event() + ", signing up at " + friday.registrationPoint);
                return;
            }
            use(helper, host, second);
            if (!runs(helper, second, friday) || !second.equals(friday.registrationPoint)) {
                helper.fail("using the free desk again did not move the tournament to it");
                return;
            }
            // And the desk it came from lets go of it. Nothing ever told a desk to stop running a
            // tournament, so the old one went on claiming it: it would host nothing else, its display
            // board showed a tournament signing up somewhere else, and one ordinary use of it pulled
            // the tournament straight back - which is the two-press rule undone.
            if (deskOf(helper, first).event().isPresent()) {
                helper.fail("the desk the tournament moved away from still runs "
                        + deskOf(helper, first).event());
                return;
            }
        } finally {
            remove(friday);
        }
        helper.succeed();
    }

    /**
     * A second use of a desk already running one of the host's own tournaments moves nothing.
     * <p>The second use is how a tournament is moved onto a <em>free</em> desk. On a busy one it used
     * to move the host's other tournament here anyway and leave this one with nowhere to sign up -
     * and a tournament with nowhere to sign up takes registrations from anywhere in the world, so two
     * clicks on your own desk quietly opened one of your tournaments to the whole server.
     */
    @GameTest(template = "empty")
    public static void asecondUseOfaBusyDeskLeavesBothTournamentsWhereTheyAre(GameTestHelper helper) {
        BlockPos first = placeDesk(helper, 1, 2, 1);
        BlockPos second = placeDesk(helper, 5, 2, 5);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState friday = null;
        EventState saturday = null;
        try {
            friday = create(host, first, "Friday Night");
            saturday = create(host, second, "Saturday Night");
            if (friday == null || saturday == null) {
                helper.fail("hosting twice created " + friday + " and " + saturday);
                return;
            }
            use(helper, host, first);
            use(helper, host, first);
            if (!first.equals(friday.registrationPoint) || !second.equals(saturday.registrationPoint)) {
                helper.fail("using a busy desk twice left Friday signing up at " + friday.registrationPoint
                        + " and Saturday at " + saturday.registrationPoint);
                return;
            }
            if (!runs(helper, first, friday) || !runs(helper, second, saturday)) {
                helper.fail("the desks run " + deskOf(helper, first).event() + " and "
                        + deskOf(helper, second).event());
                return;
            }
        } finally {
            remove(friday);
            remove(saturday);
        }
        helper.succeed();
    }

    /**
     * Breaking a desk leaves its tournament running, with nowhere to sign up - and one use of another
     * desk gives it somewhere again, because a tournament with nowhere to sign up has nothing to lose.
     */
    @GameTest(template = "empty")
    public static void abrokenDeskLeavesItsTournamentRunningAndAnotherTakesItOn(GameTestHelper helper) {
        BlockPos first = placeDesk(helper, 1, 2, 1);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState friday = null;
        try {
            friday = create(host, first, "Friday Night");
            if (friday == null) {
                helper.fail("hosting at the desk created no tournament");
                return;
            }
            helper.getLevel().destroyBlock(first, false);
            if (friday.tournament.isOver() || friday.registrationPoint != null) {
                helper.fail("breaking the desk left the tournament " + friday.tournament.phase()
                        + " signing up at " + friday.registrationPoint);
                return;
            }
            BlockPos second = placeDesk(helper, 3, 2, 3);
            use(helper, host, second);
            if (!runs(helper, second, friday) || !second.equals(friday.registrationPoint)) {
                helper.fail("a new desk did not take on the tournament whose desk was broken");
                return;
            }
        } finally {
            remove(friday);
        }
        helper.succeed();
    }

    /**
     * Add this table works from where the host is standing. No chair, and nothing the client said
     * about where that is - the button used to send the origin of the world and the server used to
     * refuse every press for being too far from it.
     */
    @GameTest(template = "tables")
    public static void ahostAddsTheTableTheyAreStandingAt(GameTestHelper helper) {
        BlockPos table = dev.gathering.neoforge.test.TestTables.place(helper, 4, 2, 4);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        host.moveTo(Vec3.atCenterOf(table).add(-1.5, 0, 0));
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Standing", host.getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of());
        Events.putForTesting(state);
        try {
            EventViews.act(host, EventActionPayload.of(state.tournament.id(), EventActionPayload.Action.ADD_TABLES));
            if (!state.tables.contains(table)) {
                helper.fail("a host standing at a free table added " + state.tables);
                return;
            }
            if (TableSeats.seatOf(helper.getLevel(), table, host.getUUID()).isPresent()) {
                helper.fail("adding a table seated the host at it");
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** Only the host adds tables: a player who is not running the event is refused, wherever they stand. */
    @GameTest(template = "tables")
    public static void onlyTheHostAddsATable(GameTestHelper helper) {
        BlockPos table = dev.gathering.neoforge.test.TestTables.place(helper, 9, 2, 4);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        ServerPlayer passerBy = helper.makeMockServerPlayerInLevel();
        passerBy.moveTo(Vec3.atCenterOf(table).add(-1.5, 0, 0));
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Not Yours", host.getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of());
        Events.putForTesting(state);
        try {
            EventViews.act(passerBy, EventActionPayload.of(state.tournament.id(), EventActionPayload.Action.ADD_TABLES));
            if (!state.tables.isEmpty()) {
                helper.fail("somebody who is not the host added " + state.tables);
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /**
     * Prizes put up on the create screen are taken when the event is created: one per slot, out of
     * the host's own hotbar, and a slot they have emptied since is passed over rather than taking
     * whatever is there now.
     */
    @GameTest(template = "empty")
    public static void prizesPutUpBeforeCreationAreTakenWhenTheEventIsMade(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper, 1, 2, 1);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        host.moveTo(Vec3.atCenterOf(desk).add(0, 0, 1.5));
        host.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        host.getInventory().setItem(2, new ItemStack(Items.EMERALD, 2));
        host.getInventory().setItem(5, ItemStack.EMPTY);
        EventState state = null;
        try {
            // Place 3's slot is empty, and place 4 promises a slot place 1 already has.
            EventViews.create(host, new CreateEventPayload(desk, "Prized",
                    EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"),
                    List.of(new PrizeOffer(1, 0, "minecraft:diamond"), new PrizeOffer(2, 2, "minecraft:emerald"),
                            new PrizeOffer(3, 5, "minecraft:diamond"), new PrizeOffer(4, 0, "minecraft:diamond"))));
            state = hostedBy(host);
            if (state == null) {
                helper.fail("hosting with prizes created no tournament");
                return;
            }
            List<String> lines = EventPrizes.describe(state);
            if (state.prizes.size() != 2 || !lines.equals(List.of("1: 5 x Diamond", "2: 2 x Emerald"))) {
                helper.fail("the event holds " + lines);
                return;
            }
            if (!host.getInventory().getItem(0).isEmpty() || !host.getInventory().getItem(2).isEmpty()) {
                helper.fail("a prize the event holds is still in the host's hotbar");
                return;
            }
        } finally {
            remove(state);
        }
        helper.succeed();
    }

    /**
     * A slot whose contents changed between promising it and creating the event is passed over.
     * <p>What was put up is a slot number, and the slot is read when the tournament is made - so
     * anything that moved in the meantime was taken instead of the prize. Every ordinary thing a
     * player does while a screen is open moves it: a hotbar swap, a pickup, a mob dropping something.
     * The prize is named as well as numbered, and a slot holding something else is left alone.
     */
    @GameTest(template = "empty")
    public static void aPrizeSlotHoldingSomethingElseIsPassedOver(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper, 1, 2, 1);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        host.moveTo(Vec3.atCenterOf(desk).add(0, 0, 1.5));
        // Diamonds were promised; a stack of netherite has arrived in that slot since.
        host.getInventory().setItem(0, new ItemStack(Items.NETHERITE_INGOT, 3));
        EventState state = null;
        try {
            EventViews.create(host, new CreateEventPayload(desk, "Switched",
                    EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"),
                    List.of(new PrizeOffer(1, 0, "minecraft:diamond"))));
            state = hostedBy(host);
            if (state == null) {
                helper.fail("hosting with a prize created no tournament");
                return;
            }
            if (!state.prizes.isEmpty()) {
                helper.fail("the event took " + EventPrizes.describe(state) + " out of a slot that had "
                        + "stopped holding what was promised");
                return;
            }
            if (host.getInventory().getItem(0).getCount() != 3) {
                helper.fail("the host's own netherite was taken as a prize");
                return;
            }
        } finally {
            remove(state);
        }
        helper.succeed();
    }

    /**
     * One host may not hold open an unbounded number of tournaments.
     * <p>More than one is the point, and the desk refuses a second of its own - but the cooldown
     * between creations is nought by default, so with a desk each there was no bound at all, and each
     * tournament is state the server keeps, saves and broadcasts until somebody ends it.
     */
    @GameTest(template = "tables")
    public static void ahostMayNotHoldOpenMoreTournamentsThanAPersonWould(GameTestHelper helper) {
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        List<EventState> made = new java.util.ArrayList<>();
        try {
            for (int number = 0; number < EventRecords.MOST_AT_ONCE + 2; number++) {
                // A fresh budget each time: these are presses a few seconds apart, not a client
                // sending ten creations in one tick, which the budget refuses for its own reasons.
                EventViews.forget(host.getUUID());
                BlockPos desk = placeDesk(helper, 1, 2, 1);
                EventState state = create(host, desk, "Open " + number);
                if (state != null) {
                    made.add(state);
                }
                // The desk is taken up again so the next creation has a free one, which is what a
                // host with a row of desks has.
                helper.getLevel().destroyBlock(desk, false);
            }
            if (made.size() != EventRecords.MOST_AT_ONCE) {
                helper.fail("a host was allowed " + made.size() + " tournaments running at once");
                return;
            }
            // And ending one makes room for another, so the bound is on what is open rather than on
            // how many a person may ever run.
            Events.cancel(host, made.get(0).tournament.id());
            EventViews.forget(host.getUUID());
            BlockPos again = placeDesk(helper, 1, 2, 1);
            EventState after = create(host, again, "After");
            if (after == null) {
                helper.fail("ending a tournament did not make room for another");
                return;
            }
            made.add(after);
        } finally {
            for (EventState state : made) {
                remove(state);
            }
        }
        helper.succeed();
    }

    /**
     * A tournament of more than four leaves the winner a trophy; a smaller one leaves nothing.
     * <p>The owner's line: a pod of four is an afternoon between friends, and a cup for it would be
     * a participation trophy. Engraved with the event, the day and the winner, and cast in a color
     * of its own, so a shelf of them is a shelf of separate afternoons.
     */
    @GameTest(template = "tables")
    public static void onlyaTournamentOfMoreThanFourLeavesaTrophy(GameTestHelper helper) {
        for (int entrants : new int[] {4, 5}) {
            // Every entrant a real player, and the one asked afterwards is whoever the tournament
            // says came first. Settling every pairing in the first seat's favor does not make the
            // first player win the event - Swiss seats them differently each round - so a test that
            // assumed it passed and failed on alternate runs, which is worse than one that fails.
            List<ServerPlayer> field = new java.util.ArrayList<>();
            for (int who = 0; who < entrants; who++) {
                ServerPlayer player = helper.makeMockServerPlayerInLevel();
                player.getInventory().clearContent();
                dev.gathering.server.Owed.forget(player.getUUID());
                field.add(player);
            }
            EventState state = Events.stateForTesting(
                    playedThrough(field, "Cup of " + entrants), helper.getLevel(), List.of());
            try {
                Events.finishForTesting(helper.getLevel().getServer(), state);

                UUID first = state.tournament.finalPlaces().get(0);
                ServerPlayer winner = field.stream().filter(one -> one.getUUID().equals(first))
                        .findFirst().orElseThrow();
                dev.gathering.item.TrophyComponent won = null;
                for (var stack : winner.getInventory().items) {
                    if (stack.getItem() instanceof dev.gathering.item.TrophyItem) {
                        won = dev.gathering.item.TrophyItem.trophyOf(stack).orElse(null);
                    }
                }
                if (entrants < Events.FEWEST_FOR_A_TROPHY) {
                    if (won != null) {
                        helper.fail("a tournament of " + entrants + " left a trophy: " + won);
                        return;
                    }
                    continue;
                }
                if (won == null) {
                    helper.fail("a tournament of " + entrants + " left the winner no trophy");
                    return;
                }
                if (!won.event().equals("Cup of " + entrants) || won.winner().isEmpty()
                        || won.day().isEmpty()) {
                    helper.fail("the trophy is engraved " + won);
                    return;
                }
            } finally {
                remove(state);
                for (ServerPlayer one : field) {
                    dev.gathering.server.Owed.forget(one.getUUID());
                }
            }
        }
        helper.succeed();
    }

    /** A tournament these players played all the way through. Who wins is the pairings' business. */
    private static dev.gathering.core.tournament.Tournament playedThrough(
            List<ServerPlayer> field, String name) {
        List<UUID> players = field.stream().map(ServerPlayer::getUUID).toList();
        var tournament = dev.gathering.core.tournament.Tournament.create(
                UUID.randomUUID(), name, players.get(0),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        for (int index = 0; index < players.size(); index++) {
            tournament = tournament.register(dev.gathering.core.tournament.Entrant.registering(
                    players.get(index), "P" + index, 1500));
        }
        tournament = tournament.beginPreparing();
        for (UUID player : players) {
            tournament = tournament.markReady(player);
        }
        tournament = tournament.startSwiss();
        while (!tournament.isOver()) {
            for (var pairing : tournament.currentRound().orElseThrow().pairings()) {
                if (!pairing.isConfirmed()) {
                    tournament = tournament.settle(pairing.table(),
                            new dev.gathering.core.tournament.MatchResult(2, 0, 0));
                }
            }
            tournament = tournament.nextRound();
        }
        return tournament;
    }

    // ------------------------------------------------------------------ fixtures

    /** Hosting at a desk the way a client's Create reaches the server. */
    private static EventState create(ServerPlayer host, BlockPos desk, String name) {
        host.moveTo(Vec3.atCenterOf(desk).add(0, 0, 1.5));
        EventViews.create(host, new CreateEventPayload(desk, name,
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"), List.of()));
        // By the desk it was created at, not by its name: these tests share a server, and an earlier
        // one of them left a tournament of the same name behind.
        return Events.all().stream()
                .filter(state -> state.tournament.name().equals(name) && desk.equals(state.registrationPoint))
                .findFirst().orElse(null);
    }

    private static EventState hostedBy(ServerPlayer host) {
        return Events.all().stream()
                .filter(state -> state.tournament.host().equals(host.getUUID()) && !state.tournament.isOver())
                .findFirst().orElse(null);
    }

    private static void remove(EventState state) {
        if (state != null) {
            Events.removeForTesting(state);
        }
    }

    /** A use of a desk the way a player's click reaches it, hand and all. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos desk) {
        player.moveTo(Vec3.atCenterOf(desk).add(0, 0, 1.5));
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(desk), Direction.UP, desk, false));
    }

    private static BlockPos placeDesk(GameTestHelper helper, int x, int y, int z) {
        helper.setBlock(new BlockPos(x, y, z), GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState()
                .setValue(dev.gathering.block.ScorekeepersDeskBlock.FACING, Direction.SOUTH));
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    private static ScorekeepersDeskBlockEntity deskOf(GameTestHelper helper, BlockPos desk) {
        if (helper.getLevel().getBlockEntity(desk) instanceof ScorekeepersDeskBlockEntity entity) {
            return entity;
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("no desk at " + desk);
    }

    private static boolean runs(GameTestHelper helper, BlockPos desk, EventState state) {
        return deskOf(helper, desk).event().equals(java.util.Optional.of(state.tournament.id()));
    }
}
