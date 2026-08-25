package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a drafted deck may be built from.
 *
 * <p>A deck handed out by a draft is a pile of picks, and the whole of limited is that you
 * play what you opened. So the pool travels with the deck: what is in it was settled when the
 * last pack emptied, and what the deck is built from is checked against it rather than
 * against a list of legal cards.
 *
 * <p>Its own component rather than a seventh field on {@link DeckComponent}, for two reasons.
 * A deck's contents change constantly as somebody builds and a pool never changes at all, so
 * they have different lifetimes; and an ordinary imported deck has no pool, which as a field
 * would be an empty list on every deck item in the game rather than an absent component.
 *
 * @param cards every card drafted, one entry per physical card
 * @param fromPod which pod it came out of, so two pools from two drafts are never confused,
 *                and so a deck check can say which draft it is checking against. A name, not
 *                the pod's shuffle seed - that value never leaves the server and must not be
 *                somewhere a field name invites somebody to log it.
 */
public record DraftedPool(List<CardComponent> cards, String fromPod) {

    public DraftedPool {
        cards = cards == null ? List.of() : List.copyOf(cards);
        fromPod = fromPod == null ? "" : fromPod;
    }

    public static final Codec<DraftedPool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CardComponent.CODEC.listOf().optionalFieldOf("cards", List.of())
                    .forGetter(DraftedPool::cards),
            Codec.STRING.optionalFieldOf("pod", "").forGetter(DraftedPool::fromPod))
            .apply(instance, DraftedPool::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DraftedPool> STREAM_CODEC =
            StreamCodec.composite(
                    CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(DeckComponent.MAX_CARDS)),
                    DraftedPool::cards,
                    ByteBufCodecs.stringUtf8(64), DraftedPool::fromPod,
                    DraftedPool::new);

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
