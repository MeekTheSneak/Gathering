package dev.gathering.core.game;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * The session's root secret.
 * <p>Every shuffle and every face-down marker in a session derives from this. Seed plus
 * decklist equals every future draw, which makes it the single most sensitive value in the
 * mod - more than any individual hidden card, because it predicts all of them.
 * <p>Accordingly: it is never logged, never included in a crash report, never sent to any
 * client, and at rest it lives only in the encrypted stream alongside the hidden-information
 * events, sealed until the session ends. {@link #toString()} is overridden so that an
 * incautious log statement prints nothing useful.
 */
public final class SessionSeed {

    private static final int SEED_BYTES = 32;

    private final byte[] material;

    private SessionSeed(byte[] material) {
        this.material = material.clone();
    }

    public static SessionSeed random() {
        byte[] material = new byte[SEED_BYTES];
        new SecureRandom().nextBytes(material);
        return new SessionSeed(material);
    }

    /** For persistence and for replay, which are the only two legitimate callers. */
    public static SessionSeed fromBytes(byte[] material) {
        if (material == null || material.length < 16) {
            throw new IllegalArgumentException("A session seed needs at least 16 bytes of material");
        }
        return new SessionSeed(material);
    }

    public byte[] toBytes() {
        return material.clone();
    }

    /** The permutation for one shuffle of one library, derived rather than stored. */
    public <T> List<T> shuffle(List<T> library, SeatId seat, int shuffleOrdinal) {
        return DeterministicRandom
                .forLabel(material, "shuffle:" + seat + ":" + shuffleOrdinal)
                .shuffled(library);
    }

    /**
     * The opaque handle a card wears while face down.
     * <p>Derived from the seed and a counter, never from the card's identity or instance id,
     * so there is no function from a marker back to a card. That is the point of it.
     */
    public MarkerId marker(int markerOrdinal) {
        return new MarkerId(DeterministicRandom.forLabel(material, "marker:" + markerOrdinal).nextHex(8));
    }

    @Override
    public String toString() {
        // Never print the seed. A stack trace or a debug line is exactly how it would escape.
        return "SessionSeed(redacted)";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SessionSeed seed && Arrays.equals(material, seed.material);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(material);
    }
}
