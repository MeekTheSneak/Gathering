package dev.gathering.network;

import dev.gathering.core.game.event.GameEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: roll a die where everyone can see it.
 * <p>Carries how many sides and nothing else. The number rolled is the server's to decide -
 * a client that sent its own result would be a client that always rolled twenty, and a die
 * only means anything because nobody at the table chose it. Same reasoning as a discard at
 * random, and the same place the randomness comes from: the level's own generator, never the
 * session's shuffle seed, which is sealed and must stay that way.
 * <p>Carries no seat either: it is your roll, and which seat that is comes from the player the
 * packet arrived from.
 */
public record RollDicePayload(BlockPos table, int sides) implements CustomPacketPayload {

    public RollDicePayload {
        // The same bound the event keeps, checked here too so a refused roll is a packet that
        // never became an event rather than one quietly rounded on arrival.
        sides = Math.max(1, Math.min(GameEvent.DiceRolled.MOST_SIDES, sides));
    }

    public static final CustomPacketPayload.Type<RollDicePayload> TYPE =
            GatheringPayloads.type("roll_dice");

    public static final StreamCodec<RegistryFriendlyByteBuf, RollDicePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RollDicePayload::table,
                    ByteBufCodecs.VAR_INT, RollDicePayload::sides,
                    RollDicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
