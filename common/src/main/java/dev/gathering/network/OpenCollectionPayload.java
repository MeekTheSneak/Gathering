package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open this collection.
 * <p>Carries what the screen needs before it has asked for anything - whose it is, how big it
 * is, and what this player is allowed to do with it - and no cards at all. The cards arrive a
 * page at a time in answer to a search, because a collection is meant to run to ten thousand
 * of them and a screen shows forty.
 * <p>What a player may do is decided on the server and sent rather than worked out here: a
 * client that decided for itself would be a client that could decide differently. Whether it is
 * theirs travels the same way, and for the same reason - it is what puts the Share button on the
 * screen, and the server refuses everything behind that button to anybody else regardless.
 * <p>The three answers are bits of one number rather than three fields, because a stream codec is
 * composed of at most six parts and this would have been seven.
 */
public record OpenCollectionPayload(
        BlockPos where, String label, int total, int distinct, int allowed)
        implements CustomPacketPayload {

    public static final int MOST_LABEL_CHARACTERS = 64;

    /** May take cards out. */
    public static final int TAKE = 1;

    /** May put cards in. */
    public static final int ADD = 2;

    /** Owns it, and so may say who else may do either. */
    public static final int OWN = 4;

    public static final CustomPacketPayload.Type<OpenCollectionPayload> TYPE =
            GatheringPayloads.type("open_collection");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCollectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenCollectionPayload::where,
                    ByteBufCodecs.stringUtf8(MOST_LABEL_CHARACTERS), OpenCollectionPayload::label,
                    ByteBufCodecs.VAR_INT, OpenCollectionPayload::total,
                    ByteBufCodecs.VAR_INT, OpenCollectionPayload::distinct,
                    ByteBufCodecs.VAR_INT, OpenCollectionPayload::allowed,
                    OpenCollectionPayload::new);

    public OpenCollectionPayload {
        label = label == null ? "" : label;
    }

    /** The number that says a player may do these things. */
    public static int allowing(boolean mayTake, boolean mayAdd, boolean yours) {
        return (mayTake ? TAKE : 0) | (mayAdd ? ADD : 0) | (yours ? OWN : 0);
    }

    public boolean mayTake() {
        return (allowed & TAKE) != 0;
    }

    public boolean mayAdd() {
        return (allowed & ADD) != 0;
    }

    public boolean yours() {
        return (allowed & OWN) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
