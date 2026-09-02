package dev.gathering.core.game;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies one event to the board.
 * <p>The whole of the game's mechanics, in one exhaustive switch. Adding an event to
 * {@link GameEvent} and forgetting to fold it is a compile error rather than a silent
 * no-op, which is the reason this is a switch over a sealed hierarchy and not a method on
 * each event.
 * <p><b>Nothing here enforces a rule.</b> Drawing from an empty library draws nothing rather
 * than failing; tapping a tapped card leaves it tapped; life goes negative; counters go
 * negative. The only things this refuses are structurally impossible - naming a card that is
 * not in the session at all - because corrupting the board is not the same as allowing a
 * misplay, and misplays are allowed.
 */
public final class GameFold {

    private GameFold() {
    }

    /** The whole log, folded from an empty table. Undone entries are skipped, never removed. */
    public static GameState fold(GameState initial, List<GameEvent> events, SessionSeed seed) {
        GameState state = initial;
        for (GameEvent event : events) {
            state = apply(state, event, seed);
        }
        return state;
    }

    public static GameState apply(GameState state, GameEvent event, SessionSeed seed) {
        return switch (event) {
            case GameEvent.SeatTaken taken ->
                    state.withSeatState(state.seatState(taken.actor()).occupiedBy(taken.player()));

            case GameEvent.SeatReleased released ->
                    state.withSeatState(state.seatState(released.actor()).released());

            case GameEvent.DeckLoaded loaded -> loadDeck(state, loaded);

            case GameEvent.SessionEnded ignored -> state.asEnded();

            case GameEvent.CardMoved moved -> movedCard(state, moved);

            case GameEvent.ZoneMoved moved -> moveZone(state, moved);

            case GameEvent.CardTapSet tap ->
                    state.withCard(state.requireCard(tap.card()).withTapped(tap.tapped()));

            case GameEvent.CardNoted noted ->
                    state.withCard(state.requireCard(noted.card()).withNote(noted.note()));

            case GameEvent.CardStrengthSet written ->
                    state.withCard(state.requireCard(written.card()).withStrength(written.strength()));

            case GameEvent.HandSorted sorted -> sortHand(state, sorted);

            case GameEvent.CardFrozen froze ->
                    state.withCard(state.requireCard(froze.card()).frozen(froze.frozen()));

            case GameEvent.CardTurnedOver turned -> state.withCard(
                    state.requireCard(turned.card()).turnedOver(turned.showingTheOtherSide()));

            case GameEvent.CardRotated rotated -> rotate(state, rotated);

            case GameEvent.CardAttached attached -> attach(state, attached);

            case GameEvent.SeatUntappedAll untapped -> untapAll(state, untapped.seat());

            case GameEvent.CardFacingSet facing -> setFacing(state, facing, seed);

            case GameEvent.CardsDrawn drawn -> draw(state, drawn.seat(), drawn.count());

            case GameEvent.Mulliganed mulligan -> mulligan(state, mulligan, seed);

            case GameEvent.LibraryShuffled shuffled -> shuffleLibrary(state, shuffled.seat(), seed);

            // Looking moves nothing. Taking a card afterwards is a separate move, and that
            // separation is what lets the log say "searched" without saying "found". What it
            // does do is open the library to the one seat doing the looking, which is the
            // only way a library is ever anything but a number.
            case GameEvent.LibrarySearched searched ->
                    state.withPeek(searched.actor(), Peek.search(searched.seat()));

            case GameEvent.LibraryLooked looked ->
                    state.withPeek(looked.actor(), Peek.top(looked.seat(), looked.count()));

            case GameEvent.LibraryClosed closed -> state.withoutPeekBy(closed.actor());

            case GameEvent.LibraryMilled milled ->
                    topInto(state, milled.seat(), Zone.GRAVEYARD, milled.count());

            case GameEvent.LibraryExiled exiled ->
                    topInto(state, exiled.seat(), Zone.EXILE, exiled.count());

            case GameEvent.LibraryRevealed revealed ->
                    state.withRevealed(revealed.seat(), revealed.count());

            case GameEvent.CardPinged ignored -> state;

            // A roll and a flip change no board. They are things that happened, which the log
            // is for - the number is in the event, so a re-fold reports the same roll rather
            // than a fresh one, and undo takes back the asking rather than rewriting chance.
            case GameEvent.DiceRolled ignored -> state;

            case GameEvent.CoinFlipped ignored -> state;

            case GameEvent.PlanarRolled ignored -> state;

            case GameEvent.LibraryReordered reordered -> reorderLibrary(state, reordered);

            case GameEvent.Surveiled surveiled -> surveil(state, surveiled);

            case GameEvent.CounterChanged counter -> state.withCard(
                    state.requireCard(counter.card()).withCounter(counter.counter(), counter.delta()));

            case GameEvent.TokenCreated token -> createTokens(state, token);

            case GameEvent.PaperCardCreated paper -> putPaperDown(state, paper);

            case GameEvent.HandShown shown -> showHand(state, shown);

            case GameEvent.TokenCopyCreated copy -> createCopy(state, copy);

            case GameEvent.TokenRemoved removed -> state.removeCard(removed.card());

            case GameEvent.SeatCounterChanged counter -> state.withSeatState(
                    state.seatState(counter.seat()).withCounter(counter.counter(), counter.delta()));

            case GameEvent.LifeChanged life ->
                    state.withSeatState(state.seatState(life.seat()).withLife(life.delta()));

            case GameEvent.CommanderDamageChanged damage -> state.withSeatState(
                    state.seatState(damage.seat()).withCommanderDamage(damage.commander(), damage.delta()));

            case GameEvent.CommanderTaxChanged tax -> state.withSeatState(
                    state.seatState(tax.seat()).withCommanderTax(tax.commander(), tax.delta()));

            case GameEvent.Conceded conceded ->
                    state.withSeatState(state.seatState(conceded.actor()).withConcede());


            case GameEvent.TurnPassed passed -> state.withTurn(state.turn().passTo(passed.toSeat()));
        };
    }

