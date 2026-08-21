package dev.gathering.core.scryfall;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * The server-side card metadata cache, on disk as JSON.
 *
 * <p>One file per printing holding Scryfall's own response, sharded two characters deep so
 * no directory ends up with a hundred thousand entries. Cards are parsed on demand rather
 * than all at startup, so a large cache costs disk and not heap.
 *
 * <p>All of this is blocking file I/O and belongs on the same dedicated executor as the
 * HTTP client. Nothing here may be called from a game thread.
 */
public final class DiskCardMetadataStore extends InMemoryCardMetadataStore {

    private static final String CARDS_DIR = "cards";

    private final Path root;

    public DiskCardMetadataStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve(CARDS_DIR));
    }

    @Override
    public Optional<CardMetadata> find(CardQuery query) {
        Optional<CardMetadata> indexed = super.find(query);
        if (indexed.isPresent()) {
            return indexed;
        }
        // Only an id query can find its file without an index; name and printing lookups
        // depend on cards having been indexed, which happens as they are stored or loaded.
        if (query instanceof CardQuery.ById byId) {
            return loadFromDisk(byId.id());
        }
        return Optional.empty();
    }

    @Override
    public void store(CardMetadata card, JsonObject raw) {
        super.store(card, raw);
        if (card == null || card.scryfallId() == null || raw == null) {
            return;
        }
        try {
            Path file = fileFor(card.scryfallId());
            Files.createDirectories(file.getParent());
            Files.writeString(file, raw.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write card cache entry for " + card.scryfallId(), e);
        }
    }

    /**
     * Reads every cached card into the name and printing indexes.
     *
     * <p>Costs one pass over the cache directory, which is why it is an explicit call rather
     * than something the constructor does: a server that only ever resolves by id never
     * needs to pay for it.
     */
    public int loadIndex() throws IOException {
        Path cards = root.resolve(CARDS_DIR);
        if (!Files.isDirectory(cards)) {
            return 0;
        }
        int loaded = 0;
        try (var stream = Files.walk(cards)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                if (readCard(file).isPresent()) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    public Path root() {
        return root;
    }

    private Optional<CardMetadata> loadFromDisk(UUID id) {
        Path file = fileFor(id);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return readCard(file);
    }

    private Optional<CardMetadata> readCard(Path file) {
        try {
            JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject json = element.getAsJsonObject();
            Optional<CardMetadata> card = ScryfallCardCodec.parse(json);
            // Indexing here is what makes a second lookup - by name or printing - a hit.
            card.ifPresent(value -> super.store(value, json));
            return card;
        } catch (IOException | RuntimeException e) {
            // A corrupt cache entry is a cache miss, never a failed import. It will be
            // rewritten the next time the card is fetched.
            return Optional.empty();
        }
    }

    private Path fileFor(UUID id) {
        String name = id.toString();
        return root.resolve(CARDS_DIR).resolve(name.substring(0, 2)).resolve(name + ".json");
    }
}
