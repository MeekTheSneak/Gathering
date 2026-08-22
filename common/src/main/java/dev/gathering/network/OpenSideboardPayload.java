package dev.gathering.network;

import dev.gathering.item.DeckComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: here is your deck, change it before the next game.
 *
 * <p>Carries the whole deck rather than a diff, because the client has not seen it since it
 * was put down and the table has been holding it ever since. It is the player's own deck, so
 * there is nothing here they are not entitled to - which is why a sideboard screen can exist
 * at all, and why it never shows anybody else's.
 *
 * @param gameNumber which game of the set is about to be played, for the screen to say so
 */
public record OpenSideboardPayload(BlockPos table, DeckComponent deck, int gameNumber, int bestOf)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenSideboardPayload> TYPE =
            GatheringPayloads.type("open_sideboard");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSideboardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenSideboardPayload::table,
                    DeckComponent.STREAM_CODEC, OpenSideboardPayload::deck,
                    ByteBufCodecs.VAR_INT, OpenSideboardPayload::gameNumber,
                    ByteBufCodecs.VAR_INT, OpenSideboardPayload::bestOf,
                    OpenSideboardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
