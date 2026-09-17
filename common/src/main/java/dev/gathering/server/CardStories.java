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
 * <p>The one place a story is ever added, so the four rules about doing it are written once:
 * only a card item takes one, only the events on {@link HowItCame} write one, the day comes
 * from the machine rather than from anything a client said, and a card that already has a
 * history keeps it. Everything else in the mod hands cards out without touching this, which is
 * the point - a card with nothing to say carries nothing.
 * <p>Server thread only.
 */
public final class CardStories {

    private CardStories() {
    }

    /**
     * Adds a chapter to whatever this stack already remembers.
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

    /**
     * Somebody ran a command and this card came out of it - directly, or out of a pack the
     * command made.
     *
     * @param who     whoever ran it, by name: a player, or the server's own name for a console
     * @param command the command's own words after {@code /gathering}, such as {@code foil} or
     *                {@code pack give} - a chapter keeps sixteen characters of each field
     */
    public static CardStory.Chapter spawnedBy(String who, String command) {
        return new CardStory.Chapter(HowItCame.SPAWNED, who == null ? "" : who,
                "", command == null ? "" : command, today());
    }

    /** The same, for a command a source ran: a player's name, or the server's for a console. */
    public static CardStory.Chapter spawnedBy(net.minecraft.commands.CommandSourceStack source, String command) {
        return spawnedBy(source.getTextName(), command);
    }

    /**
     * Puts these chapters on a pack, so every card that comes out of it carries them.
     * <p>A pack is not a card and does not show a history of its own. It holds one here only to
     * hand it on: see {@link #chaptersOnPack}, read by whatever opens it.
     */
    public static void rememberOnPack(ItemStack pack, CardStory.Chapter chapter) {
        if (pack == null || pack.isEmpty() || chapter == null
                || dev.gathering.item.PackItem.packOf(pack).isEmpty()) {
            return;
        }
        var type = GatheringComponents.STORY.get();
        StoryComponent already = pack.get(type);
        CardStory story = already == null ? CardStory.NONE : already.story();
        pack.set(type, StoryComponent.of(story.and(chapter)));
    }

    /** What a pack will hand on to every card opened out of it, which is almost always nothing. */
    public static java.util.List<CardStory.Chapter> chaptersOnPack(ItemStack pack) {
        return StoryComponent.on(pack).chapters();
    }

    /** Every one of these chapters, onto a card. */
    public static void rememberAll(ItemStack stack, java.util.List<CardStory.Chapter> chapters) {
        if (chapters == null) {
            return;
        }
        for (CardStory.Chapter chapter : chapters) {
            remember(stack, chapter);
        }
    }

    private static String nameOf(ServerPlayer player) {
        return player == null ? "" : player.getGameProfile().getName();
    }

    /**
     * Today, on the machine the server is running on.
     * <p>The real date rather than the world's, because this is read years later by a person:
     * "won in an ante game on the fourteenth of March" means something, and "won on day 4,812"
     * means nothing to anybody who was not counting.
     */
    private static String today() {
        return LocalDate.now().toString();
    }
}
