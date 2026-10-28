package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import dev.gathering.network.TablesApartPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Playing a long table as one surface, or as several tables side by side.
 * <p>A store's long table seats a four-player game or three 1v1 games, and a draft pod drafts
 * at the whole of it and then plays its matches in the same chairs. Which one it is is a
 * setting on every table in the line, changed together, and only while none of them is doing
 * anything - a game, a set between games, a draft, a signup or a pot - because each of those
 * belongs to the tables it started on and would not survive them becoming a different shape.
 */
public final class TablesApart {

    private TablesApart() {
    }

    public enum Result {
        DONE, ALREADY, IN_USE, ALONE, NO_TABLE
    }

    /** Sets every table touching this one to be played apart, or together. */
    public static Result set(ServerLevel level, BlockPos origin, boolean apart) {
        List<TableBlockEntity> tables = tablesTouching(level, origin);
        if (tables.isEmpty()) {
            return Result.NO_TABLE;
        }
        if (tables.size() < 2) {
            return Result.ALONE;
        }
        boolean already = tables.stream().allMatch(table -> table.playsApart() == apart);
        if (already) {
            return Result.ALREADY;
        }
        for (TableBlockEntity table : tables) {
            if (inUse(table)) {
                return Result.IN_USE;
            }
        }
        for (TableBlockEntity table : tables) {
            table.setPlaysApart(apart);
        }
        return Result.DONE;
    }

    /**
     * Whether anything on this table would be broken by it changing shape.
     * <p>Public because a tournament has to ask the same question when it picks its tables. It used
     * to ask a different one - "has another event claimed it" - and claim tables it would later
     * refuse to play on, which is a tournament that can never start.
     */
    public static boolean inUse(TableBlockEntity table) {
        return table.hasSession() || table.hasPod() || table.hasSignup()
                || table.match().isPresent() || !table.heldDecks().isEmpty() || !table.pot().isEmpty();
    }

    /** The long table this one is part of, every table in it, played apart or not. */
    public static List<TableBlockEntity> tablesTouching(ServerLevel level, BlockPos origin) {
        TableCluster line = TableClusters.touching(level, origin);
        List<TableBlockEntity> tables = new ArrayList<>();
        for (TableCell cell : line.cells()) {
            TableBlock.entityAt(level, TableClusters.blockPos(origin, cell)).ifPresent(tables::add);
        }
        return tables;
    }

    public static void handle(ServerPlayer player, TablesApartPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos clicked = payload.table();
        if (!TableReach.within(player, clicked) || TableBlock.entityAt(level, clicked).isEmpty()) {
            return;
        }
        BlockPos origin = TableBlock.originOf(level.getBlockState(clicked), clicked);
        // Somebody sitting at this long table, or an operator: a passer-by rearranging a room
        // other people set up is not a setting, it is a prank.
        boolean seatedHere = tablesTouching(level, origin).stream()
                .anyMatch(table -> dev.gathering.block.TableSeats.seatOf(level, table.getBlockPos(), player.getUUID()).isPresent());
        if (!seatedHere && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("message.gathering.tables.sit_first"));
            return;
        }
        Result result = set(level, origin, payload.apart());
        player.sendSystemMessage(Component.translatable(switch (result) {
            case DONE -> payload.apart() ? "message.gathering.tables.apart" : "message.gathering.tables.together";
            case ALREADY -> payload.apart() ? "message.gathering.tables.already_apart" : "message.gathering.tables.already_together";
            case IN_USE -> "message.gathering.tables.in_use";
            case ALONE -> "message.gathering.tables.alone";
            case NO_TABLE -> "message.gathering.tables.alone";
        }));
    }
}
