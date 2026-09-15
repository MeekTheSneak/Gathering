package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TablesApart;
import dev.gathering.server.events.EventRecords;
import dev.gathering.server.events.EventState;
import dev.gathering.server.events.Events;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tournaments in a world: seating, the clock, decks, results, records and prizes.
 * <p>The rules are played through at length in the pure module. What is checked here is the part
 * with blocks and players in it - that a round seats the right people at the right numbered
 * table and starts their match, that a locked deck is refused at the table, that the clock and
 * extra turns end a match on games won, and that property put up as a prize reaches its winner.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EventsGameTest {

    private static final UUID CARD = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");

    /** A round of four players at a long table of two: each pair at its numbered table, playing. */
    @GameTest(template = "tables")
    public static void aroundSeatsEachPairAtItsNumberedTableAndStartsTheirMatch(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        Round round = fixture.state.tournament().currentRound().orElseThrow();
        for (Pairing pairing : round.pairings()) {
            BlockPos table = fixture.state.table(pairing.table()).orElseThrow();
            var seats = TableSeats.seatOf(helper.getLevel(), table, pairing.a());
            var other = TableSeats.seatOf(helper.getLevel(), table, pairing.b());
            if (seats.isEmpty() || other.isEmpty()) {
                helper.fail("table " + pairing.table() + " does not seat its pairing");
                return;
            }
            var session = TableSessions.sessionAt(helper.getLevel(), table).orElse(null);
            if (session == null || session.state().seats().size() != 2) {
                helper.fail("no two-seat match was started at table " + pairing.table());
                return;
            }
            if (!TableSessions.matchAt(helper.getLevel(), table).map(match -> match.rules().bestOf() == 3
                    && match.rules().format().id().equals("modern")).orElse(false)) {
                helper.fail("table " + pairing.table() + " is not playing the event's format and length");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * In the cut the higher Swiss seed plays first in the first game (MTR 2.2), even when an
     * upset has put them in the second chair: the eighth seed beat the first, and the fourth seed
     * they meet in the semifinal goes first.
     */
    @GameTest(template = "tables")
    public static void theHigherSeedPlaysFirstInACutMatch(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        BlockPos second = place(helper, 3, 2, 1);
        List<ServerPlayer> players = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(first.getX() + 1.0, first.getY(), first.getZ() + 1.0);
            players.add(player);
        }
        EventSettings withCut = new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 8,
                EventSettings.DeckRegistration.OFF, false);
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Cut", players.get(0).getUUID(), withCut);
        for (int index = 0; index < 9; index++) {
            tournament = tournament.register(Entrant.registering(players.get(index).getUUID(), "P" + index, 1500 - index));
        }
        tournament = tournament.beginPreparing();
        for (ServerPlayer player : players) {
            tournament = tournament.markReady(player.getUUID());
        }
        tournament = tournament.startSwiss();
        while (tournament.phase() == Tournament.Phase.SWISS) {
            for (Pairing pairing : tournament.currentRound().orElseThrow().pairings()) {
                if (!pairing.isConfirmed()) {
                    tournament = tournament.settle(pairing.table(), new MatchResult(2, 1, 0));
                }
            }
            tournament = tournament.nextRound();
        }
        for (Pairing pairing : tournament.currentRound().orElseThrow().pairings()) {
            tournament = tournament.settle(pairing.table(), pairing.table() == 1 ? new MatchResult(0, 2, 0) : new MatchResult(2, 0, 0));
        }
        tournament = tournament.nextRound();
        List<UUID> seeded = tournament.standings().stream().map(row -> row.player().id()).toList();
        Pairing semifinal = tournament.currentRound().orElseThrow().atTable(1).orElseThrow();
        if (seeded.indexOf(semifinal.b()) >= seeded.indexOf(semifinal.a())) {
            helper.fail("the semifinal was meant to have the higher seed in the second chair: " + semifinal);
            return;
        }
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of(first, second));
        Events.putForTesting(state);
        TablesApart.set(helper.getLevel(), first, true);
        Events.seatRoundForTesting(helper.getLevel().getServer(), state);

        var session = TableSessions.sessionAt(helper.getLevel(), state.table(1).orElseThrow()).orElse(null);
        if (session == null) {
            helper.fail("no match was started at the semifinal table");
            return;
        }
        boolean higherSeedFirst = session.records().stream().anyMatch(record -> record instanceof dev.gathering.core.game.SessionRecord.EventRecord event
                && event.event() instanceof dev.gathering.core.game.event.GameEvent.StartingPlayerChosen starting
                && starting.why() == dev.gathering.core.game.event.GameEvent.StartingPlayerChosen.Why.HIGHER_SEED
                && starting.actor().equals(new SeatId(1)));
        if (!higherSeedFirst || !session.state().turn().activeSeat().equals(new SeatId(1))) {
            helper.fail("the higher seed, in the second chair, is not first: " + session.state().turn().activeSeat());
            return;
        }
        helper.succeed();
    }

    /** Time and the extra turns end a match on games won, with the game in progress a draw. */
    @GameTest(template = "tables")
    public static void theClockAndExtraTurnsEndAMatchOnGamesWon(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        BlockPos table = fixture.state.table(1).orElseThrow();
        // One game won by the first player before time.
        TableBlock.entityAt(helper.getLevel(), table).orElseThrow().recordMatch(
                MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3)).afterGameWonBy(new SeatId(0)));

        Events.runClockForTesting(helper.getLevel().getServer(), fixture.state,
                fixture.state.tournament().settings().roundMinutes() * 20L * 60L);
        if (!fixture.state.tournament().currentRound().orElseThrow().timeCalled()) {
            helper.fail("the round clock ran out and time was not called");
            return;
        }
        for (int turn = 0; turn <= fixture.state.tournament().settings().extraTurns(); turn++) {
            Events.turnPassed(helper.getLevel(), table);
        }
        Pairing pairing = fixture.state.tournament().currentRound().orElseThrow().atTable(1).orElseThrow();
        if (!pairing.isConfirmed()) {
            helper.fail("the extra turns ran out and the match was not recorded");
            return;
        }
        if (!pairing.result().equals(new MatchResult(1, 0, 1))) {
            helper.fail("a match up one game at time was recorded as " + pairing.result());
            return;
        }
        helper.succeed();
    }

    /** Gone for five minutes of a round: the match is conceded. */
    @GameTest(template = "tables")
    public static void aplayerGoneFiveMinutesConcedesTheirMatch(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        Pairing pairing = fixture.state.tournament().currentRound().orElseThrow().atTable(1).orElseThrow();
        // Grace is measured in real time now, not server ticks: the same five minutes, from the wall clock.
        long now = Events.wallClockForTesting();
        Events.goneForTesting(pairing.b(), now - 5L * 60_000L - 1);
        Events.runClockForTesting(helper.getLevel().getServer(), fixture.state, 1);
        Pairing after = fixture.state.tournament().currentRound().orElseThrow().atTable(1).orElseThrow();
        if (!after.isConfirmed() || !after.result().firstWon()) {
            helper.fail("a player gone five minutes did not concede: " + after.result());
            return;
        }
        Events.goneForTesting(pairing.a(), now);
        helper.succeed();
    }

    /**
     * The same in a best-of-one event, where a concession is one game to none. It was two to
     * none, which is not a best-of-one result: settling it threw on the round clock, and the
     * match stayed open with its player gone.
     */
    @GameTest(template = "tables")
    public static void aplayerGoneFromABestOfOneConcedesOneGameToNone(GameTestHelper helper) {
        EventSettings usual = EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern");
        EventSettings bestOfOne = new EventSettings(usual.kind(), usual.formatId(), usual.pod(), 1, usual.roundMinutes(),
                usual.buildMinutes(), usual.extraTurns(), usual.rounds(), usual.topCut(), usual.decks(), usual.largeEvent());
        Fixture fixture = fourPlayersPlaying(helper, bestOfOne);
        Pairing pairing = fixture.state.tournament().currentRound().orElseThrow().atTable(1).orElseThrow();
        long now = Events.wallClockForTesting();
        Events.goneForTesting(pairing.b(), now - 5L * 60_000L - 1);
        Events.runClockForTesting(helper.getLevel().getServer(), fixture.state, 1);
        Pairing after = fixture.state.tournament().currentRound().orElseThrow().atTable(1).orElseThrow();
        Events.goneForTesting(pairing.a(), now);
        if (!after.isConfirmed() || !new MatchResult(1, 0, 0).equals(after.result())) {
            helper.fail("a player gone from a best-of-one did not concede it 1-0: " + after.result());
            return;
        }
        helper.succeed();
    }

    /** A game ended at the table is what the result screen suggests. */
    @GameTest(template = "tables")
    public static void theTableSuggestsTheResultItSaw(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        BlockPos table = fixture.state.table(2).orElseThrow();
        MatchState won = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3))
                .afterGameWonBy(new SeatId(1)).afterGameWonBy(new SeatId(1));
        Events.gameEnded(helper.getLevel(), table, won);
        Optional<MatchResult> suggested = Events.suggested(fixture.state, 2);
        if (suggested.isEmpty() || !suggested.get().equals(new MatchResult(0, 2, 0))) {
            helper.fail("the table suggested " + suggested);
            return;
        }
        helper.succeed();
    }

    /**
     * Players who swap chairs keep their own games: the result the table suggests follows the
     * players, not the chairs a round put them in.
     */
    @GameTest(template = "tables")
    public static void theSuggestedResultFollowsPlayersWhoSwapChairs(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        BlockPos table = fixture.state.table(2).orElseThrow();
        Pairing pairing = fixture.state.tournament().currentRound().orElseThrow().atTable(2).orElseThrow();
        var session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatReleased(new SeatId(0)));
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatReleased(new SeatId(1)));
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatTaken(new SeatId(0),
                new dev.gathering.core.game.PlayerRef(pairing.b(), "B")));
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatTaken(new SeatId(1),
                new dev.gathering.core.game.PlayerRef(pairing.a(), "A")));
        // Chair 0 - the second player now - won both.
        MatchState won = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3))
                .afterGameWonBy(new SeatId(0)).afterGameWonBy(new SeatId(0));
        Events.gameEnded(helper.getLevel(), table, won);
        Optional<MatchResult> suggested = Events.suggested(fixture.state, 2);
        if (suggested.isEmpty() || !suggested.get().equals(new MatchResult(0, 2, 0))) {
            helper.fail("players who swapped chairs were suggested " + suggested + ", not 0-2 for the first player");
            return;
        }
        helper.succeed();
    }

    /**
     * One player stands up and the other moves into their chair: the one still sitting is found,
     * and the one who stood up gets the chair left over rather than the same chair twice - which
     * handed both players the same games and suggested a draw.
     */
    @GameTest(template = "tables")
    public static void aPlayerWhoStoodUpKeepsTheOtherChair(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        BlockPos table = fixture.state.table(2).orElseThrow();
        Pairing pairing = fixture.state.tournament().currentRound().orElseThrow().atTable(2).orElseThrow();
        var session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatReleased(new SeatId(0)));
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatReleased(new SeatId(1)));
        session.submit(new dev.gathering.core.game.event.GameEvent.SeatTaken(new SeatId(0),
                new dev.gathering.core.game.PlayerRef(pairing.b(), "B")));
        // Chair 0 - the second player, now - won both.
        MatchState won = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3))
                .afterGameWonBy(new SeatId(0)).afterGameWonBy(new SeatId(0));
        Events.gameEnded(helper.getLevel(), table, won);
        Optional<MatchResult> suggested = Events.suggested(fixture.state, 2);
        if (suggested.isEmpty() || !suggested.get().equals(new MatchResult(0, 2, 0))) {
            helper.fail("a player who stood up was suggested " + suggested + ", not 0-2 for the first player");
            return;
        }
        helper.succeed();
    }

    /** A locked registration: the registered deck is accepted, sideboarded or not, and no other. */
    @GameTest(template = "tables")
    public static void alockedDeckIsTheOnlyDeckAccepted(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        Pairing pairing = fixture.state.tournament().currentRound().orElseThrow().atTable(1).orElseThrow();
        BlockPos table = fixture.state.table(1).orElseThrow();
        DeckComponent registered = deck(List.of(card(0), card(1), card(2)), List.of(card(3)));
        Events.registerDeckForTesting(fixture.state, pairing.a(), registered);

        DeckComponent sideboarded = deck(List.of(card(0), card(1), card(3)), List.of(card(2)));
        DeckComponent other = deck(List.of(card(0), card(1), card(4)), List.of(card(3)));
        if (Events.refusesDeck(helper.getLevel(), table, pairing.a(), sideboarded, null).isPresent()) {
            helper.fail("the registered deck was refused after sideboarding");
            return;
        }
        if (Events.refusesDeck(helper.getLevel(), table, pairing.a(), other, null).isEmpty()) {
            helper.fail("a different deck was accepted from a locked registration");
            return;
        }
        if (Events.refusesDeck(helper.getLevel(), table, pairing.b(), other, null).isPresent()) {
            helper.fail("a player with no registered deck was refused");
            return;
        }
        helper.succeed();
    }

    /** An event survives its save: tournament, tables, clocks and registered decks. */
    @GameTest(template = "tables")
    public static void aneventSurvivesItsSave(GameTestHelper helper) throws Exception {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        Events.registerDeckForTesting(fixture.state, fixture.players.get(0).getUUID(), deck(List.of(card(0)), List.of()));
        Events.runClockForTesting(helper.getLevel().getServer(), fixture.state, 40);
        Events.markRegistration(fixture.players.get(0), fixture.state.tournament().id());
        if (fixture.state.registrationPoint().isEmpty()) {
            helper.fail("the host marking a registration point did not mark one");
            return;
        }
        EventState back = Events.roundTripForTesting(fixture.state);
        if (!back.tournament().equals(fixture.state.tournament()) || !back.tables().equals(fixture.state.tables())
                || back.roundTicks() != fixture.state.roundTicks() || !back.decks().equals(fixture.state.decks())
                || !back.registrationPoint().equals(fixture.state.registrationPoint())) {
            helper.fail("the event did not come back as it was saved");
            return;
        }
        helper.succeed();
    }

    /**
     * Ratings: a big enough finished event moves them; the same pair's fourth meeting in a week
     * does not; an excluded player's do not move; and voiding takes the event's changes back.
     */
    @GameTest(template = "tables")
    public static void ratingsMoveOnlyWhereTheyShould(GameTestHelper helper) {
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            players.add(UUID.randomUUID());
        }
        Tournament finished = playedThrough(players);
        UUID winner = finished.finalPlaces().get(0);
        double before = EventRecords.seedOf(winner);
        EventRecords.finished(finished, playedEverywhere(finished), System.currentTimeMillis());
        double after = EventRecords.seedOf(winner);
        if (after <= before) {
            helper.fail("winning a six-player event did not raise a rating: " + before + " to " + after);
            return;
        }
        if (EventRecords.recordOf(winner).map(record -> record.eventsWon()).orElse(0) != 1) {
            helper.fail("the winner's record does not show the event won");
            return;
        }
        if (!EventRecords.voidRatings(finished.id()) || Math.abs(EventRecords.seedOf(winner) - before) > 1e-9) {
            helper.fail("voiding the event did not take its rating changes back");
            return;
        }

        List<UUID> few = players.subList(0, 4);
        Tournament small = playedThrough(few);
        double smallBefore = EventRecords.seedOf(few.get(0));
        EventRecords.finished(small, playedEverywhere(small), System.currentTimeMillis());
        if (EventRecords.seedOf(few.get(0)) != smallBefore) {
            helper.fail("a four-player event moved a rating");
            return;
        }

        List<UUID> fresh = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            fresh.add(UUID.randomUUID());
        }
        EventRecords.setExcluded(fresh.get(0), "Excluded", true);
        long now = System.currentTimeMillis();
        for (int event = 0; event < 4; event++) {
            Tournament again = playedThrough(fresh);
            double[] ratings = fresh.stream().mapToDouble(EventRecords::seedOf).toArray();
            EventRecords.finished(again, playedEverywhere(again), now + event);
            if (EventRecords.seedOf(fresh.get(0)) != ratings[0]) {
                helper.fail("an excluded player's rating moved");
                return;
            }
            if (event == 3) {
                // Every pair among five non-excluded players has met at least three times in the week by now.
                boolean anyMoved = false;
                for (int index = 1; index < fresh.size(); index++) {
                    anyMoved |= EventRecords.seedOf(fresh.get(index)) != ratings[index];
                }
                if (anyMoved) {
                    helper.fail("the same players' fourth meeting in a week moved ratings");
                    return;
                }
            }
        }
        helper.succeed();
    }

    /**
     * The abuse rules that are not about who met whom: results nobody played a game for move no
     * rating, an official event counts twice what a player's own does, and a host runs one event
     * at a time with the server's cooldown between them.
     */
    @GameTest(template = "tables")
    public static void ratingsNeedGamesAndHostsWaitTheirTurn(GameTestHelper helper) {
        List<UUID> typed = sixPlayers();
        Tournament unplayed = playedThrough(typed);
        double[] before = typed.stream().mapToDouble(EventRecords::seedOf).toArray();
        EventRecords.finished(unplayed, Set.of(), System.currentTimeMillis());
        for (int index = 0; index < typed.size(); index++) {
            if (EventRecords.seedOf(typed.get(index)) != before[index]) {
                helper.fail("results with no game at a table moved a rating");
                return;
            }
        }
        if (EventRecords.recordOf(typed.get(0)).map(record -> record.eventsPlayed()).orElse(0) != 1) {
            helper.fail("an unplayed event was left out of the record as well as the ratings");
            return;
        }

        // One round, so every winner's change is one match from the starting rating and the
        // weight is the only difference between the two events.
        EventSettings oneRound = new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 1, 0,
                EventSettings.DeckRegistration.OFF, false);
        List<UUID> own = sixPlayers();
        Tournament ownEvent = playedThrough(own, oneRound);
        EventRecords.finished(ownEvent, playedEverywhere(ownEvent), System.currentTimeMillis());
        List<UUID> officialPlayers = sixPlayers();
        Tournament officialEvent = playedThrough(officialPlayers, oneRound);
        EventRecords.setOfficial(officialEvent.id(), true);
        EventRecords.finished(officialEvent, playedEverywhere(officialEvent), System.currentTimeMillis());
        double ownChange = EventRecords.seedOf(ownEvent.finalPlaces().get(0)) - EventRecords.STARTING_RATING;
        double officialChange = EventRecords.seedOf(officialEvent.finalPlaces().get(0)) - EventRecords.STARTING_RATING;
        if (ownChange <= 0 || Math.abs(officialChange - 2 * ownChange) > 1e-6) {
            helper.fail("an official event did not count twice a player's own: " + ownChange + " and " + officialChange);
            return;
        }

        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        UUID host = fixture.players.get(0).getUUID();
        if (!EventRecords.whyNotHost(host).equals(Optional.of("message.gathering.event.hosting_one"))) {
            helper.fail("a host running an event may host another");
            return;
        }
        Events.removeForTesting(fixture.state);
        long now = System.currentTimeMillis();
        EventRecords.hostedAtForTesting(host, now);
        if (EventRecords.whyNotHost(host, now + 1).isPresent()) {
            helper.fail("the default cooldown of none kept a host waiting");
            return;
        }
        helper.succeed();
    }

    /** A prize put up leaves the host's hand and reaches the winner; one for somebody away is kept. */
    @GameTest(template = "tables")
    public static void aprizeReachesTheWinner(GameTestHelper helper) {
        Fixture fixture = fourPlayersPlaying(helper, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        ServerPlayer host = fixture.players.get(0);
        host.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 5));
        Events.putPrizeForTesting(host, fixture.state.tournament().id(), 1);
        if (!host.getMainHandItem().isEmpty()) {
            helper.fail("a prize was put up and the host still holds it");
            return;
        }
        Tournament tournament = fixture.state.tournament();
        while (!tournament.isOver()) {
            for (Pairing pairing : tournament.currentRound().orElseThrow().pairings()) {
                if (!pairing.isConfirmed()) {
                    tournament = tournament.settle(pairing.table(), new MatchResult(2, 0, 0));
                }
            }
            tournament = tournament.nextRound();
        }
        Events.setForTesting(fixture.state, tournament);
        UUID winner = tournament.finalPlaces().get(0);
        ServerPlayer winning = fixture.players.stream().filter(player -> player.getUUID().equals(winner)).findFirst().orElseThrow();
        Events.finishForTesting(helper.getLevel().getServer(), fixture.state);
        int diamonds = 0;
        for (int slot = 0; slot < winning.getInventory().getContainerSize(); slot++) {
            if (winning.getInventory().getItem(slot).is(Items.DIAMOND)) {
                diamonds += winning.getInventory().getItem(slot).getCount();
            }
        }
        if (diamonds != 5) {
            helper.fail("the winner received " + diamonds + " of 5 diamonds");
            return;
        }
        helper.succeed();
    }

    /**
     * A venue: sixteen players across eight separate numbered tables, each pair sat at its own
     * table with its match started and the table's number over it, and nobody moved - they are
     * shown the way, since none of them was sitting at that table already.
     */
    @GameTest(template = "tables")
    public static void sixteenPlayersAreSeatedAcrossEightNumberedTables(GameTestHelper helper) {
        List<BlockPos> tables = new ArrayList<>();
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                tables.add(place(helper, column * 3, 2 + row, row * 2));
            }
        }
        List<ServerPlayer> players = new ArrayList<>();
        BlockPos hall = helper.absolutePos(new BlockPos(6, 2, 3));
        for (int index = 0; index < 16; index++) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
            players.add(player);
        }
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Venue", players.get(0).getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        for (int index = 0; index < 16; index++) {
            tournament = tournament.register(Entrant.registering(players.get(index).getUUID(), "V" + index, 1600 - index));
        }
        tournament = tournament.beginPreparing();
        for (ServerPlayer player : players) {
            tournament = tournament.markReady(player.getUUID());
        }
        tournament = tournament.startSwiss();
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), tables);
        Events.putForTesting(state);
        Events.seatRoundForTesting(helper.getLevel().getServer(), state);
        Events.labelTablesForTesting(helper.getLevel().getServer(), state);
        Round round = state.tournament().currentRound().orElseThrow();
        if (round.pairings().size() != 8) {
            helper.fail("sixteen players made " + round.pairings().size() + " pairings, not 8");
            return;
        }
        Set<Integer> numbers = new java.util.HashSet<>();
        for (Pairing pairing : round.pairings()) {
            BlockPos table = state.table(pairing.table()).orElseThrow();
            numbers.add(pairing.table());
            if (TableSeats.seatOf(helper.getLevel(), table, pairing.a()).isEmpty()
                    || TableSeats.seatOf(helper.getLevel(), table, pairing.b()).isEmpty()) {
                helper.fail("table " + pairing.table() + " does not seat its pairing");
                return;
            }
            if (TableSessions.sessionAt(helper.getLevel(), table).isEmpty()) {
                helper.fail("no match was started at table " + pairing.table());
                return;
            }
            int shown = TableBlock.entityAt(helper.getLevel(), table).map(entity -> entity.eventTable()).orElse(0);
            if (shown != pairing.table()) {
                helper.fail("table " + pairing.table() + " is labeled " + shown);
                return;
            }
        }
        if (numbers.size() != 8) {
            helper.fail("the pairings did not use eight different tables: " + numbers);
            return;
        }
        for (ServerPlayer player : players) {
            if (player.blockPosition().distSqr(hall) > 2) {
                helper.fail("a player who was not sitting at their table was moved to it rather than shown the way");
                return;
            }
        }
        helper.succeed();
    }

    /** With a registration point, signing up is only done standing at it. */
    @GameTest(template = "tables")
    public static void aregistrationPointIsWhereSigningUpHappens(GameTestHelper helper) {
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos desk = helper.absolutePos(new BlockPos(1, 1, 1));
        host.setPos(desk.getX() + 0.5, desk.getY(), desk.getZ() + 0.5);
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Desk", host.getUUID(),
                new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 0,
                        EventSettings.DeckRegistration.OFF, true));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of(place(helper, 5, 2, 1)));
        Events.putForTesting(state);
        Events.markRegistration(host, tournament.id());
        player.setPos(desk.getX() + 40.5, desk.getY(), desk.getZ() + 0.5);
        Events.register(player, tournament.id());
        if (state.tournament().entrant(player.getUUID()).isPresent()) {
            helper.fail("a player far from the registration point was registered");
            return;
        }
        player.setPos(desk.getX() + 2.5, desk.getY(), desk.getZ() + 0.5);
        Events.register(player, tournament.id());
        if (state.tournament().entrant(player.getUUID()).isEmpty()) {
            helper.fail("a player at the registration point was not registered");
            return;
        }
        Events.removeForTesting(state);
        helper.succeed();
    }

    // ------------------------------------------------------------------ fixtures

    private record Fixture(EventState state, List<ServerPlayer> players) {
    }

    private static Fixture fourPlayersPlaying(GameTestHelper helper, EventSettings settings) {
        BlockPos first = place(helper, 1, 2, 1);
        BlockPos second = place(helper, 3, 2, 1);
        List<ServerPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(first.getX() + 1.0, first.getY(), first.getZ() + 1.0);
            players.add(player);
        }
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Test", players.get(0).getUUID(), settings);
        for (int index = 0; index < 4; index++) {
            tournament = tournament.register(Entrant.registering(players.get(index).getUUID(), "P" + index, 1500 - index));
        }
        tournament = tournament.beginPreparing();
        for (ServerPlayer player : players) {
            tournament = tournament.markReady(player.getUUID());
        }
        tournament = tournament.startSwiss();
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of(first, second));
        Events.putForTesting(state);
        TablesApart.set(helper.getLevel(), first, true);
        Events.seatRoundForTesting(helper.getLevel().getServer(), state);
        return new Fixture(state, players);
    }

    private static List<UUID> sixPlayers() {
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            players.add(UUID.randomUUID());
        }
        return players;
    }

    /** Every match in the event, as having had a game played at its table. */
    private static Set<String> playedEverywhere(Tournament tournament) {
        Set<String> played = new java.util.HashSet<>();
        for (Round round : tournament.rounds()) {
            for (Pairing pairing : round.pairings()) {
                played.add(round.number() + ":" + pairing.table());
            }
        }
        return played;
    }

    private static Tournament playedThrough(List<UUID> players) {
        return playedThrough(players, EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
    }

    private static Tournament playedThrough(List<UUID> players, EventSettings settings) {
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Rated", players.get(0), settings);
        for (int index = 0; index < players.size(); index++) {
            tournament = tournament.register(Entrant.registering(players.get(index), "R" + index, 1500));
        }
        tournament = tournament.beginPreparing();
        for (UUID player : players) {
            tournament = tournament.markReady(player);
        }
        tournament = tournament.startSwiss();
        while (!tournament.isOver()) {
            for (Pairing pairing : tournament.currentRound().orElseThrow().pairings()) {
                if (!pairing.isConfirmed()) {
                    tournament = tournament.settle(pairing.table(), new MatchResult(2, 1, 0));
                }
            }
            tournament = tournament.nextRound();
        }
        return tournament;
    }

    private static DeckComponent deck(List<CardComponent> main, List<CardComponent> side) {
        return new DeckComponent("Registered", "", Optional.empty(), main, List.of(), side);
    }

    private static CardComponent card(int index) {
        return CardComponent.of(CardIdentity.ofPrinting(new UUID(CARD.getMostSignificantBits(), index)));
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
