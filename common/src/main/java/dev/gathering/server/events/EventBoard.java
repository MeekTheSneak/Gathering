package dev.gathering.server.events;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Standings;
import dev.gathering.core.tournament.Tournament;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What a tournament at a table would put on a board: its standings, its pairings, its round.
 * <p>For boards other mods own - Create's display boards, first - read through here so that none of
 * them reach into an event's state, and so what they show is what the event screen shows: public
 * results only, the same names, the same order. Plain data and no text, because each board lays
 * its lines out its own way.
 * <p>An event is found from any block of any of its tables. An event still running comes first; a
 * table whose last event has finished shows that one, final places and all, until the next begins
 * there.
 */
public final class EventBoard {

    /** One line of the standings. */
    public record Standing(int rank, String name, int points, int wins, int losses, int draws, boolean dropped) {
    }

    /** One table's match this round: a bye has no second player; a result is blank until confirmed. */
    public record Match(int table, String first, String second, String result) {

        public boolean isBye() {
            return second.isEmpty();
        }
    }

    /**
     * The event, as a board shows it.
     *
     * @param round         the round being played or last played, from one; zero before the first
     * @param secondsLeft   on the round or build clock, or -1 when no clock is running
     * @param places        final places, best first, once the event has finished
     * @param thisTable     this table's number in the event, or zero if it has none
     * @param prizes        what is put up, one place a line, best place first
     * @param players       how many have signed up
     */
    public record Board(String name, Tournament.Phase phase, int round, int plannedRounds, boolean elimination,
            long secondsLeft, List<Standing> standings, List<Match> pairings, List<String> places, int thisTable,
            List<String> prizes, int players) {

        /** This table's match in the round, if it has one. */
        public Optional<Match> match() {
            return pairings.stream().filter(match -> match.table() == thisTable && thisTable > 0).findFirst();
        }
    }

    private EventBoard() {
    }

    /** The event at the table this block is part of, as a board shows it. */
    public static Optional<Board> at(ServerLevel level, BlockPos anyTableBlock) {
        BlockPos origin = TableBlock.entityAt(level, anyTableBlock).map(TableBlockEntity::getBlockPos).orElse(null);
        if (origin == null) {
            return Optional.empty();
        }
        return eventUsing(level, origin).map(state -> boardOf(state, origin));
    }

    /** The tournament a Scorekeeper's Desk runs, as a board shows it. */
    public static Optional<Board> atDesk(ServerLevel level, BlockPos desk) {
        if (!(level.getBlockEntity(desk) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity entity)) {
            return Optional.empty();
        }
        String dimension = level.dimension().location().toString();
        return entity.event().flatMap(Events::get).filter(state -> dimension.equals(state.dimension))
                .map(state -> boardOf(state, null));
    }

    /**
     * Just what a desk's floating label says, without building the board: the name, the phase, the
     * round, the winner, and whether signing up happens at this desk.
     *
     * @param signsUpHere whether this desk is the tournament's registration point
     * @param timeCalled  whether the round being played has run out of time and is in its extra turns
     */
    public record DeskLabel(String name, Tournament.Phase phase, int round, int rounds, String winner, boolean signsUpHere,
            boolean timeCalled) {
    }

    /** What the label over a Scorekeeper's Desk says, if it runs a tournament here. Cheap enough to ask once a second. */
    public static Optional<DeskLabel> labelAtDesk(ServerLevel level, BlockPos desk) {
        if (!(level.getBlockEntity(desk) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity entity)) {
            return Optional.empty();
        }
        String dimension = level.dimension().location().toString();
        return entity.event().flatMap(Events::get).filter(state -> dimension.equals(state.dimension)).map(state -> {
            Tournament tournament = state.tournament;
            String winner = tournament.phase() == Tournament.Phase.FINISHED && !tournament.finalPlaces().isEmpty()
                    ? Events.nameOf(state, tournament.finalPlaces().get(0))
                    : "";
            boolean playing = tournament.phase() == Tournament.Phase.SWISS || tournament.phase() == Tournament.Phase.CUT;
            return new DeskLabel(tournament.name(), tournament.phase(),
                    tournament.currentRound().map(Round::number).orElse(0), tournament.plannedRounds(), winner,
                    desk.equals(state.registrationPoint),
                    playing && tournament.currentRound().map(Round::timeCalled).orElse(false));
        });
    }

    private static Optional<EventState> eventUsing(ServerLevel level, BlockPos origin) {
        Optional<EventState> running = Events.atTable(level, origin);
        if (running.isPresent()) {
            return running;
        }
        String dimension = level.dimension().location().toString();
        EventState latest = null;
        for (EventState state : Events.all()) {
            if (state.tournament.phase() == Tournament.Phase.FINISHED && dimension.equals(state.dimension)
                    && state.tables.contains(origin)) {
                latest = state;
            }
        }
        return Optional.ofNullable(latest);
    }

    static Board boardOf(EventState state, BlockPos origin) {
        Tournament tournament = state.tournament;
        List<Standing> standings = new ArrayList<>();
        for (Standings.Row row : tournament.standings()) {
            standings.add(new Standing(row.rank(), row.player().name(), row.matchPoints(), row.wins(), row.losses(),
                    row.draws(), row.player().isDropped()));
        }
        Round round = tournament.currentRound().orElse(null);
        List<Match> pairings = new ArrayList<>();
        if (round != null) {
            for (Pairing pairing : round.pairings()) {
                pairings.add(new Match(pairing.table(), Events.nameOf(state, pairing.a()),
                        pairing.isBye() ? "" : Events.nameOf(state, pairing.b()),
                        pairing.isConfirmed() ? pairing.result().label() : ""));
            }
        }
        List<String> places = new ArrayList<>();
        if (tournament.phase() == Tournament.Phase.FINISHED) {
            for (UUID place : tournament.finalPlaces()) {
                places.add(Events.nameOf(state, place));
            }
        }
        var settings = tournament.settings();
        long millisLeft = switch (tournament.phase()) {
            case SWISS, CUT -> settings.roundMinutes() * Events.MINUTE_MILLIS - state.roundMillis;
            case PREPARING -> settings.kind().isLimited() ? settings.buildMinutes() * Events.MINUTE_MILLIS - state.buildMillis : -1000;
            default -> -1000;
        };
        return new Board(tournament.name(), tournament.phase(), round == null ? 0 : round.number(),
                tournament.plannedRounds(), round != null && round.elimination(), Math.max(-1, millisLeft / 1000),
                List.copyOf(standings), List.copyOf(pairings), List.copyOf(places), origin == null ? 0 : state.numberOf(origin),
                List.copyOf(EventPrizes.describe(state)), tournament.entrants().size());
    }
}