    // ------------------------------------------------------------- lifecycle

    private static GameState loadDeck(GameState state, GameEvent.DeckLoaded loaded) {
        SeatId seat = loaded.actor();
        GameState updated = state;
        int nextId = state.nextCardId();

        // The library keeps decklist order until something shuffles it, which is the honest
        // starting point: an unshuffled deck is an unshuffled deck.
        for (CardIdentity identity : loaded.library()) {
            CardInstance card = CardInstance.faceUp(CardInstanceId.of(nextId++), identity, seat);
            updated = updated.addCard(card, ZoneRef.of(seat, Zone.LIBRARY), Placement.BOTTOM);
        }
        // One commander to a slot, in the order the deck named them. A deck with partners, a
        // background or a Doctor's companion has two cards that each start in the command
        // zone and are each cast on their own tax, and a single pile holding both made them a
        // stack of two under one number - which is the one thing about a command zone
        // anybody actually reads. Anything past the slots joins the last one rather than
        // being dropped: a deck that names three commanders is a deck somebody built wrong,
        // and losing a card is a worse answer than showing it.
        List<Zone> slots = Zone.COMMAND_SLOTS;
        List<CardInstanceId> commanders = new ArrayList<>(loaded.commanders().size());
        for (int index = 0; index < loaded.commanders().size(); index++) {
            CardInstance card = CardInstance.faceUp(
                    CardInstanceId.of(nextId++), loaded.commanders().get(index), seat);
            Zone slot = slots.get(Math.min(index, slots.size() - 1));
            updated = updated.addCard(card, ZoneRef.of(seat, slot), Placement.BOTTOM);
            commanders.add(card.id());
        }
        // Named on the seat, once, because a commander on the battlefield is still the
        // commander and nothing about its zone says so. This is what lets damage be recorded
        // against the card that dealt it wherever that card happens to be standing.
        // Named on the seat, and sleeved on the seat, for the same reason: both are facts
        // about what this player brought rather than about where any card is.
        updated = updated.withSeatState(
                updated.seatState(seat).withCommanders(commanders).withSleeve(loaded.sleeve()));
        return updated.withNextCardId(nextId);
    }

