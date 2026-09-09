package dev.gathering.core.net;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Where card art may be fetched from, and how much of it.
 * <p>The client draws a card by fetching the picture at a URL the server put in the card's
 * summary. That URL is Scryfall's - every one of the sixteen hundred in a real cache is on
 * {@code cards.scryfall.io} - but the client had no way of knowing that, and fetched whatever
 * string arrived. A server that wanted to could hand every client at the table an address on
 * that client's own network, or a cloud metadata endpoint, and have it fetched from inside;
 * or a response with no end to it, read into one byte array.
 * <p>The brief trusts the server host with the game. It does not follow that a client should
 * fetch anything the host names. {@link dev.gathering.core.deck.DeckLink} made the same call
 * for the server's own fetches - "the host list is an allowlist, and that is a security
 * decision rather than tidiness" - and this is that decision made on the client too.
 * <p>Pure, so it is a test rather than a hope.
 */
public final class ArtHosts {

    /** The one host card scans come from, and the domains Scryfall serves art under. */
    private static final String[] ALLOWED_SUFFIXES = {".scryfall.io", ".scryfall.com"};

    /**
     * The most a picture may be.
     * <p>A crisp scan is under two megabytes; the largest Scryfall serves is well under four.
     * Eight is room for either to grow and no room for a response that never ends.
     */
    public static final int MOST_BYTES = 8 << 20;

    private ArtHosts() {
    }

    /**
     * Whether a client should fetch this.
     * <p>HTTPS only, and only from Scryfall's own domains. Anything else - another scheme, an
     * address, a host that merely contains the word - is not fetched, and the card is drawn as
     * one whose art is missing rather than as a hole in somebody's network.
     */
    public static boolean isAllowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException notAUri) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String suffix : ALLOWED_SUFFIXES) {
            // The bare domain and its subdomains, never a host that only ends the same way.
            if (host.endsWith(suffix) || host.equals(suffix.substring(1))) {
                return true;
            }
        }
        return false;
    }
}
