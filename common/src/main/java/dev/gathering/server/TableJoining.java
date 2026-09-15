package dev.gathering.server;

import dev.gathering.block.ChairSeat;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.network.ChooseDeckPayload;
import dev.gathering.network.DeckNotLegalPayload;
import dev.gathering.network.JoinTableAnswerPayload;
import dev.gathering.network.JoinTablePromptPayload;
import dev.gathering.network.OpenDeckPickerPayload;
import dev.gathering.network.Sending;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Coming to a game that is on: watching it or joining it, and choosing the deck to join with.
 * <p>The owner's flow. Sitting down at a seat of a game already on asks first - join, or only watch - and
 * joining opens a list of the decks the player carries, which is how a deck goes down; a game starting
 * opens the same list for everybody seated. A chair at a table that is not at a seat watches.
 * <p>A deck not legal in the table's chosen format is not refused from that list: the player is told what
 * is not legal and asked whether to play it anyway, and if they do the table is told, and so is anybody
 * who comes to the table while the deck is down.
 */
public final class TableJoining {

    private TableJoining() {
    }

    /**
     * Somebody sat down in a chair to watch this table. Asked whether they are joining, from a chair at a
     * seat of a game on; otherwise shown the game if there is one, and told what is being played there
     * that is not legal.
     */
    public static void watching(ServerPlayer player, BlockPos tableOrigin, boolean askToJoin) {
        ServerLevel level = player.serverLevel();
        if (askToJoin) {
            Sending.to(player, new JoinTablePromptPayload(tableOrigin));
            return;
        }
        tellWhatIsNotLegal(player, tableOrigin);
        if (TableSessions.hasSession(level, tableOrigin)) {
            TableActions.openFor(player, tableOrigin);
        }
    }

    /** The answer to joining or watching, from a player still in the chair watching that table. */
    public static void answer(ServerPlayer player, JoinTableAnswerPayload payload) {
        if (!(player.getVehicle() instanceof ChairSeat seat) || !payload.table().equals(seat.watchingAt())) {
            return;
        }
        if (payload.join()) {
            Chairs.join(player, seat);
            if (seat.watchingAt() == null) {
                return;
            }
        }
        // Watching, or refused a seat and so watching after all. Joined, satDown has told them.
        tellWhatIsNotLegal(player, payload.table());
        if (TableSessions.hasSession(player.serverLevel(), payload.table())) {
            TableActions.openFor(player, payload.table());
        }
    }

    /**
     * Offers the list of this player's decks, if they are seated at the game on here with no deck down.
     *
     * @return whether it was offered, or would have been to a client that can take it
     */
    public static boolean offerDecks(ServerPlayer player, BlockPos tableOrigin) {
        ServerLevel level = player.serverLevel();
        if (!TableSessions.hasSession(level, tableOrigin) || TableSessions.isPractice(level, tableOrigin)) {
            return false;
        }
        SeatId seat = TableSessions.seatIdOf(level, tableOrigin, player.getUUID()).orElse(null);
        if (seat == null || TableBlock.hasADeckDown(level, tableOrigin, seat)) {
            return false;
        }
        Sending.to(player, new OpenDeckPickerPayload(tableOrigin, LoanerDecks.lends()));
        return true;
    }

    /** A deck chosen from the list, or a loaner asked for. */
    public static void choose(ServerPlayer player, ChooseDeckPayload payload) {
        BlockPos origin = TableReach.originFor(player, payload.table()).orElse(null);
        if (origin == null) {
            return;
        }
        if (payload.slot() == ChooseDeckPayload.BORROW) {
            Lending.offer(player, origin);
            return;
        }
        // Anyway only for the deck they were asked about: the same stack, still in that slot, at this table.
        // Another deck swapped into the slot after the question is asked about in its turn.
        Asked asked = ASKED.remove(player.getUUID());
        boolean anyway = payload.anyway() && asked != null && asked.table().equals(origin) && asked.slot() == payload.slot()
                && payload.slot() >= 0 && payload.slot() < player.getInventory().getContainerSize()
                && player.getInventory().getItem(payload.slot()) == asked.stack();
        TableBlock.chooseDeck(player.serverLevel(), origin, player, payload.slot(), anyway);
    }

