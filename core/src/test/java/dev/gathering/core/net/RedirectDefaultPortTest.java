package dev.gathering.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A redirect that spells out the default port is still the same origin.
 * <p>The rule is "same scheme and host", and it compared the ports as written too - so a
 * {@code Location} of {@code https://host:443/...} compared 443 against the request's -1 and was
 * refused. The move this following exists for, MTGJSON's {@code CON.json} to {@code CON_.json}, failed
 * whenever the far end wrote the port.
 */
@DisplayName("A same-origin redirect")
class RedirectDefaultPortTest {

    private static final Map<String, String> NO_HEADERS = Map.of();

    @Test
    @DisplayName("a same-origin redirect that spells out :443 is followed")
    void defaultPortSpelledOutIsStillTheSameOrigin() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport();
        transport.replyWith(new HttpTransport.HttpReply(301, "", 0L, "https://mtgjson.com:443/api/v5/CON_.json"));
        transport.reply(200, "conflux");
        HttpFetcher fetcher = new HttpFetcher(
                transport, new RateLimiter(0L, System::currentTimeMillis, ignored -> { }), 4, 500L, ignored -> { });

        assertThat(fetcher.get("https://mtgjson.com/api/v5/CON.json", NO_HEADERS, "Conflux").body())
                .isEqualTo("conflux");
    }
}