    // ------------------------------------------------------------ card verbs

    /**
     * Turns a card where it stands.
     * <p>A card in a pile has no angle to turn, and silently staying put is the right answer:
     * the alternative is a client that clicked a beat after somebody scooped the card losing
     * the whole session to an exception.
     */
    private static GameState rotate(GameState state, GameEvent.CardRotated event) {
        CardInstance card = state.requireCard(event.card());
        return card.placedAt()
                .map(where -> state.withCard(card.withPosition(where.rotatedTo(event.rotation()))))
                .orElse(state);
    }

    /**
     * Puts a card onto another one, or takes it off.
     * <p>Three arrangements are refused rather than drawn: a card on itself, a card on
     * something in a pile, and a card on something that is itself on a third card. The first
     * two cannot be drawn at all; the third is a chain nobody plays and the only remaining way
     * to make a loop out of this.
     * <p>Refusing here means leaving the board alone, not throwing. A stale click - the host
     * went to the graveyard a moment ago - should do nothing rather than end the session.
     */
    private static GameState attach(GameState state, GameEvent.CardAttached event) {
        CardInstance card = state.requireCard(event.card());
        if (event.host() == null) {
            return state.withCard(card.attachedToCard(null));
        }
        CardInstance host = state.card(event.host()).orElse(null);
        if (host == null || host.id().equals(card.id()) || host.isAttached() || host.position() == null) {
            return state;
        }
        return state.withCard(card.attachedToCard(host.id()));
    }

    /**
     * Untap everything, except what somebody has said does not untap.
     * <p>The one place frozen means anything. A card is frozen because an effect said it does
     * not untap during its controller's untap step, and the whole reason to record that on the
     * card is this moment - untapping everything is one press, done every turn without
     * looking, and it is exactly the press that forgets. Nothing here decides when a card
     * stops being frozen: a player took the freeze off with the same menu they put it on with,
     * because there is no rules engine and there never will be.
     */
    private static GameState untapAll(GameState state, SeatId seat) {
        GameState updated = state;
        for (CardInstanceId id : state.contents(seat, Zone.BATTLEFIELD)) {
            CardInstance card = state.requireCard(id);
            if (card.tapped() && !card.frozen()) {
                updated = updated.withCard(card.withTapped(false));
            }
        }
        return updated;
    }

    private static GameState setFacing(GameState state, GameEvent.CardFacingSet event, SessionSeed seed) {
        CardInstance card = state.requireCard(event.card());
        if (event.facing() == Facing.FACE_UP) {
            return state.withCard(card.faceUp());
        }
        // A fresh marker every time a card goes face down, so two separate face-down periods
        // cannot be correlated into one card by anyone watching.
        int ordinal = state.markerOrdinal();
        return state.withCard(card.faceDownWith(seed.marker(ordinal))).withMarkerOrdinal(ordinal + 1);
    }

    /**
     * A single card moved by hand, with one consequence the move drags behind it: a card
     * leaving a library takes the revealed-top window with it.
     * <p>The revealed count is positional - "the first N of this library are face up" - and
     * the visibility rules hand exactly that many identities to everybody. Moving a revealed
     * card out (a cascade taking its hit, a reveal-until fetch) slid the window down onto a
     * card nobody had ever revealed, and the whole table saw it. Mill and shuffle already
     * cleared it; this is the same rule at the move everybody actually performs.
     */
    private static GameState movedCard(GameState state, GameEvent.CardMoved moved) {
        ZoneRef from = state.locationOf(moved.card()).orElse(null);
        GameState updated = state.place(moved.card(), moved.to(), moved.placement());
        if (from != null && from.zone() == Zone.LIBRARY && !from.equals(moved.to())) {
            updated = updated.withRevealed(from.seat(), 0);
        }
        return updated;
    }

