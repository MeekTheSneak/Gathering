package dev.gathering.core.card;

import java.util.Locale;

/**
 * A card's status in one format, as Scryfall reports it.
 *
 * <p>This feeds the pre-game deck check and nothing else. Once a session starts the mod
 * never consults it again - there is no in-game legality enforcement, ever.
 */
public enum Legality {
    LEGAL,
    NOT_LEGAL,
    RESTRICTED,
    BANNED,
    UNKNOWN;

    public static Legality parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** Whether a deck may contain this card at all in the format. */
    public boolean playable() {
        return this == LEGAL || this == RESTRICTED;
    }

    /**
     * The per-deck copy ceiling this status imposes on its own, or -1 for "no opinion,
     * defer to the format preset". Restricted means at most one, which is how Vintage
     * falls out of the general validator with no Vintage-specific code.
     */
    public int copyCeiling() {
        return this == RESTRICTED ? 1 : -1;
    }
}
