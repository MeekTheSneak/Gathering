package dev.gathering.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: make me this many of this exact token.
 * <p>By printing, for the three places a player has already picked a particular token: a card's
 * own "make its token" row, which Scryfall links to the exact printing; a choice between the
 * tokens that share a name; and a remembered token. Asked by name, those put down whichever
 * token of that name was printed most recently.
 * <p>The client names the printing and nothing else. The server looks it up and refuses anything
 * that is not a token or an emblem, so a client can no more make a real card this way than it
 * could by typing its name - and it is the server that says what the token is.
 */
public record MakeTokenPayload(BlockPos table, UUID printing, int count) implements AtATable {

    public static final CustomPacketPayload.Type<MakeTokenPayload> TYPE =
            GatheringPayloads.type("make_token");

    public static final StreamCodec<RegistryFriendlyByteBuf, MakeTokenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MakeTokenPayload::table,
                    UUIDUtil.STREAM_CODEC, MakeTokenPayload::printing,
                    ByteBufCodecs.VAR_INT, MakeTokenPayload::count,
                    MakeTokenPayload::new);

    public MakeTokenPayload {
        count = Math.max(1, Math.min(CreateTokenPayload.MAX_COUNT, count));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
