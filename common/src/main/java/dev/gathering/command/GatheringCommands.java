package dev.gathering.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gathering.network.OpenImportScreenPayload;
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
                                        StringArgumentType.getString(context, "decklist")))));
    }

    private static int openImportScreen(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
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
