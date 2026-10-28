package dev.gathering.core.game;

/**
 * The opaque handle a face-down card wears in front of everyone not entitled to see it.
 * <p>Opponents need to be able to say "that face-down creature attacked, and now it has moved
 * to exile" without any path at all to its identity. A marker is stable while a card stays
 * face down, and a card that flips up and back down gets a fresh one, so nobody can correlate
 * two separate face-down periods into a single card.
 * <p>Markers are generated per session and are never derived from the Scryfall id, the card
 * instance id, or anything else that could be inverted. This is not a hashing detail; it is
 * the whole reason the marker exists.
 */
public record MarkerId(String value) {

    public MarkerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Marker id must not be blank");
        }
    }

    @Override
    public String toString() {
        return "marker:" + value;
    }
}
