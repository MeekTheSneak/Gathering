package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Scryfall's bulk card file, one card at a time.
 * <p>The file is over six hundred megabytes of JSON unpacked. Read whole, it is a server out of
 * memory; read like this, it is one card's worth of objects at any moment, handed on and dropped
 * before the next is read.
 * <p>Either shape Scryfall publishes - a JSON array, or one card per line - and either gzipped or
 * not, told apart by the bytes: a gzip stream starts with its magic number, an array with a
 * bracket and a line of JSON with a brace. A name or a header saying which is a claim; the bytes
 * are the thing.
 */
public final class BulkCardReader {

    /** What each card is handed to. */
    @FunctionalInterface
    public interface Sink {
        void accept(JsonObject card) throws IOException;
    }

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;

    /** A reply compressed once by the host and once by its name is still a file; three is not. */
    private static final int MOST_GZIP_LAYERS = 2;

    private BulkCardReader() {
    }

    /**
     * Reads every card object in the stream into the sink, in file order.
     *
     * @return how many objects were handed on
     */
    public static long read(InputStream in, Sink sink) throws IOException {
        InputStream unpacked = unpacked(in);
        Reader text = new InputStreamReader(unpacked, StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(text);
        // Lenient so that one card per line, which is several top-level values in a row, reads
        // as a sequence rather than as a document with something after its end.
        reader.setLenient(true);
        long count = 0;
        JsonToken first = reader.peek();
        if (first == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                count += handOn(JsonParser.parseReader(reader), sink);
            }
            reader.endArray();
            return count;
        }
        while (reader.peek() != JsonToken.END_DOCUMENT) {
            count += handOn(JsonParser.parseReader(reader), sink);
        }
        return count;
    }

    private static int handOn(JsonElement element, Sink sink) throws IOException {
        if (!element.isJsonObject()) {
            return 0;
        }
        sink.accept(element.getAsJsonObject());
        return 1;
    }

    /** The stream with every gzip layer taken off, however many the host and the file added. */
    static InputStream unpacked(InputStream in) throws IOException {
        InputStream current = in.markSupported() ? in : new BufferedInputStream(in, 1 << 16);
        for (int layer = 0; layer < MOST_GZIP_LAYERS && isGzip(current); layer++) {
            current = new BufferedInputStream(new GZIPInputStream(current, 1 << 16), 1 << 16);
        }
        return current;
    }

    private static boolean isGzip(InputStream in) throws IOException {
        in.mark(2);
        int first = in.read();
        int second = in.read();
        in.reset();
        return first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND;
    }
}
