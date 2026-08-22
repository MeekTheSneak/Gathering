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
            boolean token) implements CardView {

        public Visible {
            counters = counters == null ? Map.of() : Map.copyOf(counters);
            facing = facing == null ? Facing.FACE_UP : facing;
        }
    }

    /**
     * A face-down card, as everyone else sees it.
     *
     * <p>Carries the marker, the tap state, the counters and the spot it sits on - so an
     * opponent can follow "that face-down creature on the left is tapped and has two +1/+1
     * counters, and now it has moved to exile" exactly as they could across a real table -
     * and carries no instance id, no owner, and no identity. There is nothing here to invert.
     * Where a card <em>is</em> was never a secret; a face-down card is visibly present.
     */
    record Anonymous(
            MarkerId marker,
            boolean tapped,
            Map<String, Integer> counters,
            TablePosition position) implements CardView {

        public Anonymous {
            counters = counters == null ? Map.of() : Map.copyOf(counters);
        }
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
}
