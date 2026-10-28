package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The sleeves a deck can be in, and what happens to one that arrives damaged. */
class SleeveTest {

    @Test
    @DisplayName("a place in the list that does not exist reads as the ordinary back")
    void anImpossibleSleeveIsThePlainOne() {
        // Both of these come off a socket: a payload's number and a board view's ordinal.
        // Throwing on either would turn a corrupt packet into a disconnect over a picture.
        assertThat(Sleeve.byOrdinal(-1)).isEqualTo(Sleeve.DEFAULT);
        assertThat(Sleeve.byOrdinal(Sleeve.values().length)).isEqualTo(Sleeve.DEFAULT);
        assertThat(Sleeve.byOrdinal(Integer.MAX_VALUE)).isEqualTo(Sleeve.DEFAULT);
        assertThat(Sleeve.named("no such sleeve")).isEqualTo(Sleeve.DEFAULT);
        assertThat(Sleeve.named(null)).isEqualTo(Sleeve.DEFAULT);
    }

    @Test
    @DisplayName("every sleeve survives its own name and its own place")
    void everySleeveRoundTrips() {
        for (Sleeve sleeve : Sleeve.values()) {
            assertThat(Sleeve.named(sleeve.name())).isEqualTo(sleeve);
            assertThat(Sleeve.byOrdinal(sleeve.ordinal())).isEqualTo(sleeve);
        }
    }

    @Test
    @DisplayName("only the classic one is drawn on the printed back")
    void onlyOneIsPrinted() {
        for (Sleeve sleeve : Sleeve.values()) {
            assertThat(sleeve.isPrinted()).isEqualTo(sleeve == Sleeve.CLASSIC);
        }
        // The printed back is never tinted, so its tint has to be the one that changes
        // nothing - otherwise the one drawing path would have to special-case it.
        assertThat(Sleeve.CLASSIC.tint()).isEqualTo(0xFFFFFF);
    }

    @Test
    @DisplayName("every picture names a real Minecraft texture, and nothing else does")
    void emblemsAreVanillaTextures() {
        int withPictures = 0;
        for (Sleeve sleeve : Sleeve.values()) {
            if (!sleeve.hasEmblem()) {
                assertThat(sleeve.emblem()).isEmpty();
                continue;
            }
            withPictures++;
            // Minecraft's own art, never ours: the mod ships no vanilla textures, it points
            // at them. A path into our own namespace here would be a picture nobody painted.
            assertThat(sleeve.emblem()).startsWith("minecraft:textures/");
            assertThat(sleeve.emblem()).endsWith(".png");
        }
        assertThat(withPictures).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("the plain sleeves are the sixteen dyes, each its own color")
    void thePlainOnesAreTheSixteen() {
        java.util.Set<Integer> tints = new java.util.LinkedHashSet<>();
        for (Sleeve sleeve : Sleeve.values()) {
            if (sleeve.isPrinted() || sleeve.hasEmblem()) {
                continue;
            }
            tints.add(sleeve.tint());
        }
        assertThat(tints).hasSize(16);
    }
}
