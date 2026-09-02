package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: make me some tokens called this.
 * <p>A name and a number, never a card id. The server looks the name up against Scryfall's
 * token layout and builds the identity itself, so nothing a client types decides what a token
 * <em>is</em> - the same rule every other name that crosses this boundary follows.
 *
 * @param count how many, bounded on decode so a typo cannot ask for a million Thrulls
 */
public record CreateTokenPayload(BlockPos table, String name, int count) implements CustomPacketPayload {

    /** Longer than any real token name and short enough that a bad one is not a payload. */
    public static final int MAX_NAME = 64;

    /** More than anybody makes at once, and few enough that the board survives it. */
    public static final int MAX_COUNT =
            dev.gathering.core.game.event.GameEvent.TokenCreated.MOST_AT_ONCE;

    public static final CustomPacketPayload.Type<CreateTokenPayload> TYPE =
            GatheringPayloads.type("create_token");

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateTokenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CreateTokenPayload::table,
                    ByteBufCodecs.stringUtf8(MAX_NAME), CreateTokenPayload::name,
                    ByteBufCodecs.VAR_INT, CreateTokenPayload::count,
                    CreateTokenPayload::new);

    public CreateTokenPayload {
        count = Math.max(1, Math.min(MAX_COUNT, count));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
