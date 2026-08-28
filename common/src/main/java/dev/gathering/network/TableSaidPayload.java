package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: somebody at that table said this.
 *
 * <p>Its own payload rather than a plain chat message, because the board is a screen and a
 * screen covers the chat window. A player reading their hand has to be able to hear the person
 * across the table without closing the game, so the line goes to the client as data and the
 * client both puts it in the chat window - for whoever is walking around - and draws it over
 * the felt for whoever is playing.
 *
 * <p>Carries a name rather than a seat: the people at a table include the ones watching it,
 * and a watcher has no seat. It is the speaker's own profile name, which every player at the
 * table can already read off the name above their head.
 */
public record TableSaidPayload(BlockPos table, String who, String text) implements CustomPacketPayload {

    /** Long enough for any name Minecraft allows, and bounded because everything here is. */
    public static final int LONGEST_NAME = 64;

    public static final CustomPacketPayload.Type<TableSaidPayload> TYPE =
            GatheringPayloads.type("table_said");

    public static final StreamCodec<RegistryFriendlyByteBuf, TableSaidPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TableSaidPayload::table,
                    ByteBufCodecs.stringUtf8(LONGEST_NAME), TableSaidPayload::who,
                    ByteBufCodecs.stringUtf8(TableChatPayload.LONGEST * 4), TableSaidPayload::text,
                    TableSaidPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
