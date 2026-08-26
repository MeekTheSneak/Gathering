package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: this table is about to play for keeps, are you in?
 *
 * @param cardsEach how many cards each player stakes, so the question names the stakes
 *                  rather than saying "ante" and leaving somebody to look it up
 * @param waitingOn how many seats have not answered, so the table can see it is waiting on
 *                  people rather than broken
 * @param iAmIn     whether this player has already said yes, so the screen can show it
 * @param over      the question is finished - agreed, refused, or the table went away
 */
public record AnteConsentPayload(
        BlockPos table, int cardsEach, int waitingOn, boolean iAmIn, boolean over)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnteConsentPayload> TYPE =
            GatheringPayloads.type("ante_consent");

    public static final StreamCodec<RegistryFriendlyByteBuf, AnteConsentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AnteConsentPayload::table,
                    ByteBufCodecs.VAR_INT, AnteConsentPayload::cardsEach,
                    ByteBufCodecs.VAR_INT, AnteConsentPayload::waitingOn,
                    ByteBufCodecs.BOOL, AnteConsentPayload::iAmIn,
                    ByteBufCodecs.BOOL, AnteConsentPayload::over,
                    AnteConsentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The question is finished; take it off the screen. */
    public static AnteConsentPayload over(BlockPos table) {
        return new AnteConsentPayload(table, 0, 0, false, true);
    }
}
