package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seed does not print.
 * <p>At rest it is sealed, and StoredSessionTest byte-scans for that. During play the only thing
 * standing between it and a log line or a stack trace is {@code toString}, and nothing asserted
 * that - deleting the override, the single most likely way it would escape, was a silent change.
 */
class SessionSeedTest {

    @Test
    @DisplayName("printing a seed names no part of it")
    void aSeedDoesNotPrintItself() {
        SessionSeed seed = SessionSeed.random();
        byte[] material = seed.toBytes();
        String printed = seed.toString();
        String concatenated = "seed " + seed + " in " + java.util.List.of(seed);

        // Any four bytes of it in a row, as hex either case, is too much.
        String hex = HexFormat.of().formatHex(material);
        for (int start = 0; start + 8 <= hex.length(); start += 2) {
            String run = hex.substring(start, start + 8);
            assertThat(printed.toLowerCase()).doesNotContain(run);
            assertThat(concatenated.toLowerCase()).doesNotContain(run);
        }
        assertThat(printed).doesNotContain(java.util.Arrays.toString(material));
        assertThat(printed).doesNotContain(java.util.Base64.getEncoder().encodeToString(material));
    }
}
