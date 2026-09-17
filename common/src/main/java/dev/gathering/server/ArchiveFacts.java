package dev.gathering.server;

import dev.gathering.core.booster.ArchiveAudit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the archive's audit learned about each set, kept on disk.
 * <p>Auditing a set is a search on somebody else's server. Done once, it does not need doing again
 * each time one of its archive packs is opened: a set's printings change rarely,
 * and when they do Scryfall's count for the set changes with them. So each set's facts are kept
 * beside the card cache with the count they were read at, and read again only when the count has
 * moved, a month has passed, or a set is now drawn from whose products were never read.
 * <p>Not per world. What a set printed is the same fact in every world on the machine; which of
 * it a server reaches is worked out from these at start, against that server's own settings.
 */
public final class ArchiveFacts {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String FOLDER = "archive-audit";
    // Two: a set's catalog leaves out printings in another language.
    private static final int VERSION = 2;

    /** How long a set's facts are trusted before they are read again anyway. */
    static final long FRESH_FOR_MILLIS = java.time.Duration.ofDays(30).toMillis();

    /** The most printings one set's file may claim, so a corrupt file cannot ask for an allocation. */
    private static final int MOST = 50_000;

    /** A set's facts, and when and at what size they were read. */
    public record Kept(ArchiveAudit.SetFacts facts, long readAt, int cardCount) {

        /** Whether these can stand for this set as Scryfall lists it now. */
        public boolean stillGood(int cardCountNow, long now, boolean needsReach) {
            return cardCount == cardCountNow && now - readAt < FRESH_FOR_MILLIS
                    && (!needsReach || facts.reachKnown());
        }
    }

    private ArchiveFacts() {
    }

    /** What is kept for this set, if anything readable is. */
    public static Optional<Kept> read(Path root, String code) {
        Path file = fileFor(root, code);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != VERSION) {
                return Optional.empty();
            }
            long readAt = in.readLong();
            int cardCount = in.readInt();
            boolean reachKnown = in.readBoolean();
            List<UUID> catalog = new ArrayList<>(ids(in));
            Set<UUID> boosters = ids(in);
            Set<UUID> products = ids(in);
            return Optional.of(new Kept(
                    new ArchiveAudit.SetFacts(code, catalog, boosters, products, reachKnown), readAt, cardCount));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** Keeps this set's facts, whole or not at all. */
    public static void write(Path root, Kept kept) {
        Path file = fileFor(root, kept.facts().code());
        if (file == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                out.writeLong(kept.readAt());
                out.writeInt(kept.cardCount());
                out.writeBoolean(kept.facts().reachKnown());
                write(out, kept.facts().catalog());
                write(out, kept.facts().inBoosters());
                write(out, kept.facts().inProducts());
            }
            Files.createDirectories(file.getParent());
            Path writing = file.resolveSibling(file.getFileName() + ".writing");
            Files.write(writing, bytes.toByteArray());
            Files.move(writing, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException couldNotWrite) {
            // Kept in memory for this run either way; the cost is reading the set again next start.
            LOGGER.warn("Could not keep the archive audit of {}: {}", kept.facts().code(), couldNotWrite.getMessage());
        }
    }

    /** The file for a set, or null for a code that is not one - which never reaches a path. */
    private static Path fileFor(Path root, String code) {
        return dev.gathering.core.card.SetCode.of(code)
                .map(checked -> root.resolve(FOLDER).resolve(checked + ".facts"))
                .orElse(null);
    }

    private static void write(DataOutputStream out, java.util.Collection<UUID> ids) throws IOException {
        out.writeInt(ids.size());
        for (UUID id : ids) {
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
        }
    }

    private static Set<UUID> ids(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MOST) {
            throw new IOException("Implausible count in an archive audit file: " + count);
        }
        Set<UUID> ids = new LinkedHashSet<>(count);
        for (int index = 0; index < count; index++) {
            ids.add(new UUID(in.readLong(), in.readLong()));
        }
        return ids;
    }
}
