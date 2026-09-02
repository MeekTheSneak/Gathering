package dev.gathering.command;

import dev.gathering.network.Sending;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.server.CardGrant;
import dev.gathering.server.DecklistImport;
import dev.gathering.server.PackCoverage;
import dev.gathering.server.PackGrant;
import dev.gathering.server.PackOpening;
import dev.gathering.service.CardDataService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's commands, built once here and registered by whichever loader is running.
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
                // Sealed product from nothing is an admin grant, not a way to collect: the
                // loot and the shop are how a player comes by it. Three things worth doing
                // with a pack from a console, so they are named rather than guessed between.
                .then(Commands.literal("pack")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("open")
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .executes(context -> openPack(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "set"),
                                                ""))
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .executes(context -> openPack(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "set"),
                                                        StringArgumentType.getString(context, "kind"))))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .executes(context -> givePack(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "set"),
                                                ""))
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .executes(context -> givePack(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "set"),
                                                        StringArgumentType.getString(context, "kind"))))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .executes(context -> listProducts(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "set"))))))
                // Whether a set's own packs can produce all of it. Answered whether or not
                // collecting is turned on, because "would this set be complete if I enabled
                // it" is the question you ask before enabling it.
                .then(Commands.literal("coverage")
                        .requires(source -> source.hasPermission(2))
                        // With no set: the whole server, which is the number the Archive Pack
                        // is built out of. "How many cards can nobody here ever get" is the
                        // question the auditor exists to answer, and it had no way to be
                        // asked - only one set at a time.
                        .executes(context -> reportTheArchive(context.getSource()))
                        .then(Commands.argument("set", StringArgumentType.word())
                                .executes(context -> auditSet(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "set")))))
                // Every setting, readable by anybody and changeable by whoever runs the
                // server. Settings used to need a restart, which meant nobody could try
                // limited play without shutting the server down to turn collecting on.
                .then(Commands.literal("config")
                        .executes(context -> listSettings(context.getSource()))
                        .then(Commands.argument("setting", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String name : dev.gathering.server.Settings.names()) {
                                        builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> showSetting(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "setting")))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> setSetting(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "setting"),
                                                StringArgumentType.getString(context, "value"))))))
                // What the server lends, and re-reading the folder without a restart. An
                // admin who has just written a decklist should not have to bounce the server
                // to lend it: for the one feature whose whole point is a new player's first
                // minute, that is a poor trade.
                .then(Commands.literal("loaners")
                        .executes(context -> listLoaners(context.getSource()))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> reloadLoaners(context.getSource()))))
                // Watching a game back. A command rather than a block, because the game it is
                // about is over and the table it was played on may well have been broken up
                // since - and because a player who wants last night's game does not want to
                // walk to where it happened first.
                .then(Commands.literal("replay")
                        .executes(context -> listReplays(context.getSource())))
                // Ending a game is a command rather than a click, because it cannot be undone
                // and a table is a thing people lean on.
                .then(Commands.literal("table")
                        .then(Commands.literal("end")
                                .executes(context -> endSession(context.getSource())))
                        // What the server thinks is happening at the table you are looking
                        // at. Every line of it is something the clients at that table already
                        // hold, so it needs no permission - and "it does not work" is a
                        // sentence anybody might have to say.
                        .then(Commands.literal("info")
                                .executes(context -> reportTable(context.getSource())))
                        // A board with a real game's worth of cards on it, in one command.
                        // How a crowded table reads is a question that used to cost forty
                        // clicks to ask.
                        .then(Commands.literal("fill")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> fillTable(context.getSource(), 12))
                                .then(Commands.argument("cards",
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .integer(1, dev.gathering.server.TableReport.MOST_AT_ONCE))
                                        .executes(context -> fillTable(
                                                context.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(context, "cards"))))));
    }

    /**
     * Sends the finished games to whoever asked, which opens the list on their client.
     * <p>No permission. A replay only shows a game that has already ended, and every one of
     * them was played in public on a table anybody could stand at.
     */
    private static int listReplays(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.gathering.player_only"));
            return 0;
        }
        if (!dev.gathering.server.ReplayWatch.keeping()) {
            source.sendFailure(Component.translatable("message.gathering.replays_off"));
            return 0;
        }
        dev.gathering.server.ReplayWatch.sendList(player);
        return 1;
    }

    /** Every setting and what it is currently set to. */
    private static int listSettings(CommandSourceStack source) {
        java.util.List<String> names = dev.gathering.server.Settings.names();
        source.sendSuccess(() -> Component.translatable(
                "message.gathering.config_count", names.size()), false);
        for (String name : names) {
            source.sendSuccess(() -> Component.literal(
                    "  " + name + " = " + dev.gathering.server.Settings.valueOf(name)), false);
        }
        return names.size();
    }

    private static int showSetting(CommandSourceStack source, String name) {
        if (!dev.gathering.server.Settings.names().contains(name)) {
            source.sendFailure(Component.translatable("message.gathering.config_unknown", name));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                name + " = " + dev.gathering.server.Settings.valueOf(name)), false);
        return 1;
    }

    /**
     * Changes one, writes it to the file, and brings the server back into step with it.
     * <p>Announced to the whole server rather than to whoever typed it: what a server is for
     * is not a private setting, and somebody midway through opening a booster deserves to
     * know the rules changed underneath them.
     */
    private static int setSetting(CommandSourceStack source, String name, String value) {
        var changed = dev.gathering.server.Settings.set(name, value);
        if (!changed.worked()) {
            source.sendFailure(Component.literal(changed.problem()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "message.gathering.config_set", name,
                dev.gathering.server.Settings.valueOf(name)), true);
        for (String note : changed.notes()) {
            source.sendSuccess(() -> Component.literal("  " + note), false);
        }
        return 1;
    }

    /** What is on the shelf, for anybody: knowing what can be borrowed is not privileged. */
    private static int listLoaners(CommandSourceStack source) {
        java.util.List<String> names = dev.gathering.server.LoanerDecks.names();
        if (names.isEmpty()) {
            source.sendSuccess(
                    () -> Component.translatable("message.gathering.no_loaners"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "message.gathering.loaners_are", names.size(), String.join(", ", names)), false);
        return names.size();
    }

    /**
     * Reads the folder again.
     * <p>Answers twice: once immediately, because resolving a shelf of decklists is a network
     * call and a command that said nothing until it finished would look like it had failed,
     * and once when the decks are actually on the shelf.
     */
    private static int reloadLoaners(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.translatable("message.gathering.loaners_reloading"), true);
        dev.gathering.server.LoanerDecks.reload().thenAccept(lent ->
                source.getServer().execute(() -> source.sendSuccess(
                        () -> Component.translatable("message.gathering.loaners_reloaded", lent),
                        true)));
        return 1;
    }

    /**
     * Ends the game at the table the player is looking at.
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
        // The seat this player actually holds. Falling back to seat zero meant anybody who
        // walked past a table could end the game on it, signed as whoever was sitting in the
        // first chair - a match nobody at it agreed to stop, and with ante on, a pot settled
        // by a stranger. Ending a game cannot be undone, which is why it is a command in the
        // first place; it should not also be a thing done to you by somebody else.
        dev.gathering.core.game.SeatId seat = dev.gathering.block.TableSessions
                .seatIdOf(player.level(), origin, player.getUUID())
                .orElse(null);
        if (seat == null) {
            // Operators keep the override, because clearing a stuck table is the reason a
            // server administrator would ever type this at a table they are not sitting at.
            if (!source.hasPermission(2)) {
                source.sendFailure(net.minecraft.network.chat.Component.translatable(
                        "message.gathering.session_not_your_table"));
                return 0;
            }
            seat = new dev.gathering.core.game.SeatId(0);
        }

        dev.gathering.block.TableSessions.Outcome outcome = dev.gathering.block.TableSessions.end(
                player.level(), origin, seat, "ended by " + player.getGameProfile().getName());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                outcome.messageKey()), true);
        return outcome == dev.gathering.block.TableSessions.Outcome.ENDED ? 1 : 0;
    }

    /**
     * Opens one booster of a set into the player's inventory.
     * <p>Says nothing here about whether the server allows it; that answer lives with the
     * opening, which is also where a pack item will ask.
     */
    private static int openPack(CommandSourceStack source, String set, String kind)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PackOpening.openFor(source.getPlayerOrException(), set, kind);
        return 1;
    }

    /** One sealed pack in the hand, of a product the set really sold. */
    private static int givePack(CommandSourceStack source, String set, String kind)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PackGrant.give(source.getPlayerOrException(), set, kind);
        return 1;
    }

    /** What a set was really sold as. */
    private static int listProducts(CommandSourceStack source, String set)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PackGrant.list(source.getPlayerOrException(), set);
        return 1;
    }

    /**
     * How much of this server's own catalog nothing but the archive reaches.
     * <p>Read off what the server worked out at start rather than audited again here: the
     * audit is a file and a search per set, and a command that ran it would take a minute and
     * report a different number from the one the loot tables are using.
     */
    private static int reportTheArchive(CommandSourceStack source) {
        int size = dev.gathering.server.Archive.size();
        source.sendSuccess(() -> Component.translatable(
                size == 0 ? "message.gathering.archive_none" : "message.gathering.archive_size",
                size), false);
        return size;
    }

    private static int auditSet(CommandSourceStack source, String set)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PackCoverage.report(source.getPlayerOrException(), set);
        return 1;
    }

    private static int giveCard(CommandSourceStack source, String cardName, boolean foil)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // The same rule /gathering pack states one screen up: product out of nothing is an
        // admin grant, not a way to collect. A single card is more of a grant than a sealed
        // pack, not less - it is the thing the packs exist to produce, chosen by name, with
        // no odds in the way - and it was the one path with nothing in front of it at all.
        //
        // Only where cards are property. A server with collecting switched off is a table
        // with a box of proxies next to it, and conjuring one there is the point rather than
        // a way round anything. Said out loud instead of hidden behind requires(), because a
        // command that vanishes teaches nobody why.
        if (dev.gathering.service.ServerSettings.get().modes().collectionEnabled()
                && !source.hasPermission(2)) {
            source.sendFailure(Component.translatable("message.gathering.card_is_a_grant"));
            return 0;
        }
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
        Sending.to(player, OpenImportScreenPayload.INSTANCE);
        return 1;
    }

    /** Reads the table back, one fact to a line, for whoever is standing in front of it. */
    private static int reportTable(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        net.minecraft.core.BlockPos origin =
                dev.gathering.server.TableReport.lookedAt(player).orElse(null);
        if (origin == null) {
            source.sendFailure(Component.translatable("message.gathering.session_no_table"));
            return 0;
        }
        java.util.List<String> lines =
                dev.gathering.server.TableReport.describe(player.serverLevel(), origin);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    /** Plays cards off your own library onto your own mat, so a full board can be looked at. */
    private static int fillTable(CommandSourceStack source, int cards)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        net.minecraft.core.BlockPos origin =
                dev.gathering.server.TableReport.lookedAt(player).orElse(null);
        if (origin == null) {
            source.sendFailure(Component.translatable("message.gathering.session_no_table"));
            return 0;
        }
        dev.gathering.server.TableReport.Filled filled =
                dev.gathering.server.TableReport.fill(player.serverLevel(), origin, player, cards);
        if (!filled.worked()) {
            source.sendFailure(Component.literal(filled.problem()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "message.gathering.table_filled", filled.played()), true);
        return filled.played();
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
