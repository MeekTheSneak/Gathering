package dev.gathering.core.game.event;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.Phase;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import java.util.List;
import java.util.Optional;

/**
 * Every verb in the game, as data.
 *
 * <p>The session is event-sourced: each of these is appended to the log and the board is the
 * fold of that log. Undo is a re-fold, replay is a re-fold, and persistence is the log plus
 * the seed. None of those three needed to be designed - they fall out of this being the only
 * way state ever changes.
 *
 * <p>The set is section 7's deliberately trimmed v1 list and no more. Dice, coin flips,
 * monarch and initiative markers, a shared reveal area, clone markers distinct from
 * copy-tokens, per-player notes and a dedicated mill button all wait for post-v1 evidence
 * that anyone actually wants them.
 *
 * <p>Nothing here enforces a rule. There is no event for "this is illegal" because there is
 * no such concept: the mod moves cards, tracks numbers and shows things, and the group
 * decides what any of it means.
 */
public sealed interface GameEvent {

    /** The seat that performed this. Every action is attributable; that is the honesty layer. */
    SeatId actor();

    /**
     * The public log entry, built against the board as it was before this event.
     *
     * <p>Takes the state because whether a card may be named at all depends on where it is -
     * see {@link CardRef}. Never contains hidden card identity, and never a raw instance id
     * for a card somebody cannot already see.
     */
    LogLine describe(GameState before);

    /** Whether this event's content must go to the sealed stream at rest. */
    default Secrecy secrecy() {
        return Secrecy.PUBLIC;
    }

    /**
     * Whether this event let somebody see something they had not seen.
     *
     * <p>Undo can never cross one of these freely, in any undo mode, because a seen card
     * cannot be un-seen. Rewinding past an information boundary always escalates to
     * unanimous consent.
     *
     * <p>Takes the state before the event because some events only reveal depending on the
     * board - moving a card out of a hand reveals it, moving it within the battlefield does
     * not.
     */
    default boolean revealsInformation(GameState before) {
        return false;
    }

    // ------------------------------------------------------------- lifecycle

