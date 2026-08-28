package dev.gathering.server;

import dev.gathering.core.card.Dungeon;
import dev.gathering.network.BringInDungeonPayload;
import dev.gathering.service.CardDataService;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bringing a dungeon in from outside the game.
 *
 * <p>A dungeon is a real printed card that never goes in a deck. It starts outside the game
 * and arrives when something ventures, which means there is no way to draw one, buy one or
 * open one - so without a door for it, half of Adventures in the Forgotten Realms did nothing
 * at this table. This is the door.
 *
 * <p>It arrives as a token for the same reason every other card from outside the game does:
 * it is not anybody's card, it goes back nowhere, and it ceases to exist when the game does.
 * Which room you are in is written on it with the ordinary pen - the mod has no rules engine
 * and a dungeon is a rules engine's worth of rooms, so the table tracks it the way a table
 * does, by writing it down where everybody can see.
 *
 * <p>The client sends which of the four, never a name. See {@link Dungeon}.
 *
 * <p>Server thread only.
 */
public final class Dungeons {

    private Dungeons() {
    }

    public static void handle(ServerPlayer player, CardDataService service, BringInDungeonPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        Dungeon dungeon = payload.dungeon();
        service.findByName(dungeon.cardName())
                .whenComplete((found, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.card_lookup_failed", dungeon.cardName()));
                        return;
                    }
                    TokenCreation.put(
                            player, player.serverLevel(), at.origin(), at.seat(),
                            found.map(List::of).orElse(List.of()), 1, dungeon.cardName(),
                            "message.gathering.dungeon_entered",
                            "message.gathering.dungeon_not_found");
                }));
    }
}
