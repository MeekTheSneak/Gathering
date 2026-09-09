package dev.gathering.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The client fetches card art from Scryfall and from nowhere else.
 * <p>It used to fetch whatever URL the server put in a card's summary. The server host is
 * trusted with the game, not with every client's network.
 */
class ArtHostsTest {

    @Test
    @DisplayName("Scryfall's own art hosts are allowed")
    void scryfallIsAllowed() {
        assertThat(ArtHosts.isAllowed(
                "https://cards.scryfall.io/normal/front/6/a/6a0b230b.jpg?1783909057")).isTrue();
        assertThat(ArtHosts.isAllowed("https://c1.scryfall.com/file/scryfall-cards/png/x.png")).isTrue();
        assertThat(ArtHosts.isAllowed("https://svgs.scryfall.io/sets/fdn.svg")).isTrue();
        assertThat(ArtHosts.isAllowed("https://scryfall.io/anything")).isTrue();
        assertThat(ArtHosts.isAllowed("HTTPS://CARDS.SCRYFALL.IO/x.jpg")).isTrue();
    }

    @Test
    @DisplayName("a host that only contains the word is not Scryfall")
    void lookalikesAreNot() {
        assertThat(ArtHosts.isAllowed("https://cards.scryfall.io.evil.example/x.jpg")).isFalse();
        assertThat(ArtHosts.isAllowed("https://notscryfall.io/x.jpg")).isFalse();
        assertThat(ArtHosts.isAllowed("https://scryfall.io.attacker.net/x.jpg")).isFalse();
        assertThat(ArtHosts.isAllowed("https://evil.example/cards.scryfall.io/x.jpg")).isFalse();
    }

    @Test
    @DisplayName("nothing on the client's own network, and nothing but https")
    void nothingInsideAndNothingPlain() {
        assertThat(ArtHosts.isAllowed("http://cards.scryfall.io/x.jpg")).isFalse();
        assertThat(ArtHosts.isAllowed("https://169.254.169.254/latest/meta-data/")).isFalse();
        assertThat(ArtHosts.isAllowed("https://localhost/x.jpg")).isFalse();
        assertThat(ArtHosts.isAllowed("https://192.168.1.1/x.jpg")).isFalse();
        assertThat(ArtHosts.isAllowed("file:///etc/passwd")).isFalse();
        assertThat(ArtHosts.isAllowed("https://user:pw@cards.scryfall.io/x.jpg")).isFalse();
    }

    @Test
    @DisplayName("nothing, and not a URL, are not fetched either")
    void junkIsNotFetched() {
        assertThat(ArtHosts.isAllowed(null)).isFalse();
        assertThat(ArtHosts.isAllowed("")).isFalse();
        assertThat(ArtHosts.isAllowed("   ")).isFalse();
        assertThat(ArtHosts.isAllowed("not a url")).isFalse();
        assertThat(ArtHosts.isAllowed("https://")).isFalse();
    }

    @Test
    @DisplayName("a picture has a size")
    void aPictureHasASize() {
        assertThat(ArtHosts.MOST_BYTES).isGreaterThan(4 << 20).isLessThanOrEqualTo(16 << 20);
    }
}
