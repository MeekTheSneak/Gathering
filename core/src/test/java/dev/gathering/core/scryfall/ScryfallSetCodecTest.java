package dev.gathering.core.scryfall;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.SetRelease;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading Scryfall's list of every set. */
class ScryfallSetCodecTest {

    /** Rows in the shape Scryfall really sends, taken from a live reply. */
    private static final String REAL = """
            {"object": "list", "has_more": false, "data": [
              {"object": "set", "id": "fbbad14d-1d42-4204-ade6-911876dc7c06", "code": "slz",
               "name": "The Zeta Set", "released_at": "2026-12-31", "set_type": "box",
               "card_count": 21, "digital": false, "nonfoil_only": true, "foil_only": false},
              {"object": "set", "code": "hob", "name": "The Hobbit",
               "released_at": "2026-08-14", "set_type": "expansion",
               "card_count": 321, "digital": false},
              {"object": "set", "code": "y26", "name": "Alchemy Horizons",
               "released_at": "2026-09-01", "set_type": "alchemy",
               "card_count": 30, "digital": true}
            ]}""";

    @Test
    @DisplayName("a real reply reads, and the current set falls out of it")
    void aRealReplyReads() {
        List<SetRelease> sets = ScryfallSetCodec.parseList(parse(REAL));

        assertThat(sets).hasSize(3);
        assertThat(sets.get(1).code()).isEqualTo("hob");
        assertThat(sets.get(1).cardCount()).isEqualTo(321);
        assertThat(sets.get(1).isPremier()).isTrue();
        assertThat(sets.get(2).digital()).isTrue();
        assertThat(SetRelease.current(sets, "2026-08-25"))
                .map(SetRelease::code).contains("hob");
    }

    @Test
    @DisplayName("a strange row is skipped, not a reason to have no set at all")
    void oneStrangeRowDoesNotSinkTheList() {
        // The list runs back to 1993 and holds things that barely fit the shape of a set.
        JsonObject reply = parse("""
                {"data": [
                  "not an object",
                  {"object": "set", "name": "no code at all", "set_type": "expansion"},
                  {"code": "hob", "set_type": "expansion", "released_at": "2026-08-14",
                   "card_count": "three hundred", "digital": "no"}
                ]}""");

        List<SetRelease> sets = ScryfallSetCodec.parseList(reply);

        assertThat(sets).hasSize(1);
        assertThat(sets.getFirst().code()).isEqualTo("hob");
        // A count that is not a number and a flag that is not a boolean read as absent
        // rather than throwing: neither decides anything here.
        assertThat(sets.getFirst().cardCount()).isZero();
        assertThat(sets.getFirst().digital()).isFalse();
        assertThat(sets.getFirst().isPremier()).isTrue();
    }

    @Test
    @DisplayName("a reply that is not a list reads as no sets")
    void nonsenseIsNoSets() {
        assertThat(ScryfallSetCodec.parseList(null)).isEmpty();
        assertThat(ScryfallSetCodec.parseList(parse("{}"))).isEmpty();
        assertThat(ScryfallSetCodec.parseList(parse("{\"data\": \"soon\"}"))).isEmpty();
        assertThat(ScryfallSetCodec.parseList(parse("{\"data\": []}"))).isEmpty();
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
