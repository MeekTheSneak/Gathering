package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ForeignPrintings;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.ScryfallCardCodec;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * Every printing Scryfall publishes, on this disk, answered without asking Scryfall.
 * <p>What is held in memory is what finding a card takes and nothing more: each printing's id,
 * where its JSON is, its price and release date for ordering, its set and number, and the name and
 * set lists. The card itself stays packed on disk and is read and parsed when somebody asks for it,
 * with the last few thousand kept parsed.
 * <p>The answers are shaped as Scryfall's own for the same question: a card's printings cheapest
 * first and without another language's copies, a set's in collector-number order, tokens one per
 * distinct token and newest first. Where Scryfall's search decides something this cannot see - a
 * tie in price, say - the order is still fixed, just not necessarily Scryfall's.
 * <p>Safe to share between threads. Reading is blocking file I/O, and never belongs on a game
 * thread; {@link #contains} is the one question that touches no file.
 */
public final class BulkCardIndex implements AutoCloseable {

    /** How many parsed cards are kept, which is a few decks or a set being opened. */
    private static final int PARSED_KEPT = 1024;

    private final String name;
    private final String updatedAt;
    private final long builtAt;
    private final long[] high;
    private final long[] low;
    private final long[] offsets;
    private final int[] lengths;
    private final int[] released;
    private final float[] prices;
    private final byte[] flags;
    private final String[] setCodes;
    private final String[] numbers;
    private final Map<String, int[]> byName;
    private final Map<String, int[]> bySet;
    private final String[] tokenNames;
    private final FileChannel data;

    private final Map<Integer, CardMetadata> parsed = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CardMetadata> eldest) {
            return size() > PARSED_KEPT;
        }
    };

    private BulkCardIndex(String name, String updatedAt, long builtAt, long[] high, long[] low, long[] offsets,
            int[] lengths, int[] released, float[] prices, byte[] flags, String[] setCodes, String[] numbers,
            Map<String, int[]> byName, Map<String, int[]> bySet, FileChannel data) {
        this.name = name;
        this.updatedAt = updatedAt;
        this.builtAt = builtAt;
        this.high = high;
        this.low = low;
        this.offsets = offsets;
        this.lengths = lengths;
        this.released = released;
        this.prices = prices;
        this.flags = flags;
        this.setCodes = setCodes;
        this.numbers = numbers;
        this.byName = byName;
        this.bySet = bySet;
        this.data = data;
        List<String> tokens = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : byName.entrySet()) {
            for (int ordinal : entry.getValue()) {
                if ((flags[ordinal] & BulkIndexFiles.TOKEN) != 0) {
                    tokens.add(entry.getKey());
                    break;
                }
            }
        }
        tokens.sort(null);
        this.tokenNames = tokens.toArray(String[]::new);
    }

    /** The name of the index the pointer file names, if it names one this would open. */
    static Optional<String> currentName(Path bulkDir) {
        Path pointer = bulkDir.resolve(BulkIndexFiles.CURRENT);
        try {
            if (!Files.isRegularFile(pointer) || Files.size(pointer) > 256) {
                return Optional.empty();
            }
            String named = Files.readString(pointer, StandardCharsets.UTF_8).strip();
            return BulkIndexFiles.INDEX_NAME.matcher(named).matches() ? Optional.of(named) : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Opens the current index.
     *
     * @return empty when there is none yet
     * @throws IOException when there is one and it cannot be trusted - another version's, cut short,
     *                     or not matching its data file. The caller builds a new one.
     */
    public static Optional<BulkCardIndex> open(Path bulkDir) throws IOException {
        Optional<String> current = currentName(bulkDir);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Path dir = bulkDir.resolve(current.get());
        Path indexFile = dir.resolve(BulkIndexFiles.INDEX_FILE);
        Path dataFile = dir.resolve(BulkIndexFiles.DATA_FILE);
        if (!Files.isRegularFile(indexFile) || !Files.isRegularFile(dataFile)) {
            throw new IOException("The card index " + current.get() + " is missing a file");
        }
        CRC32 crc = new CRC32();
        try (InputStream file = Files.newInputStream(indexFile)) {
            DataInputStream in = new DataInputStream(new CheckedInputStream(new BufferedInputStream(file, 1 << 16), crc));
            if (in.readInt() != BulkIndexFiles.MAGIC) {
                throw new IOException("The card index " + current.get() + " is not a card index");
            }
            int version = in.readInt();
            if (version != BulkIndexFiles.VERSION) {
                throw new IOException("The card index " + current.get() + " is version " + version
                        + ", and this reads version " + BulkIndexFiles.VERSION);
            }
            String updatedAt = in.readUTF();
            long builtAt = in.readLong();
            long dataLength = in.readLong();
            if (dataLength != Files.size(dataFile)) {
                throw new IOException("The card index " + current.get() + " does not match its data file");
            }
            int count = in.readInt();
            if (count <= 0 || count > BulkIndexFiles.MOST_CARDS) {
                throw new IOException("The card index " + current.get() + " says it holds " + count + " cards");
            }
            long[] high = new long[count];
            long[] low = new long[count];
            long[] offsets = new long[count];
            int[] lengths = new int[count];
            int[] released = new int[count];
            float[] prices = new float[count];
            byte[] flags = new byte[count];
            String[] setCodes = new String[count];
            String[] numbers = new String[count];
            Map<String, String> sharedSets = new HashMap<>();
            for (int i = 0; i < count; i++) {
                high[i] = in.readLong();
                low[i] = in.readLong();
                offsets[i] = in.readLong();
                lengths[i] = in.readInt();
                prices[i] = in.readFloat();
                released[i] = in.readInt();
                flags[i] = in.readByte();
                String set = in.readUTF();
                setCodes[i] = sharedSets.computeIfAbsent(set, same -> same);
                numbers[i] = in.readUTF();
                if (offsets[i] < 0 || lengths[i] <= 0 || offsets[i] + lengths[i] > dataLength) {
                    throw new IOException("The card index " + current.get() + " points outside its data file");
                }
                if (i > 0 && compare(high[i - 1], low[i - 1], high[i], low[i]) >= 0) {
                    throw new IOException("The card index " + current.get() + " is out of order");
                }
            }
            Map<String, int[]> byName = readLists(in, count, current.get());
            Map<String, int[]> bySet = readLists(in, count, current.get());
            long computed = crc.getValue();
            if (in.readLong() != computed || in.readInt() != BulkIndexFiles.END) {
                throw new IOException("The card index " + current.get() + " is damaged");
            }
            FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ);
            return Optional.of(new BulkCardIndex(current.get(), updatedAt, builtAt, high, low, offsets, lengths,
                    released, prices, flags, setCodes, numbers, byName, bySet, channel));
        } catch (java.io.EOFException | java.io.UTFDataFormatException | IllegalArgumentException cutShort) {
            throw new IOException("The card index " + current.get() + " is cut short or damaged", cutShort);
        }
    }

    private static Map<String, int[]> readLists(DataInputStream in, int count, String name) throws IOException {
        int keys = in.readInt();
        if (keys < 0 || keys > BulkIndexFiles.MOST_CARDS * 4) {
            throw new IOException("The card index " + name + " has a list count that cannot be right");
        }
        Map<String, int[]> lists = new HashMap<>(Math.max(16, keys * 4 / 3 + 1));
        for (int k = 0; k < keys; k++) {
            String key = in.readUTF();
            int size = in.readInt();
            if (size <= 0 || size > count) {
                throw new IOException("The card index " + name + " has a list that cannot be right");
            }
            int[] ordinals = new int[size];
            for (int i = 0; i < size; i++) {
                int ordinal = in.readInt();
                if (ordinal < 0 || ordinal >= count) {
                    throw new IOException("The card index " + name + " names a card it does not have");
                }
                ordinals[i] = ordinal;
            }
            lists.put(key, ordinals);
        }
        return lists;
    }

    /** Which build this is, by its directory's name. */
    public String name() {
        return name;
    }

    /** Scryfall's stamp for the file this was built from. */
    public String updatedAt() {
        return updatedAt;
    }

    /** When this was built, by this server's clock, in milliseconds. */
    public long builtAt() {
        return builtAt;
    }

    /**
     * How old what this says is: Scryfall's stamp for the file, or when it was built if that will
     * not read. A card answered from here is as current as this and no more.
     */
    public Instant asOf() {
        try {
            return java.time.OffsetDateTime.parse(updatedAt).toInstant();
        } catch (java.time.format.DateTimeParseException notADate) {
            return Instant.ofEpochMilli(builtAt);
        }
    }

    /** How many printings this holds. */
    public int size() {
        return high.length;
    }

    /** Whether this printing is here, without reading anything off disk. */
    public boolean contains(UUID id) {
        return id != null && ordinalOf(id) >= 0;
    }

    /** One printing by its id. */
    public Optional<CardMetadata> byId(UUID id) throws IOException {
        int ordinal = id == null ? -1 : ordinalOf(id);
        return ordinal < 0 ? Optional.empty() : Optional.ofNullable(card(ordinal));
    }

    /**
     * The same answer a cache lookup gives for a query, from here.
     * <p>A name is answered with the cheapest printing that a search for the name would list, which
     * is what the in-memory store keeps under a name and what an import means by a line that names
     * no printing. A name in a set is the cheapest printing of it in that set.
     */
    public Optional<CardMetadata> find(CardQuery query) throws IOException {
        return switch (query) {
            case CardQuery.ById byIdQuery -> byId(byIdQuery.id());
            case CardQuery.ByPrinting printing -> byPrinting(printing.setCode(), printing.collectorNumber());
            case CardQuery.ByName named -> first(named.name(), null);
            case CardQuery.ByNameInSet inSet -> first(inSet.name(), inSet.setCode());
        };
    }

    /** One printing by set and collector number. */
    public Optional<CardMetadata> byPrinting(String setCode, String collectorNumber) throws IOException {
        if (setCode == null || collectorNumber == null) {
            return Optional.empty();
        }
        String number = BulkIndexFiles.lower(collectorNumber).strip();
        for (int ordinal : bySet.getOrDefault(BulkIndexFiles.lower(setCode).strip(), new int[0])) {
            if (numbers[ordinal].equals(number)) {
                return Optional.ofNullable(card(ordinal));
            }
        }
        return Optional.empty();
    }

    private Optional<CardMetadata> first(String name, String setCode) throws IOException {
        String wantedSet = setCode == null ? null : BulkIndexFiles.lower(setCode).strip();
        List<Integer> candidates = new ArrayList<>();
        for (int ordinal : printingOrdinals(name)) {
            if (wantedSet == null || setCodes[ordinal].equals(wantedSet)) {
                candidates.add(ordinal);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // The list puts unpriced printings first, as Scryfall's price sort does; the pick for a
        // name puts them last, as the in-memory store does, so "Lightning Bolt" is not whichever
        // online-only promo nobody has priced. Stable, so equal prices keep the list's order.
        candidates.sort((a, b) -> Float.compare(
                Float.isNaN(prices[a]) ? Float.MAX_VALUE : prices[a],
                Float.isNaN(prices[b]) ? Float.MAX_VALUE : prices[b]));
        if ((flags[candidates.get(0)] & BulkIndexFiles.ENGLISH) != 0) {
            // An English printing is never somebody's copy, so the cheapest one heading the list is
            // the answer without reading the other nine hundred Forests to find out.
            return Optional.ofNullable(card(candidates.get(0)));
        }
        List<CardMetadata> kept = ForeignPrintings.withoutCopies(cards(candidates));
        return kept.isEmpty() ? Optional.empty() : Optional.of(kept.get(0));
    }

    /**
     * Every printing of a card, cheapest first, as the printing chooser lists them.
     * <p>The combined name of a split or double-faced card finds it by its first half, as the
     * network lookup does. The first page's worth, then without another language's copies.
     */
    public List<CardMetadata> printingsOf(String cardName) throws IOException {
        int[] ordinals = printingOrdinals(cardName);
        List<Integer> page = new ArrayList<>();
        for (int i = 0; i < ordinals.length && page.size() < BulkIndexFiles.SEARCH_PAGE; i++) {
            page.add(ordinals[i]);
        }
        return ForeignPrintings.withoutCopies(cards(page));
    }

    private int[] printingOrdinals(String cardName) {
        if (cardName == null || cardName.isBlank()) {
            return new int[0];
        }
        int[] all = byName.getOrDefault(BulkIndexFiles.nameKey(CardQuery.lookupName(cardName)), new int[0]);
        return Arrays.stream(all).filter(ordinal -> (flags[ordinal] & BulkIndexFiles.HIDDEN) == 0).toArray();
    }

    /**
     * Every printing in a set, in collector-number order.
     *
     * @return empty when this index has never heard of the set - a set newer than the file, most
     *         likely - so the caller asks Scryfall rather than taking "no cards" for an answer
     */
    public Optional<List<CardMetadata>> printingsIn(String setCode) throws IOException {
        int[] ordinals = setCode == null ? null : bySet.get(BulkIndexFiles.lower(setCode).strip());
        if (ordinals == null) {
            return Optional.empty();
        }
        List<Integer> all = new ArrayList<>(ordinals.length);
        for (int ordinal : ordinals) {
            all.add(ordinal);
        }
        return Optional.of(cards(all));
    }

    /**
     * Tokens with this name, one per distinct token, newest first.
     * <p>Exactly the name first, and only when nothing has exactly it, every token whose name has it
     * in - the same two steps the network lookup takes, for the same reason. One printing stands for
     * each token: its newest English one, or its newest at all.
     */
    public List<CardMetadata> tokensNamed(String tokenName) throws IOException {
        String key = BulkIndexFiles.nameKey(tokenName == null ? "" : tokenName.replace("\"", ""));
        if (key.isEmpty()) {
            return List.of();
        }
        List<Integer> exactly = tokenOrdinals(byName.getOrDefault(key, new int[0]));
        if (!exactly.isEmpty()) {
            return newestOfEach(exactly);
        }
        List<Integer> loosely = new ArrayList<>();
        for (String token : tokenNames) {
            if (token.contains(key)) {
                loosely.addAll(tokenOrdinals(byName.get(token)));
            }
        }
        return newestOfEach(loosely);
    }

    private List<Integer> tokenOrdinals(int[] ordinals) {
        List<Integer> tokens = new ArrayList<>();
        for (int ordinal : ordinals) {
            if ((flags[ordinal] & BulkIndexFiles.TOKEN) != 0 && !tokens.contains(ordinal)) {
                tokens.add(ordinal);
            }
        }
        return tokens;
    }

    private List<CardMetadata> newestOfEach(List<Integer> ordinals) throws IOException {
        List<Integer> newestFirst = new ArrayList<>(ordinals);
        newestFirst.sort((a, b) -> {
            boolean aEnglish = (flags[a] & BulkIndexFiles.ENGLISH) != 0;
            boolean bEnglish = (flags[b] & BulkIndexFiles.ENGLISH) != 0;
            if (aEnglish != bEnglish) {
                return aEnglish ? -1 : 1;
            }
            return Integer.compare(released[b], released[a]);
        });
        Map<Object, Integer> chosen = new LinkedHashMap<>();
        Map<Integer, CardMetadata> cards = new HashMap<>();
        for (int ordinal : newestFirst) {
            CardMetadata card = card(ordinal);
            if (card == null) {
                continue;
            }
            Object token = card.oracleId() != null ? card.oracleId() : card.scryfallId();
            if (chosen.putIfAbsent(token, ordinal) == null) {
                cards.put(ordinal, card);
            }
        }
        List<Integer> picked = new ArrayList<>(chosen.values());
        picked.sort((a, b) -> Integer.compare(released[b], released[a]));
        List<CardMetadata> out = new ArrayList<>(picked.size());
        for (int ordinal : picked) {
            out.add(cards.get(ordinal));
        }
        return List.copyOf(out);
    }

    private List<CardMetadata> cards(List<Integer> ordinals) throws IOException {
        List<CardMetadata> out = new ArrayList<>(ordinals.size());
        for (int ordinal : ordinals) {
            CardMetadata card = card(ordinal);
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    /** One printing, parsed, from memory if it was read lately and off disk if not. */
    private CardMetadata card(int ordinal) throws IOException {
        synchronized (parsed) {
            CardMetadata kept = parsed.get(ordinal);
            if (kept != null) {
                return kept;
            }
        }
        ByteBuffer buffer = ByteBuffer.allocate(lengths[ordinal]);
        long position = offsets[ordinal];
        while (buffer.hasRemaining()) {
            int read = data.read(buffer, position + buffer.position());
            if (read < 0) {
                throw new IOException("The card data file ended early");
            }
        }
        JsonElement json;
        try {
            json = JsonParser.parseString(BulkIndexFiles.unpack(buffer.array()));
        } catch (RuntimeException notJson) {
            throw new IOException("A card in the index could not be read", notJson);
        }
        CardMetadata card = json.isJsonObject() ? ScryfallCardCodec.parse(json.getAsJsonObject()).orElse(null) : null;
        if (card != null) {
            synchronized (parsed) {
                parsed.put(ordinal, card);
            }
        }
        return card;
    }

    private int ordinalOf(UUID id) {
        long wantHigh = id.getMostSignificantBits();
        long wantLow = id.getLeastSignificantBits();
        int from = 0;
        int to = high.length - 1;
        while (from <= to) {
            int middle = (from + to) >>> 1;
            int order = compare(high[middle], low[middle], wantHigh, wantLow);
            if (order < 0) {
                from = middle + 1;
            } else if (order > 0) {
                to = middle - 1;
            } else {
                return middle;
            }
        }
        return -1;
    }

    private static int compare(long aHigh, long aLow, long bHigh, long bLow) {
        int order = Long.compareUnsigned(aHigh, bHigh);
        return order != 0 ? order : Long.compareUnsigned(aLow, bLow);
    }

    /** Every set code this index has cards for. */
    public Set<String> setCodes() {
        return Set.copyOf(bySet.keySet());
    }

    @Override
    public void close() throws IOException {
        data.close();
    }
}
