package dev.gathering.core.scryfall;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The three indexes, and the ways they must not shadow one another. */
class InMemoryCardMetadataStoreTest {

    @Test
    @DisplayName("a cheaper printing from another set does not hide the one a decklist named")
    void byNameInSetSurvivesACheaperPrintingElsewhere() {
        // The name index keeps the single cheapest printing across all sets - the right
        // default for a line that names no set. Answering the named-set query through that
        // same index made it a permanent miss whenever the cheapest printing was from some
        // other set: the store held the card, said it did not, and the import re-fetched
        // the same "Name (SET)" line from the network every time.
        InMemoryCardMetadataStore store = new InMemoryCardMetadataStore();
        CardMetadata expensive = printing("Sol Ring", "ltc", "10.00");
        CardMetadata cheap = printing("Sol Ring", "cmr", "1.00");
        store.store(expensive, null);
        store.store(cheap, null);

        assertThat(store.find(CardQuery.byName("Sol Ring"))).contains(cheap);
        assertThat(store.find(CardQuery.byNameInSet("Sol Ring", "ltc"))).contains(expensive);
        assertThat(store.find(CardQuery.byNameInSet("Sol Ring", "cmr"))).contains(cheap);
        assertThat(store.find(CardQuery.byNameInSet("Sol Ring", "who"))).isEmpty();
    }

    private static CardMetadata printing(String name, String setCode, String usd) {
        return new CardMetadata(UUID.randomUUID(), UUID.randomUUID(), name, "{1}", 1,
                "Artifact", null, null, null, null, "normal", setCode, setCode, "1",
                Rarity.UNCOMMON, false, true, true, false, false, List.of("paper"),
                null, Map.of("usd", usd), null);
    }
}