    private static GameState draw(GameState state, SeatId seat, int count) {
        ZoneRef library = ZoneRef.of(seat, Zone.LIBRARY);
        ZoneRef hand = ZoneRef.of(seat, Zone.HAND);
        GameState updated = state;
        // Drawing from an empty library draws nothing. Whether that means the player has lost
        // is a rules question, and there is no rules engine here to answer it.
        boolean any = false;
        for (int drawn = 0; drawn < count; drawn++) {
            List<CardInstanceId> contents = updated.contents(library);
            if (contents.isEmpty()) {
                break;
            }
            updated = updated.place(contents.get(0), hand, Placement.BOTTOM);
            any = true;
        }
        // The card that was revealed on top just left; the one now on top was never shown.
        // Mill clears this the same way, and for the same reason.
        return any ? updated.withRevealed(seat, 0) : updated;
    }

    /**
     * Cards off the top of a library into the graveyard.
     * <p>Milling an empty library mills nothing. Whether that means the player has lost is a
     * rules question, and there is no rules engine here to answer it - the same answer drawing
     * from an empty library gets.
     */
    private static GameState topInto(GameState state, SeatId seat, Zone pile, int count) {
        ZoneRef library = ZoneRef.of(seat, Zone.LIBRARY);
        ZoneRef into = ZoneRef.of(seat, pile);
        GameState updated = state;
        for (int moved = 0; moved < count; moved++) {
            List<CardInstanceId> contents = updated.contents(library);
            if (contents.isEmpty()) {
                break;
            }
            updated = updated.place(contents.get(0), into, Placement.TOP);
        }
        // Whatever was revealed off the top is not on top any more.
        return count > 0 ? updated.withRevealed(seat, 0) : updated;
    }

    /**
     * Empties one zone into another, keeping the order the cards were in.
     * <p>Read once, up front, rather than re-read each time round: the list being walked is
     * the list being emptied, and a zone moving onto itself - which a misaimed drag can ask
     * for - would otherwise never run out of cards to move.
     */
    private static GameState moveZone(GameState state, GameEvent.ZoneMoved moved) {
        List<CardInstanceId> contents = state.contents(moved.seat(), moved.from());
        if (contents.isEmpty() || moved.fromRef().equals(moved.to())) {
            return state;
        }
        GameState updated = state;
        // Bottom first when they are going onto a top, so the card that was on top of the
        // graveyard is on top of the library when it gets there.
        List<CardInstanceId> order = new ArrayList<>(contents);
        if (moved.placement().isTop()) {
            java.util.Collections.reverse(order);
        }
        for (CardInstanceId card : order) {
            updated = updated.place(card, moved.to(), moved.placement());
        }
        // Whatever was face up off the top of a library has left it.
        return moved.from() == Zone.LIBRARY ? updated.withRevealed(moved.seat(), 0) : updated;
    }

    private static GameState mulligan(GameState state, GameEvent.Mulliganed event, SessionSeed seed) {
        SeatId seat = event.seat();
        ZoneRef library = ZoneRef.of(seat, Zone.LIBRARY);
        GameState updated = state;
        for (CardInstanceId id : state.contents(seat, Zone.HAND)) {
            updated = updated.place(id, library, Placement.BOTTOM);
        }
        updated = shuffleLibrary(updated, seat, seed);
        return draw(updated, seat, event.newHandSize());
    }

    // ------------------------------------------------------------ pile verbs

