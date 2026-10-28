package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one rule about what may go in a URL and a file name. */
class SetCodeTest {

    @Test
    @DisplayName("real set codes are set codes, however they were typed")
    void realSetCodesPass() {
        // Whitespace round the outside is somebody's config file, not a different code.
        for (String code : new String[] {
                "blb", "BLB", " dmu ", "lea\n", "40k", "10e", "plst", "pdmu"}) {
            assertThat(SetCode.of(code)).as(code).isPresent();
        }
        assertThat(SetCode.of("  BLB ")).contains("blb");
        assertThat(SetCode.upper("blb")).contains("BLB");
    }

    @Test
    @DisplayName("anything that could walk out of a directory or bend a URL is not one")
    void anythingElseIsRefused() {
        for (String notACode : new String[] {
                "", "   ", null, "../../etc/passwd", "blb/../x", "blb.json", "b lb", "blb\"",
                "https://elsewhere.example/x", "set:blb", "toolongaset", "blb%2f", "b\nlb"}) {
            assertThat(SetCode.of(notACode)).as(String.valueOf(notACode)).isEmpty();
            assertThat(SetCode.isOne(notACode)).isFalse();
            assertThat(SetCode.upper(notACode)).isEmpty();
        }
    }

    @Test
    @DisplayName("the length limit is a bound rather than a rounding")
    void theLengthLimitIsExact() {
        assertThat(SetCode.of("a".repeat(SetCode.LONGEST))).isPresent();
        assertThat(SetCode.of("a".repeat(SetCode.LONGEST + 1))).isEmpty();
    }
}
