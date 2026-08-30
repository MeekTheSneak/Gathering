package dev.gathering.server;

import dev.gathering.Gathering;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's advancements, and the one way anything grants one.
 *
 * <p>Most of them cannot be described to the game: "opened a booster", "finished a game",
 * "completed a set" are not things vanilla has a trigger for. So each is declared with an
 * impossible criterion - nothing in the world can ever satisfy it - and granted from the code
 * that knows the thing happened. The advancement file stays the single place that says what
 * the tree looks like, and a pack that removes one simply removes it.
 *
 * <p>Named for what they are rather than for the mechanism: these are the four or five
 * moments in this mod worth remembering, and the whole list is short on purpose. An
 * advancement for every action is a list nobody reads.
 */
public final class Achievements {

    /** Held cardboard, which is the root and the only one the game itself can see. */
    public static final String ROOT = "root";

    public static final String FIRST_PACK = "first_pack";
    public static final String FIRST_MYTHIC = "first_mythic";
    public static final String FIRST_DRAFT = "first_draft";
    public static final String SET_COMPLETE = "set_complete";
    public static final String FIRST_DECK = "first_deck";
    public static final String FIRST_GAME = "first_game";
    public static final String FIRST_TRADE = "first_trade";

    private Achievements() {
    }

    /**
     * Grants one, if this server has it and this player has not already got it.
     *
     * <p>Every remaining criterion rather than a named one, so the code that grants an
     * advancement does not have to know what the file called the criterion inside it - which
     * is the detail that would silently stop matching the first time somebody renamed one.
     *
     * <p>Silent when the advancement is not there. A data pack is allowed to remove any of
     * these, and a server that has is not a server with a fault in it.
     *
     * <p>Server thread only.
     */
    public static void award(ServerPlayer player, String name) {
        if (player == null || player.getServer() == null) {
            return;
        }
        AdvancementHolder holder = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, name));
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        // Copied first: awarding mutates the progress this is walking.
        java.util.List<String> remaining = new java.util.ArrayList<>();
        for (String criterion : progress.getRemainingCriteria()) {
            remaining.add(criterion);
        }
        for (String criterion : remaining) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
