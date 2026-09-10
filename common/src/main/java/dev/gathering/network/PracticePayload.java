package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "let me practise at that table", or "I am done practising".
 * <p>Two words rather than a decklist or a seat number, because everything else about a
 * practice game is the server's to decide - which cards, which seat, how many. A client that
 * skipped the screen entirely can send this and gets exactly what the button would have got.
 * <p>An enum on the wire, not a boolean and not a string: a payload that carried a name would
 * be a payload somebody could send an unexpected name in, and one that carried a boolean would
 * read as {@code practice(true)} at every call site.
 */
public record PracticePayload(BlockPos table, What what) implements CustomPacketPayload {

    /** What is being asked for. */
    public enum What {

        /** Start one here, if this table is free and nobody else is at it. */
        START,

        /** End the one here, if it is mine and it is practice. */
        STOP
    }

    public static final CustomPacketPayload.Type<PracticePayload> TYPE =
            GatheringPayloads.type("practice");

    public static final StreamCodec<RegistryFriendlyByteBuf, PracticePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PracticePayload::table,
                    ByteBufCodecs.idMapper(
                            ordinal -> What.values()[Math.clamp(ordinal, 0, What.values().length - 1)],
                            What::ordinal),
                    PracticePayload::what,
                    PracticePayload::new);

    public PracticePayload {
        what = what == null ? What.STOP : what;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
