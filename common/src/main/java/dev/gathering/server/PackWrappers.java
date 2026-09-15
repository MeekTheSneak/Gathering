package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Packs opened by hand whose cards are waiting under the wrapper until it is torn.
 * <p>The cards used to go into the inventory the moment the pack was right-clicked, and the tearing
 * was theater over something that had already happened. The owner found that the wrong way round: a
 * pack's cards are what you find by opening it. So they wait - written down in {@link Owed} under a
 * token, the player's property from the moment they are drawn - and are handed over when the client
 * says the wrapper is open, or its screen closed.
 * <p>Nothing about that can lose a card. A player who disconnects is handed them on the next join, as
 * anything owed is; a server that stops has them on disk; a client that never says anything has them
 * handed over after {@link #WAIT_TICKS}. The token is unguessable, so nobody can tear somebody else's
 * wrapper, and a token already torn tears nothing.
 * <p>Server thread only.
 */
public final class PackWrappers {

    /** How long a wrapper waits to be torn before its cards are handed over anyway: a minute and a half. */
    static final int WAIT_TICKS = 20 * 90;

    /** Whose wrapper a token is, and which card the pack was opened for, to remember being pulled. */
    private record Held(UUID player, String set, CardIdentity best) {
    }

    private static final Map<String, Held> HELD = new HashMap<>();

    private PackWrappers() {
    }

    /**
     * Keeps a pack's cards under a wrapper for this player.
     * <p>The token is {@code UUID.randomUUID()}, the project's one deliberate exception to the
     * level's random source, for the reason it is used for trade handles: a handle has to be
     * unguessable, not reproducible.
     *
     * @param receipt the opening receipt to replace with the wrapped cards in one write, or null
     * @return the wrapper's token, or null if it could not be written down - in which case the caller
     *     hands the cards over at once rather than risk them
     */
    public static String hold(ServerPlayer player, String receipt, String set, CardIdentity best, List<CardIdentity> giving) {
        String token = UUID.randomUUID().toString();
        boolean written = receipt == null
                ? Owed.wrapped(player.getUUID(), token, giving)
                : Owed.settledInWrapper(player.getUUID(), receipt, token, giving);
        if (!written) {
            return null;
        }
        HELD.put(token, new Held(player.getUUID(), set, best));
        MinecraftServer server = player.getServer();
        UUID who = player.getUUID();
        if (server != null) {
            ServerTicks.on(key(token), server.getTickCount() + WAIT_TICKS, () -> {
                ServerPlayer still = server.getPlayerList().getPlayer(who);
                if (still != null) {
                    torn(still, token);
                } else {
                    HELD.remove(token);
                }
            });
        }
        return token;
    }

    /** The client says this wrapper is open. Only its own player's token tears anything. */
    public static void torn(ServerPlayer player, String token) {
        Held held = HELD.get(token);
        if (held == null || !held.player().equals(player.getUUID())) {
            return;
        }
        HELD.remove(token);
        ServerTicks.forget(key(token));
        ItemStack best = held.best() == null ? ItemStack.EMPTY : CardItem.of(CardComponent.of(held.best()));
        Owed.unwrap(player, token, stack -> {
            if (!best.isEmpty() && ItemStack.isSameItemSameComponents(stack, best)) {
                CardStories.remember(stack, CardStories.pulledBy(player, held.set()));
            }
            return stack;
        });
    }

    /** A player who left keeps their wrapped cards on disk, for the next join; the memory of them goes. */
    public static void forget(UUID player) {
        HELD.entrySet().removeIf(entry -> {
            if (entry.getValue().player().equals(player)) {
                ServerTicks.forget(key(entry.getKey()));
                return true;
            }
            return false;
        });
    }

    /** For a server that is stopping: what is waiting is on disk. */
    public static void clear() {
        HELD.clear();
    }

    /** How many wrappers are waiting, for tests. */
    public static int waiting() {
        return HELD.size();
    }

    private static String key(String token) {
        return "wrapper:" + token;
    }
}
