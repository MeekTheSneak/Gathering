package dev.gathering.server;

import dev.gathering.core.story.CardStory;
import dev.gathering.core.story.HowItCame;
import dev.gathering.item.StoryComponent;
import dev.gathering.registry.GatheringComponents;
import java.time.LocalDate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Writing on a card what has just happened to it.
 *
 * <p>The one place a story is ever added, so the four rules about doing it are written once:
 * only a card item takes one, only the events on {@link HowItCame} write one, the day comes
 * from the machine rather than from anything a client said, and a card that already has a
 * history keeps it. Everything else in the mod hands cards out without touching this, which is
 * the point - a card with nothing to say carries nothing.
 *
 * <p>Server thread only.
 */
public final class CardStories {

    private CardStories() {
    }

    /**
     * Adds a chapter to whatever this stack already remembers.
     *
     * <p>Quietly does nothing to something that is not a card. Every caller is handing over
     * whatever it happened to be giving somebody, and a pack of boosters going through here
     * should be a pack of boosters coming out rather than a check at each call site.
     */
    public static void remember(ItemStack stack, CardStory.Chapter chapter) {
        if (stack == null || stack.isEmpty() || chapter == null
                || !dev.gathering.item.CardItem.cardOf(stack).isPresent()) {
            return;
        }
        var type = GatheringComponents.STORY.get();
        StoryComponent already = stack.get(type);
        CardStory story = already == null ? CardStory.NONE : already.story();
        stack.set(type, StoryComponent.of(story.and(chapter)));
    }

    /** What a card remembers, which for almost every card is nothing. */
    public static CardStory storyOf(ItemStack stack) {
        return StoryComponent.on(stack);
    }

    /** Somebody opened a pack and this came out of it. */
    public static CardStory.Chapter pulledBy(ServerPlayer player, String setCode) {
        return new CardStory.Chapter(HowItCame.PULLED, nameOf(player), "", setCode, today());
    }

    /** Somebody won this in an ante pot, off whoever staked it. */
    public static CardStory.Chapter wonBy(ServerPlayer player, String from) {
        return new CardStory.Chapter(HowItCame.WON, nameOf(player), from, "", today());
    }

    /** Somebody traded for this, with whoever put it up. */
    public static CardStory.Chapter tradedTo(ServerPlayer player, ServerPlayer from) {
        return new CardStory.Chapter(
                HowItCame.TRADED, nameOf(player), nameOf(from), "", today());
    }

    private static String nameOf(ServerPlayer player) {
        return player == null ? "" : player.getGameProfile().getName();
    }

    /**
     * Today, on the machine the server is running on.
     *
     * <p>The real date rather than the world's, because this is read years later by a person:
     * "won in an ante game on the fourteenth of March" means something, and "won on day 4,812"
     * means nothing to anybody who was not counting.
     */
    private static String today() {
        return LocalDate.now().toString();
    }
}
