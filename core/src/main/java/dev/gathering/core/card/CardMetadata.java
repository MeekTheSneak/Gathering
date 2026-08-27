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
        // The front face's line, not the whole card's. Scryfall joins a double-faced card's
        // types as "Instant // Land", and a word search over that makes a land of Malakir
        // Rebirth - whose front face is the instant, and whose front face is what a card is
        // everywhere but the battlefield (CR 712.4a). Cascade stops on it; a fetch skips it.
        String line = frontFace().map(CardFace::typeLine).orElse(typeLine);
        if (line == null) {
            line = typeLine;
        }
        if (line == null) {
            return false;
        }
        for (String word : line.split("[^A-Za-z]+")) {
            if (word.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Read as "any number" wherever a printed copy allowance comes back with it. */
    public static final int ANY_NUMBER = Integer.MAX_VALUE;

    private static final java.util.regex.Pattern UP_TO_COPIES =
            java.util.regex.Pattern.compile("A deck can have up to (\\w+) cards named");

    /**
     * The copy allowance the card's own text grants, when it grants one.
     *
     * <p>"A deck can have any number of cards named ..." - Relentless Rats, Persistent
     * Petitioners - comes back as {@link #ANY_NUMBER}. "A deck can have up to seven cards
     * named Seven Dwarves." comes back as seven; Nazgul's nine as nine. Both read off the
     * printed text rather than a maintained name list, so a new printing needs no code
     * change - which is also why the second template matters: matching only the first made
     * the deck check call a legal seven-Dwarves deck a rules violation, and a check that
     * cries wolf is the one kind of rules enforcement this mod does do.
     */
    public java.util.OptionalInt printedCopyAllowance() {
        java.util.OptionalInt fromCard = allowanceIn(oracleText);
        if (fromCard.isPresent()) {
            return fromCard;
        }
        return faces.stream()
                .map(CardFace::oracleText)
                .map(CardMetadata::allowanceIn)
                .filter(java.util.OptionalInt::isPresent)
                .findFirst()
                .orElse(java.util.OptionalInt.empty());
    }

    private static java.util.OptionalInt allowanceIn(String text) {
        if (text == null) {
            return java.util.OptionalInt.empty();
        }
        if (text.contains("A deck can have any number of cards named")) {
            return java.util.OptionalInt.of(ANY_NUMBER);
        }
        java.util.regex.Matcher upTo = UP_TO_COPIES.matcher(text);
        if (upTo.find()) {
            int count = numberFrom(upTo.group(1));
            if (count > 0) {
                return java.util.OptionalInt.of(count);
            }
        }
        return java.util.OptionalInt.empty();
    }

    /** Wizards writes the number out as a word; nobody should have to guess which ones. */
    private static int numberFrom(String word) {
        List<String> words = List.of("one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen");
        int index = words.indexOf(word.toLowerCase(java.util.Locale.ROOT));
        if (index >= 0) {
            return index + 1;
        }
        try {
            return Integer.parseInt(word);
        } catch (NumberFormatException impossibleToRead) {
            return 0;
        }
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
