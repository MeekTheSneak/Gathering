package dev.gathering.core.card;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The whole of what a card item carries: which printing it is, whether it is foil,
 * and - for server-hosted custom cards - which custom card it is instead.
 * <p>Canonical identity is the Scryfall ID, the UUID of one specific printing. Name,
 * oracle text, mana cost and image URIs are all derived data, fetched and cached from
 * Scryfall rather than stored here. A card in a deck box and the same card on a
 * battlefield are the same three fields.
 * <p>Exactly one of {@code scryfallId} and {@code customId} is present. Custom cards
 * live in their own namespace precisely so they can never collide with a Scryfall ID.
 */
public record CardIdentity(UUID scryfallId, boolean foil, String customId) {

    public CardIdentity {
        boolean hasScryfall = scryfallId != null;
        boolean hasCustom = customId != null && !customId.isBlank();
        if (hasScryfall == hasCustom) {
            throw new IllegalArgumentException(
                    "A card identity is either a Scryfall printing or a custom card, never both and never neither: "
                            + "scryfallId=" + scryfallId + ", customId=" + customId);
        }
        if (hasCustom) {
            customId = customId.trim();
        }
    }

    public static CardIdentity ofPrinting(UUID scryfallId, boolean foil) {
        return new CardIdentity(Objects.requireNonNull(scryfallId, "scryfallId"), foil, null);
    }

    public static CardIdentity ofPrinting(UUID scryfallId) {
        return ofPrinting(scryfallId, false);
    }

    /**
     * The custom id the redacted stand-in carries, which no real card may.
     * <p>A deck is sent to other clients with every card replaced by this, and everything that
     * reads a deck treats a card with this id as "not a real card" - it drops it, or goes to look
     * the real one up. A card of anybody's that happened to be called this would be destroyed on
     * the way through. Nothing makes one today; every door a custom id comes in by refuses it, so
     * nothing can later either.
     */
    public static final String STAND_IN = "hidden";

    /** Whether this custom id is the stand-in's, which only the redaction may write. */
    public static boolean isStandIn(String customId) {
        return STAND_IN.equals(customId);
    }

    public static CardIdentity ofCustom(String customId, boolean foil) {
        return new CardIdentity(null, foil, Objects.requireNonNull(customId, "customId"));
    }

    public boolean isCustom() {
        return customId != null;
    }

    public Optional<UUID> printing() {
        return Optional.ofNullable(scryfallId);
    }

    public Optional<String> custom() {
        return Optional.ofNullable(customId);
    }

    /** The same card in the other finish. Used by import and by store products. */
    public CardIdentity withFoil(boolean newFoil) {
        return newFoil == foil ? this : new CardIdentity(scryfallId, newFoil, customId);
    }

    /**
     * A stable string form for logs and cache keys. Never used as a network identity for
     * hidden zones - see the visibility rules, where face-down cards travel as per-session
     * marker IDs that are not derived from this at all.
     */
    public String cacheKey() {
        return (isCustom() ? "custom:" + customId : scryfallId.toString()) + (foil ? ":foil" : "");
    }
}
