package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.network.OpenTableSetupPayload;
import dev.gathering.network.StartTablePayload;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Starting a game: the asking and the answering.
 *
 * <p>Crouching on a table asks what kind of game this is, and the answer comes back as a
 * format id and a length. Neither is taken at face value. The id is looked up against the
 * server's own presets, so a client cannot invent a format with a two-card minimum and a
 * thousand life; the length is checked against the ones a match can be, so it cannot be best
 * of two - which can be drawn, and a drawn match settles nothing.
 *
 * <p>Everything else about starting a game already lived in {@link TableSessions} and stays
 * there. This is the part that has a client in it.
 */
public final class TableSetup {

    /** How far a player may be from a table and still be setting a game up at it. */
    private static final double REACH = 12.0d;

    private TableSetup() {
    }

    /** Asks the player what kind of game they want, on the client that asked for one. */
    public static void ask(ServerPlayer player, BlockPos tableOrigin) {
        Sending.to(player, new OpenTableSetupPayload(tableOrigin));
    }

    public static void handle(ServerPlayer player, StartTablePayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = originFor(player, payload.table(), level).orElse(null);
        if (origin == null) {
            return;
        }

        boolean freePlay = isFreePlay(payload);
        MatchRules rules = rulesOf(payload).orElse(null);
        if (rules == null) {
            // A client asking for a format this server does not have is either out of date or
            // making things up, and there is nothing useful to say to either.
            player.sendSystemMessage(Component.translatable("message.gathering.session_unknown_format"));
            return;
        }
        // Only somebody sitting at the table gets to say what is played on it.
        if (TableSeats.seatOf(level, origin, player.getUUID()).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.gathering.session_nobody_seated"));
            return;
        }

        // A table playing for keeps asks everybody first, and the game starts when the last
        // seat answers rather than now. Nothing is staked until they have all said yes; this
        // is only the question.
        if (Antes.askedFirst(level, origin, rules)) {
            return;
        }

        TableSessions.Outcome outcome = TableSessions.start(level, origin, rules);
        player.sendSystemMessage(Component.translatable(outcome.messageKey()));
        if (outcome == TableSessions.Outcome.STARTED) {
            // Named, not assumed - which is what turns the deck check from a note into a
            // refusal. Somebody who picked Modern off this screen asked to be held to it;
            // somebody who picked free play asked for the opposite, and gets the walk-up
            // path's numbers and none of its refusals.
            TableSessions.anchorOf(level, origin)
                    .flatMap(anchor -> dev.gathering.block.TableBlock.entityAt(level, anchor))
                    .ifPresent(table -> table.formatWasChosen(!freePlay));
            TableBroadcast.sendToTable(level, origin);
        }
    }

    /**
     * Whether this payload is asking for a game with no format at all.
     *
     * <p>One empty id and nothing else. A format this server does not have is not free play,
     * it is a client that is out of date or making things up, and it goes on being refused -
     * otherwise "start a game of Vintage" on a server without Vintage would quietly become a
     * game with no rules rather than a message saying so.
     */
    public static boolean isFreePlay(StartTablePayload payload) {
        return StartTablePayload.FREE_PLAY.equals(payload.formatId());
    }

    /**
     * The rules to play by: the named format's, or the table's own when none was named.
     *
     * <p>Free play borrows the walk-up path's numbers - Commander's life total and command
     * zone - because a table has to start somewhere, and the player never named a format so
     * nothing holds them to one.
     */
    public static Optional<MatchRules> rulesOf(StartTablePayload payload) {
        if (isFreePlay(payload)) {
            return Optional.of(TableSessions.defaultRules());
        }
        return rulesFrom(payload);
    }

    /**
     * The rules this payload names, if the server agrees they exist.
     *
     * <p>Rebuilt from the server's own preset rather than from anything in the payload beyond
     * a name, which is the same shape as every other place the mod takes a string from a
     * client.
     */
    static Optional<MatchRules> rulesFrom(StartTablePayload payload) {
        Optional<FormatPreset> preset = FormatPresets.byId(payload.formatId());
        if (preset.isEmpty() || !MatchRules.SUPPORTED_LENGTHS.contains(payload.bestOf())) {
            return Optional.empty();
        }
        return Optional.of(new MatchRules(preset.get(), payload.bestOf()));
    }

    private static Optional<BlockPos> originFor(ServerPlayer player, BlockPos clicked, ServerLevel level) {
        if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > REACH * REACH) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        return Optional.of(TableBlock.originOf(state, clicked));
    }
}