    /**
     * A shuffle, and the end of anybody's look at that library.
     * <p>Closing the look is not tidiness. Whatever somebody had open is no longer in front of
     * them once the order has changed, and leaving it open would keep sending them a library
     * they are not looking at any more - which is the same leak as never having closed it.
     */
    private static GameState shuffleLibrary(GameState state, SeatId seat, SessionSeed seed) {
        ZoneRef library = ZoneRef.of(seat, Zone.LIBRARY);
        int ordinal = state.shuffleOrdinal();
        List<CardInstanceId> shuffled = seed.shuffle(state.contents(library), seat, ordinal);
        return state.withZone(library, shuffled)
                .withShuffleOrdinal(ordinal + 1)
                .withoutPeeksAt(seat)
                .withRevealed(seat, 0);
    }

    private static GameState reorderLibrary(GameState state, GameEvent.LibraryReordered event) {
        ZoneRef library = ZoneRef.of(event.seat(), Zone.LIBRARY);
        // Only cards the library really holds, exactly as sortHand does and for the same
        // reason - the decision arrives from a client that worked it out a moment ago, and a
        // card can have been drawn since. Without the filter the list also accepted ids the
        // library NEVER held: a card in two zones at once from an ordinary race, or, from a
        // crafted event, somebody else's card quietly written into this library.
        List<CardInstanceId> onTop = keptOf(state.contents(library), event.onTop());
        List<CardInstanceId> toBottom = keptOf(state.contents(library), event.toBottom());
        List<CardInstanceId> rest = remaining(state.contents(library), onTop, toBottom);

        List<CardInstanceId> updated = new ArrayList<>(onTop);
        updated.addAll(rest);
        updated.addAll(toBottom);
        // Deciding is the end of looking: the cards have been put back and the decision made.
        // And whatever was revealed off the top has been rearranged out from under the
        // count, so the count goes too.
        return state.withZone(library, updated)
                .withRevealed(event.seat(), 0)
                .withoutPeekBy(event.actor());
    }

    /**
     * Puts a hand in the order the client asked for.
     * <p>Asked for, not obeyed. The order arrives from a client that worked it out a moment
     * ago, and a card can have been drawn or played since - so what is named and really there
     * goes first, in the order named, and whatever is there but was not named keeps its place
     * behind it. A hand cannot lose a card to a stale sort and cannot gain one from a made-up
     * list, whatever arrives.
     */
    private static GameState sortHand(GameState state, GameEvent.HandSorted event) {
        ZoneRef hand = ZoneRef.of(event.seat(), Zone.HAND);
        List<CardInstanceId> now = state.contents(hand);
        Set<CardInstanceId> placed = new LinkedHashSet<>();
        for (CardInstanceId card : event.order()) {
            if (now.contains(card)) {
                placed.add(card);
            }
        }
        for (CardInstanceId card : now) {
            placed.add(card);
        }
        return state.withZone(hand, List.copyOf(placed));
    }

    private static GameState surveil(GameState state, GameEvent.Surveiled event) {
        ZoneRef library = ZoneRef.of(event.seat(), Zone.LIBRARY);
        // The same membership filter as reorderLibrary, and it matters more here: the
        // graveyard half MOVES cards, so an unchecked id was a card conjured into a public
        // zone - which the broadcast then could not describe - or somebody else's card
        // walked off their battlefield by an event whose log line names no card.
        List<CardInstanceId> onTop = keptOf(state.contents(library), event.onTop());
        List<CardInstanceId> toGraveyard = keptOf(state.contents(library), event.toGraveyard());
        List<CardInstanceId> rest = remaining(state.contents(library), onTop, toGraveyard);

        List<CardInstanceId> updated = new ArrayList<>(onTop);
        updated.addAll(rest);
        GameState result = state.withZone(library, updated);

        ZoneRef graveyard = ZoneRef.of(event.seat(), Zone.GRAVEYARD);
        for (CardInstanceId id : toGraveyard) {
            result = result.place(id, graveyard, Placement.TOP);
        }
        // The top the table was shown has been rearranged; the count over it is stale.
        return result.withRevealed(event.seat(), 0).withoutPeekBy(event.actor());
    }

