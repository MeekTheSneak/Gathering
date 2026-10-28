package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;

/**
 * Turns Scryfall's bulk card file into the local index, beside the one already there.
 * <p>Everything is written into a directory of its own first. Only when both files are whole and
 * on disk does the new directory get its final name and the pointer to it get replaced, and each
 * of those is one atomic rename. A crash, a full disk or a failed download at any point before
 * that leaves the index the server was already using exactly as it was, and the half-built one is
 * swept up the next time.
 * <p>Streams: one card's JSON at a time is in memory, plus a few dozen bytes per printing of what
 * the index keeps about it.
 * <p>Blocking file I/O. Never on a game thread.
 */
public final class BulkIndexBuilder {

    private BulkIndexBuilder() {
    }

    /** What the index keeps about one printing while it is being built. */
    private static final class Entry {
        final UUID id;
        final String setCode;
        final String collectorNumber;
        final List<String> names;
        final float usd;
        final int released;
        final byte flags;
        long offset;
        int length;

        Entry(UUID id, String setCode, String collectorNumber, List<String> names, float usd, int released,
                byte flags) {
            this.id = id;
            this.setCode = setCode;
            this.collectorNumber = collectorNumber;
            this.names = names;
            this.usd = usd;
            this.released = released;
            this.flags = flags;
        }

