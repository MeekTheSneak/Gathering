package dev.gathering.core.tournament;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A tournament: who is in it, the rounds played, and what happens next.
 * <p>Every change is a method that returns the tournament it became, and refuses what cannot
 * happen with a translation key the player can be told. The mod runs the structure and nothing
 * about play: who won a game is reported by the players, confirmed by both, settled by the host
 * when they disagree, and decided on games won when time runs out.
 * <p>Pure and immutable, so a whole event can be played through in a test and saved as bytes.
 *
 * @param id            which tournament
 * @param name          what it is called on screens
 * @param host          who created it and settles disputes
 * @param settings      what the host decided
 * @param phase         where it has got to
 * @param entrants      everybody registered, in the order they registered
 * @param checkedIn     who has checked in, for an event that needs it
 * @param ready         who has said they are ready to play after building
 * @param rounds        every round paired so far, Swiss then cut
 * @param plannedRounds how many Swiss rounds were planned when play began
 */
public record Tournament(
        UUID id, String name, UUID host, EventSettings settings, Phase phase, List<Entrant> entrants,
        Set<UUID> checkedIn, Set<UUID> ready, List<Round> rounds, int plannedRounds) {

    public enum Phase {
        /** Taking registrations. */
        SIGNUP,
        /** Registration closed; registered players are checking in. */
        CHECK_IN,
        /** Packs being opened, drafted or built with; decks being registered. */
        PREPARING,
        /** Swiss rounds under way. */
        SWISS,
        /** The top cut under way. */
        CUT,
        /** Over, with final standings. */
        FINISHED,
        /** Called off before it finished. */
        CANCELLED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** More than any server fills; a bound on what one event holds. */
    public static final int MOST_PLAYERS = 256;

    public Tournament {
        if (id == null || host == null || settings == null || phase == null) {
            throw new IllegalArgumentException("A tournament needs an id, a host, settings and a phase");
        }
        name = name == null || name.isBlank() ? "Tournament" : name;
        entrants = List.copyOf(entrants);
        checkedIn = Set.copyOf(checkedIn);
        ready = Set.copyOf(ready);
        rounds = List.copyOf(rounds);
    }

    public static Tournament create(UUID id, String name, UUID host, EventSettings settings) {
        String problem = settings.problem().orElse(null);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        return new Tournament(id, name, host, settings, Phase.SIGNUP, List.of(), Set.of(), Set.of(), List.of(), 0);
    }

    // ------------------------------------------------------------------ reading

    public Optional<Entrant> entrant(UUID player) {
        return entrants.stream().filter(entrant -> entrant.id().equals(player)).findFirst();
    }

    public boolean isRegistered(UUID player) {
        return entrant(player).isPresent();
    }

    /** Everybody still playing. */
    public List<Entrant> stillIn() {
        return entrants.stream().filter(entrant -> !entrant.isDropped()).toList();
    }

    public Optional<Round> currentRound() {
        return rounds.isEmpty() ? Optional.empty() : Optional.of(rounds.get(rounds.size() - 1));
    }

    public List<Round> swissRounds() {
        return rounds.stream().filter(round -> !round.elimination()).toList();
    }

    public List<Standings.Row> standings() {
        return Standings.of(entrants, swissRounds());
    }

    /**
     * Whether the first player of this pairing finished the Swiss rounds above the second: in a
     * cut match, the one who chooses to play or draw in the first game (MTR 2.2).
     */
    public boolean firstSeededHigher(Pairing pairing) {
        List<UUID> order = standings().stream().map(row -> row.player().id()).toList();
        int first = order.indexOf(pairing.a());
        int second = pairing.b() == null ? -1 : order.indexOf(pairing.b());
        return second < 0 || (first >= 0 && first < second);
    }

    public boolean isOver() {
        return phase == Phase.FINISHED || phase == Phase.CANCELLED;
    }

    // ------------------------------------------------------------------ signing up

    public Tournament register(Entrant entrant) {
        if (phase != Phase.SIGNUP) {
            throw new IllegalArgumentException("message.gathering.event.registration_closed");
        }
        if (isRegistered(entrant.id())) {
            throw new IllegalArgumentException("message.gathering.event.already_registered");
        }
        if (entrants.size() >= mostPlayers()) {
            throw new IllegalArgumentException("message.gathering.event.full");
        }
        List<Entrant> more = new ArrayList<>(entrants);
        more.add(entrant);
        return with(phase, more, checkedIn, ready, rounds, plannedRounds);
    }

    /** Leaving before play begins: gone from the event entirely, as if never registered. */
    public Tournament withdraw(UUID player) {
        if (phase != Phase.SIGNUP && phase != Phase.CHECK_IN && phase != Phase.PREPARING) {
            throw new IllegalArgumentException("message.gathering.event.drop_instead");
        }
        List<Entrant> fewer = entrants.stream().filter(entrant -> !entrant.id().equals(player)).toList();
        Set<UUID> checked = new LinkedHashSet<>(checkedIn);
        checked.remove(player);
        Set<UUID> readied = new LinkedHashSet<>(ready);
        readied.remove(player);
        return with(phase, fewer, checked, readied, rounds, plannedRounds);
    }

    /** Closes registration and asks registered players to check in. For a large event. */
    public Tournament openCheckIn() {
        require(Phase.SIGNUP);
        return with(Phase.CHECK_IN, entrants, checkedIn, ready, rounds, plannedRounds);
    }

    public Tournament checkIn(UUID player) {
        require(Phase.CHECK_IN);
        if (!isRegistered(player)) {
            throw new IllegalArgumentException("message.gathering.event.not_registered");
        }
        Set<UUID> checked = new LinkedHashSet<>(checkedIn);
        checked.add(player);
        return with(phase, entrants, checked, ready, rounds, plannedRounds);
    }

    /**
     * Registration is over and preparation begins. At a large event anybody who did not check in
     * is left out now, so no seat is held for a player who is not there.
     */
    public Tournament beginPreparing() {
        if (phase != Phase.SIGNUP && phase != Phase.CHECK_IN) {
            throw new IllegalArgumentException("message.gathering.event.not_signing_up");
        }
        List<Entrant> playing = playingIfBegunNow();
        if (playing.size() < fewestPlayers()) {
            throw new IllegalArgumentException("message.gathering.event.too_few");
        }
        return with(Phase.PREPARING, playing, checkedIn, Set.of(), rounds, plannedRounds);
    }

    /**
     * Who would play if registration closed now: at check-in, only those who checked in.
     * <p>One answer, because there were three. {@code beginPreparing} asked the phase, the host
     * screen's refusal asked the phase, and the seat check before a limited event asked whether the
     * event was a large one - which is a different question, and nothing ties the two together. A
     * large event whose host never opened check-in counted nobody, so the check that refuses a pod
     * too big for its table passed every time; a small event whose host opened it anyway counted
     * players who were never going to play, and refused a pod that fitted.
     */
    public List<Entrant> playingIfBegunNow() {
        return phase == Phase.CHECK_IN
                ? entrants.stream().filter(entrant -> checkedIn.contains(entrant.id())).toList()
                : List.copyOf(entrants);
    }

    /**
     * The most players this event can take, which for a draft or a sealed pod is the pod's own
     * ceiling rather than the tournament's.
     * <p>Asked at sign-up, and it was not: a limited event took registrations up to two hundred and
     * fifty-six and found out at Begin that a pod holds eight. Sixteen people would sign up for a
     * sealed event, be refused as one, and the host's only way forward was to drop eight of them by
     * hand. Refusing the ninth is one message to one person at the moment they ask.
     */
    public int mostPlayers() {
        int most = MOST_PLAYERS;
        if (settings.kind().isLimited() && settings.pod() != null) {
            most = Math.min(most, settings.pod().mostPlayers());
        }
        return most;
    }

    /** The fewest players this event can be played with. */
    public int fewestPlayers() {
        return settings.kind() == EventSettings.Kind.DRAFT ? dev.gathering.core.draft.DraftRules.SMALLEST_POD : 2;
    }

    /** A player is ready to play: their deck is built, or registered. */
    public Tournament markReady(UUID player) {
        require(Phase.PREPARING);
        if (!isRegistered(player)) {
            throw new IllegalArgumentException("message.gathering.event.not_registered");
        }
        Set<UUID> readied = new LinkedHashSet<>(ready);
        readied.add(player);
        return with(phase, entrants, checkedIn, readied, rounds, plannedRounds);
    }

    public boolean everyoneIsReady() {
        return stillIn().stream().allMatch(entrant -> ready.contains(entrant.id()));
    }

    // ------------------------------------------------------------------ playing

    /** The first Swiss round, paired by seed. Refused before preparation is over. */
    public Tournament startSwiss() {
        require(Phase.PREPARING);
        int planned = settings.roundsFor(stillIn().size());
        List<Round> first = List.of(new Round(1, false, SwissPairer.pair(stillIn(), List.of(), 1), false));
        return with(Phase.SWISS, entrants, checkedIn, ready, first, planned);
    }

    /**
     * A player's report of their match, as they see it: their games first.
     * <p>Confirmed the moment the other player reports the same; disputed while they differ.
     */
    public Tournament report(UUID player, MatchResult asTheySeeIt) {
        Round round = playingRound();
        Pairing pairing = round.pairingOf(player).orElseThrow(() ->
                new IllegalArgumentException("message.gathering.event.not_playing"));
        if (pairing.isBye()) {
            throw new IllegalArgumentException("message.gathering.event.bye");
        }
        if (pairing.isConfirmed()) {
            throw new IllegalArgumentException("message.gathering.event.already_confirmed");
        }
        if (round.elimination() && asTheySeeIt.isDraw()) {
            throw new IllegalArgumentException("message.gathering.event.cut_needs_a_winner");
        }
        if (!asTheySeeIt.fits(settings.bestOf())) {
            throw new IllegalArgumentException("message.gathering.event.not_a_result");
        }
        return replace(round, pairing.withReport(player, asTheySeeIt));
    }

    /** The host settles a match, from the first player's chair. */
    public Tournament settle(int table, MatchResult fromFirstPlayer) {
        Round round = playingRound();
        Pairing pairing = round.atTable(table).orElseThrow(() ->
                new IllegalArgumentException("message.gathering.event.no_such_table"));
        if (round.elimination() && fromFirstPlayer.isDraw()) {
            throw new IllegalArgumentException("message.gathering.event.cut_needs_a_winner");
        }
        if (!fromFirstPlayer.fits(settings.bestOf())) {
            throw new IllegalArgumentException("message.gathering.event.not_a_result");
        }
        return replace(round, pairing.settled(fromFirstPlayer));
    }

    /** The round clock has run out: every unfinished match starts counting its extra turns. */
    public Tournament callTime() {
        Round round = playingRound();
        if (round.timeCalled()) {
            return this;
        }
        List<Round> changed = new ArrayList<>(rounds);
        changed.set(changed.size() - 1, round.withTimeCalled());
        return with(phase, entrants, checkedIn, ready, changed, plannedRounds);
    }

    /**
     * A turn passed at this table. Counted only once time has been called - asked of the round,
     * because the count itself can be below zero by then: see {@link #turnsRewound}.
     */
    public Tournament turnPassed(int table) {
        Round round = playingRound();
        Pairing pairing = round.atTable(table).orElse(null);
        if (pairing == null || pairing.isConfirmed() || !round.timeCalled()) {
            return this;
        }
        return replace(round, pairing.withTurnsAfterTime(pairing.turnsAfterTime() + 1));
    }

    /**
     * Turns passed at this table and then taken back: they stop counting toward its extra turns.
     * <p>Extra turns are counted from the turns passed, and a pass rewound at the table is a turn
     * nobody took. Counted anyway, a pass pressed by mistake and undone once time was called used
     * up an extra turn, and five of them ended a match on games won without one being played.
     * <p>Below zero when the rewind reaches back past the turn that was in progress when time was
     * called: a pass made just before the call and taken back just after it leaves the table on the
     * turn before turn 0, and the pass that reaches turn 0 again is turn 0, not extra turn 1. Stopped
     * at zero, the count lost that, and the match ended a turn early. A match already decided is left
     * alone - its result was recorded, and settling it again is the host's.
     */
    public Tournament turnsRewound(int table, int passes) {
        Round round = playingRound();
        Pairing pairing = round.atTable(table).orElse(null);
        if (passes <= 0 || pairing == null || pairing.isConfirmed() || !round.timeCalled()) {
            return this;
        }
        return replace(round, pairing.withTurnsAfterTime(pairing.turnsAfterTime() - passes));
    }

    /**
     * Whether this table has finished its extra turns: the turn in progress when time was called
     * and then the host's number more.
     */
    public boolean extraTurnsAreOver(int table) {
        return currentRound().flatMap(round -> round.atTable(table))
                .map(pairing -> !pairing.isConfirmed() && pairing.turnsAfterTime() > settings.extraTurns())
                .orElse(false);
    }

    /**
     * Records a match ended by time, from the first player's chair: the games each had won and the
     * game in progress as a draw.
     */
    public Tournament endAtTime(int table, int winsA, int winsB, boolean gameInProgress) {
        return endAtTime(table, winsA, winsB, 0, gameInProgress);
    }

    /** The same, with the games already drawn in the match counted among its games. */
    public Tournament endAtTime(int table, int winsA, int winsB, int drawnGames, boolean gameInProgress) {
        Round round = playingRound();
        Pairing pairing = round.atTable(table).orElseThrow(() ->
                new IllegalArgumentException("message.gathering.event.no_such_table"));
        MatchResult atTime = MatchResult.atTime(winsA, winsB, gameInProgress);
        MatchResult result = new MatchResult(winsA, winsB,
                Math.min(MatchResult.MOST_GAMES - winsA - winsB, Math.max(0, drawnGames) + atTime.draws()));
        if (round.elimination() && result.isDraw()) {
            // A cut cannot end in a draw; the host decides a tied match at time.
            return this;
        }
        return replace(round, pairing.settled(result));
    }

    /**
     * Records a match ended by time, with the life totals of the game in progress: in a cut match
     * tied on games, the player with the higher life total wins that game (MTR 2.4). Tied on life
     * too, nothing is recorded and the host decides.
     */
    public Tournament endAtTime(int table, int winsA, int winsB, boolean gameInProgress, int lifeA, int lifeB) {
        return endAtTime(table, winsA, winsB, 0, gameInProgress, lifeA, lifeB);
    }

    /** The same, with the games already drawn in the match counted among its games. */
    public Tournament endAtTime(int table, int winsA, int winsB, int drawnGames, boolean gameInProgress, int lifeA,
            int lifeB) {
        if (playingRound().elimination() && gameInProgress && winsA == winsB && lifeA != lifeB) {
            return endAtTime(table, lifeA > lifeB ? winsA + 1 : winsA, lifeB > lifeA ? winsB + 1 : winsB, drawnGames,
                    false);
        }
        return endAtTime(table, winsA, winsB, drawnGames, gameInProgress);
    }

    /**
     * A player leaves. Their unfinished match this round is conceded; they are not paired again
     * and keep their record.
     */
    public Tournament drop(UUID player) {
        Entrant entrant = entrant(player).orElseThrow(() ->
                new IllegalArgumentException("message.gathering.event.not_registered"));
        if (phase == Phase.SIGNUP || phase == Phase.CHECK_IN || phase == Phase.PREPARING) {
            return withdraw(player);
        }
        if (isOver() || entrant.isDropped()) {
            return this;
        }
        Tournament after = this;
        Round round = currentRound().orElse(null);
        if (round != null) {
            Pairing pairing = round.pairingOf(player).orElse(null);
            if (pairing != null && !pairing.isConfirmed() && !pairing.isBye()) {
                after = after.replace(round, pairing.settled(MatchResult.conceded(pairing.a().equals(player), settings.bestOf())));
            }
        }
        int lastRound = after.currentRound().map(Round::number).orElse(0);
        List<Entrant> changed = after.entrants.stream()
                .map(each -> each.id().equals(player) ? each.droppedAfter(lastRound) : each).toList();
        return after.with(after.phase, changed, after.checkedIn, after.ready, after.rounds, after.plannedRounds);
    }

    /**
     * The next round, once every match in this one is confirmed: another Swiss round, the cut,
     * the next round of the cut, or the end.
     */
    public Tournament nextRound() {
        Round round = playingRound();
        if (!round.isComplete()) {
            throw new IllegalArgumentException("message.gathering.event.round_not_complete");
        }
        if (phase == Phase.SWISS) {
            if (swissRounds().size() < plannedRounds && stillIn().size() >= 2) {
                List<Round> more = new ArrayList<>(rounds);
                int number = rounds.size() + 1;
                more.add(new Round(number, false, SwissPairer.pair(stillIn(), swissRounds(), number), false));
                return with(phase, entrants, checkedIn, ready, more, plannedRounds);
            }
            int cut = settings.cutFor(entrants.size());
            List<java.util.UUID> seeded = standings().stream()
                    .filter(row -> !row.player().isDropped()).map(row -> row.player().id()).toList();
            if (cut >= 2 && seeded.size() >= cut) {
                List<Round> more = new ArrayList<>(rounds);
                more.add(new Round(rounds.size() + 1, true, Bracket.firstRound(seeded.subList(0, cut)), false));
                return with(Phase.CUT, entrants, checkedIn, ready, more, plannedRounds);
            }
            return with(Phase.FINISHED, entrants, checkedIn, ready, rounds, plannedRounds);
        }
        List<Pairing> next = Bracket.nextRound(round);
        if (next.isEmpty()) {
            return with(Phase.FINISHED, entrants, checkedIn, ready, rounds, plannedRounds);
        }
        next = next.stream().map(this::concededByWhoeverDropped).toList();
        List<Round> more = new ArrayList<>(rounds);
        more.add(new Round(rounds.size() + 1, true, next, false));
        return with(phase, entrants, checkedIn, ready, more, plannedRounds);
    }

    /**
     * A cut match one of whose players has already dropped, conceded by them as it is paired.
     * <p>The bracket pairs the winners of the last round, and a player who won and then dropped -
     * leaving in the pause, or gone when it ended - is still a winner. A dropped player is not
     * paired again, but this one was, and nothing conceded the match: the drop had already
     * happened, so the host's Drop did nothing either, and their opponent waited for the host to
     * settle it, forever if the host never did. The Swiss rounds never had this, because they pair
     * only who is still in.
     * <p>Both players dropped is left as it is, for the host to settle: a cut match needs a winner,
     * and which of two absent players goes through is the host's call.
     */
    private Pairing concededByWhoeverDropped(Pairing pairing) {
        boolean aGone = entrant(pairing.a()).map(Entrant::isDropped).orElse(false);
        boolean bGone = pairing.b() != null && entrant(pairing.b()).map(Entrant::isDropped).orElse(false);
        return aGone == bGone ? pairing : pairing.settled(MatchResult.conceded(aGone, settings.bestOf()));
    }

    public Tournament cancel() {
        if (isOver()) {
            return this;
        }
        return with(Phase.CANCELLED, entrants, checkedIn, ready, rounds, plannedRounds);
    }

    /**
     * Everybody in their final place: the cut decides the top places - winner, finalist, then the
     * losers of each earlier cut round by their Swiss standing - and the Swiss standings the rest.
     */
    public List<UUID> finalPlaces() {
        List<UUID> swiss = standings().stream().map(row -> row.player().id()).toList();
        List<Round> cut = rounds.stream().filter(Round::elimination).toList();
        if (cut.isEmpty()) {
            return swiss;
        }
        List<UUID> places = new ArrayList<>();
        for (int index = cut.size() - 1; index >= 0; index--) {
            Round round = cut.get(index);
            List<UUID> winners = new ArrayList<>();
            List<UUID> losers = new ArrayList<>();
            for (Pairing pairing : round.pairings()) {
                MatchResult result = pairing.result();
                if (result == null) {
                    continue;
                }
                winners.add(result.firstWon() ? pairing.a() : pairing.b());
                losers.add(result.firstWon() ? pairing.b() : pairing.a());
            }
            if (index == cut.size() - 1) {
                winners.sort(java.util.Comparator.comparingInt(swiss::indexOf));
                for (UUID winner : winners) {
                    if (!places.contains(winner)) {
                        places.add(winner);
                    }
                }
            }
            losers.sort(java.util.Comparator.comparingInt(swiss::indexOf));
            for (UUID loser : losers) {
                if (!places.contains(loser)) {
                    places.add(loser);
                }
            }
        }
        for (UUID player : swiss) {
            if (!places.contains(player)) {
                places.add(player);
            }
        }
        return List.copyOf(places);
    }

    // ------------------------------------------------------------------ helpers

    private Round playingRound() {
        if (phase != Phase.SWISS && phase != Phase.CUT) {
            throw new IllegalArgumentException("message.gathering.event.no_round");
        }
        return currentRound().orElseThrow(() -> new IllegalArgumentException("message.gathering.event.no_round"));
    }

    private Tournament replace(Round round, Pairing pairing) {
        List<Round> changed = new ArrayList<>(rounds);
        changed.set(changed.indexOf(round), round.with(pairing));
        return with(phase, entrants, checkedIn, ready, changed, plannedRounds);
    }

    private void require(Phase wanted) {
        if (phase != wanted) {
            throw new IllegalArgumentException("message.gathering.event.not_now");
        }
    }

    private Tournament with(Phase newPhase, List<Entrant> newEntrants, Set<UUID> newCheckedIn, Set<UUID> newReady,
            List<Round> newRounds, int newPlanned) {
        return new Tournament(id, name, host, settings, newPhase, newEntrants, newCheckedIn, newReady, newRounds, newPlanned);
    }
}