    /** The deck each player was last asked about playing anyway: where, which slot, and which stack. */
    private static final Map<java.util.UUID, Asked> ASKED = new java.util.HashMap<>();

    private record Asked(BlockPos table, int slot, net.minecraft.world.item.ItemStack stack) {
    }

    /** Forgets every question about a deck, for a server that is stopping. */
    public static void clear() {
        ASKED.clear();
    }

    /** Asks whether to play the deck in this slot, which is not legal in the table's format, anyway. */
    public static void askAnyway(ServerPlayer player, BlockPos tableOrigin, int slot, net.minecraft.world.item.ItemStack stack,
            String deck, TableBlockEntity.NotLegal why) {
        if (ASKED.size() > 512) {
            ASKED.clear();
        }
        ASKED.put(player.getUUID(), new Asked(tableOrigin.immutable(), slot, stack));
        Sending.to(player, new DeckNotLegalPayload(tableOrigin, slot, cut(deck, 256), cut(why.format(), 64),
                why.problems().stream().limit(DeckNotLegalPayload.MOST).map(line -> cut(line, 512)).toList(),
                why.more() + Math.max(0, why.problems().size() - DeckNotLegalPayload.MOST)));
    }

    /** What the table is told about a deck played though not legal: whose board, the format, and why. */
    public static Component notLegalLine(Component whose, TableBlockEntity.NotLegal why) {
        net.minecraft.network.chat.MutableComponent line = Component.translatable(
                "message.gathering.deck_played_anyway", whose, why.format(), String.join("; ", why.problems()));
        if (why.more() > 0) {
            line.append(Component.translatable("message.gathering.deck_played_anyway_more", why.more()));
        }
        return line.withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    /** Says something to everybody at this table: those seated, and those watching from its chairs. */
    public static void tellTheTable(ServerLevel level, BlockPos tableOrigin, Component line) {
        for (ServerPlayer player : atTheTable(level, tableOrigin)) {
            player.sendSystemMessage(line);
        }
    }

    /**
     * Tells somebody who has just come to this table about every deck down here that is not legal in its
     * format, as the table was told when it went down.
     */
    public static void tellWhatIsNotLegal(ServerPlayer player, BlockPos tableOrigin) {
        ServerLevel level = player.serverLevel();
        GameSession session = TableSessions.sessionAt(level, tableOrigin).orElse(null);
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor)).orElse(null);
        if (table == null) {
            return;
        }
        for (Map.Entry<SeatId, TableBlockEntity.NotLegal> entry : table.notLegal().entrySet()) {
            Component whose = session != null && session.state().hasSeat(entry.getKey())
                    ? dev.gathering.SeatNames.of(session.state().seatState(entry.getKey()))
                    : Component.translatable("message.gathering.seat_number", entry.getKey().index() + 1);
            player.sendSystemMessage(notLegalLine(whose, entry.getValue()));
        }
    }

    /** Everybody at this table who is online: seated at it, or watching from one of its chairs. */
    public static List<ServerPlayer> atTheTable(ServerLevel level, BlockPos tableOrigin) {
        Set<ServerPlayer> found = new LinkedHashSet<>();
        TableBroadcast.seatedAt(level, tableOrigin).forEach(seated -> found.add(seated.player()));
        found.addAll(watchers(level, tableOrigin));
        return new ArrayList<>(found);
    }

    /** The players watching this table from chairs against it, without a seat. */
    public static List<ServerPlayer> watchers(ServerLevel level, BlockPos tableOrigin) {
        List<ServerPlayer> watching = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.getVehicle() instanceof ChairSeat seat && tableOrigin.equals(seat.watchingAt())) {
                watching.add(player);
            }
        }
        return watching;
    }

    private static String cut(String text, int most) {
        return text == null ? "" : text.length() <= most ? text : text.substring(0, most);
    }
}
