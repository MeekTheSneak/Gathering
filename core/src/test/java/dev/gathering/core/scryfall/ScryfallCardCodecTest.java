package dev.gathering.core.scryfall;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Legality;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.testing.Fixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScryfallCardCodecTest {

    @Test
    void readsARealPrinting() {
        CardMetadata solRing = Fixtures.card("sol_ring");

        assertThat(solRing.scryfallId()).isEqualTo(UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba"));
        assertThat(solRing.name()).isEqualTo("Sol Ring");
        assertThat(solRing.setCode()).isEqualTo("ltc");
        assertThat(solRing.collectorNumber()).isEqualTo("284");
        assertThat(solRing.rarity()).isEqualTo(Rarity.UNCOMMON);
        assertThat(solRing.typeLine()).contains("Artifact");
        assertThat(solRing.oracleId()).isNotNull();
        assertThat(solRing.legalityIn("commander")).isEqualTo(Legality.LEGAL);
        assertThat(solRing.usdPrice()).isPresent();
    }

    @Test
    @DisplayName("a transform card's faces each keep their own text and art")
    void readsBothFacesOfATransformCard() {
        CardMetadata delver = Fixtures.card("delver_of_secrets");

        assertThat(delver.layout()).isEqualTo("transform");
        assertThat(delver.faces()).hasSize(2);
        assertThat(delver.faces().get(0).oracleText()).isNotBlank();
        assertThat(delver.faces().get(1).power()).isEqualTo("3");
        assertThat(delver.faces().get(0).imageUris().normal())
                .isNotEqualTo(delver.faces().get(1).imageUris().normal());
    }

    @Test
    @DisplayName("a split card is two lots of text but only one picture")
    void aSplitCardCarriesItsOneImageOnTheFrontFaceOnly() {
        // Fire // Ice is two halves printed on one piece of card, so Scryfall publishes one
        // image at card level and none per face. Handing that image to both faces makes the
        // card look like two faces that happen to match, and everything that draws per face
        // then draws the same picture twice - which is what it did.
        CardMetadata fireIce = Fixtures.card("fire_ice");

        assertThat(fireIce.layout()).isEqualTo("split");
        assertThat(fireIce.faces()).hasSize(2);
        assertThat(fireIce.faces().get(0).name()).isEqualTo("Fire");
        assertThat(fireIce.faces().get(1).name()).isEqualTo("Ice");

        assertThat(fireIce.faces().get(0).hasImages()).isTrue();
        assertThat(fireIce.faces().get(1).hasImages()).isFalse();
    }

    @Test
    void unknownRaritiesAndLegalitiesDegradeRatherThanThrow() {
        JsonObject json = Fixtures.json("sol_ring");
        json.addProperty("rarity", "supermythic");
        json.getAsJsonObject("legalities").addProperty("commander", "sideways");

        CardMetadata card = ScryfallCardCodec.parse(json).orElseThrow();

        assertThat(card.rarity()).isEqualTo(Rarity.UNKNOWN);
        assertThat(card.legalityIn("commander")).isEqualTo(Legality.UNKNOWN);
    }

    @Test
    @DisplayName("an object that is not a card is a miss, not an exception")
    void nonCardObjectsAreEmpty() {
        assertThat(ScryfallCardCodec.parse(null)).isEmpty();
        assertThat(ScryfallCardCodec.parse(new JsonObject())).isEmpty();

        JsonObject error = JsonParser.parseString(
                "{\"object\":\"error\",\"status\":404,\"details\":\"No card found\"}").getAsJsonObject();
        assertThat(ScryfallCardCodec.parse(error)).isEmpty();
    }

    @Test
    void missingOptionalFieldsBecomeNullsRatherThanCrashes() {
        JsonObject json = Fixtures.json("sol_ring");
        json.remove("oracle_text");
        json.remove("prices");
        json.remove("legalities");
        json.remove("image_uris");
        json.remove("oracle_id");

        CardMetadata card = ScryfallCardCodec.parse(json).orElseThrow();

        assertThat(card.oracleText()).isNull();
        assertThat(card.oracleId()).isNull();
        assertThat(card.prices()).isEmpty();
        assertThat(card.legalities()).isEmpty();
        assertThat(card.images().isEmpty()).isTrue();
        assertThat(card.usdPrice()).isEmpty();
    }

    @Test
    void collectionEnvelopesKeepEachCardsRawJson() {
        JsonObject response = JsonParser
                .parseString(Fixtures.collectionResponse("sol_ring", "delver_of_secrets"))
                .getAsJsonObject();

        var entries = ScryfallCardCodec.parseCollectionEntries(response);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).raw().get("id").getAsString())
                .isEqualTo(entries.get(0).metadata().scryfallId().toString());
    }
}
