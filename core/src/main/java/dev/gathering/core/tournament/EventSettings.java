package dev.gathering.core.tournament;

import dev.gathering.core.draft.PodSettings;
import java.util.Locale;
import java.util.Optional;

/**
 * What a host decides when they create a tournament.
 * <p>Fixed from the moment the first player registers, for the reason a pod's settings are:
 * everything here is a promise to the people who join - how long a round is, whether the deck
 * they registered is the one they must play - and a promise that changed after people had
 * signed up to it is not one.
 *
 * @param kind            draft, sealed or constructed
 * @param formatId        the format preset a constructed event is played in; blank for limited
 * @param pod             how a draft or sealed event gets its packs; null for constructed
 * @param bestOf          games in a match
 * @param roundMinutes    how long a round runs before time is called
 * @param buildMinutes    how long players have to build after a draft or sealed opening
 * @param extraTurns      turns after time is called, counting the one in progress as turn 0
 * @param rounds          Swiss rounds, or 0 for the player count to decide
 * @param topCut          0 for none, or 4 or 8 for a single elimination cut after Swiss
 * @param decks           how constructed decks are registered
 * @param largeEvent      whether players sign up in advance and check in at a venue
 */
public record EventSettings(
        Kind kind, String formatId, PodSettings pod, int bestOf, int roundMinutes, int buildMinutes,
        int extraTurns, int rounds, int topCut, DeckRegistration decks, boolean largeEvent) {

    public enum Kind {
        DRAFT, SEALED, CONSTRUCTED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean isLimited() {
            return this != CONSTRUCTED;
        }
    }

    /** How a constructed player's deck is tied to them. */
    public enum DeckRegistration {
        /** Bring any deck to each match. */
        OFF,
        /** Signing up submits a deck, which has to pass the format's deck check. */
        CHECKED,
        /** As checked, and the list is theirs for the event: no other deck is accepted. */
        LOCKED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int USUAL_ROUND_MINUTES = 50;
    public static final int USUAL_BUILD_MINUTES = 30;
    public static final int USUAL_EXTRA_TURNS = 5;
    public static final int MOST_MINUTES = 240;
    public static final int MOST_ROUNDS = 15;

    public EventSettings {
        kind = kind == null ? Kind.CONSTRUCTED : kind;
        formatId = formatId == null ? "" : formatId.trim().toLowerCase(Locale.ROOT);
        decks = decks == null ? DeckRegistration.LOCKED : decks;
        if (kind == Kind.CONSTRUCTED) {
            pod = null;
        } else if (pod == null) {
            pod = PodSettings.usual(kind == Kind.SEALED ? PodSettings.Kind.SEALED : PodSettings.Kind.DRAFT);
        }
    }

    /** An event as it runs when the host changes nothing. */
    public static EventSettings usual(Kind kind, String formatId) {
        return new EventSettings(kind, formatId, null, 3, USUAL_ROUND_MINUTES, USUAL_BUILD_MINUTES,
                USUAL_EXTRA_TURNS, 0, 0, DeckRegistration.LOCKED, false);
    }

    /** Why these settings cannot make an event, or empty when they can. A translation key. */
    public Optional<String> problem() {
        if (bestOf != 1 && bestOf != 3 && bestOf != 5) {
            return Optional.of("message.gathering.event.best_of");
        }
        if (roundMinutes < 10 || roundMinutes > MOST_MINUTES || buildMinutes < 5 || buildMinutes > MOST_MINUTES) {
            return Optional.of("message.gathering.event.minutes");
        }
        if (extraTurns < 0 || extraTurns > 10) {
            return Optional.of("message.gathering.event.extra_turns");
        }
        if (rounds < 0 || rounds > MOST_ROUNDS) {
            return Optional.of("message.gathering.event.rounds");
        }
        if (topCut != 0 && topCut != 4 && topCut != 8) {
            return Optional.of("message.gathering.event.top_cut");
        }
        if (kind == Kind.CONSTRUCTED && formatId.isEmpty()) {
            return Optional.of("message.gathering.event.needs_a_format");
        }
        if (pod != null) {
            if (pod.kind() != (kind == Kind.SEALED ? PodSettings.Kind.SEALED : PodSettings.Kind.DRAFT)) {
                return Optional.of("message.gathering.event.pod_kind");
            }
            if (pod.cardsGo() != PodSettings.CardsGo.PLAYERS_KEEP) {
                // A tournament's players build and play from their pools for the whole event.
                // Cards going to a sponsor or back to their packs' owners would leave them
                // nothing to build from - and taking a pool back out of a player's inventory at
                // the end is not something the server can promise. A pod on its own still can.
                return Optional.of("message.gathering.event.players_keep_pools");
            }
            return pod.problem();
        }
        return Optional.empty();
    }

    /** How many Swiss rounds, for this many players. */
    public int roundsFor(int players) {
        return rounds > 0 ? rounds : SwissRounds.forPlayers(players);
    }

    /** Whether a top cut is played for this many players. Off below nine, as decided. */
    public int cutFor(int players) {
        return players >= 9 ? Math.min(topCut, players) : 0;
    }
}
