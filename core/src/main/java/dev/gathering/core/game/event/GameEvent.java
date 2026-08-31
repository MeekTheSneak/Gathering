package dev.gathering.core.game.event;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.CardNote;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.GameState;
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
 * <p>The set is section 7's deliberately trimmed v1 list, plus what has since earned its
 * way in. Dice and coin flips were on the deferred list and came off it: Magic asks for a
 * d20 across half of Adventures in the Forgotten Realms and for a coin as far back as
 * Krark's Thumb, so a table without them sends players to a physical die and loses the one
 * thing this mod is for, which is everybody watching the same thing happen.
 *
 * <p>The monarch and the initiative came off it too, and not as themselves. Both are table
 * states tracked at a real table by putting something down and writing on it, and so is the
 * ring tempting you, and so will be whatever the set after this one invents - so what came
 * off the list is {@link PaperCardCreated}, blank stock and a pen, which is never one set
 * behind. A shared reveal area and clone markers distinct from copy-tokens are still waiting
 * for the same kind of evidence.
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
    record DeckLoaded(
            SeatId actor, List<CardIdentity> library, List<CardIdentity> commanders,
            dev.gathering.core.card.Sleeve sleeve) implements GameEvent {

        /** The same deck, in the sleeves a deck arrives in when nobody has picked any. */
        public DeckLoaded(SeatId actor, List<CardIdentity> library, List<CardIdentity> commanders) {
            this(actor, library, commanders, dev.gathering.core.card.Sleeve.DEFAULT);
        }

        public DeckLoaded {
            library = List.copyOf(library);
            commanders = List.copyOf(commanders);
            // The sleeve is the one part of this event that is not a secret: what a deck is
            // sleeved in is the first thing everybody at the table sees. Defaulted rather
            // than rejected, because logs written before sleeves existed have none.
            sleeve = sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
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
     * table. They are all the same act and modeling them separately would only invite them
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
            // Which end of a pile a card went to is worth reading - somebody putting a card
            // back on top of their library is a thing that happens to you next turn - so it
            // is in the verb rather than in a trailing note. The spot on a battlefield is not:
            // it is a pair of coordinates, and it is visible on the table anyway.
            return LogLine.of(placement.logKey(), actor, reference, to.seat(), to.zone());
        }

        @Override
        public boolean revealsInformation(GameState before) {
            // Out of a hand or library into the open is a reveal; anything else is not.
            return before.locationOf(card)
                    .map(from -> from.zone().isHidden() && to.zone().isPublic())
                    .orElse(false);
        }
    }

    /**
     * Moving every card in one zone somewhere else, in the order they were in.
     *
     * <p>The whole graveyard back into the library, a hand discarded, a library dumped. Its
     * own verb rather than a run of {@link CardMoved} for the same reason milling is: nobody
     * may name the cards in a hidden zone, so a client that had to list them could not ask
     * for this at all - and a run of moves would also fill the log with forty lines and give
     * undo forty steps to walk back through what was one decision.
     *
     * <p>Order is kept. The cards arrive in the destination in the order they left, so a
     * graveyard put back on top of a library is the graveyard, not a shuffle of it - the
     * shuffle is a separate thing somebody asks for separately.
     */
    record ZoneMoved(SeatId actor, SeatId seat, Zone from, ZoneRef to, Placement placement)
            implements GameEvent {

        public ZoneMoved {
            if (seat == null || from == null || to == null) {
                throw new IllegalArgumentException("A zone move needs a source and a destination");
            }
            if (placement == null) {
                placement = Placement.TOP;
            }
        }

        /** Where the cards are coming from, as a reference like any other. */
        public ZoneRef fromRef() {
            return ZoneRef.of(seat, from);
        }

        @Override
        public LogLine describe(GameState before) {
            int count = before.contents(seat, from).size();
            // Whether it is all one player's own business is decided here rather than left to
            // the display layer's own-line rule, which asks only whether the actor is named
            // twice - true of somebody emptying their graveyard into an opponent's library,
            // and that line must not read "to their library". Hence a key spelled with an
            // underscore: it is a wording chosen here, not one the display layer may upgrade
            // to on its own.
            boolean allMine = actor.equals(seat) && actor.equals(to.seat());
            String key = count == 1
                    ? (allMine ? "log.gathering.zone_moved_one_own" : "log.gathering.zone_moved_one")
                    : (allMine ? "log.gathering.zone_moved_own" : "log.gathering.zone_moved");
            return LogLine.of(key, actor, seat, from, to.seat(), to.zone(), count);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            // Out of somewhere secret into the open is a reveal, exactly as it is for one card.
            return fromRef().isHidden() && to.zone().isPublic() && !before.contents(seat, from).isEmpty();
        }
    }

    /**
     * Writing on a card, or rubbing out what was written.
     *
     * <p>The pen at a real table. A mod with no rules engine remembers "flying until end of
     * turn" by somebody writing it on the card, and the whole table reading it is the point -
     * so this is a public event like every other, attributed by name in the log and taken
     * back by undo like anything else.
     *
     * <p>The text is a player's, so it is nobody's secret and reveals nothing the table did
     * not already have: what it says is whatever the person holding the pen decided to say.
     */
    record CardNoted(SeatId actor, CardInstanceId card, String note) implements GameEvent {

        public CardNoted {
            note = dev.gathering.core.game.CardNote.clean(note);
        }

        /** Whether this rubs a note out rather than writing one. */
        public boolean isBlank() {
            return note == null;
        }

        @Override
        public LogLine describe(GameState before) {
            // What was written is not in the log line. It is drawn on the card, where it
            // belongs, and a log that repeated every note would be a log of notes.
            return LogLine.of(isBlank() ? "log.gathering.card_rubbed_out" : "log.gathering.card_written_on",
                    actor, CardRef.publicRefFor(before, card));
        }
    }

    /**
     * Writing a power and toughness over the printed ones, or taking the writing off.
     *
     * <p>The same pen as {@link CardNoted}, in the corner of the card where the numbers are.
     * A separate event rather than a note that happens to look like numbers, because the two
     * are drawn in different places and a reminder that reads "4/5" is a reminder, not a
     * creature that is a 4/5.
     *
     * <p>Typed, never worked out. Nothing in the mod adds counters to printed numbers - see
     * {@link dev.gathering.core.game.CardStrength} for why that is deliberate.
     */
    record CardStrengthSet(SeatId actor, CardInstanceId card, String strength) implements GameEvent {

        public CardStrengthSet {
            strength = dev.gathering.core.game.CardStrength.clean(strength);
        }

        /** Whether this takes the writing off rather than putting it on. */
        public boolean isBlank() {
            return strength == null;
        }

        @Override
        public LogLine describe(GameState before) {
            // The numbers are on the card, so the line says whose card changed and not to
            // what. A log that repeated every pump would be a log of pumps.
            return LogLine.of(
                    isBlank() ? "log.gathering.card_strength_cleared" : "log.gathering.card_strength_set",
                    actor, CardRef.publicRefFor(before, card));
        }
    }

    /**
     * Putting a hand in a different order.
     *
     * <p>Cosmetic and private, and it is still an event. A hand's order is what the fan on
     * screen is drawn from, so a client that reordered its own display would be a second
     * answer to where a card is - and the first time somebody drew a card, or rejoined, the
     * two answers would differ.
     *
     * <p>The order arrives from the client because sorting by mana value needs the card data,
     * and the client is what has it. Nothing is given away: it is your own hand, nobody else
     * can see into it, and the server keeps only the cards that are really in it - see the
     * fold, which drops anything named that is not there and keeps anything there that was
     * not named.
     */
    record HandSorted(SeatId actor, SeatId seat, List<CardInstanceId> order) implements GameEvent {

        public HandSorted {
            order = order == null ? List.of() : List.copyOf(order);
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.hand_sorted", actor);
        }
    }

    /**
     * Freezing a card, or thawing it.
     *
     * <p>A frozen card does not untap when its controller untaps everything. Nothing else
     * about it changes: it taps, takes counters, gets written on and goes to a graveyard like
     * any other card.
     *
     * <p>It is on the card rather than in somebody's head because untapping is one press,
     * made every turn without looking, and that is the press that forgets. An effect that
     * costs a card to play should not be undone by a habit.
     *
     * <p>Nothing decides when it ends. A player takes it off with the same menu they put it
     * on with - no rules engine, section 16.
     */
    record CardFrozen(SeatId actor, CardInstanceId card, boolean frozen) implements GameEvent {

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(frozen ? "log.gathering.card_frozen" : "log.gathering.card_unfrozen",
                    actor, CardRef.publicRefFor(before, card));
        }
    }

    /**
     * Turning a card to its other printed face, or back.
     *
     * <p>Not the same as turning it face down. A transformed permanent is public on both
     * sides - everybody at the table can read the werewolf and the wolf - and a face-down one
     * is a sleeve nobody may name. Keeping them apart is what lets a card be both: turn a
     * transformed creature face down for a trick and it comes back up transformed, the way it
     * would on a real table.
     *
     * <p>Whether the card has a second face at all is not asked here. That is a fact about a
     * printing and lives in the card data a client holds, not in the game - so the game
     * records which side is being shown and the drawing decides what that means.
     */
    record CardTurnedOver(SeatId actor, CardInstanceId card, boolean showingTheOtherSide)
            implements GameEvent {

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(
                    showingTheOtherSide ? "log.gathering.card_turned_over" : "log.gathering.card_turned_back",
                    actor, CardRef.publicRefFor(before, card));
        }
    }

    record CardTapSet(SeatId actor, CardInstanceId card, boolean tapped) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(tapped ? "log.gathering.card_tapped" : "log.gathering.card_untapped",
                    actor, CardRef.publicRefFor(before, card));
        }
    }

    /**
     * Turning a card on the table to any angle.
     *
     * <p>Distinct from {@link CardTapSet} even though both end up as an angle on screen.
     * Tapping is a game state with a meaning everyone at the table agrees on; turning a card
     * sideways to show it is attacking, or upside down to show you have read it, means
     * whatever the group says it means. Keeping them apart is what lets the log say "turned"
     * rather than "moved", and lets untap-all leave a deliberately angled card alone.
     *
     * <p>The angle is absolute rather than a delta, because two clicks racing each other
     * should land on one angle rather than compounding.
     */
    record CardRotated(SeatId actor, CardInstanceId card, int rotation) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.card_rotated", actor, CardRef.publicRefFor(before, card), rotation);
        }
    }

    /**
     * Putting a card onto another one, or taking it off again with a null host.
     *
     * <p>An aura on a creature, a piece of equipment on the thing it is equipping, a card
     * somebody is using to mean "this one is blocking that one". The mod knows none of those
     * words: attaching is a drawing relationship, and what it means is the group's business.
     *
     * <p>One verb for on and off, because they are one decision - "this card is on that card,
     * or on nothing" - and two verbs could get out of step with each other.
     */
    record CardAttached(SeatId actor, CardInstanceId card, CardInstanceId host) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return host == null
                    ? LogLine.of("log.gathering.card_detached", actor, CardRef.publicRefFor(before, card))
                    : LogLine.of("log.gathering.card_attached", actor,
                            CardRef.publicRefFor(before, card), CardRef.publicRefFor(before, host));
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
            return LogLine.of(oneOrMany("log.gathering.card_drawn", "log.gathering.cards_drawn",
                    count), actor, seat, count);
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
     * Opening a library to look through it.
     *
     * <p>Moves no cards - taking one is a separate {@link CardMoved} - but it opens the
     * library to the searcher until {@link LibraryClosed}, and it is very much an information
     * boundary. Everyone at the table is told it happened, which is exactly what they would
     * see across a real one.
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

    /**
     * Shutting a library again.
     *
     * <p>Its own event rather than something a screen closing implies, because whether a
     * library is open is server state and a client that simply stopped drawing one would
     * still be being sent it.
     */
    record LibraryClosed(SeatId actor) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.library_closed", actor);
        }
    }

    /**
     * Cards off the top of a library into the graveyard.
     *
     * <p>Its own verb rather than a run of {@link CardMoved}, because nobody may name a card
     * in a library - the whole point of milling is that you find out what it was by it
     * arriving in the graveyard, and a client that could name the cards it wanted milled would
     * already have had to see them.
     *
     * <p>Public. The cards land face up in a graveyard anybody may read, so nothing here is a
     * secret a moment later, and the log saying "milled four" is what a table would see.
     */
    record LibraryMilled(SeatId actor, SeatId seat, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(oneOrMany("log.gathering.library_milled_one",
                    "log.gathering.library_milled", count), actor, seat, count);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    /**
     * Cards off the top of a library into exile.
     *
     * <p>The same shape as {@link LibraryMilled} and for the same reason: nobody may name a
     * card in a library, so the only honest way to move the top few is to say how many. Exile
     * rather than the graveyard because a great many effects put the top of a library there
     * instead, and doing it as a mill followed by dragging every card across is both slower
     * and a lie in the log.
     *
     * <p>Public. Exile is a pile anybody may read, so what landed there is common knowledge
     * the moment it arrives.
     */
    record LibraryExiled(SeatId actor, SeatId seat, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(oneOrMany("log.gathering.library_exiled_one",
                    "log.gathering.library_exiled", count), actor, seat, count);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return true;
        }
    }

    /**
     * Turning the top of a library face up for the whole table.
     *
     * <p>Distinct from {@link LibraryLooked}, which opens a library to one seat. This opens it
     * to everybody, spectators included, which is what "reveal" means - and it stays revealed
     * until somebody says otherwise, because that is how a revealed card behaves while people
     * read it and argue about it.
     *
     * <p>A count of zero puts it back face down, so stopping is the same verb rather than a
     * second one that could get out of step with the first.
     */
    record LibraryRevealed(SeatId actor, SeatId seat, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return count > 0
                    ? LogLine.of(oneOrMany("log.gathering.library_revealed_one",
                            "log.gathering.library_revealed", count), actor, seat, count)
                    : LogLine.of("log.gathering.library_unrevealed", actor, seat);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            return count > 0;
        }
    }

    /** The looking half of scry and surveil, before anything is decided. */
    record LibraryLooked(SeatId actor, SeatId seat, int count) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(oneOrMany("log.gathering.library_looked_one",
                    "log.gathering.library_looked", count), actor, seat, count);
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

        /** As many as the payload allows, and no other door lets in more. */
        public static final int MOST_AT_ONCE = 32;

        public TokenCreated {
            // Clamped on the event, not only on the typed payload: an event also arrives
            // through the raw codec, and each token copies the whole board's maps - an
            // unbounded count was a request to hang the server thread inside one fold.
            count = Math.max(1, Math.min(MOST_AT_ONCE, count));
        }

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

    /**
     * Blank stock, put on the table with something written on it.
     *
     * <p>Magic keeps printing table states that are not cards - the monarch, the initiative,
     * the ring tempting you, whatever the set after this one calls its version - and a table
     * tracks every one of them by putting an object down and writing on it. Guessing at each
     * mechanic in turn would be a feature that is always one set behind; blank stock and a
     * pen is never behind at all. See {@link PaperStock}.
     *
     * <p>It arrives as a token, which is exactly what it is: a thing that exists for this
     * game and is thrown away afterwards. So it is removed with the same verb, it goes in the
     * bin at session end with the other tokens, and nobody has to have written code deciding
     * what happens to the monarch when everybody stands up.
     *
     * <p>What is written on it is a note, cleaned by the same rule every other note is - it
     * is drawn across a card and read by everybody, so it is one line of ordinary letters.
     * Rewriting it later is the pen, not this: a card already on the table is written on with
     * {@link CardNoted}, which is why "I have the monarch" can become "Chris has the monarch"
     * without anybody making a second emblem.
     */
    record PaperCardCreated(SeatId actor, SeatId seat, PaperStock stock, String text) implements GameEvent {

        public PaperCardCreated {
            stock = stock == null ? PaperStock.BLANK : stock;
            text = CardNote.clean(text);
        }

        @Override
        public LogLine describe(GameState before) {
            // Empty rather than null: a log line's arguments are an immutable list, which a
            // null would refuse to go into - and a blank card with nothing on it yet is a
            // perfectly ordinary thing to put down and write on after.
            String written = text == null ? "" : text;
            return LogLine.of(
                    stock == PaperStock.EMBLEM
                            ? "log.gathering.emblem_created"
                            : "log.gathering.paper_created",
                    actor, seat, written);
        }
    }

    /**
     * Turning your hand round so somebody can read it.
     *
     * <p>Always your own hand. Magic is full of cards that make an opponent reveal their hand
     * - Duress, Thoughtseize, Peek - and at a real table every one of them is resolved by the
     * person being Duressed picking their hand up and turning it toward you. That is exactly
     * what this is, and it is why nothing anybody else submits can open somebody's hand: the
     * one thing the mod ever says no to is an action that would show the actor a card they are
     * not entitled to, and "make that player reveal their hand" is that action written out in
     * full. See {@link dev.gathering.core.game.Authorization}.
     *
     * <p>A state rather than a moment. A hand shown is a hand that stays shown until it is
     * taken back, because that is how it works when you put it face up on the table - and
     * because a one-off reveal would be a screenful of somebody else's cards that vanished
     * before they had read them.
     *
     * @param to the seat being shown, or null for the whole table at once
     * @param showing whether it is being turned toward them or away again
     */
    record HandShown(SeatId actor, SeatId to, boolean showing) implements GameEvent {

        /** Whether this is the whole table rather than one player. */
        public boolean everybody() {
            return to == null;
        }

        @Override
        public LogLine describe(GameState before) {
            if (everybody()) {
                return LogLine.of(showing
                        ? "log.gathering.hand_shown_all" : "log.gathering.hand_hidden_all", actor);
            }
            return LogLine.of(showing
                    ? "log.gathering.hand_shown" : "log.gathering.hand_hidden", actor, to);
        }

        @Override
        public boolean revealsInformation(GameState before) {
            // Only the turning toward. Taking a hand back shows nobody anything, and an undo
            // that had to be agreed on unanimously just because somebody stopped revealing
            // would be a boundary in the wrong direction.
            return showing;
        }
    }

    // --------------------------------------------------------- player verbs

    /**
     * Poison, energy, experience, or anything else somebody is keeping count of beside a seat.
     *
     * <p>Distinct from {@link CounterChanged}, which is about a card. A counter on a player and
     * a counter on a permanent are different things that happen to share a word, and the log
     * has to be able to say which - "three poison" and "three +1/+1 counters on the bear" are
     * not the same sentence.
     */
    record SeatCounterChanged(SeatId actor, SeatId seat, String counter, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.seat_counter_changed", actor, seat, counter, delta);
        }
    }

    record LifeChanged(SeatId actor, SeatId seat, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.life_changed", actor, seat, delta);
        }
    }

    /**
     * Commander damage, recorded against the commander that dealt it.
     *
     * <p>The card and not the seat, because the rule is twenty-one from the <em>same</em>
     * commander and a partner deck fields two. One number per enemy seat could not tell
     * Halana's damage from Tevesh's - which is the pair the rule exists to separate, and the
     * deck this project's own brief was written around.
     */
    record CommanderDamageChanged(SeatId actor, SeatId seat, CardInstanceId commander, int delta)
            implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.commander_damage",
                    actor, seat, CardRef.publicRefFor(before, commander), delta);
        }
    }

    /** Commander tax as a displayed number. Nothing charges it; players do their own arithmetic. */
    record CommanderTaxChanged(SeatId actor, SeatId seat, CardInstanceId commander, int delta) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.commander_tax", actor, seat, CardRef.publicRefFor(before, commander), delta);
        }
    }

    /**
     * Which of two keys to write a line under, by how many of a thing it is about.
     *
     * <p>"Drew 1 cards" is the most common line a game of this produces, because drawing one
     * card is the most common thing anybody does, and it is wrong every time. Minecraft's
     * translations have no plural rule, so the line picks its own key and each language writes
     * both - which is also the only way this works for languages whose plural is not English's.
     */
    static String oneOrMany(String one, String many, int count) {
        return count == 1 ? one : many;
    }

    /** Conceding is a player's own decision and the only thing close to a game result here. */
    record Conceded(SeatId actor) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.conceded", actor);
        }
    }

    // ---------------------------------------------------------- table verbs

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

    /**
     * A die rolled where the whole table can see it.
     *
     * <p>Magic asks for one often enough to deserve a verb - a d20 on half of Adventures in
     * the Forgotten Realms, a d6 on a Sarkhan, a d4 somewhere in between - and a die nobody
     * else watched is not a die, it is a claim. So the server rolls it, everyone is told the
     * number, and the log keeps it under the name of whoever asked.
     *
     * <p>The result is carried rather than the seed: a roll is one number that happened once,
     * and re-deriving it on each client would be a second implementation of chance that could
     * disagree with the first.
     */
    record DiceRolled(SeatId actor, int sides, int result) implements GameEvent {

        /** A d20 is the biggest die Magic prints on a card. */
        public static final int MOST_SIDES = 20;

        public DiceRolled {
            // Clamped on the event, so the raw codec is no way round it either. A die with
            // no sides has no roll to report, and one with two billion is a number nobody
            // asked for on a card nobody printed.
            sides = Math.max(1, Math.min(MOST_SIDES, sides));
            result = Math.max(1, Math.min(sides, result));
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of("log.gathering.rolled", actor, sides, result);
        }
    }

    /**
     * A coin flipped where the whole table can see it.
     *
     * <p>Its own verb rather than a two-sided die, because Magic's own words are heads and
     * tails - a card says "flip a coin", never "roll a d2", and Krark's Thumb cares which one
     * you did. A log line that said "rolled a 1" would be true and useless.
     */
    record CoinFlipped(SeatId actor, boolean heads) implements GameEvent {
        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(heads ? "log.gathering.flipped_heads" : "log.gathering.flipped_tails",
                    actor);
        }
    }

    /**
     * A planar die rolled where the whole table can see it.
     *
     * <p>Its own verb rather than a d6, because its faces are symbols: four blanks, a chaos and
     * a planeswalk, and "rolled a 3" would be a true sentence about the wrong thing. The same
     * reason a coin is heads and tails here.
     *
     * <p>The rest of Planechase is not here and is not planned - a planar deck is owned by the
     * table and every zone in this game is owned by a seat - but a group can play it by hand,
     * and this is the part of it that cannot honestly be played by hand: a player rolling their
     * own planar die is a player claiming a chaos symbol.
     */
    record PlanarRolled(SeatId actor, PlanarFace face) implements GameEvent {

        public PlanarRolled {
            face = face == null ? PlanarFace.BLANK : face;
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(switch (face) {
                case CHAOS -> "log.gathering.planar_chaos";
                case PLANESWALK -> "log.gathering.planar_walk";
                case BLANK -> "log.gathering.planar_blank";
            }, actor);
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
            case LibraryMilled milled -> Optional.of(milled.seat());
            case LibraryExiled exiled -> Optional.of(exiled.seat());
            case LibraryRevealed revealed -> Optional.of(revealed.seat());
            case LibraryReordered reordered -> Optional.of(reordered.seat());
            case Surveiled surveiled -> Optional.of(surveiled.seat());
            case SeatUntappedAll untapped -> Optional.of(untapped.seat());
            case LifeChanged life -> Optional.of(life.seat());
            case SeatCounterChanged counter -> Optional.of(counter.seat());
            case CommanderDamageChanged damage -> Optional.of(damage.seat());
            case CommanderTaxChanged tax -> Optional.of(tax.seat());
            case TokenCreated token -> Optional.of(token.seat());
            case PaperCardCreated paper -> Optional.of(paper.seat());
            case TokenCopyCreated copy -> Optional.of(copy.seat());
            case CardMoved moved -> Optional.of(moved.to().seat());
            case ZoneMoved moved -> Optional.of(moved.seat());
            default -> Optional.empty();
        };
    }

    /** Whether this event only ever concerns a hidden zone, for the persistence split. */
    static boolean touchesHiddenZone(GameEvent event, GameState before) {
        return switch (event) {
            case CardMoved moved -> moved.to().zone().isHidden()
                    || before.locationOf(moved.card()).map(ZoneRef::isHidden).orElse(false);
            case ZoneMoved moved -> moved.to().zone().isHidden() || moved.fromRef().isHidden();
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
            case LibraryMilled ignored -> List.of(Zone.LIBRARY, Zone.GRAVEYARD);
            case LibraryExiled ignored -> List.of(Zone.LIBRARY, Zone.EXILE);
            case LibraryRevealed ignored -> List.of(Zone.LIBRARY);
            case LibraryShuffled ignored -> List.of(Zone.LIBRARY);
            case LibrarySearched ignored -> List.of(Zone.LIBRARY);
            case LibraryLooked ignored -> List.of(Zone.LIBRARY);
            case LibraryReordered ignored -> List.of(Zone.LIBRARY);
            case Surveiled ignored -> List.of(Zone.LIBRARY, Zone.GRAVEYARD);
            case CardMoved moved -> List.of(moved.to().zone());
            case ZoneMoved moved -> List.of(moved.from(), moved.to().zone());
            default -> List.of();
        };
    }
}