        static Entry of(JsonObject card) {
            String rawId = BulkIndexFiles.text(card, "id");
            if (rawId == null) {
                return null;
            }
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException notAnId) {
                return null;
            }
            String set = BulkIndexFiles.lower(BulkIndexFiles.text(card, "set")).strip();
            String number = BulkIndexFiles.lower(BulkIndexFiles.text(card, "collector_number")).strip();
            if (set.length() > BulkIndexFiles.LONGEST_KEY || number.length() > BulkIndexFiles.LONGEST_KEY) {
                return null;
            }
            return new Entry(id, set, number, List.copyOf(BulkIndexFiles.nameKeys(card)),
                    BulkIndexFiles.usd(card), BulkIndexFiles.released(card), BulkIndexFiles.flags(card));
        }
    }

    /**
     * Cheapest first, as Scryfall's {@code order=usd dir=asc} lists a card's printings.
     * <p>Printings with no price at all come first, which is where Scryfall puts them - compared
     * against its reply for Lightning Bolt, not assumed. Then the newest, then by set and number, so
     * the order never depends on the file's; how Scryfall breaks those ties is not known.
     */
    static final Comparator<Entry> CHEAPEST_FIRST = (a, b) -> {
        boolean aPriced = !Float.isNaN(a.usd);
        boolean bPriced = !Float.isNaN(b.usd);
        if (aPriced != bPriced) {
            return aPriced ? 1 : -1;
        }
        if (aPriced && a.usd != b.usd) {
            return Float.compare(a.usd, b.usd);
        }
        if (a.released != b.released) {
            return Integer.compare(b.released, a.released);
        }
        int bySet = a.setCode.compareTo(b.setCode);
        return bySet != 0 ? bySet : BulkIndexFiles.COLLECTOR_ORDER.compare(a.collectorNumber, b.collectorNumber);
    };

    private static final Comparator<Entry> BY_ID = (a, b) -> {
        int high = Long.compareUnsigned(a.id.getMostSignificantBits(), b.id.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.id.getLeastSignificantBits(), b.id.getLeastSignificantBits());
    };

    /**
     * Builds an index from this stream and makes it the current one.
     *
     * @param bulkDir   the directory every index lives under
     * @param updatedAt Scryfall's stamp for the file being read, kept so the next check can compare
     * @param builtAt   when, in milliseconds, so a check can tell how long ago it was
     * @return the name of the index directory now current
     * @throws IOException when the stream cannot be read to the end; nothing current has changed
     */
    public static String build(Path bulkDir, String updatedAt, long builtAt, InputStream source) throws IOException {
        Files.createDirectories(bulkDir);
        Path building = Files.createTempDirectory(bulkDir, BulkIndexFiles.BUILDING_PREFIX);
        try {
            List<Entry> entries = new ArrayList<>();
            long dataLength = writeData(building.resolve(BulkIndexFiles.DATA_FILE), source, entries);
            if (entries.isEmpty()) {
                throw new IOException("The bulk card file had no cards in it");
            }
            entries.sort(BY_ID);
            List<Entry> distinct = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                if (distinct.isEmpty() || !distinct.get(distinct.size() - 1).id.equals(entry.id)) {
                    distinct.add(entry);
                }
            }
            writeIndex(building.resolve(BulkIndexFiles.INDEX_FILE), updatedAt, builtAt, dataLength, distinct);

            String name = BulkIndexFiles.INDEX_PREFIX + builtAt + "-"
                    + building.getFileName().toString().substring(BulkIndexFiles.BUILDING_PREFIX.length())
                            .replaceAll("[^0-9A-Za-z]", "");
            Path finished = bulkDir.resolve(name);
            Files.move(building, finished, StandardCopyOption.ATOMIC_MOVE);
            building = null;
            pointAt(bulkDir, name);
            return name;
        } finally {
            if (building != null) {
                BulkIndexFiles.deleteQuietly(building);
            }
        }
    }

    /** Replaces the pointer in one rename, so a reader sees the old index or the new one. */
    static void pointAt(Path bulkDir, String name) throws IOException {
        Path part = bulkDir.resolve(BulkIndexFiles.CURRENT + ".part");
        Files.writeString(part, name, StandardCharsets.UTF_8);
        Files.move(part, bulkDir.resolve(BulkIndexFiles.CURRENT),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Deletes whatever under the bulk directory is not the current index.
     * <p>Half-built indexes, downloads that never finished, and the index that was current before
     * the last build. Anything still in use where the system will not let it go is left for next
     * time.
     */
    public static void sweep(Path bulkDir) {
        if (!Files.isDirectory(bulkDir)) {
            return;
        }
        String current = BulkCardIndex.currentName(bulkDir).orElse(null);
        try (var listing = Files.list(bulkDir)) {
            for (Path each : listing.toList()) {
                String name = each.getFileName().toString();
                boolean leftover = name.startsWith(BulkIndexFiles.BUILDING_PREFIX)
                        || name.startsWith(BulkIndexFiles.DOWNLOAD_PREFIX)
                        || (name.startsWith(BulkIndexFiles.INDEX_PREFIX) && !name.equals(current));
                if (leftover) {
                    BulkIndexFiles.deleteQuietly(each);
                }
            }
        } catch (IOException unreadable) {
            // Nothing swept this time; nothing depends on it.
        }
    }

    private static long writeData(Path file, InputStream source, List<Entry> entries) throws IOException {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (FileOutputStream fileOut = new FileOutputStream(file.toFile());
                BufferedOutputStream out = new BufferedOutputStream(fileOut, 1 << 16)) {
            long[] offset = {0L};
            try {
                BulkCardReader.read(source, card -> {
                    Entry entry = Entry.of(card);
                    if (entry == null) {
                        return;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        // The server is stopping. A build that carried on regardless would still be
                        // writing into this directory while the next world's service opened it.
                        throw new java.io.InterruptedIOException("Stopped while building the card index");
                    }
                    if (entries.size() >= BulkIndexFiles.MOST_CARDS) {
                        throw new IOException("The bulk card file has more cards in it than any real one could");
                    }
                    // Compact, whole: every field Scryfall sent, so a field read later is already here.
                    byte[] packed = BulkIndexFiles.pack(deflater, card.toString().getBytes(StandardCharsets.UTF_8));
                    out.write(packed);
                    entry.offset = offset[0];
                    entry.length = packed.length;
                    offset[0] += packed.length;
                    entries.add(entry);
                });
            } catch (RuntimeException malformed) {
                // Gson says a broken file with an unchecked exception. It is still a broken file.
                throw new IOException("The bulk card file could not be read", malformed);
            }
            out.flush();
            fileOut.getChannel().force(true);
            return offset[0];
        } finally {
            deflater.end();
        }
    }

    private static void writeIndex(Path file, String updatedAt, long builtAt, long dataLength, List<Entry> entries)
            throws IOException {
        Map<String, List<Integer>> names = new TreeMap<>();
        Map<String, List<Integer>> sets = new TreeMap<>();
        for (int ordinal = 0; ordinal < entries.size(); ordinal++) {
            Entry entry = entries.get(ordinal);
            for (String name : entry.names) {
                names.computeIfAbsent(name, key -> new ArrayList<>()).add(ordinal);
            }
            if (!entry.setCode.isEmpty()) {
                sets.computeIfAbsent(entry.setCode.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(ordinal);
            }
        }
        // Sorted once here, so a lookup only ever filters.
        names.values().forEach(list -> list.sort(Comparator.comparing(entries::get, CHEAPEST_FIRST)));
        sets.values().forEach(list -> list.sort(Comparator.comparing(
                (Integer ordinal) -> entries.get(ordinal).collectorNumber, BulkIndexFiles.COLLECTOR_ORDER)));

        try (FileOutputStream fileOut = new FileOutputStream(file.toFile())) {
            CRC32 crc = new CRC32();
            DataOutputStream out = new DataOutputStream(
                    new CheckedOutputStream(new BufferedOutputStream(fileOut, 1 << 16), crc));
            out.writeInt(BulkIndexFiles.MAGIC);
            out.writeInt(BulkIndexFiles.VERSION);
            out.writeUTF(updatedAt);
            out.writeLong(builtAt);
            out.writeLong(dataLength);
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeLong(entry.id.getMostSignificantBits());
                out.writeLong(entry.id.getLeastSignificantBits());
                out.writeLong(entry.offset);
                out.writeInt(entry.length);
                out.writeFloat(entry.usd);
                out.writeInt(entry.released);
                out.writeByte(entry.flags);
                out.writeUTF(entry.setCode);
                out.writeUTF(entry.collectorNumber);
            }
            writeLists(out, names);
            writeLists(out, sets);
            out.flush();
            long sum = crc.getValue();
            out.writeLong(sum);
            out.writeInt(BulkIndexFiles.END);
            out.flush();
            fileOut.getChannel().force(true);
        }
    }

    private static void writeLists(DataOutputStream out, Map<String, List<Integer>> lists) throws IOException {
        out.writeInt(lists.size());
        for (Map.Entry<String, List<Integer>> list : lists.entrySet()) {
            out.writeUTF(list.getKey());
            out.writeInt(list.getValue().size());
            for (int ordinal : list.getValue()) {
                out.writeInt(ordinal);
            }
        }
    }
}
