package dev.gathering.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.server.CardGrant;
import dev.gathering.server.DecklistImport;
import dev.gathering.service.CardDataService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's commands, built once here and registered by whichever loader is running.
 *
 * <p>Import is a command rather than a client keybind so it is discoverable, and so a
 * server that has import switched off can simply not grant it - a keybind would work
 * whether the server allowed importing or not.
 */
public final class GatheringCommands {

    private GatheringCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("gathering")
                .then(Commands.literal("import")
                        .executes(context -> openImportScreen(context.getSource()))
                        .then(Commands.argument("decklist", StringArgumentType.greedyString())
                                .executes(context -> importInline(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "decklist")))))
                // A single card in hand: the only way to hold one, and therefore the only way
                // to read one, until tables exist to deal them out.
                .then(Commands.literal("card")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> giveCard(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        false))))
                .then(Commands.literal("foil")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> giveCard(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        true))))
                // Ending a game is a command rather than a click, because it cannot be undone
                // and a table is a thing people lean on.
                .then(Commands.literal("table")
                        .then(Commands.literal("end")
                                .executes(context -> endSession(context.getSource()))));
    }

    /**
     * Ends the game at the table the player is looking at.
     *
     * <p>The table has to be named by pointing at it: a player standing in a shop full of
     * them should not be able to end the wrong game by typing.
     */
    private static int endSession(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        net.minecraft.world.phys.HitResult hit = player.pick(6.0d, 0.0f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "message.gathering.session_no_table"));
            return 0;
        }

        net.minecraft.world.level.block.state.BlockState state =
                player.level().getBlockState(block.getBlockPos());
        if (!(state.getBlock() instanceof dev.gathering.block.TableBlock)) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "message.gathering.session_no_table"));
            return 0;
        }

        net.minecraft.core.BlockPos origin =
                dev.gathering.block.TableBlock.originOf(state, block.getBlockPos());
        dev.gathering.core.game.SeatId seat = dev.gathering.block.TableSessions
                .seatIdOf(player.level(), origin, player.getUUID())
                .orElseGet(() -> new dev.gathering.core.game.SeatId(0));

        dev.gathering.block.TableSessions.Outcome outcome = dev.gathering.block.TableSessions.end(
                player.level(), origin, seat, "ended by " + player.getGameProfile().getName());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                outcome.messageKey()), true);
        return outcome == dev.gathering.block.TableSessions.Outcome.ENDED ? 1 : 0;
    }

    private static int giveCard(CommandSourceStack source, String cardName, boolean foil)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            source.sendFailure(Component.translatable("message.gathering.pipeline_unavailable"));
            return 0;
        }
        CardGrant.byName(player, service, cardName, foil);
        return 1;
    }

    private static int openImportScreen(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = DecklistImport.whyNot(player);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(OpenImportScreenPayload.INSTANCE));
        return 1;
    }

    /**
     * The one-line form, for a single card or a quick test. A real decklist goes through the
     * screen, where newlines exist.
     */
    private static int importInline(CommandSourceStack source, String decklist)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            source.sendFailure(Component.translatable("message.gathering.pipeline_unavailable"));
            return 0;
        }
        DecklistImport.importFor(player, service, decklist);
        source.sendSuccess(() -> Component.translatable("message.gathering.import_started"), false);
        return 1;
    }
}
