package dev.gathering.core.game.visibility;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.MarkerId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import java.util.Map;
import java.util.Optional;

/**
 * A card as one viewer is entitled to know it.
 *
 * <p>Two shapes, and the split is the security property made structural: {@link Visible}
 * carries identity, {@link Anonymous} carries no path to it. There is no third shape
 * carrying "identity, but redacted", because a sanitised secret is still a secret in
 * somebody's memory and network traffic.
 */
public sealed interface CardView {

    /**
     * Everything about a card the viewer may know.
     *
     * <p>Carries its facing even though the viewer can see it either way. A card you played
     * face down is one you are entitled to know and everyone else is not, and it still has to
     * be drawn face down on your own screen - otherwise your board and their board disagree
     * about what is on the table, and you cannot tell which of your own permanents your
     * opponents can read.
     */
    record Visible(
            CardInstanceId id,
            CardIdentity identity,
            SeatId owner,
            Facing facing,
            boolean tapped,
            Map<String, Integer> counters,
            TablePosition position,
            boolean token,
            CardInstanceId attachedTo,
            String note,
            boolean turnedOver) implements CardView {

        public Visible {
            counters = CardView.kept(counters);
            facing = facing == null ? Facing.FACE_UP : facing;
            note = dev.gathering.core.game.CardNote.clean(note);
        }
    }

    /**
     * A face-down card, as everyone else sees it.
     *
     * <p>Carries the marker, the tap state, the counters, whatever is written on it and the
     * spot it sits on - so an opponent can follow "that face-down creature on the left is
     * tapped and has two +1/+1 counters, and now it has moved to exile" exactly as they could
     * across a real table - and carries no instance id, no owner, and no identity. There is
     * nothing here to invert. A note is a player's own words about the card and not the mod's,
     * which is exactly why it is safe on a card whose name is a secret: the person who wrote
     * it decided what it gave away.
     * Where a card <em>is</em> was never a secret; a face-down card is visibly present.
     */
    record Anonymous(
            MarkerId marker,
            boolean tapped,
            Map<String, Integer> counters,
            TablePosition position,
            CardInstanceId attachedTo,
            String note) implements CardView {

        public Anonymous {
            counters = CardView.kept(counters);
            note = dev.gathering.core.game.CardNote.clean(note);
        }
    }

    /**
     * Keeps counters in the order they were put on.
     *
     * <p>They are drawn as a stack of labels along the bottom of the card, so the order is
     * something a player reads - and {@code Map.copyOf} orders by a hash salted once per
     * launch, which would shuffle that stack every time the game started. The seat's own
     * counters have been kept in order for exactly this reason since they were added; these
     * were not, which made the same rule mean two different things depending on whether the
     * counter was on a card or beside a board.
     */
    static Map<String, Integer> kept(Map<String, Integer> counters) {
        return counters == null || counters.isEmpty()
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(counters));
    }

    default boolean carriesIdentity() {
        return this instanceof Visible;
    }

    /** Where to draw it, for cards on a surface. Empty for cards in a pile. */
    default Optional<TablePosition> placedAt() {
        return Optional.ofNullable(switch (this) {
            case Visible visible -> visible.position();
            case Anonymous anonymous -> anonymous.position();
        });
    }

    default boolean tapped() {
        return switch (this) {
            case Visible visible -> visible.tapped();
            case Anonymous anonymous -> anonymous.tapped();
        };
    }

    /**
     * Which way up the card is lying.
     *
     * <p>An anonymous card is face down by definition - that is the only reason it is
     * anonymous - so this is not a guess.
     */
    default Facing facing() {
        return switch (this) {
            case Visible visible -> visible.facing();
            case Anonymous ignored -> Facing.FACE_DOWN;
        };
    }

    default boolean isFaceDown() {
        return facing().isFaceDown();
    }

    default Map<String, Integer> counters() {
        return switch (this) {
            case Visible visible -> visible.counters();
            case Anonymous anonymous -> anonymous.counters();
        };
    }

    default int counter(String name) {
        return counters().getOrDefault(name, 0);
    }

    /**
     * Whether it is showing its second printed face.
     *
     * <p>Only a card whose name the viewer may read: which side of a transforming card is up
     * is only meaningful to somebody who can see that it is a transforming card at all, and a
     * face-down one is a sleeve either way.
     */
    default boolean turnedOver() {
        return this instanceof Visible visible && visible.turnedOver();
    }

    /**
     * What a player has written on this card, if anybody has.
     *
     * <p>Carried by a face-down card as well as a face-up one. A note is what somebody chose
     * to say about a card, not what the card is, so it gives away nothing the person holding
     * the pen did not mean to give away - and marking a face-down creature so the table
     * remembers what everybody agreed it was is most of what the pen is for.
     */
    default Optional<String> writtenOn() {
        return Optional.ofNullable(switch (this) {
            case Visible visible -> visible.note();
            case Anonymous anonymous -> anonymous.note();
        });
    }

    /**
     * The card this one is sitting on, if any.
     *
     * <p>Carried by an anonymous card too. Which card a face-down permanent is attached to is
     * not a secret - everybody at a real table can see the equipment lying across it - and the
     * host it names is a card the viewer can already see, so there is nothing here to invert.
     */
    default Optional<CardInstanceId> host() {
        return Optional.ofNullable(switch (this) {
            case Visible visible -> visible.attachedTo();
            case Anonymous anonymous -> anonymous.attachedTo();
        });
    }
}
