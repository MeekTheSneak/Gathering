package dev.gathering.network;

import dev.gathering.core.draft.PodSettings;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a signup as a player at it sees it.
 * <p>Everything here is already public at the table - who is sitting, who has put in how many
 * packs - and nothing about what is in any pack, which nobody knows until it is opened.
 *
 * @param open     false when the signup has ended, so a screen showing it closes
 * @param show     whether to open the signup screen, rather than only update one already open
 * @param host     the host's name
 * @param youHost  whether the player this is sent to is the host
 * @param players  who is seated, and where their packs stand
 * @param status   a translation key: why it cannot start yet, or that it can
 * @param opening  whether the packs are being opened right now
 */
public record PodLobbyPayload(
        BlockPos table, boolean open, boolean show, String host, boolean youHost,
        PodSettings settings, List<Player> players, String status, boolean opening)
        implements AtATable {

    /** One seated player: their name, packs put in, packs still owed. */
    public record Player(String name, int in, int owed) {

        static final StreamCodec<RegistryFriendlyByteBuf, Player> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Player::name,
                ByteBufCodecs.VAR_INT, Player::in,
                ByteBufCodecs.VAR_INT, Player::owed,
                Player::new);
    }

    /** A cluster seats eight. */
    public static final int MOST_PLAYERS = 8;

    public static final CustomPacketPayload.Type<PodLobbyPayload> TYPE = GatheringPayloads.type("pod_lobby");

    public static final StreamCodec<RegistryFriendlyByteBuf, PodLobbyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.table());
                buffer.writeBoolean(payload.open());
                buffer.writeBoolean(payload.show());
                buffer.writeUtf(payload.host(), 64);
                buffer.writeBoolean(payload.youHost());
                PodWire.SETTINGS.encode(buffer, payload.settings());
                Player.CODEC.apply(ByteBufCodecs.list(MOST_PLAYERS)).encode(buffer, payload.players());
                buffer.writeUtf(payload.status(), 128);
                buffer.writeBoolean(payload.opening());
            },
            buffer -> new PodLobbyPayload(
                    BlockPos.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(64),
                    buffer.readBoolean(),
                    PodWire.SETTINGS.decode(buffer),
                    Player.CODEC.apply(ByteBufCodecs.list(MOST_PLAYERS)).decode(buffer),
                    buffer.readUtf(128),
                    buffer.readBoolean()));

    public PodLobbyPayload {
        players = players == null ? List.of() : List.copyOf(players);
        host = host == null ? "" : host;
        status = status == null ? "" : status;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
