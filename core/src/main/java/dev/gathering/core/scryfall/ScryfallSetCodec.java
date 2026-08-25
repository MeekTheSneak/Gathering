package dev.gathering.core.scryfall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.gathering.core.card.SetRelease;
import java.util.ArrayList;
import java.util.List;

/**
 * Scryfall's list of every set, read.
 *
 * <p>Only the six fields that decide which set is current. The reply is a megabyte of set
 * descriptions and none of the rest of it is worth carrying around: what a set is called on
 * the shelf, what kind it is, when it came out, and whether it exists on paper.
 *
 * <p>A row missing a field it needs is skipped rather than refused. Scryfall's list runs back
 * to 1993 and includes things that barely fit the shape of a set; one strange row is not a
 * reason for a server to have no current set at all.
 *
 * <p>Pure.
 */
public final class ScryfallSetCodec {

    private ScryfallSetCodec() {
    }

    /** Every set in a {@code /sets} reply, in the order it arrived. */
    public static List<SetRelease> parseList(JsonObject reply) {
        if (reply == null) {
            return List.of();
        }
        JsonElement data = reply.get("data");
        if (data == null || !data.isJsonArray()) {
            return List.of();
        }
        JsonArray rows = data.getAsJsonArray();
        List<SetRelease> sets = new ArrayList<>(rows.size());
        for (JsonElement row : rows) {
            if (!row.isJsonObject()) {
                continue;
            }
            JsonObject set = row.getAsJsonObject();
            String code = text(set, "code");
            if (code.isEmpty()) {
                continue;
            }
            sets.add(new SetRelease(
                    code,
                    text(set, "name"),
                    text(set, "set_type"),
                    text(set, "released_at"),
                    flag(set, "digital"),
                    number(set, "card_count")));
        }
        return List.copyOf(sets);
    }

    private static String text(JsonObject set, String field) {
        JsonElement value = set.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static boolean flag(JsonObject set, String field) {
        JsonElement value = set.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    private static int number(JsonObject set, String field) {
        JsonElement value = set.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        long count = value.getAsLong();
        return count < 0 || count > Integer.MAX_VALUE ? 0 : (int) count;
    }
}
