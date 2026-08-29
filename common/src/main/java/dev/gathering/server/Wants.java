package dev.gathering.server;

import dev.gathering.core.collection.WantsList;
import dev.gathering.network.Sending;
import dev.gathering.network.WantsPayload;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cards each player is chasing, on this server.
 *
 * <p>Per player rather than per collection, because a collection is shared - a playgroup pools
 * one - and what somebody is looking for is theirs. Two people at one binder are after
 * different cards, and a wants list attached to the box would be one of them writing on the
 * other's.
 *
 * <p>On the server rather than the client for the reason everything else about a collection
 * is: it has to survive changing machines, and it has to be readable by the things that will
 * want to read it - a pack being opened, a trade being offered - which run here.
 *
 * <p>One small file per player under the mod's data directory, which is how this mod keeps
 * everything that is not block state. Read when a player joins and written when their list
 * changes; a list that could not be read is an empty list and a line in the log, never a
 * refusal to let somebody play.
 *
 * <p>The map is concurrent because a save can be asked for off the server thread when the
 * world does; everything else here is server thread only.
 */
public final class Wants {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** Where the lists live, under the mod's own data directory. */
    private static final String FOLDER = "wants";

    private static final String SUFFIX = ".txt";

    /** What goes at the top of a fresh file, since somebody may well open it. */
    private static final String HEADING =
            "# Cards this player is chasing, one printing to a line.";

    private static final Map<UUID, WantsList> HELD = new ConcurrentHashMap<>();

    private Wants() {
    }

    /** What this player is chasing. Never null. */
    public static WantsList of(UUID player) {
        return player == null ? WantsList.EMPTY : HELD.getOrDefault(player, WantsList.EMPTY);
    }

    /**
     * Reads a player's list and tells them what is on it.
     *
     * <p>On joining, so a client knows from its first frame which cards to mark - a list that
     * arrived when the first pack was opened would have marked nothing on the screen before
     * it.
     */
    public static void joined(ServerPlayer player) {
        UUID who = player.getUUID();
        WantsList wants = read(who);
        HELD.put(who, wants);
        Sending.to(player, WantsPayload.of(wants));
    }

    /** Forgets a player who has gone, so a long-running server does not hold every list ever. */
    public static void left(ServerPlayer player) {
        HELD.remove(player.getUUID());
    }

    /**
     * Puts a card on this player's list, or takes it off, and tells them.
     *
     * <p>Told rather than assumed. The client asked for a change and the server decides
     * whether it happened - a full list takes nothing more - so the answer comes back and the
     * screen draws what is true rather than what it hoped.
     */
    public static void mark(ServerPlayer player, UUID printing, boolean wanted) {
        if (printing == null) {
            return;
        }
        UUID who = player.getUUID();
        WantsList before = of(who);
        WantsList after = before.toggled(printing, wanted);
        if (after.equals(before)) {
            if (wanted && before.isFull()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.gathering.wants_full", dev.gathering.core.collection.WantsList.MOST));
            }
            return;
        }
        HELD.put(who, after);
        write(who, after);
        Sending.to(player, WantsPayload.of(after));
    }

    private static Path fileFor(UUID player) {
        return Platform.get().dataDirectory().resolve(FOLDER)
                .resolve(player.toString() + SUFFIX);
    }

    private static WantsList read(UUID player) {
        Path where;
        try {
            where = fileFor(player);
        } catch (RuntimeException noPlatform) {
            return WantsList.EMPTY;
        }
        if (!Files.isRegularFile(where)) {
            return WantsList.EMPTY;
        }
        try {
            return WantsList.read(Files.readAllLines(where, StandardCharsets.UTF_8));
        } catch (IOException couldNotRead) {
            LOGGER.warn("Could not read the wants list at {}: {}", where, couldNotRead.getMessage());
            return WantsList.EMPTY;
        }
    }

    private static void write(UUID player, WantsList wants) {
        Path where;
        try {
            where = fileFor(player);
        } catch (RuntimeException noPlatform) {
            return;
        }
        try {
            Files.createDirectories(where.getParent());
            List<String> lines = new java.util.ArrayList<>();
            lines.add(HEADING);
            lines.addAll(wants.lines());
            Files.write(where, lines, StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            LOGGER.warn("Could not save the wants list to {}: {}", where, couldNotWrite.getMessage());
        }
    }
}
