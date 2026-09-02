package dev.gathering.client;

/**
 * Everything on this client that goes and gets something, told once.
 * <p>Two things fetch: card art and set symbols, and both are asked to identify themselves as
 * the API guidelines they work to ask. Saying that to each of them from each loader is four
 * places to remember, and the symbol fetcher had already been forgotten in two of them - so
 * it is said here instead, once per loader. A third fetcher is one line rather than a bug
 * nobody notices until somebody else's server logs an unnamed client.
 * <p>Client-only.
 */
public final class ClientFetching {

    private ClientFetching() {
    }

    /** Called once at client init, before anything has had a chance to fetch. */
    public static void identifyAs(String agent) {
        ClientCardImages.get().identifyAs(agent);
        ClientSetSymbols.get().identifyAs(agent);
    }
}
