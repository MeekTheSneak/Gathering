package dev.gathering.core.game;

import dev.gathering.core.card.CardIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One card in one session: what it is, whose it is, and what has been done to it.
 *
 * <p>Two fields deserve their own sentence.
 *
 * <p><b>Owner is immutable.</b> A card belongs to the deck or collection it entered from,
 * for the whole session, whatever happens to it. Control is not stored here at all - it is
 * just which seat's side of the table the card is currently sitting on, which lives in the
 * zone map. Tevesh Szat's ultimate steals every commander at the table and none of them
 * change owner, which is why at session end every card goes home without any bookkeeping.
 *
 * <p><b>The marker is present exactly when the card is face down.</b> It is what opponents
 * see instead of identity, and it is regenerated on every flip down so two separate
 * face-down periods cannot be correlated.
 *
 * @param token tokens and copies cease to exist at end of session rather than returning to
 *              a deck, which is the only case where a card does not go home
 */
public record CardInstance(
        CardInstanceId id,
        CardIdentity identity,
        SeatId owner,
        Facing facing,
        boolean tapped,
        Map<String, Integer> counters,
        MarkerId marker,
        boolean token) {

    public CardInstance {
        if (id == null || identity == null || owner == null || facing == null) {
            throw new IllegalArgumentException("A card instance needs an id, an identity, an owner and a facing");
        }
        if (facing.isFaceDown() == (marker == null)) {
            throw new IllegalArgumentException(
                    "A face-down card carries a marker and a face-up card does not: " + id + " is " + facing
                            + " with marker " + marker);
        }
        counters = counters == null || counters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }

    public static CardInstance faceUp(CardInstanceId id, CardIdentity identity, SeatId owner) {
        return new CardInstance(id, identity, owner, Facing.FACE_UP, false, Map.of(), null, false);
    }

    public static CardInstance token(CardInstanceId id, CardIdentity identity, SeatId owner) {
        return new CardInstance(id, identity, owner, Facing.FACE_UP, false, Map.of(), null, true);
    }

    public CardInstance withTapped(boolean newTapped) {
        return newTapped == tapped
                ? this
                : new CardInstance(id, identity, owner, facing, newTapped, counters, marker, token);
    }

    /** Flipping down needs a fresh marker; flipping up drops the one it had. */
    public CardInstance faceDownWith(MarkerId newMarker) {
        return new CardInstance(id, identity, owner, Facing.FACE_DOWN, tapped, counters, newMarker, token);
    }

    public CardInstance faceUp() {
        return facing == Facing.FACE_UP
                ? this
                : new CardInstance(id, identity, owner, Facing.FACE_UP, tapped, counters, null, token);
    }

    /**
     * Adds to a named counter, removing the entry when it reaches zero.
     *
     * <p>Counters go negative freely. Nothing here decides whether minus three loyalty is
     * legal; a player who wants a negative counter has a reason, and the mod does not argue.
     */
    public CardInstance withCounter(String name, int delta) {
        Map<String, Integer> updated = new LinkedHashMap<>(counters);
        int now = updated.getOrDefault(name, 0) + delta;
        if (now == 0) {
            updated.remove(name);
        } else {
            updated.put(name, now);
        }
        return new CardInstance(id, identity, owner, facing, tapped, updated, marker, token);
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    public boolean isFaceDown() {
        return facing.isFaceDown();
    }

    public Optional<MarkerId> markerId() {
        return Optional.ofNullable(marker);
    }

    /** Counters commonly enough named to be worth spelling once. */
    public static final class Counters {
        public static final String LOYALTY = "loyalty";
        public static final String PLUS_ONE_PLUS_ONE = "+1/+1";
        public static final String MINUS_ONE_MINUS_ONE = "-1/-1";

        private Counters() {
        }
    }
}
