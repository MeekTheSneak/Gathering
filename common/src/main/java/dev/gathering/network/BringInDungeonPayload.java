package dev.gathering.network;

import dev.gathering.core.card.Dungeon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: put a dungeon on the table.
 *
 * <p>Carries which one rather than what it is called. A dungeon starts outside the game and
 * there is no way to draw one, so something has to bring it in - and the thing that brings it
 * in must not be a name a client made up. There are four dungeons and they are a fact about
 * Magic rather than about this mod, so they are an enum and the wire carries its position.
 *
 * <p>Clamped on decode, so an index nobody printed is the first dungeon rather than a crash.
 */
public record BringInDungeonPayload(BlockPos table, int which) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BringInDungeonPayload> TYPE =
            GatheringPayloads.type("bring_in_dungeon");

    public static final StreamCodec<RegistryFriendlyByteBuf, BringInDungeonPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BringInDungeonPayload::table,
                    ByteBufCodecs.VAR_INT, BringInDungeonPayload::which,
                    BringInDungeonPayload::new);

    public BringInDungeonPayload {
        which = Math.max(0, Math.min(Dungeon.values().length - 1, which));
    }

    /** Which dungeon this is, resolved through the enum rather than trusted as a number. */
    public Dungeon dungeon() {
        return Dungeon.at(which);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
