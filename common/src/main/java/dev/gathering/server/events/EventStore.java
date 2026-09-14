package dev.gathering.server.events;

import dev.gathering.core.tournament.TournamentCodec;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.server.ServerRun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tournaments in the world save: one file each, in the save's own folder.
 * <p>Written whole and moved into place, so a crash mid-write leaves the last good copy. A
 * tournament that will not read is left on disk and logged rather than deleted: it is a record
 * other players depend on.
 */
final class EventStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");
    private static final String FOLDER = "gathering-events";
    private static final String SUFFIX = ".dat";

    private EventStore() {
    }

    /**
     * The most ended events kept loaded. Older ones are moved to an archive folder beside them,
     * still on disk and still readable, so a world that has held events for years does not read
     * and list every one of them at every start. Players' records are kept separately and are
     * not affected.
     */
    static final int MOST_ENDED_KEPT = 200;

    static Map<UUID, EventState> readAll() {
        Map<UUID, EventState> events = new LinkedHashMap<>();
        Path folder = ServerRun.inSave(FOLDER).orElse(null);
        if (folder == null || !Files.isDirectory(folder)) {
            return events;
        }
        Map<UUID, Path> from = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).sorted().forEach(path -> {
                try {
                    EventState state = read(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
                    events.put(state.tournament.id(), state);
                    from.put(state.tournament.id(), path);
                } catch (IOException | RuntimeException unreadable) {
                    LOGGER.error("The tournament in {} will not load: {}", path.getFileName(), unreadable.toString());
                }
            });
        } catch (IOException unlistable) {
            LOGGER.error("The tournaments folder could not be read: {}", unlistable.toString());
        }
        archiveTheOldest(folder, events, from);
        return events;
    }

    private static void archiveTheOldest(Path folder, Map<UUID, EventState> events, Map<UUID, Path> from) {
        java.util.List<UUID> ended = events.values().stream()
                .filter(state -> state.tournament.isOver() && state.prizes.isEmpty() && state.waitingPrizes.isEmpty())
                .map(state -> state.tournament.id())
                .sorted(java.util.Comparator.comparingLong(id -> modified(from.get(id))))
                .toList();
        if (ended.size() <= MOST_ENDED_KEPT) {
            return;
        }
        Path archive = folder.resolve("archive");
        for (UUID id : ended.subList(0, ended.size() - MOST_ENDED_KEPT)) {
            try {
                Files.createDirectories(archive);
                Path source = from.get(id);
                Files.move(source, archive.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                events.remove(id);
            } catch (IOException failed) {
                LOGGER.warn("Could not archive the ended tournament {}: {}", id, failed.toString());
                return;
            }
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException unknown) {
            return 0L;
        }
    }

    static boolean write(EventState state) {
        Path folder = ServerRun.inSave(FOLDER).orElse(null);
        if (folder == null) {
            return false;
        }
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(state.tournament.id() + SUFFIX);
            Path temporary = folder.resolve(state.tournament.id() + SUFFIX + ".tmp");
            NbtIo.writeCompressed(toTag(state), temporary);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException failed) {
            LOGGER.error("The tournament {} could not be saved: {}", state.tournament.id(), failed.toString());
            return false;
        }
    }

    static CompoundTag toTag(EventState state) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("tournament", TournamentCodec.write(state.tournament));
        tag.putString("dimension", state.dimension == null ? "" : state.dimension);
        long[] tables = state.tables.stream().mapToLong(BlockPos::asLong).toArray();
        tag.putLongArray("tables", tables);
        tag.putLong("round_millis", state.roundMillis);
        tag.putLong("build_millis", state.buildMillis);
        tag.putBoolean("pod_opened", state.podOpened);
        if (state.registrationPoint != null) {
            tag.putLong("registration", state.registrationPoint.asLong());
        }
        ListTag decks = new ListTag();
        state.decks.forEach((player, deck) -> DeckComponent.CODEC.encodeStart(NbtOps.INSTANCE, deck).result()
                .ifPresent(encoded -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("player", player);
                    entry.put("deck", encoded);
                    decks.add(entry);
                }));
        tag.put("decks", decks);
        ListTag pools = new ListTag();
        state.pools.forEach((player, pool) -> DraftedPool.CODEC.encodeStart(NbtOps.INSTANCE, pool).result()
                .ifPresent(encoded -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("player", player);
                    entry.put("pool", encoded);
                    pools.add(entry);
                }));
        tag.put("pools", pools);
        tag.putString("played", String.join(",", state.playedAtTable));
        ListTag allocated = new ListTag();
        state.allocated.forEach((player, cards) -> dev.gathering.item.CardComponent.CODEC.listOf()
                .encodeStart(NbtOps.INSTANCE, cards).result().ifPresent(encoded -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("player", player);
                    entry.put("cards", encoded);
                    allocated.add(entry);
                }));
        tag.put("allocated", allocated);
        ListTag log = new ListTag();
        for (EventState.LogLine line : state.log) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("at", line.at());
            if (line.actor() != null) {
                entry.putUUID("actor", line.actor());
            }
            entry.putString("action", line.action());
            entry.putString("detail", line.detail());
            log.add(entry);
        }
        tag.put("log", log);
        registries().ifPresent(registries -> {
            ListTag prizes = new ListTag();
            for (EventPrizes.Prize prize : state.prizes) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("place", prize.place());
                entry.put("stack", prize.stack().save(registries));
                prizes.add(entry);
            }
            tag.put("prizes", prizes);
            ListTag waiting = new ListTag();
            state.waitingPrizes.forEach((player, stacks) -> {
                for (net.minecraft.world.item.ItemStack stack : stacks) {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("player", player);
                    entry.put("stack", stack.save(registries));
                    waiting.add(entry);
                }
            });
            tag.put("waiting", waiting);
        });
        return tag;
    }

    static EventState read(CompoundTag tag) throws IOException {
        EventState state = new EventState(TournamentCodec.read(tag.getByteArray("tournament")), tag.getString("dimension"));
        for (long packed : tag.getLongArray("tables")) {
            state.tables.add(BlockPos.of(packed));
        }
        // Saved in ticks before the clocks counted real time; a tick was meant as 50 ms.
        state.roundMillis = tag.contains("round_millis") ? tag.getLong("round_millis") : tag.getLong("round_ticks") * 50L;
        state.buildMillis = tag.contains("build_millis") ? tag.getLong("build_millis") : tag.getLong("build_ticks") * 50L;
        state.podOpened = tag.getBoolean("pod_opened");
        if (tag.contains("registration")) {
            state.registrationPoint = BlockPos.of(tag.getLong("registration"));
        }
        ListTag decks = tag.getList("decks", Tag.TAG_COMPOUND);
        for (int index = 0; index < decks.size(); index++) {
            CompoundTag entry = decks.getCompound(index);
            DeckComponent.CODEC.parse(NbtOps.INSTANCE, entry.get("deck")).result()
                    .ifPresent(deck -> state.decks.put(entry.getUUID("player"), deck));
        }
        ListTag pools = tag.getList("pools", Tag.TAG_COMPOUND);
        for (int index = 0; index < pools.size(); index++) {
            CompoundTag entry = pools.getCompound(index);
            DraftedPool.CODEC.parse(NbtOps.INSTANCE, entry.get("pool")).result()
                    .ifPresent(pool -> state.pools.put(entry.getUUID("player"), pool));
        }
        for (String played : tag.getString("played").split(",")) {
            if (!played.isBlank()) {
                state.playedAtTable.add(played);
            }
        }
        ListTag allocated = tag.getList("allocated", Tag.TAG_COMPOUND);
        for (int index = 0; index < allocated.size(); index++) {
            CompoundTag entry = allocated.getCompound(index);
            dev.gathering.item.CardComponent.CODEC.listOf().parse(NbtOps.INSTANCE, entry.get("cards")).result()
                    .ifPresent(cards -> state.allocated.put(entry.getUUID("player"), java.util.List.copyOf(cards)));
        }
        ListTag log = tag.getList("log", Tag.TAG_COMPOUND);
        for (int index = 0; index < log.size() && index < EventState.MOST_LOG_LINES; index++) {
            CompoundTag entry = log.getCompound(index);
            state.log.add(new EventState.LogLine(entry.getLong("at"), entry.hasUUID("actor") ? entry.getUUID("actor") : null,
                    entry.getString("action"), entry.getString("detail")));
        }
        var registries = registries().orElse(null);
        if (registries != null) {
            ListTag prizes = tag.getList("prizes", Tag.TAG_COMPOUND);
            for (int index = 0; index < prizes.size(); index++) {
                CompoundTag entry = prizes.getCompound(index);
                int place = entry.getInt("place");
                net.minecraft.world.item.ItemStack.parse(registries, entry.getCompound("stack"))
                        .ifPresent(stack -> state.prizes.add(new EventPrizes.Prize(place, stack)));
            }
            ListTag waiting = tag.getList("waiting", Tag.TAG_COMPOUND);
            for (int index = 0; index < waiting.size(); index++) {
                CompoundTag entry = waiting.getCompound(index);
                UUID player = entry.getUUID("player");
                net.minecraft.world.item.ItemStack.parse(registries, entry.getCompound("stack"))
                        .ifPresent(stack -> state.waitingPrizes.computeIfAbsent(player, ignored -> new java.util.ArrayList<>()).add(stack));
            }
        }
        return state;
    }

    private static java.util.Optional<net.minecraft.core.HolderLookup.Provider> registries() {
        return ServerRun.server().map(server -> server.registryAccess());
    }
}
