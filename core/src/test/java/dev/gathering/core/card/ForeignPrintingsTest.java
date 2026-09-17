package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForeignPrintingsTest {

    private static CardMetadata printing(String set, String language, String picture) {
        return new CardMetadata(UUID.randomUUID(), UUID.randomUUID(), "Dark Ritual", "{B}", 1, "Instant", "",
                Set.of("B"), Set.of("B"), List.of(), "normal", set, set, "1", Rarity.COMMON, false, false, true, false,
                false, List.of("paper"), Map.of(), Map.of(), "", List.of(), false, language, picture);
    }

    @Test
    @DisplayName("the chooser leaves out another language's copy of an English printing, and keeps a card of its own")
    void copiesLeaveTheChooser() {
        CardMetadata english = printing("3ed", "en", "revised-art");
        CardMetadata spanishCopy = printing("4bb", "es", "revised-art");
        CardMetadata japanesePromo = printing("pjsc", "ja", "promo-art");

        assertThat(ForeignPrintings.withoutCopies(List.of(spanishCopy, english, japanesePromo)))
                .containsExactly(english, japanesePromo);
    }

    @Test
    @DisplayName("a set keeps its own printings in another language, and a set of copies keeps none")
    void setsKeepTheirOwnForeignPrintings() {
        CardMetadata english = printing("sos", "en", "a");
        CardMetadata bonusSheet = printing("sos", "ja", "b");
        assertThat(ForeignPrintings.keptIn(List.of(english, bonusSheet), "expansion")).containsExactly(english, bonusSheet);

        CardMetadata cupPromo = printing("pjjt", "ja", "c");
        assertThat(ForeignPrintings.keptIn(List.of(cupPromo), "promo")).containsExactly(cupPromo);

        CardMetadata renaissance = printing("ren", "fr", "d");
        assertThat(ForeignPrintings.keptIn(List.of(renaissance), "masters")).isEmpty();
    }
}
