package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open the decklist import screen.
 *
 * <p>Import is reached through a command so it is discoverable and permission-gated on the
 * server, rather than through a client keybind that would work whether or not the server
 * allows importing at all.
 */
public record OpenImportScreenPayload() implements CustomPacketPayload {

    public static final OpenImportScreenPayload INSTANCE = new OpenImportScreenPayload();

    public static final CustomPacketPayload.Type<OpenImportScreenPayload> TYPE =
            GatheringPayloads.type("open_import_screen");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenImportScreenPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
