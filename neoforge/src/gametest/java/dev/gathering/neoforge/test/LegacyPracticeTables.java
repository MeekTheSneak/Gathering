package dev.gathering.neoforge.test;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.server.PracticeTable;
import dev.gathering.server.TableBroadcast;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the practice table an old save can contain, for the tests that take one apart.
 * <p>Nothing in the mod creates one of these any more: the guided first game is a local
 * demonstration in the player's own client. This used to live in production as
 * {@code PracticeTable.start}, reachable from nothing, kept only so these tests could build
 * the shape a legacy save has. A cleanup audit asked for it to leave the shipped jar, and
 * here it is - the same seating, the same session, the same blank library and the same flag,
 * because a test that built the leftovers some other way would be testing its own idea of
 * them.
 * <p>{@link #stop} is the old ending, flag still on, kept for the same reason: the tests that
 * guard {@code TableSessions.giveBack}'s refusal to hand a practice deck to anybody need the
 * path that refusal is on. Production never reaches that path now - a legacy table is retired
 * by {@link PracticeTable#retire}, which takes the flag off first.
 * <p>Server thread only, like everything it calls.
 */
final class LegacyPracticeTables {

    /** How many cards the old practice library held. */
    private static final int PRACTICE_LIBRARY = 20;

    private LegacyPracticeTables() {
    }

    /** Why the old practice game could not start, or that it did. */
    enum Outcome {
        STARTED,
        NO_TABLE,
        GAME_RUNNING,
        SOMEBODY_ELSE_HERE,
        TOO_SMALL,
        COULD_NOT_SIT
    }

    /** Starts a practice game the way the retired design did, refusing rather than clearing. */
    static Outcome start(ServerPlayer learner, BlockPos tableOrigin) {
        if (learner == null || tableOrigin == null) {
            return Outcome.NO_TABLE;
        }
        ServerLevel level = learner.serverLevel();
        BlockPos anchor = TableSessions.anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null) {
            return Outcome.NO_TABLE;
        }
        if (table.hasSession()) {
            return Outcome.GAME_RUNNING;
        }

        List<SeatAnchor> seats = TableClusters.at(level, tableOrigin).seats();
        if (seats.size() < 2) {
            return Outcome.TOO_SMALL;
        }
        if (somebodyElseIsHere(level, tableOrigin, learner.getUUID())) {
            return Outcome.SOMEBODY_ELSE_HERE;
        }

        UUID demonstration = PracticeTable.demonstrationSeat();
        if (TableSeats.seatOf(level, tableOrigin, learner.getUUID()).isEmpty()
                && !sit(level, tableOrigin, seats, learner.getUUID())) {
            return Outcome.COULD_NOT_SIT;
        }
        if (!sit(level, tableOrigin, seats, demonstration)) {
            TableSeats.leave(level, tableOrigin, learner.getUUID());
            return Outcome.TOO_SMALL;
        }

        TableSessions.Outcome started = TableSessions.start(level, tableOrigin,
                new MatchRules(FormatPresets.COMMANDER, 1));
        if (started != TableSessions.Outcome.STARTED) {
            TableSeats.leave(level, tableOrigin, learner.getUUID());
            TableSeats.leave(level, tableOrigin, demonstration);
            return Outcome.GAME_RUNNING;
        }

        // Marked before a single card is dealt, as it always was.
        table.markAsPractice();

        GameSession session = table.session().orElse(null);
        SeatId mine = TableSessions.seatIdOf(level, tableOrigin, learner.getUUID()).orElse(null);
        SeatId theirs = TableSessions.seatIdOf(level, tableOrigin, demonstration).orElse(null);
        if (session == null || mine == null || theirs == null) {
            stop(level, tableOrigin);
            return Outcome.COULD_NOT_SIT;
        }

        deal(session, mine);
        deal(session, theirs);
        session.submit(new GameEvent.PaperCardCreated(theirs, theirs, PaperStock.BLANK,
                Component.translatable("tutorial.gathering.demonstration_card").getString()));
        TableBroadcast.sendToTable(level, tableOrigin);
        return Outcome.STARTED;
    }

    /**
     * The old practice ending: the ordinary ending with the flag still on, then the
     * demonstration seat emptied. Does nothing where there is no practice game.
     */
    static void stop(ServerLevel level, BlockPos tableOrigin) {
        if (level == null || tableOrigin == null) {
            return;
        }
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .orElse(null);
        if (table == null || !table.isPractice()) {
            return;
        }
        TableSessions.end(level, tableOrigin, null, "practice over");
        TableSeats.leave(level, tableOrigin, PracticeTable.demonstrationSeat());
        TableBroadcast.sendToTable(level, tableOrigin);
    }

    /** Deals one seat a library of blank stock, straight into the session. */
    private static void deal(GameSession session, SeatId seat) {
        List<CardIdentity> library = new ArrayList<>(PRACTICE_LIBRARY);
        for (int card = 0; card < PRACTICE_LIBRARY; card++) {
            library.add(PaperStock.BLANK.identity());
        }
        session.submit(new GameEvent.DeckLoaded(seat, library, List.of(), null));
        session.submit(new GameEvent.LibraryShuffled(seat, seat));
    }

    /** Whether anybody but this player is sitting at this cluster. */
    private static boolean somebodyElseIsHere(ServerLevel level, BlockPos tableOrigin, UUID learner) {
        for (SeatAnchor seat : TableClusters.at(level, tableOrigin).seats()) {
            Optional<UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, seat.cell()))
                    .flatMap(table -> table.occupantOf(seat.side()));
            if (occupant.isPresent() && !occupant.get().equals(learner)) {
                return true;
            }
        }
        return false;
    }

    /** Puts somebody in the first free seat, and says whether there was one. */
    private static boolean sit(
            ServerLevel level, BlockPos tableOrigin, List<SeatAnchor> seats, UUID who) {
        for (SeatAnchor seat : seats) {
            if (TableSeats.take(level, tableOrigin, seat.cell(), seat.side(), who)
                    == TableSeats.Claim.TAKEN) {
                return true;
            }
        }
        return false;
    }
}
