package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.story.CardStory;
import dev.gathering.core.story.HowItCame;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a card has been, carried on the card.
 * <p>A second component beside {@link CardComponent} rather than a field in it, on purpose.
 * What a card <em>is</em> is a printing and a finish, and that has to stay the cheapest thing
 * in the mod - it is on every card of every deck of every table. What a card has <em>done</em>
 * is a different question, most cards have no answer to it, and a card with no answer carries
 * nothing at all.
 * <p>It does mean a card with a story does not stack with one without, which is correct: they
 * are not the same object. Identity is untouched either way - {@link CardComponent#toIdentity}
 * knows nothing about this - so a collection still counts two copies of one printing as two
 * copies of one printing however different their histories.
 * <p>See {@link CardStory} for why it is bounded and which end it drops.
 */
public record StoryComponent(CardStory story) {

    private static final Codec<HowItCame> HOW = Codec.STRING.xmap(
            said -> {
                HowItCame how = HowItCame.named(said);
                return how == null ? HowItCame.PULLED : how;
            },
            HowItCame::id);

    private static final Codec<CardStory.Chapter> CHAPTER = RecordCodecBuilder.create(
            instance -> instance.group(
                    HOW.fieldOf("how").forGetter(CardStory.Chapter::how),
                    Codec.STRING.optionalFieldOf("who", "").forGetter(CardStory.Chapter::who),
                    Codec.STRING.optionalFieldOf("from", "").forGetter(CardStory.Chapter::from),
                    Codec.STRING.optionalFieldOf("what", "").forGetter(CardStory.Chapter::what),
                    Codec.STRING.optionalFieldOf("day", "").forGetter(CardStory.Chapter::day))
                    .apply(instance, CardStory.Chapter::new));

    public static final Codec<StoryComponent> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    CHAPTER.listOf().fieldOf("chapters")
                            .forGetter(component -> component.story().chapters()),
                    Codec.INT.optionalFieldOf("forgotten", 0)
                            .forGetter(component -> component.story().forgotten()))
                    .apply(instance, (chapters, forgotten) ->
                            new StoryComponent(new CardStory(chapters, forgotten))));

    private static final StreamCodec<io.netty.buffer.ByteBuf, HowItCame> HOW_STREAM =
            ByteBufCodecs.idMapper(
                    id -> id >= 0 && id < HowItCame.all().length
                            ? HowItCame.all()[id]
                            : HowItCame.PULLED,
                    HowItCame::ordinal);

    private static final StreamCodec<RegistryFriendlyByteBuf, CardStory.Chapter> CHAPTER_STREAM =
            StreamCodec.composite(
                    HOW_STREAM, CardStory.Chapter::how,
                    ByteBufCodecs.stringUtf8(CardStory.LONGEST_NAME), CardStory.Chapter::who,
                    ByteBufCodecs.stringUtf8(CardStory.LONGEST_NAME), CardStory.Chapter::from,
                    ByteBufCodecs.stringUtf8(CardStory.LONGEST_NAME), CardStory.Chapter::what,
                    ByteBufCodecs.stringUtf8(CardStory.LONGEST_NAME), CardStory.Chapter::day,
                    CardStory.Chapter::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, StoryComponent> STREAM_CODEC =
            StreamCodec.composite(
                    CHAPTER_STREAM.apply(ByteBufCodecs.list(CardStory.MOST)),
                    component -> component.story().chapters(),
                    ByteBufCodecs.VAR_INT, component -> component.story().forgotten(),
                    (chapters, forgotten) ->
                            new StoryComponent(new CardStory(chapters, forgotten)));

    public StoryComponent {
        story = story == null ? CardStory.NONE : story;
    }

    public static StoryComponent of(CardStory story) {
        return new StoryComponent(story);
    }

    /**
     * What this card remembers, which for almost every card is nothing.
     * <p>Here rather than beside each reader so the server writing one and the client drawing
     * one are asking the same question of the same component.
     */
    public static CardStory on(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return CardStory.NONE;
        }
        StoryComponent held = stack.get(
                dev.gathering.registry.GatheringComponents.STORY.get());
        return held == null ? CardStory.NONE : held.story();
    }

    /** Whether this is worth putting on a card at all. */
    public boolean isEmpty() {
        return story.isEmpty();
    }

    /** Every chapter, for anything reading it. */
    public List<CardStory.Chapter> chapters() {
        return story.chapters();
    }
}
