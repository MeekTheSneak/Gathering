package dev.gathering.core.card;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything derived from a Scryfall printing that the mod actually uses.
 *
 * <p>This is cache content, not game state. A {@link CardIdentity} plus this cache is
 * enough to render, read, and validate a card; nothing here is ever authoritative for a
 * session, and nothing here is ever consulted during play.
 *
 * @param scryfallId    canonical identity - the UUID of this specific printing
 * @param oracleId      shared across every printing of the same card; the copy-limit key,
 *                      because four copies means four of the card, not four of the printing
 * @param faces         one entry for single-faced cards, two for double-faced ones
 * @param legalities    format key to status, straight from Scryfall, consumed by the
 *                      pre-game validator only
 * @param colorIdentity the commander color identity check operates on this
 */
public record CardMetadata(
        UUID scryfallId,
        UUID oracleId,
        String name,
        String manaCost,
        double cmc,
        String typeLine,
        String oracleText,
        Set<String> colors,
        Set<String> colorIdentity,
        List<CardFace> faces,
        String layout,
        String setCode,
        String setName,
        String collectorNumber,
        Rarity rarity,
        boolean reserved,
        boolean foilAvailable,
        boolean nonfoilAvailable,
        boolean digitalOnly,
        boolean oversized,
        List<String> games,
        Map<String, Legality> legalities,
        Map<String, String> prices,
        String scryfallUri) {

    public CardMetadata {
        colors = colors == null ? Set.of() : Set.copyOf(colors);
        colorIdentity = colorIdentity == null ? Set.of() : Set.copyOf(colorIdentity);
        faces = faces == null ? List.of() : List.copyOf(faces);
        games = games == null ? List.of() : List.copyOf(games);
        legalities = legalities == null ? Map.of() : Map.copyOf(legalities);
        prices = prices == null ? Map.of() : Collections.unmodifiableMap(new java.util.HashMap<>(prices));
    }

    /** The face the table shows by default, and the one the overlay opens on. */
    public Optional<CardFace> frontFace() {
        return faces.isEmpty() ? Optional.empty() : Optional.of(faces.get(0));
    }

    public boolean isDoubleFaced() {
        return faces.size() > 1;
    }

    /** Images for the front face, or the card-level images for a single-faced printing. */
    public ImageUris images() {
        return frontFace().map(CardFace::imageUris).filter(u -> u != null && !u.isEmpty())
                .orElse(ImageUris.EMPTY);
    }

    public Legality legalityIn(String format) {
        return legalities.getOrDefault(format, Legality.UNKNOWN);
    }

    /**
     * Whether this printing exists on cardboard. Digital-only cards are excluded from the
     * default collection catalog because they have no paper existence and are illegal in
     * every paper format.
     */
    public boolean existsInPaper() {
        return games.contains("paper") && !digitalOnly;
    }

    /**
     * Whether this card is a land, read off its type line.
     *
     * <p>The word, not the substring, so a subtype that merely contains the letters cannot
     * make a land of something else. This is the one place the question is answered - it was
     * briefly answered three ways in three files, each one drift waiting to happen, and
     * cascade's whole correctness turns on it.
     */
    public boolean isLand() {
        return hasTypeWord("Land");
    }

    /** A basic land: the supertype and the type, both as words. Snow-covered basics count. */
    public boolean isBasicLand() {
        return isLand() && hasTypeWord("Basic");
    }

    private boolean hasTypeWord(String wanted) {
        if (typeLine == null) {
            return false;
        }
        for (String word : typeLine.split("[^A-Za-z]+")) {
            if (word.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The oracle-text exception to singleton copy limits: cards that say a deck may contain
     * any number of them. Checked against printed text rather than a hardcoded name list,
     * so new printings need no code change.
     */
    public boolean allowsAnyNumber() {
        String text = oracleText == null ? "" : oracleText;
        if (text.contains("A deck can have any number of cards named")) {
            return true;
        }
        return faces.stream()
                .map(CardFace::oracleText)
                .filter(java.util.Objects::nonNull)
                .anyMatch(t -> t.contains("A deck can have any number of cards named"));
    }

    /** The price used only to pick a default printing on import - never shown, never in the economy. */
    public Optional<Double> usdPrice() {
        for (String key : List.of("usd", "usd_foil", "usd_etched")) {
            String raw = prices.get(key);
            if (raw != null && !raw.isBlank()) {
                try {
                    return Optional.of(Double.parseDouble(raw));
                } catch (NumberFormatException ignored) {
                    // A malformed price is not worth failing an import over.
                }
            }
        }
        return Optional.empty();
    }
}
