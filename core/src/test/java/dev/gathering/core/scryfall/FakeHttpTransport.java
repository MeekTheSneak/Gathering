package dev.gathering.core.scryfall;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** A transport that answers from a script and remembers everything it was asked. */
final class FakeHttpTransport implements HttpTransport {

    private final Deque<Object> scripted = new ArrayDeque<>();
    private final List<Recorded> requests = new ArrayList<>();

    FakeHttpTransport reply(int status, String body) {
        scripted.add(new HttpReply(status, body));
        return this;
    }

    FakeHttpTransport failWith(IOException failure) {
        scripted.add(failure);
        return this;
    }

    List<Recorded> requests() {
        return List.copyOf(requests);
    }

    int requestCount() {
        return requests.size();
    }

    @Override
    public HttpReply get(String url, Map<String, String> headers) throws IOException {
        return next(new Recorded("GET", url, null, headers));
    }

    @Override
    public HttpReply post(String url, String body, Map<String, String> headers) throws IOException {
        return next(new Recorded("POST", url, body, headers));
    }

    private HttpReply next(Recorded recorded) throws IOException {
        requests.add(recorded);
        Object scriptedReply = scripted.poll();
        if (scriptedReply == null) {
            throw new AssertionError("Unscripted request: " + recorded.method() + " " + recorded.url());
        }
        if (scriptedReply instanceof IOException failure) {
            throw failure;
        }
        return (HttpReply) scriptedReply;
    }

    record Recorded(String method, String url, String body, Map<String, String> headers) {
    }
}
