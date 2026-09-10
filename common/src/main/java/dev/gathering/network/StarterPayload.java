package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.List;

/**
 * Client to server: "these are the two colors I picked".
 * <p>Two letters and nothing else. What a color buys is decided at the other end, out of the
 * server's own settings and the published collation, so the most a client can say here is
 * which ninth of a product its seed should choose from - and even that is checked against the
 * five real colors before it is used.
 * <p>Bounded at two, because two is what the screen asks for and a list of four hundred
 * letters is not a player.
 */
public record StarterPayload(List<String> colors) implements CustomPacketPayload {

    /** Two, which is how many the screen asks for. */
    public static final int MOST_COLOURS = 2;

    public static final CustomPacketPayload.Type<StarterPayload> TYPE =
            GatheringPayloads.type("starter");

    public static final StreamCodec<RegistryFriendlyByteBuf, StarterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1).apply(ByteBufCodecs.list(MOST_COLOURS)),
                    StarterPayload::colors,
                    StarterPayload::new);

    public StarterPayload {
        colors = colors == null ? List.of() : List.copyOf(colors);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
