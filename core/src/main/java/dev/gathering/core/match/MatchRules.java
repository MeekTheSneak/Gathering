package dev.gathering.core.match;

import dev.gathering.core.format.FormatPreset;
import java.util.List;

/**
 * What kind of game this is: which format, and how many of them.
 *
 * <p>Best-of-three is not a nicety for the sixty-card formats, it is how they are played -
 * a deck with fifteen cards it never gets to use is a deck missing a quarter of itself.
 * Commander is the format that does not want this, so one is a supported answer rather than
 * the absence of one.
 */
public record MatchRules(FormatPreset format, int bestOf) {

    /** Odd numbers only: an even one can be drawn, and a drawn match settles nothing. */
    public static final List<Integer> SUPPORTED_LENGTHS = List.of(1, 3, 5);

    public MatchRules {
        if (format == null) {
            throw new IllegalArgumentException("A match needs a format");
        }
        if (!SUPPORTED_LENGTHS.contains(bestOf)) {
            throw new IllegalArgumentException(
                    "A match is best of " + SUPPORTED_LENGTHS + ", not " + bestOf);
        }
    }

    /** A single game, which is what a Commander pod plays. */
    public static MatchRules single(FormatPreset format) {
        return new MatchRules(format, 1);
    }

    public int gamesToWin() {
        return bestOf / 2 + 1;
    }

    public boolean isSingleGame() {
        return bestOf == 1;
    }

    /** Sideboarding happens between games, so a single game never has any. */
    public boolean hasSideboarding() {
        return !isSingleGame() && format.hasSideboard();
    }
}
