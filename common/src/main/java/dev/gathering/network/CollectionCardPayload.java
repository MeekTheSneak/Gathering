package dev.gathering.network;

import dev.gathering.core.story.CardStory;
import dev.gathering.item.CardComponent;
import dev.gathering.item.StoryComponent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one card in a collection - how many copies there are, and the histories of the
 * copies that have one, newest first.
 *
 * @param copies    every copy of the card in the box
 * @param stories   the histories, at most {@link #MOST_STORIES} of them
 * @param untold    how many copies with a history there are past those
 */
public record CollectionCardPayload(BlockPos where, CardComponent card, int copies, List<CardStory> stories,
        int untold) implements CustomPacketPayload {

    /** The most histories one answer carries. A box keeps up to a thousand; a screen reads a handful. */
    public static final int MOST_STORIES = 32;

    public static final CustomPacketPayload.Type<CollectionCardPayload> TYPE =
            GatheringPayloads.type("collection_card");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionCardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionCardPayload::where,
                    CardComponent.STREAM_CODEC, CollectionCardPayload::card,
                    ByteBufCodecs.VAR_INT, CollectionCardPayload::copies,
                    StoryComponent.STORY_STREAM_CODEC.apply(ByteBufCodecs.list(MOST_STORIES)), CollectionCardPayload::stories,
                    ByteBufCodecs.VAR_INT, CollectionCardPayload::untold,
                    CollectionCardPayload::new);

    public CollectionCardPayload {
        copies = Math.max(0, copies);
        stories = stories == null ? List.of() : List.copyOf(stories.subList(0, Math.min(stories.size(), MOST_STORIES)));
        untold = Math.max(0, untold);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
