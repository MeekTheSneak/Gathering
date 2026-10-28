package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads Scryfall's list of bulk files and picks the one this mod keeps.
 * <p>{@code default_cards}: every printing, in English or in the one language a card was printed
 * in. Not {@code all_cards}, which is every language's copy of everything and five times the size
 * for cards {@link dev.gathering.core.card.ForeignPrintings} would throw away anyway.
 * <p>Scryfall has published the file two ways: a JSON array at {@code download_uri}, and gzipped
 * JSON lines at {@code jsonl_download_uri}. At the time of writing only the second is listed, so
 * the second is preferred and the first kept as a fallback; {@link BulkCardReader} reads either,
 * compressed or not, by looking at the bytes rather than trusting the name.
 * <p>The address comes out of a reply, so it is checked against the one host Scryfall serves
 * bulk files from before anything fetches it. Every other address this mod reaches is built from
 * parts it checked, and a reply is not a part it checked.
 */
public final class BulkCatalog {

    /** The file kept. */
    public static final String DEFAULT_CARDS = "default_cards";

    /** Where Scryfall serves bulk files from, and the only place one is fetched from. */
    static final String BULK_HOST = "data.scryfall.io";

    private BulkCatalog() {
    }

    /**
     * One bulk file as the catalog describes it.
     *
     * @param updatedAt Scryfall's own stamp for this edition of the file, compared as text: it is
     *                  only ever asked whether it changed
     * @param uri       where to fetch it, already checked
     */
    public record Entry(String updatedAt, String uri) {
    }

    /** The default cards file, or empty when the reply names none this will fetch. */
    public static Optional<Entry> defaultCards(String body) {
        JsonObject reply;
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            reply = parsed.getAsJsonObject();
        } catch (RuntimeException notJson) {
            return Optional.empty();
        }
        JsonElement data = reply.get("data");
        if (data == null || !data.isJsonArray()) {
            return Optional.empty();
        }
        for (JsonElement element : data.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            if (!DEFAULT_CARDS.equals(text(file, "type"))) {
                continue;
            }
            String updatedAt = text(file, "updated_at");
            if (updatedAt == null || updatedAt.isBlank()) {
                return Optional.empty();
            }
            String uri = allowed(text(file, "jsonl_download_uri"))
                    .or(() -> allowed(text(file, "download_uri")))
                    .orElse(null);
            return uri == null ? Optional.empty() : Optional.of(new Entry(updatedAt.trim(), uri));
        }
        return Optional.empty();
    }

    /**
     * An address, if it is an https address on Scryfall's bulk host and nothing else.
     * <p>No user info, no port but the default, no other host however similar - a reply that
     * pointed this at an internal address would otherwise have the server fetch it.
     */
    static Optional<String> allowed(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(raw.trim());
            boolean ok = "https".equals(uri.getScheme())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getHost() != null
                    && BULK_HOST.equals(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getRawPath() != null && uri.getRawPath().startsWith("/");
            return ok ? Optional.of(uri.toString()) : Optional.empty();
        } catch (java.net.URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
