package dev.gathering.core.game;

import dev.gathering.core.card.CardIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One card in one session: what it is, whose it is, and what has been done to it.
 * <p>Two fields deserve their own sentence.
 * <p><b>Owner is immutable.</b> A card belongs to the deck or collection it entered from,
 * for the whole session, whatever happens to it. Control is not stored here at all - it is
 * just which seat's side of the table the card is currently sitting on, which lives in the
 * zone map. Tevesh Szat's ultimate steals every commander at the table and none of them
 * change owner, which is why at session end every card goes home without any bookkeeping.
 * <p><b>The marker is present exactly when the card is face down.</b> It is what opponents
 * see instead of identity, and it is regenerated on every flip down so two separate
 * face-down periods cannot be correlated.
 * <p><b>Position is where it was dropped.</b> Cards on a surface - the battlefield, and
 * exile when a group spreads it out - carry the exact spot and angle they were put down at,
 * so every client draws the same board and a player's arrangement of their lands survives
 * being looked at from the other side of the table. Cards in a pile carry no position,
 * because a pile is an order rather than a place.
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
 * <p><b>Turned over is not face down.</b> A transforming card has two printed faces and both
 * of them are public; turning one over shows the table its other side. A card that is face
 * down is showing a sleeve and nobody may name it at all. The two are separate because a
 * werewolf that flips at dusk and a morph played for three are different acts, and a card can
 * be in both states at once - a transformed permanent that somebody then turns face down
 * comes back up transformed, the way it would on a real table.
 *
 * @param note what a player has written on it, or null for a card nobody has written on
 * @param turnedOver whether it is showing its second printed face, for a card that has one
 * @param strength a power and toughness written over the printed ones, or null for a card
 *     showing what it was printed as - typed by a player and never worked out, see
 *     {@link CardStrength}
 * @param frozen whether it stays tapped through its controller's untap step, because
 *     something froze it. Recorded on the card because untapping everything is one press
 *     done every turn without looking, and that is the press that forgets
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
        String note,
        boolean turnedOver,
        String strength,
        boolean frozen) {

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
        // Same door for the same reason: an override read off a save file or a packet is
        // cleaned exactly as one typed into the box is.
        strength = CardStrength.clean(strength);
    }

    public static CardInstance faceUp(CardInstanceId id, CardIdentity identity, SeatId owner) {
        return new CardInstance(
                id, identity, owner, Facing.FACE_UP, false, Map.of(), null, null, false, null, null, false, null, false);
    }

    public static CardInstance token(CardInstanceId id, CardIdentity identity, SeatId owner) {
        return new CardInstance(
                id, identity, owner, Facing.FACE_UP, false, Map.of(), null, null, true, null, null, false, null, false);
    }

    public CardInstance withTapped(boolean newTapped) {
        return newTapped == tapped
                ? this
                : new CardInstance(id, identity, owner, facing, newTapped, counters, marker, position, token, attachedTo, note, turnedOver, strength, frozen);
    }

    /** Where a drag dropped it, or nothing once it goes back into a pile. */
    public CardInstance withPosition(TablePosition newPosition) {
        return java.util.Objects.equals(newPosition, position)
                ? this
                : new CardInstance(id, identity, owner, facing, tapped, counters, marker, newPosition, token, attachedTo, note, turnedOver, strength, frozen);
    }

    /** Flipping down needs a fresh marker; flipping up drops the one it had. */
    public CardInstance faceDownWith(MarkerId newMarker) {
        return new CardInstance(
                id, identity, owner, Facing.FACE_DOWN, tapped, counters, newMarker, position, token, attachedTo, note, turnedOver, strength, frozen);
    }

    public CardInstance faceUp() {
        return facing == Facing.FACE_UP
                ? this
                : new CardInstance(
                        id, identity, owner, Facing.FACE_UP, tapped, counters, null, position, token,
                        attachedTo, note, turnedOver, strength, frozen);
    }

    /**
     * Adds to a named counter, removing the entry when it reaches zero.
     * <p>Counters go negative freely. Nothing here decides whether minus three loyalty is
     * legal; a player who wants a negative counter has a reason, and the mod does not argue.
     */
    public CardInstance withCounter(String name, int delta) {
        if (name == null) {
            return this;
        }
        Map<String, Integer> updated = new LinkedHashMap<>(counters);
        // A name this card does not already carry is a new key, and keys are what grow. One
        // card cannot collect more kinds of counter than anybody would put on it; changing a
        // counter it already has is never refused.
        if (!updated.containsKey(name) && updated.size() >= CounterName.MOST_PER_CARD) {
            return this;
        }
        int now = updated.getOrDefault(name, 0) + delta;
        if (now == 0) {
            updated.remove(name);
        } else {
            updated.put(name, now);
        }
        return new CardInstance(
                id, identity, owner, facing, tapped, updated, marker, position, token, attachedTo, note, turnedOver, strength, frozen);
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
     * <p>Refuses to attach a card to itself, which is the one arrangement that cannot be
     * drawn and the only way to make a cycle out of a relationship this shallow.
     */
    public CardInstance attachedToCard(CardInstanceId host) {
        CardInstanceId target = id.equals(host) ? null : host;
        return java.util.Objects.equals(target, attachedTo)
                ? this
                : new CardInstance(
                        id, identity, owner, facing, tapped, counters, marker, position, token, target,
                        note, turnedOver, strength, frozen);
    }

    public Optional<CardInstanceId> host() {
        return Optional.ofNullable(attachedTo);
    }

    /**
     * Writes on it, or rubs it out with nothing.
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
                        attachedTo, tidy, turnedOver, strength, frozen);
    }

    /** What somebody wrote on it, if anybody has. */
    public Optional<String> writtenOn() {
        return Optional.ofNullable(note);
    }

    /**
     * Writes a power and toughness over the printed ones, or takes the writing off.
     * <p>What is typed is what is shown. Nothing here adds counters up or looks at what the
     * card was printed as - see {@link CardStrength}.
     */
    public CardInstance withStrength(String written) {
        String tidy = CardStrength.clean(written);
        return java.util.Objects.equals(tidy, strength)
                ? this
                : new CardInstance(
                        id, identity, owner, facing, tapped, counters, marker, position, token,
                        attachedTo, note, turnedOver, tidy, frozen);
    }

    /** The power and toughness somebody wrote over the printed ones, if anybody did. */
    public Optional<String> writtenStrength() {
        return Optional.ofNullable(strength);
    }

    /**
     * Freezes it, or thaws it.
     * <p>A frozen card does not untap when its controller untaps everything. That is the only
     * thing frozen does, and it is enough: untapping is one press made every turn without
     * looking, so it is the press that quietly undoes what an opponent spent a card on.
     * <p>Nothing here knows when a freeze ends. A player takes it off with the same menu they
     * put it on with - no rules engine, section 16 - and a frozen card is otherwise an
     * ordinary card that can be tapped, moved, written on and binned like any other.
     */
    public CardInstance frozen(boolean stunned) {
        return stunned == frozen
                ? this
                : new CardInstance(
                        id, identity, owner, facing, tapped, counters, marker, position, token,
                        attachedTo, note, turnedOver, strength, stunned);
    }

    /**
     * Turns it to its other printed face, or back.
     * <p>Nothing here knows whether the card has a second face. Whether a transform exists is
     * a fact about a printing, which lives in the card data the client holds and not in the
     * game - so the game records which side is being shown and the drawing decides what that
     * means. A card with one face turned over shows the same face, which is what a table full
     * of people turning the wrong card over would produce anyway.
     */
    public CardInstance turnedOver(boolean showingTheOtherSide) {
        return showingTheOtherSide == turnedOver
                ? this
                : new CardInstance(
                        id, identity, owner, facing, tapped, counters, marker, position, token,
                        attachedTo, note, showingTheOtherSide, strength, frozen);
    }

    public boolean isAttached() {
        return attachedTo != null;
    }

    /** Counters commonly enough named to be worth spelling once. */
    public static final class Counters {
        public static final String LOYALTY = "loyalty";
        public static final String PLUS_ONE_PLUS_ONE = "+1/+1";
        public static final String MINUS_ONE_MINUS_ONE = "-1/-1";

        /**
         * A counter whose name is a power and toughness, so that several of them add up.
         * <p>Two +1/+1 counters are +2/+2. That is not a way of writing it, it is what the
         * creature is - it is the number a player reads off the board and adds to the
         * printed one, and nobody at a table says "plus one plus one, times two". Written
         * out as a count beside a name, the way charge and stun and loyalty counters are,
         * it makes the reader do the multiplication every time they look at the board.
         * <p>Matched rather than listed, so a card that arrives with +2/+2 counters on it -
         * they exist - adds up the same way without anybody adding a constant for it.
         */
        private static final java.util.regex.Pattern POWER_AND_TOUGHNESS =
                java.util.regex.Pattern.compile("([+-])(\\d+)/([+-])(\\d+)");

        private Counters() {
        }

        /** Whether several of this counter are read as one bigger counter. */
        public static boolean addUp(String counter) {
            return counter != null && POWER_AND_TOUGHNESS.matcher(counter).matches();
        }

        /**
         * What a pile of this many of this counter says on the card.
         * <p>{@code "+2/+2"} for two +1/+1 counters, and {@code null} for everything else -
         * a charge counter has no arithmetic to do, and the caller writes its name with the
         * count beside it instead. Null rather than the name itself so a caller cannot
         * accidentally lose the count by treating every counter as though it added up.
         */
        public static String addedUp(String counter, int howMany) {
            if (howMany <= 0 || !addUp(counter)) {
                return null;
            }
            java.util.regex.Matcher parts = POWER_AND_TOUGHNESS.matcher(counter);
            if (!parts.matches()) {
                return null;
            }
            return side(parts.group(1), parts.group(2), howMany)
                    + "/" + side(parts.group(3), parts.group(4), howMany);
        }

        /**
         * One half of it, multiplied out.
         * <p>{@code long} on the way through because a player who has been pressing the plus
         * button all game can get a counter into the millions, and a card reading a negative
         * power because the arithmetic wrapped would be worse than one reading a silly
         * number. Clamped to what an int holds, which is far past any real board.
         */
        private static String side(String sign, String size, int howMany) {
            long total = Long.parseLong(size) * howMany;
            long capped = Math.min(total, Integer.MAX_VALUE);
            return sign + capped;
        }
    }
}
