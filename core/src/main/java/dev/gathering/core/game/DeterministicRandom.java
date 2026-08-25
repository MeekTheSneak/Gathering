package dev.gathering.core.game;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * A deterministic, high-quality random stream derived from a session seed and a label.
 *
 * <p>Two requirements pull in opposite directions and both are non-negotiable.
 *
 * <p><b>It must be deterministic</b>, because replay is guaranteed by architecture here: the
 * sealed event stream plus the session seed has to reproduce a finished game exactly. That
 * is only possible if a shuffle is derived from the seed rather than stored as an order, and
 * it is what keeps the log small and the secret in one place.
 *
 * <p><b>It must be a good shuffle</b>, because this is a card game. {@link java.util.Random}
 * is not usable here: it carries 48 bits of state, so it can reach at most 2^48 distinct
 * permutations. A 100-card Commander library has 100! of them - a number with 158 digits.
 * Seeding {@code java.util.Random} would silently confine every game in the mod to a
 * vanishing sliver of the possible shuffles. So this runs SHA-256 in counter mode instead,
 * which has no such ceiling and costs microseconds at the once-per-shuffle rate we use it.
 *
 * <p>The seed is the most sensitive value in the system - seed plus decklist is every future
 * draw - and no code path here or anywhere else puts it in a log, a debug message, or a
 * crash report.
 */
public final class DeterministicRandom {

    private static final String ALGORITHM = "SHA-256";
    private static final int BLOCK_BYTES = 32;

    private final byte[] seedMaterial;
    private long counter;
    private byte[] block = new byte[0];
    private int blockOffset;

    private DeterministicRandom(byte[] seedMaterial) {
        this.seedMaterial = seedMaterial;
    }

    /**
     * A stream for one specific use, so two different uses of the same session seed never
     * produce the same bytes.
     *
     * @param label what this stream is for - "shuffle:seat1:3", "marker:7"
     */
    public static DeterministicRandom forLabel(byte[] sessionSeed, String label) {
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        byte[] material = new byte[sessionSeed.length + 1 + labelBytes.length];
        System.arraycopy(sessionSeed, 0, material, 0, sessionSeed.length);
        material[sessionSeed.length] = ':';
        System.arraycopy(labelBytes, 0, material, sessionSeed.length + 1, labelBytes.length);
        return new DeterministicRandom(material);
    }

    /**
     * A uniformly distributed value in {@code [0, bound)}.
     *
     * <p>Rejection-sampled rather than taken modulo, because modulo skews toward low values
     * whenever the bound does not divide the range - a bias that in a shuffle would quietly
     * favour certain positions.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive: " + bound);
        }
        if (bound == 1) {
            return 0;
        }
        int limit = Integer.MAX_VALUE - (Integer.MAX_VALUE % bound) - 1;
        while (true) {
            int candidate = nextNonNegativeInt();
            if (candidate <= limit) {
                return candidate % bound;
            }
        }
    }

    /**
     * A uniformly distributed value in {@code [0, bound)}, for bounds an int cannot hold.
     *
     * <p>Real print sheets need this. Published collation expresses a foil sheet's odds as
     * exact integer ratios, and those run to hundreds of billions - one real set's foil sheet
     * comes to 210,395,225,040 - so a draw against a sheet total is not an int draw.
     *
     * <p>A bound that does fit in an int is drawn as one, deliberately: every stream that
     * never needed the wider draw stays byte for byte what it was, so old seeds still
     * reproduce old shuffles and old packs.
     *
     * <p>Rejection-sampled for the same reason {@link #nextInt} is: modulo alone would skew
     * toward the cards at the front of the sheet.
     */
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive: " + bound);
        }
        if (bound <= Integer.MAX_VALUE) {
            return nextInt((int) bound);
        }
        long limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound) - 1;
        while (true) {
            long candidate = nextNonNegativeLong();
            if (candidate <= limit) {
                return candidate % bound;
            }
        }
    }

    /** Fisher-Yates, which visits every permutation with equal probability given a fair source. */
    public <T> List<T> shuffled(List<T> source) {
        List<T> result = new ArrayList<>(source);
        for (int index = result.size() - 1; index > 0; index--) {
            int swap = nextInt(index + 1);
            T held = result.get(index);
            result.set(index, result.get(swap));
            result.set(swap, held);
        }
        return List.copyOf(result);
    }

    /** Hex, for opaque identifiers such as face-down markers. */
    public String nextHex(int bytes) {
        StringBuilder out = new StringBuilder(bytes * 2);
        for (int index = 0; index < bytes; index++) {
            out.append(String.format("%02x", nextByte() & 0xFF));
        }
        return out.toString();
    }

    private long nextNonNegativeLong() {
        long value = 0;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (nextByte() & 0xFFL);
        }
        return value & Long.MAX_VALUE;
    }

    private int nextNonNegativeInt() {
        int value = 0;
        for (int index = 0; index < 4; index++) {
            value = (value << 8) | (nextByte() & 0xFF);
        }
        return value & Integer.MAX_VALUE;
    }

    private byte nextByte() {
        if (blockOffset >= block.length) {
            block = digestBlock(counter++);
            blockOffset = 0;
        }
        return block[blockOffset++];
    }

    private byte[] digestBlock(long blockCounter) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(seedMaterial);
            for (int shift = 56; shift >= 0; shift -= 8) {
                digest.update((byte) (blockCounter >>> shift));
            }
            byte[] out = digest.digest();
            if (out.length != BLOCK_BYTES) {
                throw new IllegalStateException(ALGORITHM + " produced " + out.length + " bytes, expected " + BLOCK_BYTES);
            }
            return out;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform, so this cannot happen; if it
            // somehow did, silently degrading the shuffle would be far worse than failing.
            throw new IllegalStateException(ALGORITHM + " is unavailable, so no shuffle can be trusted", e);
        }
    }
}
