package dev.gathering.core.card;

import java.util.ArrayList;
import java.util.List;

/**
 * Which card textures to let go of, and in what order.
 * <p>Separated from the cache that holds them because the holding is GL and the deciding is
 * arithmetic, and the arithmetic is where this went wrong. The rule reads as one sentence -
 * oldest first until it fits - and that sentence is only true of two of the three caps.
 * <p>The byte ceiling and the count are the whole cache's, and are paid oldest-first out of
 * whatever is oldest. The crisp allowance is a tier's own, and has to be paid out of crisp
 * textures: there are two of those allowed at a time and they are by definition the two most
 * recently looked at, so oldest-first walked the entire board's art before reaching one that
 * brought the count down. Reading a third card in a row released every texture on the table
 * and downloaded them all again, which is the one thing a cache exists to stop.
 * <p>Pure.
 */
public final class ResidentTextures {

    private ResidentTextures() {
    }

    /** One texture that is resident, as far as the decision is concerned. */
    public record Held(String key, long bytes, boolean crisp) {
    }

    /**
     * The keys to release, in the order to release them.
     *
     * @param oldestFirst everything resident, least recently used first
     * @param mostTextures the count ceiling for the whole cache
     * @param mostBytes the byte ceiling for the whole cache
     * @param mostCrisp how many of the large tier may be resident at once
     */
    public static List<String> toRelease(
            List<Held> oldestFirst, int mostTextures, long mostBytes, int mostCrisp) {
        if (oldestFirst == null || oldestFirst.isEmpty()) {
            return List.of();
        }
        List<String> going = new ArrayList<>();
        int count = oldestFirst.size();
        long bytes = 0;
        int crisp = 0;
        for (Held held : oldestFirst) {
            bytes += held.bytes();
            if (held.crisp()) {
                crisp++;
            }
        }

        // The whole cache's two caps, oldest first, whatever tier that happens to be.
        int at = 0;
        while (at < oldestFirst.size() && (count > mostTextures || bytes > mostBytes)) {
            Held goingAway = oldestFirst.get(at);
            going.add(goingAway.key());
            count--;
            bytes -= goingAway.bytes();
            if (goingAway.crisp()) {
                crisp--;
            }
            at++;
        }

        // Then the tier's own, out of that tier. Still oldest-first within it, so the card
        // being read now is the last thing to go rather than the first.
        for (int index = at; index < oldestFirst.size() && crisp > mostCrisp; index++) {
            Held held = oldestFirst.get(index);
            if (held.crisp()) {
                going.add(held.key());
                crisp--;
            }
        }
        return List.copyOf(going);
    }
}
