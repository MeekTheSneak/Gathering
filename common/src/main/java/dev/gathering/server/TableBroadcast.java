package dev.gathering.server;

import dev.gathering.network.AntePotPayload;
import dev.gathering.network.Sending;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.persistence.ViewCodec;
import dev.gathering.core.game.visibility.GameView;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells everyone at a table what the board looks like - each of them something different.
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
     * <p>Enough to read what just happened during a complicated turn, and few enough that the
     * board payload stays a board payload. The whole log lives on the server.
     */
    private static final int LOG_LINES_SENT = 40;

    /**
     * How many boards have been built and sent since the counter was last cleared.
     * <p>Here so that "a hundred-card gesture is a hundred broadcasts" can be a number rather
     * than a claim. Building a view means walking every zone of every seat through the
     * visibility rules and serializing the result, once per person who can see the table - so
     * the cost of a bulk gesture is this count multiplied by the people at it, and until it
     * was counted nobody knew which of those two numbers was the problem.
     * <p>Plain arithmetic on the server thread, which is the only thread that broadcasts.
     */
    private static int boardsSent;

    /** How many boards have gone out. For a test that wants to count them. */
    public static int boardsSent() {
        return boardsSent;
    }

    /**
     * How many views have been worked out and encoded, which is fewer than the boards sent
     * whenever several spectators share one. For a test that wants to count them.
     */
    private static int viewsBuilt;

    /** How many views have been built. */
    public static int viewsBuilt() {
        return viewsBuilt;
    }

    /** Starts both counts again. */
    public static void forgetTheCount() {
        boardsSent = 0;
        viewsBuilt = 0;
    }

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
        // With the board, always. Two things that are drawn together and sent separately are
        // two things that can disagree, and the one anybody would notice is a pot still
        // showing on a table whose game has moved on.
        sendPot(level, tableOrigin);
    }

    /**
     * Sends the public board to everyone nearby who is not sitting at it.
     * <p>This is what the miniature on the table top is drawn from, and it is the spectator
     * view rather than anybody's: a player walking past a game sees what somebody standing
     * over the table would see, which is the whole point of having a table in a world rather
     * than a menu. Nobody's hand is in it.
     */
    public static void sendAmbient(
            ServerLevel level, BlockPos tableOrigin, GameSession session,
            java.util.Set<java.util.UUID> exclude) {
        // Built once for everybody watching rather than once each. Every spectator is the same
        // viewer - Viewer.Spectator has no identity to differ by - so the view, and the bytes
        // it encodes to, are the same for all of them; building it per person was the same
        // walk through the visibility rules repeated for each. Built only if somebody is
        // watching, and never shared with a seated player, whose view is a different view.
        GameView shared = null;
        byte[] encoded = null;
        for (ServerPlayer nearby : level.players()) {
            if (exclude.contains(nearby.getUUID())) {
                continue;
            }
            if (nearby.distanceToSqr(tableOrigin.getX() + 1.0, tableOrigin.getY() + 1.0,
                    tableOrigin.getZ() + 1.0) > AMBIENT_RANGE * AMBIENT_RANGE) {
                continue;
            }
            if (shared == null) {
                try {
                    shared = VisibilityRules.viewFor(
                            session.state(), Viewer.SPECTATOR, session.recentLog(LOG_LINES_SENT));
                    encoded = ViewCodec.write(shared);
                    viewsBuilt++;
                } catch (IOException e) {
                    LOGGER.error("Could not build the public board at {}: {}",
                            tableOrigin, e.getMessage());
                    return;
                }
            }
            boardsSent++;
            Sending.to(nearby, new TableViewPayload(tableOrigin, encoded, false));
            CardArtPush.sendFor(nearby, shared);
        }
    }

    /**
     * Sends the pot to everybody who can see this table.
     * <p>Alongside the board rather than inside it. Every card in a pot is face up to the
     * room by definition, so this needs none of the visibility machinery the board needs -
     * and the pictures go out through the same channel a public zone's do, for the same
     * reason: a row of sleeves under a count is not a pot anybody can look at.
     */
    public static void sendPot(ServerLevel level, BlockPos tableOrigin) {
        dev.gathering.core.ante.AntePot pot = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> dev.gathering.block.TableBlock.entityAt(level, anchor))
                .map(dev.gathering.block.TableBlockEntity::pot)
                .orElse(dev.gathering.core.ante.AntePot.EMPTY);

        List<dev.gathering.item.CardComponent> cards = new java.util.ArrayList<>(pot.size());
        java.util.Set<java.util.UUID> printings = new java.util.LinkedHashSet<>();
        for (dev.gathering.core.card.CardIdentity card : pot.everything()) {
            cards.add(dev.gathering.item.CardComponent.of(card));
            card.printing().ifPresent(printings::add);
        }

        AntePotPayload payload = new AntePotPayload(tableOrigin, cards);
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(tableOrigin.getX() + 1.0, tableOrigin.getY() + 1.0,
                    tableOrigin.getZ() + 1.0) > AMBIENT_RANGE * AMBIENT_RANGE) {
                continue;
            }
            dev.gathering.network.Sending.to(nearby, payload);
            if (!printings.isEmpty()) {
                CardArtPush.send(nearby, printings);
            }
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
            GameView seen = VisibilityRules.viewFor(
                    session.state(), viewer, session.recentLog(LOG_LINES_SENT));
            viewsBuilt++;
            boardsSent++;
            Sending.to(player,
                    new TableViewPayload(tableOrigin, ViewCodec.write(seen), open));
            // What the table is playing, beside it. Nothing about it is hidden, so it is the same for
            // everybody who can see the board.
            Sending.to(player, new dev.gathering.network.TableTermsPayload(tableOrigin,
                    termsAt(player.serverLevel(), tableOrigin)));
            // And the pictures for what is in it. A client only ever asked about cards in its
            // own inventory, so a rival's graveyard opened onto empty recesses under a count
            // that said there was something there. Sent from the view rather than asked for,
            // so a viewer learns about exactly the cards the rules just showed them.
            CardArtPush.sendFor(player, seen);
        } catch (IOException e) {
            LOGGER.error("Could not send the board at {} to {}: {}",
                    tableOrigin, player.getGameProfile().getName(), e.getMessage());
        }
    }

    /**
     * What this table is playing, as the server knows it: the match it started, the table's own
     * record of whether a format was chosen, and whether it is for keeps.
     */
    public static dev.gathering.core.match.TableTerms termsAt(ServerLevel level, BlockPos tableOrigin) {
        var match = dev.gathering.block.TableSessions.matchAt(level, tableOrigin).orElse(null);
        var table = dev.gathering.block.TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> dev.gathering.block.TableBlock.entityAt(level, anchor)).orElse(null);
        boolean formatChosen = table != null && table.formatWasChosen();
        return new dev.gathering.core.match.TableTerms(
                match == null ? "" : match.rules().format().id(),
                match == null ? 1 : match.rules().bestOf(),
                match == null ? 1 : match.gameNumber(),
                // A tournament names its format for every table, and a practice table is not a
                // format at all; neither is somebody's game with none.
                !formatChosen && table != null && table.eventTable() == 0 && !table.isPractice(),
                table != null && table.playingForKeeps(),
                table != null && table.isPractice(),
                table == null ? 0 : table.eventTable());
    }

    /** Tells everyone at this cluster that the game is over and to stop watching it. */
    public static void closeAtTable(ServerLevel level, BlockPos tableOrigin) {
        // Everyone who can see it, not only the people sitting at it. A spectator was left
        // holding the last board the table ever sent, which looks like a game still going.
        CloseTablePayload closing = new CloseTablePayload(tableOrigin);
        for (ServerPlayer nearby : watchingNearby(level, tableOrigin)) {
            Sending.to(nearby, closing);
        }
        for (Seated seated : seatedAt(level, tableOrigin)) {
            Sending.to(seated.player(), closing);
        }
    }

    /**
     * Everybody near enough to be watching this table, seated or not.
     * <p>The same range the ambient board goes out at, and the same one for the same reason:
     * a person who can see your game is a person at your game. Written once here so that the
     * miniature's audience and the table's conversation cannot come to be two different
     * groups of people.
     */
    public static List<ServerPlayer> watchingNearby(ServerLevel level, BlockPos tableOrigin) {
        List<ServerPlayer> nearby = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(tableOrigin.getX() + 1.0, tableOrigin.getY() + 1.0,
                    tableOrigin.getZ() + 1.0) <= AMBIENT_RANGE * AMBIENT_RANGE) {
                nearby.add(player);
            }
        }
        return nearby;
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
