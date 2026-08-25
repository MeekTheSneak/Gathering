package dev.gathering.core.card;

import java.util.Locale;
import java.util.Optional;

/**
 * What counts as a set code, decided once.
 *
 * <p>Four things now put a set code somewhere it matters - the collation feed's URL and its
 * cache file name, a card search, a symbol's URL and its cache file name - and every one of
 * them takes the code from a server config, a command argument or an item's data component.
 * All three of those are places somebody types.
 *
 * <p>So the rule lives here rather than in each of them. It had already been written three
 * times and the three had already drifted: two threw and one returned nothing, two lowered
 * the case and one raised it. A rule about what may go in a URL is exactly the wrong rule to
 * have three of.
 *
 * <p>Letters and digits only, and short. That leaves no way to walk out of a cache directory
 * and no way to bend a URL, and it is what a set code has always looked like.
 */
public final class SetCode {

    /** The longest real one is four; this is room for whatever comes. */
    public static final int LONGEST = 8;

    private SetCode() {
    }

    /**
     * The set code this text is, lower case as Scryfall writes it, or empty if it is not one.
     *
     * @param raw as somebody typed it, or as it arrived on an item
     */
    public static Optional<String> of(String raw) {
        String code = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > LONGEST) {
            return Optional.empty();
        }
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            if ((character < 'a' || character > 'z') && (character < '0' || character > '9')) {
                return Optional.empty();
            }
        }
        return Optional.of(code);
    }

    /** The same, upper case, which is how MTGJSON names its files. */
    public static Optional<String> upper(String raw) {
        return of(raw).map(code -> code.toUpperCase(Locale.ROOT));
    }

    public static boolean isOne(String raw) {
        return of(raw).isPresent();
    }
}