    /** Sitting down. A registration, not a chair lock. */
    record SeatTaken(SeatId actor, PlayerRef player) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.seat_taken", actor, player.name());
        }
    }

    /** Leaving the session for good, as distinct from walking away from the table. */
    record SeatReleased(SeatId actor) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.seat_released", actor);
        }
    }

    /**
     * A deck arriving at the table: the library in decklist order and any commanders.
     *
     * <p>Secret, and the most secret thing in the log after the seed itself. The library's
     * order at this moment plus the seed determines every draw in the game.
     */
    record DeckLoaded(SeatId actor, List<CardIdentity> library, List<CardIdentity> commanders)
            implements GameEvent {

        public DeckLoaded {
            library = List.copyOf(library);
            commanders = List.copyOf(commanders);
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.deck_loaded", actor, library.size(), commanders.size());
        }

        @Override
        public Secrecy secrecy() {
            return Secrecy.SECRET;
        }
    }

    /** A session ends by unanimous vote or by the table owner. */
    record SessionEnded(SeatId actor, String reason) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.session_ended", actor, reason);
        }
    }

    // ----------------------------------------------------------- card verbs

    /**
     * Moving a card anywhere.
     *
     * <p>One event covers play-to-battlefield, send-to-graveyard, exile, bounce to hand, put
     * on top or bottom of library, and dragging a stolen creature to your own side of the
     * table. They are all the same act and modelling them separately would only invite them
     * to drift apart.
     */
    record CardMoved(SeatId actor, CardInstanceId card, ZoneRef to, Placement placement) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            // The destination decides what may be said, because that is where the card ends
            // up: one going into a hand becomes "a card" whatever it was, and one arriving on
            // the battlefield can be named, because everyone is about to see it anyway.
            CardRef reference;
            if (to.zone().isHidden()) {
                reference = CardRef.ANONYMOUS;
            } else {
                reference = before.card(card)
                        .flatMap(instance -> instance.markerId())
                        .<CardRef>map(CardRef.ByMarker::new)
                        .orElseGet(() -> new CardRef.ById(card));
            }
            return LogLine.of(
                    "log.gathering.card_moved", actor, reference, to.seat(), to.zone().name(), placement.name());
        }

        @Override
        public boolean revealsInformation(GameState before) {
            // Out of a hand or library into the open is a reveal; anything else is not.
            return before.locationOf(card)
                    .map(from -> from.zone().isHidden() && to.zone().isPublic())
                    .orElse(false);
        }
    }

    record CardTapSet(SeatId actor, CardInstanceId card, boolean tapped) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(tapped ? "log.gathering.card_tapped" : "log.gathering.card_untapped",
                    actor, CardRef.publicRefFor(before, card));
        }
    }

    /** Untap-all, because doing it one permanent at a time is how a turn takes four minutes. */
    record SeatUntappedAll(SeatId actor, SeatId seat) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.untapped_all", actor, seat);
        }
    }

    record CardFacingSet(SeatId actor, CardInstanceId card, Facing facing) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(
                    facing == Facing.FACE_UP ? "log.gathering.card_turned_up" : "log.gathering.card_turned_down",
                    actor, CardRef.publicRefFor(before, card));
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return facing == Facing.FACE_UP;
        }
    }

    /** Draws from the top of a library. The log says how many, never which. */
    record CardsDrawn(SeatId actor, SeatId seat, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.cards_drawn", actor, seat, count);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    /** Hand back, shuffle, draw a smaller one. One log line because it is one decision. */
    record Mulliganed(SeatId actor, SeatId seat, int newHandSize) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.mulliganed", actor, seat, newHandSize);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    // ----------------------------------------------------------- pile verbs

    /**
     * A shuffle, announced in the log and derived from the session seed.
     *
     * <p>The resulting order is never stored - it is recomputed from the seed and the
     * session's shuffle count, which is what keeps the log free of the one thing that would
     * spoil a game if it leaked.
     */
    record LibraryShuffled(SeatId actor, SeatId seat) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.library_shuffled", actor, seat);
        }
    }

    /**
     * Opening a library to look through it. Changes nothing on its own - taking a card is a
     * separate {@link CardMoved} - but it is very much an information boundary.
     */
    record LibrarySearched(SeatId actor, SeatId seat) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.library_searched", actor, seat);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    /** The looking half of scry and surveil, before anything is decided. */
    record LibraryLooked(SeatId actor, SeatId seat, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.library_looked", actor, seat, count);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    /**
     * The deciding half of a scry: some cards stay on top in a chosen order, the rest go to
     * the bottom.
     *
     * <p>Secret, because it names cards. The public log gets "scried 2, kept 1 on top",
     * which is exactly what an opponent watching across a table would know.
     */
    record LibraryReordered(SeatId actor, SeatId seat, List<CardInstanceId> onTop, List<CardInstanceId> toBottom)
            implements GameEvent {

        public LibraryReordered {
            onTop = List.copyOf(onTop);
            toBottom = List.copyOf(toBottom);
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.scried", actor, seat, onTop.size() + toBottom.size(), onTop.size());
        }

        @Override
        public Secrecy secrecy() {
            return Secrecy.SECRET;
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    /** Surveil: looked cards either stay on top or go to the graveyard. Also secret. */
    record Surveiled(SeatId actor, SeatId seat, List<CardInstanceId> onTop, List<CardInstanceId> toGraveyard)
            implements GameEvent {

        public Surveiled {
            onTop = List.copyOf(onTop);
            toGraveyard = List.copyOf(toGraveyard);
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(
                    "log.gathering.surveilled", actor, seat, onTop.size() + toGraveyard.size(), toGraveyard.size());
        }

        @Override
        public Secrecy secrecy() {
            return Secrecy.SECRET;
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    // ------------------------------------------------- counters and tokens

    /** Loyalty, +1/+1, and anything a player cares to name. Deltas, and they may go negative. */
    record CounterChanged(SeatId actor, CardInstanceId card, String counter, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.counter_changed", actor, CardRef.publicRefFor(before, card), counter, delta);
        }
    }

    /** Tokens are real printings from Scryfall's token search, not invented cards. */
    record TokenCreated(SeatId actor, SeatId seat, CardIdentity identity, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.token_created", actor, seat, count);
        }
    }

    /** A copy of something already on the table, which is what most copy effects want. */
    record TokenCopyCreated(SeatId actor, CardInstanceId source, SeatId seat) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.token_copied", actor, CardRef.publicRefFor(before, source), seat);
        }
    }

    /** Tokens cease to exist rather than going anywhere, which is the one exception to going home. */
    record TokenRemoved(SeatId actor, CardInstanceId card) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.token_removed", actor, CardRef.publicRefFor(before, card));
        }
    }

    // --------------------------------------------------------- player verbs

    record LifeChanged(SeatId actor, SeatId seat, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.life_changed", actor, seat, delta);
        }
    }

    record CommanderDamageChanged(SeatId actor, SeatId toSeat, SeatId fromSeat, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.commander_damage", actor, toSeat, fromSeat, delta);
        }
    }

    /** Commander tax as a displayed number. Nothing charges it; players do their own arithmetic. */
    record CommanderTaxChanged(SeatId actor, SeatId seat, CardInstanceId commander, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.commander_tax", actor, seat, CardRef.publicRefFor(before, commander), delta);
        }
    }

    /** Conceding is a player's own decision and the only thing close to a game result here. */
    record Conceded(SeatId actor) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.conceded", actor);
        }
    }

    // ---------------------------------------------------------- table verbs

    record PhaseSet(SeatId actor, Phase phase) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.phase_set", actor, phase.name());
        }
    }

    record TurnPassed(SeatId actor, SeatId toSeat) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.turn_passed", actor, toSeat);
        }
    }

    /**
     * Pointing at the table. Highlights a public card for everyone for a few seconds and
     * changes nothing, which is precisely why it is worth having: "in response to that" needs
     * a "that".
     */
    record CardPinged(SeatId actor, CardInstanceId card) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            // Pinging is pointing at the table, so it only ever names something public anyway.
            return LogLine.of("log.gathering.pinged", actor, CardRef.publicRefFor(before, card));
        }
    }

    /** Convenience for the callers that only care whether an event touched a particular seat. */
    default Optional<SeatId> subjectSeat() {
        return switch (this) {
            case CardsDrawn drawn -> Optional.of(drawn.seat());
            case Mulliganed mulligan -> Optional.of(mulligan.seat());
            case LibraryShuffled shuffled -> Optional.of(shuffled.seat());
            case LibrarySearched searched -> Optional.of(searched.seat());
            case LibraryLooked looked -> Optional.of(looked.seat());
            case LibraryReordered reordered -> Optional.of(reordered.seat());
            case Surveiled surveiled -> Optional.of(surveiled.seat());
            case SeatUntappedAll untapped -> Optional.of(untapped.seat());
            case LifeChanged life -> Optional.of(life.seat());
            case CommanderDamageChanged damage -> Optional.of(damage.toSeat());
            case CommanderTaxChanged tax -> Optional.of(tax.seat());
            case TokenCreated token -> Optional.of(token.seat());
            case TokenCopyCreated copy -> Optional.of(copy.seat());
            case CardMoved moved -> Optional.of(moved.to().seat());
            default -> Optional.empty();
        };
    }

    /** Whether this event only ever concerns a hidden zone, for the persistence split. */
    static boolean touchesHiddenZone(GameEvent event, GameState before) {
        return switch (event) {
            case CardMoved moved -> moved.to().zone().isHidden()
                    || before.locationOf(moved.card()).map(ZoneRef::isHidden).orElse(false);
            case CardsDrawn ignored -> true;
            case Mulliganed ignored -> true;
            case LibraryReordered ignored -> true;
            case Surveiled ignored -> true;
            case DeckLoaded ignored -> true;
            default -> false;
        };
    }

    /** Zones this event is about, for tests that want to assert on scope rather than type. */
    static List<Zone> zonesTouched(GameEvent event) {
        return switch (event) {
            case CardsDrawn ignored -> List.of(Zone.LIBRARY, Zone.HAND);
            case Mulliganed ignored -> List.of(Zone.LIBRARY, Zone.HAND);
            case LibraryShuffled ignored -> List.of(Zone.LIBRARY);
            case LibrarySearched ignored -> List.of(Zone.LIBRARY);
            case LibraryLooked ignored -> List.of(Zone.LIBRARY);
            case LibraryReordered ignored -> List.of(Zone.LIBRARY);
            case Surveiled ignored -> List.of(Zone.LIBRARY, Zone.GRAVEYARD);
            case CardMoved moved -> List.of(moved.to().zone());
            default -> List.of();
        };
    }
}
