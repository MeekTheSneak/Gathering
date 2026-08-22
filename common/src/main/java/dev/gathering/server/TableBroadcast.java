package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.persistence.ViewCodec;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import dev.gathering.network.CloseTablePayload;
import dev.gathering.network.TableViewPayload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells everyone at a table what the board looks like - each of them something different.
 *
 * <p>A view is built per recipient and sent to that recipient alone. There is no shared board
 * packet, because a shared board packet is the whole class of bug this design exists to
 * prevent: it would have to contain everybody's hand, and every client would hold it.
 */
public final class TableBroadcast {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private TableBroadcast() {
    }

    /** How far away a table's miniature is worth keeping up to date. */
    private static final double AMBIENT_RANGE = 32.0d;

    /**
     * How much of the log goes out with each board.
     *
     * <p>Enough to read what just happened during a complicated turn, and few enough that the
     * board payload stays a board payload. The whole log lives on the server.
     */
    private static final int LOG_LINES_SENT = 40;

    /** Sends the board to every seated player at this cluster, and the public one to the room. */
    public static void sendToTable(ServerLevel level, BlockPos tableOrigin) {
        GameSession session = TableSessions.sessionAt(level, tableOrigin).orElse(null);
        if (session == null) {
            return;
        }
        java.util.Set<java.util.UUID> seated = new java.util.HashSet<>();
        for (Seated player : seatedAt(level, tableOrigin)) {
            seated.add(player.player().getUUID());
            send(player.player(), tableOrigin, session, Optional.of(player.seat()), false);
        }
        sendAmbient(level, tableOrigin, session, seated);
    }

    /**
     * Sends the public board to everyone nearby who is not sitting at it.
     *
     * <p>This is what the miniature on the table top is drawn from, and it is the spectator
     * view rather than anybody's: a player walking past a game sees what somebody standing
     * over the table would see, which is the whole point of having a table in a world rather
     * than a menu. Nobody's hand is in it.
     */
    public static void sendAmbient(
            ServerLevel level, BlockPos tableOrigin, GameSession session,
            java.util.Set<java.util.UUID> exclude) {
        for (ServerPlayer nearby : level.players()) {
            if (exclude.contains(nearby.getUUID())) {
                continue;
            }
            if (nearby.distanceToSqr(tableOrigin.getX() + 1.0, tableOrigin.getY() + 1.0,
                    tableOrigin.getZ() + 1.0) > AMBIENT_RANGE * AMBIENT_RANGE) {
                continue;
            }
            send(nearby, tableOrigin, session, Optional.empty(), false);
        }
    }

    /** Sends the board to one player, seated or not. */
    public static void send(
            ServerPlayer player, BlockPos tableOrigin, GameSession session, Optional<SeatId> seat,
            boolean open) {
        Viewer viewer = seat.<Viewer>map(Viewer.Seated::new).orElseGet(Viewer.Spectator::new);
        try {
            // The tail rather than the whole log: a long game's log is thousands of lines and
            // nobody scrolls back past the last dozen. What is kept is kept on the server.
            byte[] view = ViewCodec.write(VisibilityRules.viewFor(
                    session.state(), viewer, session.recentLog(LOG_LINES_SENT)));
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new TableViewPayload(tableOrigin, view, open)));
        } catch (IOException e) {
            LOGGER.error("Could not send the board at {} to {}: {}",
                    tableOrigin, player.getGameProfile().getName(), e.getMessage());
        }
    }

    /** Tells everyone at this cluster that the game is over and to stop watching it. */
    public static void closeAtTable(ServerLevel level, BlockPos tableOrigin) {
        for (Seated seated : seatedAt(level, tableOrigin)) {
            seated.player().connection.send(new ClientboundCustomPayloadPacket(CloseTablePayload.INSTANCE));
        }
    }

    /** Says something to everyone at this cluster. What happened at the table is table news. */
    public static void tell(ServerLevel level, BlockPos tableOrigin, net.minecraft.network.chat.Component line) {
        for (Seated seated : seatedAt(level, tableOrigin)) {
            seated.player().sendSystemMessage(line);
        }
    }

    /** Everyone registered at this cluster who is actually online. */
    public static List<Seated> seatedAt(ServerLevel level, BlockPos tableOrigin) {
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        List<Seated> found = new ArrayList<>();

        List<SeatAnchor> anchors = cluster.seats();
        for (int index = 0; index < anchors.size(); index++) {
            SeatAnchor anchor = anchors.get(index);
            Optional<UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, anchor.cell()))
                    .flatMap(table -> table.occupantOf(anchor.side()));
            if (occupant.isEmpty()) {
                continue;
            }
            // Registered but offline is normal - the design says leaving does not drop your
            // seat - so an absent player is simply somebody there is nothing to send to.
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(occupant.get());
            if (player != null) {
                found.add(new Seated(player, new SeatId(index)));
            }
        }
        return found;
    }

    /** Which seat of the session a player at this cluster holds. */
    public static Optional<SeatId> seatOf(ServerLevel level, BlockPos tableOrigin, UUID player) {
        return TableSessions.seatIdOf(level, tableOrigin, player);
    }

    /** A player and the seat they hold. */
    public record Seated(ServerPlayer player, SeatId seat) {
    }

    /** Whether anybody at all is registered here, online or not. */
    public static boolean anybodySeated(ServerLevel level, BlockPos tableOrigin) {
        return TableSeats.occupiedSeats(level, tableOrigin) > 0;
    }
}
