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

    static Map<UUID, EventState> readAll() {
        Map<UUID, EventState> events = new LinkedHashMap<>();
        Path folder = ServerRun.inSave(FOLDER).orElse(null);
        if (folder == null || !Files.isDirectory(folder)) {
            return events;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).sorted().forEach(path -> {
                try {
                    EventState state = read(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
                    events.put(state.tournament.id(), state);
                } catch (IOException | RuntimeException unreadable) {
                    LOGGER.error("The tournament in {} will not load: {}", path.getFileName(), unreadable.toString());
                }
            });
        } catch (IOException unlistable) {
            LOGGER.error("The tournaments folder could not be read: {}", unlistable.toString());
        }
        return events;
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
        tag.putLong("round_ticks", state.roundTicks);
        tag.putLong("build_ticks", state.buildTicks);
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
        state.roundTicks = tag.getLong("round_ticks");
        state.buildTicks = tag.getLong("build_ticks");
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
