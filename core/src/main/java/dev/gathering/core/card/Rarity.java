package dev.gathering.core.card;

import java.util.Locale;

/** Scryfall's rarity values. Unknown strings degrade to {@link #UNKNOWN} rather than throwing. */
public enum Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    MYTHIC,
    SPECIAL,
    BONUS,
    UNKNOWN;

    public static Rarity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public String scryfallName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
