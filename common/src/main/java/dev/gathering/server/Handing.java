package dev.gathering.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Giving something to a player without ever destroying it.
 * <p>{@code Inventory#add} cannot be trusted to say whether it worked. Its last branch reads:
 * <pre>
 *     if (stack.getCount() == i &amp;&amp; this.player.hasInfiniteMaterials()) {
 *         stack.setCount(0);
 *         return true;
 *     }
 * </pre>
 * so for a creative player with no room it empties the stack and reports success. Every caller
 * in this mod was written as {@code if (!add(stack)) drop(stack)}, which is correct in survival
 * and never fires in creative - the card is gone and the code believes it was delivered.
 * <p>Reported from a real session as a card deleted when a deck was edited with a full bag. It
 * was every route a card can arrive by, not that one: packs, trades, draft picks, loaners, a
 * collection take, the ante pot paying out. A card is a collection item and must never
 * evaporate because a bag was full.
 * <p>So {@code add} is never handed more than the slot it is filling has room for - see
 * {@link #give} - and what will not fit is dropped rather than offered.
 * <p>And nothing is handed to a player who cannot keep it. A player on the death screen is still
 * the entity the server holds for them until they respawn, its bag already emptied onto the
 * ground, and respawning makes a new one without copying that bag across: a booster torn open by
 * the death screen closing its reveal went into it and out of the world, and so did a prize, a
 * pool, a deck or a pot paid out to somebody who had just died. An entity that has been removed
 * is the same, and so is one whose connection has gone. What is meant for any of them goes to the
 * same player if they are here as another entity - work that finished after a respawn held the
 * one the respawn replaced - and otherwise is written down in {@link Owed} and handed over when
 * they respawn or next join.
 */
public final class Handing {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private Handing() {
    }

    /**
     * Puts this stack in the player's inventory, or on the floor at their feet.
     * <p>The stack is consumed either way: after this it is empty, and the item exists in the
     * world exactly once.
     * <p>To the player as they are now - see {@link #canReceive}. One who has respawned or
     * reconnected since whoever is giving looked them up is handed it as the entity the server
     * holds for them now; one who is dead or gone has it kept for them.
     */
    public static void give(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        Player to = canReceive(player) ? player : stillHere(player);
        if (to == null) {
            keepFor(player, stack);
            return;
        }
        // One slot at a time, each handed exactly what it has room for, and the rest dropped at
        // their feet: vanilla's own way of putting a stack back. Handing add the whole stack
        // whenever any one slot had room was the creative branch above again, a step removed -
        // a stack of sixty-four with room for four kept the four and deleted the sixty.
        to.getInventory().placeItemBackInInventory(stack.copyAndClear());
    }

    /**
     * Whether a player can be handed something and keep it.
     * <p>Alive - not dead, not removed - and still connected. Vanilla asks the same thing before
     * putting back what was left in a crafting grid ({@code AbstractContainerMenu#clearContainer})
     * and drops it instead; this keeps it instead, for the reasons on the class.
     */
    public static boolean canReceive(Player player) {
        return player != null && player.isAlive()
                && !(player instanceof ServerPlayer connected && connected.hasDisconnected());
    }

    /**
     * The same player as the entity the server holds for them now, if that one can be handed
     * things: the player after a respawn, or after logging out and back in. Null otherwise.
     */
    private static Player stillHere(Player player) {
        if (!(player instanceof ServerPlayer old) || old.getServer() == null) {
            return null;
        }
        ServerPlayer now = old.getServer().getPlayerList().getPlayer(old.getUUID());
        return now != null && now != old && canReceive(now) ? now : null;
    }

    /**
     * Writes down, for a player who cannot be handed it, a stack they are owed, and empties it.
     * <p>Whole, as {@link Owed#anItem} keeps a deck, so a card's history goes with it. Only if that
     * cannot be written is it put on the ground where they are, which is what vanilla does with
     * everything a dead player was carrying.
     */
    private static void keepFor(Player player, ItemStack stack) {
        if (Owed.anItem(player.getUUID(), stack)) {
            stack.setCount(0);
            return;
        }
        LOGGER.error("Could not keep {} for {}, who cannot be handed anything now, so it is on the ground where"
                + " they are", stack, player.getUUID());
        player.drop(stack.copyAndClear(), false);
    }
}