    /** The named cards that are really in the zone, in the order named. */
    private static List<CardInstanceId> keptOf(
            List<CardInstanceId> zone, List<CardInstanceId> named) {
        List<CardInstanceId> kept = new ArrayList<>(named.size());
        for (CardInstanceId id : named) {
            if (zone.contains(id) && !kept.contains(id)) {
                kept.add(id);
            }
        }
        return kept;
    }

    /** The library minus the cards the player made a decision about, order preserved. */
    private static List<CardInstanceId> remaining(
            List<CardInstanceId> library, List<CardInstanceId> first, List<CardInstanceId> second) {
        Set<CardInstanceId> decided = new LinkedHashSet<>(first);
        decided.addAll(second);
        List<CardInstanceId> rest = new ArrayList<>(library.size());
        for (CardInstanceId id : library) {
            if (!decided.contains(id)) {
                rest.add(id);
            }
        }
        return rest;
    }

    // ---------------------------------------------------------------- tokens

    private static GameState createTokens(GameState state, GameEvent.TokenCreated event) {
        GameState updated = state;
        int nextId = state.nextCardId();
        ZoneRef battlefield = ZoneRef.of(event.seat(), Zone.BATTLEFIELD);
        for (int made = 0; made < event.count(); made++) {
            CardInstance token = CardInstance.token(CardInstanceId.of(nextId++), event.identity(), event.seat());
            updated = updated.addCard(token, battlefield, Placement.BOTTOM);
        }
        return updated.withNextCardId(nextId);
    }

    /**
     * Turns a hand toward one seat, or toward the whole table, or away again.
     * <p>The table means every other seat as it stands now, written out one by one rather than
     * kept as a flag. A player who shows the table and then shows one more person has done two
     * things that mean the same thing, and a board that recorded them as two different kinds
     * of state would have to decide which one wins.
     */
    private static GameState showHand(GameState state, GameEvent.HandShown event) {
        SeatState seat = state.seatState(event.actor());
        if (!event.everybody()) {
            return state.withSeatState(seat.withHandShownTo(event.to(), event.showing()));
        }
        if (!event.showing()) {
            return state.withSeatState(seat.withHandShownTo(java.util.Set.of()));
        }
        java.util.Set<SeatId> everyone = new java.util.LinkedHashSet<>(state.seats());
        everyone.remove(event.actor());
        return state.withSeatState(seat.withHandShownTo(everyone));
    }

    /**
     * Blank stock on the table, with whatever was written on it already written.
     * <p>Built before it is put down rather than added and then written on, so a card never
     * exists in a board anybody could see without the words that are the entire reason it is
     * there. A token, because that is what it is - see {@link GameEvent.PaperCardCreated}.
     */
    private static GameState putPaperDown(GameState state, GameEvent.PaperCardCreated event) {
        int nextId = state.nextCardId();
        CardInstance paper = CardInstance
                .token(CardInstanceId.of(nextId), event.stock().identity(), event.seat())
                .withNote(event.text());
        return state
                .addCard(paper, ZoneRef.of(event.seat(), Zone.BATTLEFIELD), Placement.BOTTOM)
                .withNextCardId(nextId + 1);
    }

    private static GameState createCopy(GameState state, GameEvent.TokenCopyCreated event) {
        CardInstance source = state.requireCard(event.source());
        int nextId = state.nextCardId();
        CardInstance copy = CardInstance.token(CardInstanceId.of(nextId), source.identity(), event.seat());
        return state
                .addCard(copy, ZoneRef.of(event.seat(), Zone.BATTLEFIELD), Placement.BOTTOM)
                .withNextCardId(nextId + 1);
    }
}
