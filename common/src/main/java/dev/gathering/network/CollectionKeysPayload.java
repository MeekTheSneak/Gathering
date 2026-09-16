package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: who is let into this collection, and whether anybody may look in it.
 * <p>Sent only to its owner, and only when asked. Names rather than ids, because a name is what
 * the owner typed and what they will read back; a client that was handed ids would be a client
 * that had been told who plays on this server.
 */
public record CollectionKeysPayload(BlockPos where, Key everyone, List<Key> keys)
        implements CustomPacketPayload {

    /** Enough for a shared base's worth of people, and a bound on what one message can carry. */
    public static final int MOST_KEYS = 64;

    /** One person, and what they may do. */
    public record Key(String name, boolean look, boolean take, boolean add) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Key> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(CollectionKeyPayload.MOST_NAME_CHARACTERS), Key::name,
                        ByteBufCodecs.BOOL, Key::look,
                        ByteBufCodecs.BOOL, Key::take,
                        ByteBufCodecs.BOOL, Key::add,
                        Key::new);

        public Key {
            name = name == null ? "" : name;
        }
    }

    public static final CustomPacketPayload.Type<CollectionKeysPayload> TYPE =
            GatheringPayloads.type("collection_keys");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionKeysPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionKeysPayload::where,
                    Key.STREAM_CODEC, CollectionKeysPayload::everyone,
                    Key.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_KEYS)), CollectionKeysPayload::keys,
                    CollectionKeysPayload::new);

    public CollectionKeysPayload {
        everyone = everyone == null ? new Key("", false, false, false) : everyone;
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
