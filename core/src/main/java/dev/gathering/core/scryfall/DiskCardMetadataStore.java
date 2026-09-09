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
 * <p>One file per printing holding Scryfall's own response, sharded two characters deep so
 * no directory ends up with a hundred thousand entries. Cards are parsed on demand rather
 * than all at startup, so a large cache costs disk and not heap.
 * <p>All of this is blocking file I/O and belongs on the same dedicated executor as the
 * HTTP client. Nothing here may be called from a game thread.
 */
public final class DiskCardMetadataStore extends InMemoryCardMetadataStore {

    private static final String CARDS_DIR = "cards";

    private final Path root;

    /**
     * When each printing this process has seen was last written, so asking is a map lookup.
     * <p>The age of a cached card is asked once per deck check, which happens on the game
     * thread - and asking the filesystem a hundred times there is a hundred stat calls in a
     * tick for a question that has the same answer every time. Filled as cards are stored and
     * as they are read off disk; the disk is only consulted for a printing this process has
     * not touched.
     */
    private final java.util.Map<UUID, java.time.Instant> cachedWhen =
            new java.util.concurrent.ConcurrentHashMap<>();

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

    /**
     * When this printing was last read from upstream, if it is on this disk at all.
     * <p>A cached card is returned for ever, which is right for what a printing is - a name,
     * a picture, a mana cost - and wrong for the one part of the same record that changes
     * under it: what is legal where. Bans and rotations happen to cards nobody has looked up
     * since. The file's own age is the answer, so nothing has to be written to ask it.
     */
    public Optional<java.time.Instant> cachedAt(java.util.UUID printing) {
        if (printing == null) {
            return Optional.empty();
        }
        Optional<java.time.Instant> known = cachedAtInMemory(printing);
        if (known.isPresent()) {
            return known;
        }
        Path file = fileFor(printing);
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            java.time.Instant when = Files.getLastModifiedTime(file).toInstant();
            cachedWhen.put(printing, when);
            return Optional.of(when);
        } catch (IOException cannotTell) {
            return Optional.empty();
        }
    }

    /**
     * The same answer, but only for a printing this process has already touched.
     * <p>What a caller on the game thread asks. {@link #cachedAt} will go to the disk for a
     * printing it has not seen, which is right on the card executor and wrong in a tick.
     */
    public Optional<java.time.Instant> cachedAtInMemory(java.util.UUID printing) {
        return printing == null ? Optional.empty() : Optional.ofNullable(cachedWhen.get(printing));
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
            // Remembered as of now rather than read back off the file, so a refresh that
            // actually happened is visible to the next freshness check without a stat.
            cachedWhen.put(card.scryfallId(), java.time.Instant.now());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write card cache entry for " + card.scryfallId(), e);
        }
    }

    /**
     * Reads every cached card into the name and printing indexes.
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
            // super.store rather than this.store, so reading a file back does not rewrite it
            // and does not make an old entry look freshly fetched. The age comes off the file.
            card.ifPresent(value -> {
                super.store(value, json);
                try {
                    cachedWhen.put(value.scryfallId(), Files.getLastModifiedTime(file).toInstant());
                } catch (IOException cannotTell) {
                    // Then it is asked of the disk next time, which is what used to happen
                    // every time.
                }
            });
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
