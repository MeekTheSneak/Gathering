package dev.gathering.core.game;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies one event to the board.
 *
 * <p>The whole of the game's mechanics, in one exhaustive switch. Adding an event to
 * {@link GameEvent} and forgetting to fold it is a compile error rather than a silent
 * no-op, which is the reason this is a switch over a sealed hierarchy and not a method on
 * each event.
 *
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

            case GameEvent.CardMoved moved -> state.place(moved.card(), moved.to(), moved.placement());

            case GameEvent.ZoneMoved moved -> moveZone(state, moved);

            case GameEvent.CardTapSet tap ->
                    state.withCard(state.requireCard(tap.card()).withTapped(tap.tapped()));

            case GameEvent.CardNoted noted ->
                    state.withCard(state.requireCard(noted.card()).withNote(noted.note()));

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

            case GameEvent.LibraryMilled milled -> mill(state, milled.seat(), milled.count());

            case GameEvent.LibraryRevealed revealed ->
                    state.withRevealed(revealed.seat(), revealed.count());

            case GameEvent.CardPinged ignored -> state;

            case GameEvent.LibraryReordered reordered -> reorderLibrary(state, reordered);

            case GameEvent.Surveiled surveiled -> surveil(state, surveiled);

            case GameEvent.CounterChanged counter -> state.withCard(
                    state.requireCard(counter.card()).withCounter(counter.counter(), counter.delta()));

            case GameEvent.TokenCreated token -> createTokens(state, token);

            case GameEvent.TokenCopyCreated copy -> createCopy(state, copy);

            case GameEvent.TokenRemoved removed -> state.removeCard(removed.card());

            case GameEvent.SeatCounterChanged counter -> state.withSeatState(
                    state.seatState(counter.seat()).withCounter(counter.counter(), counter.delta()));

            case GameEvent.LifeChanged life ->
                    state.withSeatState(state.seatState(life.seat()).withLife(life.delta()));

            case GameEvent.CommanderDamageChanged damage -> state.withSeatState(
                    state.seatState(damage.toSeat()).withCommanderDamage(damage.fromSeat(), damage.delta()));

            case GameEvent.CommanderTaxChanged tax -> state.withSeatState(
                    state.seatState(tax.seat()).withCommanderTax(tax.commander(), tax.delta()));

            case GameEvent.Conceded conceded ->
                    state.withSeatState(state.seatState(conceded.actor()).withConcede());

            case GameEvent.PhaseSet phase -> state.withTurn(state.turn().withPhase(phase.phase()));

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
        for (int index = 0; index < loaded.commanders().size(); index++) {
            CardInstance card = CardInstance.faceUp(
                    CardInstanceId.of(nextId++), loaded.commanders().get(index), seat);
            Zone slot = slots.get(Math.min(index, slots.size() - 1));
            updated = updated.addCard(card, ZoneRef.of(seat, slot), Placement.BOTTOM);
        }
        return updated.withNextCardId(nextId);
    }

    // ------------------------------------------------------------ card verbs

    /**
     * Turns a card where it stands.
     *
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
     *
     * <p>Three arrangements are refused rather than drawn: a card on itself, a card on
     * something in a pile, and a card on something that is itself on a third card. The first
     * two cannot be drawn at all; the third is a chain nobody plays and the only remaining way
     * to make a loop out of this.
     *
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

    private static GameState untapAll(GameState state, SeatId seat) {
        GameState updated = state;
        for (CardInstanceId id : state.contents(seat, Zone.BATTLEFIELD)) {
            CardInstance card = state.requireCard(id);
            if (card.tapped()) {
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

    private static GameState draw(GameState state, SeatId seat, int count) {
        ZoneRef library = ZoneRef.of(seat, Zone.LIBRARY);
        ZoneRef hand = ZoneRef.of(seat, Zone.HAND);
        GameState updated = state;
        // Drawing from an empty library draws nothing. Whether that means the player has lost
        // is a rules question, and there is no rules engine here to answer it.
        for (int drawn = 0; drawn < count; drawn++) {
            List<CardInstanceId> contents = updated.contents(library);
            if (contents.isEmpty()) {
                break;
            }
            updated = updated.place(contents.get(0), hand, Placement.BOTTOM);
        }
        return updated;
    }

    /**
     * Cards off the top of a library into the graveyard.
     *
     * <p>Milling an empty library mills nothing. Whether that means the player has lost is a
     * rules question, and there is no rules engine here to answer it - the same answer drawing
     * from an empty library gets.
     */
    private static GameState mill(GameState state, SeatId seat, int count) {
        ZoneRef library = ZoneRef.of(seat, Zone.LIBRARY);
        ZoneRef graveyard = ZoneRef.of(seat, Zone.GRAVEYARD);
        GameState updated = state;
        for (int milled = 0; milled < count; milled++) {
            List<CardInstanceId> contents = updated.contents(library);
            if (contents.isEmpty()) {
                break;
            }
            updated = updated.place(contents.get(0), graveyard, Placement.TOP);
        }
        // Whatever was revealed off the top is not on top any more.
        return count > 0 ? updated.withRevealed(seat, 0) : updated;
    }

    /**
     * Empties one zone into another, keeping the order the cards were in.
     *
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
     *
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
        List<CardInstanceId> rest = remaining(state.contents(library), event.onTop(), event.toBottom());

        List<CardInstanceId> updated = new ArrayList<>(event.onTop());
        updated.addAll(rest);
        updated.addAll(event.toBottom());
        // Deciding is the end of looking: the cards have been put back and the decision made.
        return state.withZone(library, updated).withoutPeekBy(event.actor());
    }

    private static GameState surveil(GameState state, GameEvent.Surveiled event) {
        ZoneRef library = ZoneRef.of(event.seat(), Zone.LIBRARY);
        List<CardInstanceId> rest = remaining(state.contents(library), event.onTop(), event.toGraveyard());

        List<CardInstanceId> updated = new ArrayList<>(event.onTop());
        updated.addAll(rest);
        GameState result = state.withZone(library, updated);

        ZoneRef graveyard = ZoneRef.of(event.seat(), Zone.GRAVEYARD);
        for (CardInstanceId id : event.toGraveyard()) {
            result = result.place(id, graveyard, Placement.TOP);
        }
        return result.withoutPeekBy(event.actor());
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

    private static GameState createCopy(GameState state, GameEvent.TokenCopyCreated event) {
        CardInstance source = state.requireCard(event.source());
        int nextId = state.nextCardId();
        CardInstance copy = CardInstance.token(CardInstanceId.of(nextId), source.identity(), event.seat());
        return state
                .addCard(copy, ZoneRef.of(event.seat(), Zone.BATTLEFIELD), Placement.BOTTOM)
                .withNextCardId(nextId + 1);
    }
}
