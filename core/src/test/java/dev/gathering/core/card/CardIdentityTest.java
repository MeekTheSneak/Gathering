package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardIdentityTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");

    @Test
    void printingIdentityCarriesTheScryfallId() {
        CardIdentity identity = CardIdentity.ofPrinting(SOL_RING, true);

        assertThat(identity.printing()).contains(SOL_RING);
        assertThat(identity.foil()).isTrue();
        assertThat(identity.isCustom()).isFalse();
        assertThat(identity.custom()).isEmpty();
    }

    @Test
    void customIdentityCarriesTheCustomId() {
        CardIdentity identity = CardIdentity.ofCustom("myserver:goblin_king", false);

        assertThat(identity.isCustom()).isTrue();
        assertThat(identity.custom()).contains("myserver:goblin_king");
        assertThat(identity.printing()).isEmpty();
    }

    @Test
    @DisplayName("a custom id can never collide with a Scryfall id, because it is never both")
    void neverBothNeverNeither() {
        assertThatThrownBy(() -> new CardIdentity(SOL_RING, false, "custom"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardIdentity(null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardIdentity(null, false, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void foilIsPartOfIdentityButSwappable() {
        CardIdentity plain = CardIdentity.ofPrinting(SOL_RING);
        CardIdentity foil = plain.withFoil(true);

        assertThat(plain).isNotEqualTo(foil);
        assertThat(foil.printing()).contains(SOL_RING);
        assertThat(plain.withFoil(false)).isSameAs(plain);
    }

    @Test
    void cacheKeysDistinguishFinishesAndNamespaces() {
        assertThat(CardIdentity.ofPrinting(SOL_RING).cacheKey()).isEqualTo(SOL_RING.toString());
        assertThat(CardIdentity.ofPrinting(SOL_RING, true).cacheKey()).isEqualTo(SOL_RING + ":foil");
        assertThat(CardIdentity.ofCustom("srv:x", false).cacheKey()).isEqualTo("custom:srv:x");
    }
}
