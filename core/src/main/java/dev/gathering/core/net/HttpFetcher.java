package dev.gathering.core.net;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * The manners every outbound request in this mod is made with.
 * <p>One request at a time behind a {@link RateLimiter}, a bounded number of attempts, a
 * backoff that doubles between them, and one rule about which failures are worth trying
 * again: a 429 or a 5xx is the far end having a moment, while a 404 is an answer and a 400 is
 * this mod's mistake. A 429 also holds the limiter itself back, so the other threads asking
 * the same place slow down rather than earning one of their own. Interruption is honored rather than swallowed, because these calls sit on a
 * pool that gets shut down when a world closes.
 * <p>Here rather than inside one API's client because the mod talks to three different places
 * - card data, deck sites, collation - and being a good citizen at all of them should not be
 * three copies of the same loop drifting apart.
 * <p>This blocks. It is pure core with an injected transport, so it knows nothing about
 * threads; keeping it off every game thread is the adapter layer's job.
 */
public final class HttpFetcher {

    public static final int DEFAULT_MAX_ATTEMPTS = 4;
    public static final long DEFAULT_BACKOFF_MILLIS = 500L;

    /** "You are asking too often." Not a failure of this request, but of the rate. */
    private static final int TOO_MANY = 429;

    /** As far as the pause doubles, so a long outage does not become a long sleep. */
    private static final int DOUBLINGS = 4;

    private final HttpTransport transport;
    private final RateLimiter rateLimiter;
    private final int maxAttempts;
    private final long backoffMillis;
    private final RateLimiter.Sleeper sleeper;

    public HttpFetcher(HttpTransport transport, RateLimiter rateLimiter) {
        this(transport, rateLimiter, DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF_MILLIS, Thread::sleep);
    }

    public HttpFetcher(
            HttpTransport transport,
            RateLimiter rateLimiter,
            int maxAttempts,
            long backoffMillis,
            RateLimiter.Sleeper sleeper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMillis = Math.max(0, backoffMillis);
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * @param description what this request is for, in words that can go in front of a person
     */
    public HttpTransport.HttpReply get(String url, Map<String, String> headers, String description)
            throws IOException {
        return send(() -> transport.get(url, headers), description);
    }

    public HttpTransport.HttpReply post(
            String url, String body, Map<String, String> headers, String description)
            throws IOException {
        return send(() -> transport.post(url, body, headers), description);
    }

    private HttpTransport.HttpReply send(Request request, String description) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("Interrupted before " + description, e);
            }

            HttpTransport.HttpReply reply;
            try {
                reply = request.send();
            } catch (IOException e) {
                lastFailure = new FetchException(description + " failed", e);
                backoff(attempt, description);
                continue;
            }

            if (reply.isSuccess() || reply.status() == 404) {
                return reply;
            }
            if (!reply.isRetryable()) {
                throw new FetchException(
                        description + " returned HTTP " + reply.status(), reply.status());
            }
            lastFailure = new FetchException(
                    description + " returned HTTP " + reply.status(), reply.status());
            if (reply.status() == TOO_MANY) {
                // Everything else waiting on this limiter waits too. Retrying this one page
                // while the other nine carry on at full speed earns nine more of these.
                rateLimiter.holdOff(pauseAfter(attempt));
            }
            backoff(attempt, description);
        }
        throw lastFailure != null
                ? lastFailure
                : new FetchException(description + " failed after " + maxAttempts + " attempts", -1);
    }

    /**
     * How long to wait before the next attempt: doubling, rather than adding.
     * <p>Three tries five hundred milliseconds apart is not long enough for a far end that
     * has asked to be left alone - it is barely longer than the burst that annoyed it. The
     * doubling stops after a few, because past that the mod is not being throttled, it is
     * down, and a player waiting on a card list would rather hear so.
     */
    private long pauseAfter(int attempt) {
        return backoffMillis << Math.min(DOUBLINGS, Math.max(0, attempt - 1));
    }

    private void backoff(int attempt, String description) throws FetchException {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            sleeper.sleep(pauseAfter(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException(
                    "Interrupted while backing off before retrying " + description, e);
        }
    }

    @FunctionalInterface
    private interface Request {
        HttpTransport.HttpReply send() throws IOException;
    }
}
