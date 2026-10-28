package dev.gathering.core.scryfall.bulk;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.gathering.core.net.StallingServer;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bulk card file gives up when it stops arriving.
 * <p>It has no total deadline on purpose - a hundred megabytes on a slow line takes as long as it
 * takes - and its request timeout lasts until the reply starts. So a download that went quiet without
 * closing was waited on for good, on the one thread the bulk refresh has.
 */
@DisplayName("The bulk card file's download")
class BulkDownloadStallTest {

    @Test
    @DisplayName("gives up once nothing has come for its deadline")
    void aDownloadThatGoesQuietGivesUp() throws IOException {
        try (StallingServer far = new StallingServer(StallingServer.Reply.STALL)) {
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                InputStream body = new BulkDownload.Jdk(Duration.ofSeconds(1)).open(far.url(), Map.of());
                try (body) {
                    assertThatThrownBy(body::readAllBytes)
                            .isInstanceOf(IOException.class)
                            .hasMessageContaining("stopped arriving");
                }
            });
        }
    }
}
