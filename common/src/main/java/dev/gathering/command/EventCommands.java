package dev.gathering.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gathering.server.events.EventRecords;
import dev.gathering.server.events.EventViews;
import dev.gathering.server.events.Events;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /gathering events}: the tournament list for everybody, anybody's public record, and the
 * admin tools the owner asked for - reading a private rating, voiding an event's effect on ratings,
 * leaving a player out of ratings, and marking an event official.
 */
public final class EventCommands {

    private EventCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> events() {
        return Commands.literal("events")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    EventViews.list(player, true);
                    return 1;
                })
                .then(Commands.literal("record")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> record(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("rating")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> rating(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("void")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(context -> voidRatings(context.getSource(), StringArgumentType.getString(context, "event")))))
                .then(Commands.literal("exclude")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("excluded", BoolArgumentType.bool())
                                        .executes(context -> exclude(context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                BoolArgumentType.getBool(context, "excluded"))))))
                .then(Commands.literal("official")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .then(Commands.argument("official", BoolArgumentType.bool())
                                        .executes(context -> official(context.getSource(),
                                                StringArgumentType.getString(context, "event"),
                                                BoolArgumentType.getBool(context, "official"))))));
    }

    private static int record(CommandSourceStack source, String name) {
        var found = EventRecords.byName(name).orElse(null);
        if (found == null) {
            source.sendFailure(Component.translatable("message.gathering.event.no_record"));
            return 0;
        }
        var record = found.getValue();
        source.sendSuccess(() -> Component.translatable("message.gathering.event.record", record.name(),
                record.matchWins(), record.matchLosses(), record.matchDraws(),
                Math.round(record.matchWinRate() * 100), record.eventsPlayed(), record.eventsWon()), false);
        return 1;
    }

    private static int rating(CommandSourceStack source, String name) {
        var found = EventRecords.byName(name).orElse(null);
        if (found == null) {
            source.sendFailure(Component.translatable("message.gathering.event.no_record"));
            return 0;
        }
        var record = found.getValue();
        source.sendSuccess(() -> Component.translatable("message.gathering.event.rating", record.name(),
                Math.round(record.rating()), record.excluded()), false);
        return 1;
    }

    private static int voidRatings(CommandSourceStack source, String event) {
        UUID id = eventId(event);
        if (id == null || !EventRecords.voidRatings(id)) {
            source.sendFailure(Component.translatable("message.gathering.event.nothing_to_void"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.gathering.event.voided"), true);
        return 1;
    }

    private static int exclude(CommandSourceStack source, String name, boolean excluded) {
        var server = source.getServer();
        var online = server.getPlayerList().getPlayerByName(name);
        UUID id = online != null ? online.getUUID() : EventRecords.byName(name).map(java.util.Map.Entry::getKey).orElse(null);
        if (id == null) {
            source.sendFailure(Component.translatable("message.gathering.event.no_record"));
            return 0;
        }
        EventRecords.setExcluded(id, name, excluded);
        source.sendSuccess(() -> Component.translatable(excluded
                ? "message.gathering.event.excluded" : "message.gathering.event.included", name), true);
        return 1;
    }

    private static int official(CommandSourceStack source, String event, boolean isOfficial) {
        UUID id = eventId(event);
        if (id == null) {
            source.sendFailure(Component.translatable("message.gathering.event.no_such_event"));
            return 0;
        }
        EventRecords.setOfficial(id, isOfficial);
        source.sendSuccess(() -> Component.translatable(isOfficial
                ? "message.gathering.event.marked_official" : "message.gathering.event.unmarked_official"), true);
        return 1;
    }

    /** An event by its id, or the first whose name starts with what was typed. */
    private static UUID eventId(String typed) {
        try {
            return UUID.fromString(typed);
        } catch (IllegalArgumentException notAnId) {
            return Events.all().stream()
                    .filter(state -> state.tournament().name().replace(' ', '_').toLowerCase(java.util.Locale.ROOT)
                            .startsWith(typed.toLowerCase(java.util.Locale.ROOT)))
                    .map(state -> state.tournament().id()).findFirst().orElse(null);
        }
    }
}
