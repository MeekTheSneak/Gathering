package dev.gathering.core.game.visibility;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.MarkerId;
import java.util.Map;

/**
 * A card as one viewer is entitled to know it.
 *
 * <p>Two shapes, and the split is the security property made structural: {@link Visible}
 * carries identity, {@link Anonymous} carries no path to it. There is no third shape
 * carrying "identity, but redacted", because a sanitised secret is still a secret in
 * somebody's memory and network traffic.
 */
public sealed interface CardView {

    /** Everything about a card the viewer may know. */
    record Visible(
            CardInstanceId id,
            CardIdentity identity,
            dev.gathering.core.game.SeatId owner,
            boolean tapped,
            Map<String, Integer> counters,
            dev.gathering.core.game.TablePosition position,
            boolean token) implements CardView {

        public Visible {
            counters = counters == null ? Map.of() : Map.copyOf(counters);
        }
    }

    /**
     * A face-down card, as everyone else sees it.
     *
     * <p>Carries the marker, the tap state, the counters and the square it sits on - so an
     * opponent can follow "that face-down creature on the left is tapped and has two +1/+1
     * counters, and now it has moved to exile" exactly as they could across a real table -
     * and carries no instance id, no owner, and no identity. There is nothing here to invert.
     * Where a card <em>is</em> was never a secret; a face-down card is visibly present.
     */
    record Anonymous(
            MarkerId marker,
            boolean tapped,
            Map<String, Integer> counters,
            dev.gathering.core.game.TablePosition position) implements CardView {

        public Anonymous {
            counters = counters == null ? Map.of() : Map.copyOf(counters);
        }
    }

    default boolean carriesIdentity() {
        return this instanceof Visible;
    }

    /** Where to draw it, for cards on a surface. Empty for cards in a pile. */
    default java.util.Optional<dev.gathering.core.game.TablePosition> square() {
        return java.util.Optional.ofNullable(switch (this) {
            case Visible visible -> visible.position();
            case Anonymous anonymous -> anonymous.position();
        });
    }
}
