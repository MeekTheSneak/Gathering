package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: turn my library over until you find one of these.
 * <p>A request rather than an instruction, and that is the whole reason it exists. Every other
 * reveal says how many cards to show, because the player can see how many they mean. Nobody
 * can see their own library, so nobody can say "four" for a cascade - the number is a fact
 * about cards this client is not entitled to know, and the server is the only thing that can
 * work it out.
 * <p>What comes back is an ordinary reveal of that many cards, through the same event and the
 * same visibility decision as any other. This is the question; the answer is not a new kind of
 * thing.
 * <p>Carries no seat: it is your own library, and which library that is comes from the player
 * the packet arrived from. A client asking to cascade through somebody else's deck is asking
 * to read it, and there is nothing here to ask with.
 */
public record RevealUntilPayload(BlockPos table, Until until, int manaValue, String wanted)
        implements CustomPacketPayload {

    /**
     * Which question is being asked of each card on the way down.
     * <p>Decoded strictly rather than wrapped round: an id this build has no name for is a
     * client from another version, and guessing which question it meant would turn somebody
     * else's library over on a whim.
     */
    public enum Until {
        CHEAPER_THAN,
        OF_TYPE;

        static final StreamCodec<io.netty.buffer.ByteBuf, Until> STREAM_CODEC =
                ByteBufCodecs.idMapper(Until::byId, Until::ordinal);

        private static Until byId(int id) {
            Until[] questions = values();
            if (id < 0 || id >= questions.length) {
                throw new io.netty.handler.codec.DecoderException(
                        "Unknown reveal-until question " + id);
            }
            return questions[id];
        }
    }

    /**
     * The longest type anybody types. A type line is a few words; this is a typing box, and a
     * box with no ceiling on it is a box somebody puts a novel in.
     */
    public static final int LONGEST_TYPE = 40;

    /** Past any real mana value, and short of a number that means anything odd. */
    public static final int MOST_MANA = 99;

    public RevealUntilPayload {
        manaValue = Math.max(0, Math.min(MOST_MANA, manaValue));
        wanted = wanted == null ? "" : wanted.strip();
        if (wanted.length() > LONGEST_TYPE) {
            wanted = wanted.substring(0, LONGEST_TYPE);
        }
    }

    public static final CustomPacketPayload.Type<RevealUntilPayload> TYPE =
            GatheringPayloads.type("reveal_until");

    public static final StreamCodec<RegistryFriendlyByteBuf, RevealUntilPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RevealUntilPayload::table,
                    Until.STREAM_CODEC, RevealUntilPayload::until,
                    ByteBufCodecs.VAR_INT, RevealUntilPayload::manaValue,
                    ByteBufCodecs.stringUtf8(LONGEST_TYPE), RevealUntilPayload::wanted,
                    RevealUntilPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
