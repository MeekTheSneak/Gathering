package dev.gathering.core.deck;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A link to a deck on a site we know how to read.
 *
 * <p><b>The host list is an allowlist, and that is a security decision rather than tidiness.</b>
 * This is user input that makes the <em>server</em> issue an HTTP request. Fetching whatever
 * URL a player pastes would let any player point the server at anything it can reach - a
 * cloud metadata endpoint, a service on the host's own network, an internal admin page - and
 * read back the result. So the only addresses the server will ever fetch are the exact hosts
 * named here, and a link to anything else is not a deck link at all.
 */
public record DeckLink(DeckLink.Provider provider, String deckId) {

    public enum Provider {
        /** Public deck API, and it hands back Scryfall ids, so imports resolve exactly. */
        ARCHIDEKT("Archidekt", true),

        /**
         * Recognized so the player gets told what to do, never fetched.
         *
         * <p>Moxfield's API answers third-party requests with 403. Working around an access
         * control somebody deliberately put up is not something this mod is going to do, so
         * a Moxfield link asks the player for the text export instead.
         */
        MOXFIELD("Moxfield", false);

        private final String displayName;
        private final boolean fetchable;

        Provider(String displayName, boolean fetchable) {
            this.displayName = displayName;
            this.fetchable = fetchable;
        }

        public String displayName() {
            return displayName;
        }

        /** Whether the mod can read a deck from this site, as opposed to merely recognizing it. */
        public boolean isFetchable() {
            return fetchable;
        }
    }

    private static final Pattern ARCHIDEKT = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?archidekt\\.com/decks/(\\d+)(?:[/?#].*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern MOXFIELD = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?moxfield\\.com/decks/([A-Za-z0-9_-]+)(?:[/?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    /** Recognizes a link, or returns empty for anything that is not one we will fetch. */
    public static Optional<DeckLink> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.strip();
        if (trimmed.isEmpty() || trimmed.contains("\n")) {
            return Optional.empty();
        }

        Matcher archidekt = ARCHIDEKT.matcher(trimmed);
        if (archidekt.matches()) {
            return Optional.of(new DeckLink(Provider.ARCHIDEKT, archidekt.group(1)));
        }
        Matcher moxfield = MOXFIELD.matcher(trimmed);
        if (moxfield.matches()) {
            return Optional.of(new DeckLink(Provider.MOXFIELD, moxfield.group(1)));
        }
        return Optional.empty();
    }

    /** Whether the whole of a pasted box is one link rather than a decklist. */
    public static boolean isOnlyALink(String input) {
        return input != null && parse(input.strip()).isPresent();
    }

    /**
     * The exact address to fetch. Built here from the id rather than from the pasted string,
     * so nothing a player typed reaches the network verbatim.
     */
    public String apiUrl() {
        return switch (provider) {
            case ARCHIDEKT -> "https://archidekt.com/api/decks/" + deckId + "/";
            case MOXFIELD -> throw new UnsupportedOperationException(
                    "Moxfield blocks third-party API access; a Moxfield link is never fetched");
        };
    }

    @Override
    public String toString() {
        return provider.displayName() + " deck " + deckId;
    }

    public String describeUnfetchable() {
        return provider.displayName() + " does not allow other tools to read decks from it. "
                + "Open the deck there, choose More, Export, Text, and paste that instead.";
    }

    static String normalizeHost(String host) {
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }
}
