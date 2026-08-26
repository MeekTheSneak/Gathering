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
 * <p><b>Position is where it was dropped.</b> Cards on a surface - the battlefield, and
 * exile when a group spreads it out - carry the exact spot and angle they were put down at,
 * so every client draws the same board and a player's arrangement of their lands survives
 * being looked at from the other side of the table. Cards in a pile carry no position,
 * because a pile is an order rather than a place.
 *
 * <p><b>Attachment is a drawing relationship, not a rule.</b> A card attached to another
 * follows it around the table and draws small beside it, which is what an aura or a piece of
 * equipment looks like. Nothing here knows what an aura is or checks that one may legally be
 * where it is - the group decides that, as with everything else.
 *
 * @param token tokens and copies cease to exist at end of session rather than returning to
 *              a deck, which is the only case where a card does not go home
 * <p><b>The note is what somebody wrote on it.</b> A reminder written by a player and read
 * by everybody, because a mod with no rules engine remembers a rule by somebody writing it
 * down. It is text and nothing else: nothing here reads it, and nothing it says has any
 * effect on anything.
 *
 * @param attachedTo the card this one is on, or null for a card standing on its own
 * @param note what a player has written on it, or null for a card nobody has written on
 */
public record CardInstance(
        CardInstanceId id,
        CardIdentity identity,
        SeatId owner,
        Facing facing,
        boolean tapped,
        Map<String, Integer> counters,
        MarkerId marker,
        TablePosition position,
        boolean token,
        CardInstanceId attachedTo,
        String note) {

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
        // Cleaned here rather than trusted from whoever built it, so there is one place a
        // note can be made and one shape it can be in - a record read off a save file or a
        // packet goes through the same door as one built by the fold.
        note = CardNote.clean(note);
    }

    public static CardInstance faceUp(CardInstanceId id, CardIdentity identity, SeatId owner) {
        return new CardInstance(
                id, identity, owner, Facing.FACE_UP, false, Map.of(), null, null, false, null, null);
    }

    public static CardInstance token(CardInstanceId id, CardIdentity identity, SeatId owner) {
        return new CardInstance(
                id, identity, owner, Facing.FACE_UP, false, Map.of(), null, null, true, null, null);
    }

    public CardInstance withTapped(boolean newTapped) {
        return newTapped == tapped
                ? this
                : new CardInstance(id, identity, owner, facing, newTapped, counters, marker, position, token, attachedTo, note);
    }

    /** Where a drag dropped it, or nothing once it goes back into a pile. */
    public CardInstance withPosition(TablePosition newPosition) {
        return java.util.Objects.equals(newPosition, position)
                ? this
                : new CardInstance(id, identity, owner, facing, tapped, counters, marker, newPosition, token, attachedTo, note);
    }

    /** Flipping down needs a fresh marker; flipping up drops the one it had. */
    public CardInstance faceDownWith(MarkerId newMarker) {
        return new CardInstance(
                id, identity, owner, Facing.FACE_DOWN, tapped, counters, newMarker, position, token, attachedTo, note);
    }

    public CardInstance faceUp() {
        return facing == Facing.FACE_UP
                ? this
                : new CardInstance(
                        id, identity, owner, Facing.FACE_UP, tapped, counters, null, position, token,
                        attachedTo, note);
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
        return new CardInstance(
                id, identity, owner, facing, tapped, updated, marker, position, token, attachedTo, note);
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

    public Optional<TablePosition> placedAt() {
        return Optional.ofNullable(position);
    }

    /**
     * Puts this card on another one, or takes it off with null.
     *
     * <p>Refuses to attach a card to itself, which is the one arrangement that cannot be
     * drawn and the only way to make a cycle out of a relationship this shallow.
     */
    public CardInstance attachedToCard(CardInstanceId host) {
        CardInstanceId target = id.equals(host) ? null : host;
        return java.util.Objects.equals(target, attachedTo)
                ? this
                : new CardInstance(
                        id, identity, owner, facing, tapped, counters, marker, position, token, target,
                        note);
    }

    public Optional<CardInstanceId> host() {
        return Optional.ofNullable(attachedTo);
    }

    /**
     * Writes on it, or rubs it out with nothing.
     *
     * <p>One note to a card rather than a list of them. A card with four remarks stacked up
     * its face is a card nobody can see, and a player who wants to say two things has room
     * to say them in one line.
     */
    public CardInstance withNote(String written) {
        String tidy = CardNote.clean(written);
        return java.util.Objects.equals(tidy, note)
                ? this
                : new CardInstance(
                        id, identity, owner, facing, tapped, counters, marker, position, token,
                        attachedTo, tidy);
    }

    /** What somebody wrote on it, if anybody has. */
    public Optional<String> writtenOn() {
        return Optional.ofNullable(note);
    }

    public boolean isAttached() {
        return attachedTo != null;
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
