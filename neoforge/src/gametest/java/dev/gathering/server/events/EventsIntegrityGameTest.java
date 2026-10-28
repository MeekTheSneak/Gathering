package dev.gathering.server.events;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.DraftedPool;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.EventActionPayload;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.ServerRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * The cases around the tournament audit's findings that its own guards leave open: the pool that
 * should be accepted, both chairs' view of a report, a newer event's labels, a payout whose save
 * fails, a pod too big for its table, and the real-time clock's cap.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EventsIntegrityGameTest {

    private record Fixture(EventState state, ServerPlayer host, ServerPlayer other, BlockPos table) {
    }

    /** The pool this event handed this player, held while building, makes them ready. */
    /**
     * Something going wrong in one event's clock stays in that event: it used to leave the server's
     * tick and crash the whole server. Reported once while it keeps failing, and again only after
     * the clock has run cleanly in between.
     */
    @GameTest(template = "empty")
    public static void aFailingEventClockDoesNotStopTheServer(GameTestHelper helper) {
        EventState state = Events.stateForTesting(Tournament.create(UUID.randomUUID(), "DELIBERATELY BROKEN", UUID.randomUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern")), helper.getLevel(), List.of());
        var server = helper.getLevel().getServer();
        try {
            Events.contained(server, state, () -> {
                throw new IllegalArgumentException("message.gathering.event.not_a_result");
            });
        } catch (RuntimeException escaped) {
            helper.fail("a failing event clock escaped into the server's tick: " + escaped);
            return;
        }
        String reported = state.lastFailure;
        if (reported == null || !reported.contains("not_a_result")) {
            helper.fail("the failure was not kept to report once: " + reported);
            return;
        }
        Events.contained(server, state, () -> { });
        if (state.lastFailure != null) {
            helper.fail("a clean tick did not clear the last failure");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void thePoolThisEventHandedOutIsAccepted(GameTestHelper helper) {
        Fixture fixture = fixture(helper, EventSettings.Kind.SEALED, false);
        try {
            List<CardComponent> cards = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                cards.add(CardComponent.of(CardIdentity.ofPrinting(new UUID(77L, index))));
            }
            Events.allocateForTesting(fixture.state, fixture.host.getUUID(), cards);
            ItemStack stack = DeckItem.of(new DeckComponent("Pool", "", Optional.of(fixture.host.getUUID()), cards, List.of(), List.of()));
            stack.set(GatheringComponents.POOL.get(), new DraftedPool(cards, fixture.state.podName()));
            fixture.host.getInventory().setItem(fixture.host.getInventory().selected, stack);
            EventViews.act(fixture.host, EventActionPayload.of(fixture.state.tournament.id(), EventActionPayload.Action.READY));
            if (!fixture.state.tournament.ready().contains(fixture.host.getUUID())) {
                helper.fail("the pool this event handed out was refused");
                return;
            }
            // And somebody else holding a copy of it is not them.
            fixture.other.getInventory().setItem(fixture.other.getInventory().selected, stack.copy());
            EventViews.act(fixture.other, EventActionPayload.of(fixture.state.tournament.id(), EventActionPayload.Action.READY));
            if (fixture.state.tournament.ready().contains(fixture.other.getUUID())) {
                helper.fail("another player's pool made somebody ready");
                return;
            }
            helper.succeed();
        } finally {
            Events.removeForTesting(fixture.state);
        }
    }

    /** One report, read from both chairs: each sees it with their own games first. */
    @GameTest(template = "tables")
    public static void areportReadsTheRightWayRoundFromBothChairs(GameTestHelper helper) {
        Fixture fixture = fixture(helper, EventSettings.Kind.CONSTRUCTED, true);
        try {
            var pairing = fixture.state.tournament.currentRound().orElseThrow().pairingOf(fixture.host.getUUID()).orElseThrow();
            ServerPlayer first = pairing.a().equals(fixture.host.getUUID()) ? fixture.host : fixture.other;
            ServerPlayer second = first == fixture.host ? fixture.other : fixture.host;
            EventViews.act(first, new EventActionPayload(fixture.state.tournament.id(), EventActionPayload.Action.REPORT,
                    0, 2, 1, 0, EventActionPayload.NONE, BlockPos.ZERO));
            var firstView = EventViews.viewFor(helper.getLevel().getServer(), first.getUUID(), fixture.state, false).mine();
            var secondView = EventViews.viewFor(helper.getLevel().getServer(), second.getUUID(), fixture.state, false).mine();
            if (!"2-1".equals(firstView.myReport()) || !"1-2".equals(secondView.theirReport())) {
                helper.fail("a 2-1 report reads " + firstView.myReport() + " to its reporter and " + secondView.theirReport()
                        + " to their opponent");
                return;
            }
            helper.succeed();
        } finally {
            Events.removeForTesting(fixture.state);
        }
    }

    /** An old event bringing its labels up to date leaves the newer event's number on a shared table. */
    @GameTest(template = "tables")
    public static void anOldEventDoesNotRelabelANewEventsTable(GameTestHelper helper) {
        Fixture fixture = fixture(helper, EventSettings.Kind.CONSTRUCTED, true);
        EventState old = Events.stateForTesting(Tournament.create(UUID.randomUUID(), "Old", fixture.host.getUUID(),
                fixture.state.tournament.settings()).cancel(), helper.getLevel(), List.of(fixture.table));
        Events.putForTesting(old);
        try {
            Events.labelTablesForTesting(helper.getLevel().getServer(), fixture.state);
            Events.labelTablesForTesting(helper.getLevel().getServer(), old);
            int shown = TableBlock.entityAt(helper.getLevel(), fixture.table).orElseThrow().eventTable();
            if (shown != 1) {
                helper.fail("an ended event took the number off a newer event's table: " + shown);
                return;
            }
            helper.succeed();
        } finally {
            Events.removeForTesting(old);
            Events.removeForTesting(fixture.state);
        }
    }

    /** A payout whose save fails hands nothing over; the prize is kept for its winner, who gets it later. */
    @GameTest(template = "tables")
    public static void aprizeWhoseSaveFailsIsKeptNotHandedTwice(GameTestHelper helper) throws Exception {
        Fixture fixture = fixture(helper, EventSettings.Kind.CONSTRUCTED, true);
        Path folder = ServerRun.inSave("gathering-events").orElseThrow();
        Path target = folder.resolve(fixture.state.tournament.id() + ".dat");
        Path temporary = folder.resolve(fixture.state.tournament.id() + ".dat.tmp");
        try {
            fixture.host.getInventory().setItem(fixture.host.getInventory().selected, new ItemStack(Items.EMERALD, 4));
            EventPrizes.put(fixture.host, fixture.state.tournament.id(), 1);
            Tournament tournament = fixture.state.tournament;
            while (!tournament.isOver()) {
                for (var pairing : tournament.currentRound().orElseThrow().pairings()) {
                    if (!pairing.isConfirmed()) {
                        tournament = tournament.settle(pairing.table(), new MatchResult(2, 0, 0));
                    }
                }
                tournament = tournament.nextRound();
            }
            fixture.state.tournament = tournament;
            UUID winnerId = tournament.finalPlaces().get(0);
            ServerPlayer winner = winnerId.equals(fixture.host.getUUID()) ? fixture.host : fixture.other;
            int before = emeralds(winner);
            Files.deleteIfExists(target);
            Files.createDirectories(target);
            Files.writeString(target.resolve("blocker"), "integrity");
            Events.finishForTesting(helper.getLevel().getServer(), fixture.state);
            if (emeralds(winner) != before) {
                helper.fail("a prize was handed over while the event could not be saved without it");
                return;
            }
            if (fixture.state.waitingPrizes.getOrDefault(winnerId, List.of()).isEmpty()) {
                helper.fail("a prize whose hand-over could not be saved was not kept for its winner");
                return;
            }
            Files.delete(target.resolve("blocker"));
            Files.delete(target);
            EventPrizes.arrived(winner);
            if (emeralds(winner) != before + 4 || !fixture.state.waitingPrizes.isEmpty()) {
                helper.fail("the kept prize did not reach its winner once the event could be saved");
                return;
            }
            helper.succeed();
        } finally {
            if (Files.isDirectory(target)) {
                Files.deleteIfExists(target.resolve("blocker"));
                Files.deleteIfExists(target);
            }
            Files.deleteIfExists(temporary);
            Events.removeForTesting(fixture.state);
        }
    }

    /** A draft or sealed event with more players than its long table seats is refused before it begins. */
    @GameTest(template = "tables")
    public static void aPodTooBigForItsTableIsRefusedBeforeItBegins(GameTestHelper helper) {
        Fixture fixture = fixture(helper, EventSettings.Kind.SEALED, false);
        try {
            Tournament signingUp = Tournament.create(fixture.state.tournament.id(), "Crowded", fixture.host.getUUID(),
                    EventSettings.usual(EventSettings.Kind.SEALED, ""));
            for (int index = 0; index < 5; index++) {
                signingUp = signingUp.register(Entrant.registering(new UUID(5L, index), "C" + index, 1500));
            }
            fixture.state.tournament = signingUp;
            EventViews.act(fixture.host, EventActionPayload.of(signingUp.id(), EventActionPayload.Action.BEGIN));
            if (fixture.state.tournament.phase() != Tournament.Phase.SIGNUP) {
                helper.fail("five players at one table began as " + fixture.state.tournament.phase());
                return;
            }
            helper.succeed();
        } finally {
            Events.removeForTesting(fixture.state);
        }
    }

    /** The round clock counts real time between ticks, and a stalled server counts no more than a second. */
    @GameTest(template = "tables")
    public static void theRoundClockCountsRealTimeButNotAStall(GameTestHelper helper) {
        Fixture fixture = fixture(helper, EventSettings.Kind.CONSTRUCTED, true);
        var real = Events.clock;
        long[] now = {1_000_000_000L};
        try {
            Events.clock = () -> now[0];
            Events.tick(helper.getLevel().getServer());
            long start = fixture.state.roundMillis;
            now[0] += 200_000_000L;
            Events.tick(helper.getLevel().getServer());
            long afterATick = fixture.state.roundMillis - start;
            now[0] += 3_600_000_000_000L;
            Events.tick(helper.getLevel().getServer());
            long afterAStall = fixture.state.roundMillis - start - afterATick;
            if (afterATick != 200 || afterAStall != Events.LONGEST_TICK_MILLIS) {
                helper.fail("a 200 ms tick counted " + afterATick + " ms and an hour's stall " + afterAStall + " ms");
                return;
            }
            helper.succeed();
        } finally {
            Events.clock = real;
            Events.removeForTesting(fixture.state);
        }
    }

    private static int emeralds(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.EMERALD)) {
                count += player.getInventory().getItem(slot).getCount();
            }
        }
        return count;
    }

    private static Fixture fixture(GameTestHelper helper, EventSettings.Kind kind, boolean playing) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(at), GatheringContent.TABLE.get().defaultBlockState()
                    .setValue(TableBlock.PART, part), 3);
        }
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        host.setGameMode(GameType.SURVIVAL);
        other.setGameMode(GameType.SURVIVAL);
        host.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        other.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        EventSettings settings = new EventSettings(kind, "modern", null, 3, 50, 30, 5, 2, 0,
                EventSettings.DeckRegistration.OFF, false);
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Integrity", host.getUUID(), settings)
                .register(Entrant.registering(host.getUUID(), "Host", 1600))
                .register(Entrant.registering(other.getUUID(), "Other", 1500)).beginPreparing();
        if (playing) {
            tournament = tournament.startSwiss();
        }
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of(at));
        Events.putForTesting(state);
        if (playing) {
            Events.seatRoundForTesting(helper.getLevel().getServer(), state);
        }
        return new Fixture(state, host, other, at);
    }
}
