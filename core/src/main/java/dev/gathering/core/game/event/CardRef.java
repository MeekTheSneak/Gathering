package dev.gathering.core.game.event;

import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.MarkerId;
import dev.gathering.core.game.ZoneRef;
import java.util.Optional;

/**
 * How the public event log is allowed to point at a card.
 * <p>This type exists because of a leak that is easy to miss. Card instance ids are handed
 * out in order as decks load, so instance #37 is the thirty-seventh card of somebody's
 * decklist - and Commander decklists are routinely public. A log line reading "Chris moved
 * card #37 from their hand to their library" therefore tells every opponent exactly which
 * card that was, without any hidden payload ever being sent.
 * <p>So the log never names a card by id unless everyone can already see it. Three forms,
 * and the choice between them is made against the board rather than left to each call site:
 *
 * <ul>
 *   <li>{@link ById} - the card is face up in a public zone. Everyone can read it anyway, so
 *       the log says which one, and "Chris tapped Halana and Alena" is possible.</li>
 *   <li>{@link ByMarker} - the card is face down in a public zone. The log tracks it by the
 *       same opaque marker opponents already see, so "that face-down thing moved" is
 *       expressible without identity.</li>
 *   <li>{@link Anonymous} - the card is in a hidden zone. The log says "a card" and nothing
 *       more.</li>
 * </ul>
 */
public sealed interface CardRef {

    record ById(CardInstanceId id) implements CardRef {
    }

    record ByMarker(MarkerId marker) implements CardRef {
    }

    record Anonymous() implements CardRef {
    }

    CardRef ANONYMOUS = new Anonymous();

    /**
     * The strongest reference the whole table is entitled to for this card.
     * <p>Deliberately state-derived. A call site that had to decide this for itself would
     * eventually get it wrong, and getting it wrong is silent.
     */
    static CardRef publicRefFor(GameState state, CardInstanceId id) {
        Optional<ZoneRef> location = state.locationOf(id);
        if (location.isEmpty() || location.get().isHidden()) {
            return ANONYMOUS;
        }
        Optional<CardInstance> card = state.card(id);
        if (card.isEmpty()) {
            return ANONYMOUS;
        }
        return card.get().markerId().<CardRef>map(ByMarker::new).orElseGet(() -> new ById(id));
    }

    /** Whether this reference names a specific card, which only ever happens for public ones. */
    default boolean isIdentifying() {
        return this instanceof ById;
    }
}
