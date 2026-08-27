package dev.gathering.core.ante;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a server refuses to let anybody stake.
 *
 * <p>Written in the config as a list of plain words - {@code exclusions = ["basic lands"]} -
 * because the person setting it is deciding what their server's ante feels like, not writing
 * a query. A short vocabulary answers every reason anybody has for protecting a category:
 * lands nobody would miss, cards too good to lose, or a set an era server is built around.
 *
 * <p>An unrecognized word protects nothing and says so. It never protects everything and
 * never protects nothing silently: a typo that quietly turned the exclusion list off would
 * be a server where somebody loses a card they were told was safe, which is the one failure
 * this list exists to prevent.
 */
public record AnteExclusions(Set<String> categories) {

    /** Every basic land, which is what the shipped default protects. */
    public static final String BASIC_LANDS = "basic lands";

    /** Every land, basic or not. */
    public static final String LANDS = "lands";

    /** Rares and mythics both, for a server that wants ante to stay low stakes. */
    public static final String RARES = "rares";

    /** Mythics alone. */
    public static final String MYTHICS = "mythics";

    /** Foils, which on most servers are the cards people care about most. */
    public static final String FOILS = "foils";

    /** Everything this list knows how to protect. Anything else is a note in the log. */
    public static final Set<String> KNOWN =
            Set.of(BASIC_LANDS, LANDS, RARES, MYTHICS, FOILS);

    /** The exclusions a config's words came to, and what could not be read. */
    public record Reading(AnteExclusions exclusions, List<String> notes) {

        public Reading {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    public static final AnteExclusions NOTHING = new AnteExclusions(Set.of());

    public AnteExclusions {
        categories = categories == null ? Set.of() : Set.copyOf(categories);
    }

    /**
     * Reads a config's list, keeping what it understands and naming what it does not.
     *
     * <p>Case and surrounding space are the config author's business, not the rule's, so
     * {@code "Basic Lands"} and {@code "basic lands"} are the same thing. A word this does not
     * know is dropped with a note rather than guessed at.
     */
    public static Reading of(List<String> written) {
        if (written == null || written.isEmpty()) {
            return new Reading(NOTHING, List.of());
        }
        Set<String> kept = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();
        for (String word : written) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String tidied = word.strip().toLowerCase(Locale.ROOT);
            if (KNOWN.contains(tidied)) {
                kept.add(tidied);
            } else {
                notes.add("ante.exclusions does not know \"" + word.strip()
                        + "\", so nothing is protected by it; known are " + sortedKnown());
            }
        }
        return new Reading(new AnteExclusions(kept), notes);
    }

    private static String sortedKnown() {
        List<String> known = new ArrayList<>(KNOWN);
        java.util.Collections.sort(known);
        return String.join(", ", known);
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }

    /**
     * Whether this card may not be staked.
     *
     * <p>A card nothing is known about is protected. Everywhere else in the mod an unknown
     * card is a blank to be filled in later; here it is somebody's property about to change
     * hands, and the only safe answer to "is this one of the ones we agreed not to play for"
     * is no when nobody can tell.
     */
    public boolean protects(CardMetadata card, boolean foil) {
        if (card == null) {
            return true;
        }
        if (categories.contains(FOILS) && foil) {
            return true;
        }
        String types = card.typeLine() == null ? "" : card.typeLine().toLowerCase(Locale.ROOT);
        boolean land = types.contains("land");
        if (categories.contains(LANDS) && land) {
            return true;
        }
        if (categories.contains(BASIC_LANDS) && land && types.contains("basic")) {
            return true;
        }
        Rarity rarity = card.rarity();
        if (categories.contains(MYTHICS) && rarity == Rarity.MYTHIC) {
            return true;
        }
        return categories.contains(RARES)
                && (rarity == Rarity.RARE || rarity == Rarity.MYTHIC);
    }
}
