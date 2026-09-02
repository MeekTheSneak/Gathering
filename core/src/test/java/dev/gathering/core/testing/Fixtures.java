package dev.gathering.core.testing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.scryfall.ScryfallCardCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Real Scryfall responses, saved verbatim.
 * <p>Hand-written fixtures test the parser we imagined; these test the one Scryfall
 * actually talks to, including the fields nobody remembers exist.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static JsonObject json(String name) {
        String path = "/scryfall/" + name + ".json";
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + path);
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CardMetadata card(String name) {
        return ScryfallCardCodec.parse(json(name)).orElseThrow();
    }

    /** Wraps fixtures in the envelope the collection endpoint returns. */
    public static String collectionResponse(String... fixtureNames) {
        JsonObject response = new JsonObject();
        com.google.gson.JsonArray data = new com.google.gson.JsonArray();
        for (String name : fixtureNames) {
            data.add(json(name));
        }
        response.addProperty("object", "list");
        response.add("data", data);
        response.add("not_found", new com.google.gson.JsonArray());
        return response.toString();
    }
}
