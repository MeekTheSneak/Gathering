package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: put this many of a basic land into the deck I am holding.
 *
 * <p>A land type and a number, never a printing. Every other card that reaches a deck came
 * from somewhere - imported, drafted, taken out of a pack - and this is the one that is
 * conjured, so it is the one place a client could name any card in Magic and be handed it.
 * Naming a type instead leaves nothing to check: the server looks the printing up itself,
 * and the whole of what a client may ask for is one of five words and a count.
 */
public record AddBasicsPayload(boolean offHand, Basic land, int howMany)
        implements CustomPacketPayload {

    /** The five, which is the whole list and has been since 1993. */
    public enum Basic {
        PLAINS("Plains", "{W}"),
        ISLAND("Island", "{U}"),
        SWAMP("Swamp", "{B}"),
        MOUNTAIN("Mountain", "{R}"),
        FOREST("Forest", "{G}");

        private final String cardName;
        private final String symbol;

        Basic(String cardName, String symbol) {
            this.cardName = cardName;
            this.symbol = symbol;
        }

        /** What to ask Scryfall for, which is also what it says on the card. */
        public String cardName() {
            return cardName;
        }

        /**
         * The mana symbol its button is marked with.
         *
         * <p>Rather than the card's name. Five names do not fit across a panel that is a
         * fraction of the window - they came out as "ain", "lar", "mp", "uni", "est", which
         * is five buttons nobody can tell apart. A mana symbol is one glyph, it is what every
         * player of this game already reads colours as, and the name is on the tooltip for
         * anybody who wants it spelled out.
         */
        public String symbol() {
            return symbol;
        }

        /** The key the tooltip names it from. */
        public String translationKey() {
            return "screen.gathering.deck.basic." + name().toLowerCase(java.util.Locale.ROOT);
        }

        static final StreamCodec<io.netty.buffer.ByteBuf, Basic> STREAM_CODEC =
                ByteBufCodecs.idMapper(Basic::byId, Basic::ordinal);

        private static Basic byId(int id) {
            Basic[] lands = values();
            if (id < 0 || id >= lands.length) {
                throw new io.netty.handler.codec.DecoderException("Unknown basic land id " + id);
            }
            return lands[id];
        }
    }

    /**
     * The most that may be asked for at once.
     *
     * <p>A limited deck runs seventeen or so and a Commander deck maybe forty, so this is
     * well clear of anybody's mana base. It exists because the number crosses the wire: a
     * request for two billion Forests must be refused rather than allocated.
     */
    public static final int MOST_AT_ONCE = 64;

    public AddBasicsPayload {
        howMany = Math.max(1, Math.min(MOST_AT_ONCE, howMany));
    }

    public static final CustomPacketPayload.Type<AddBasicsPayload> TYPE =
            GatheringPayloads.type("add_basics");

    public static final StreamCodec<RegistryFriendlyByteBuf, AddBasicsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AddBasicsPayload::offHand,
                    Basic.STREAM_CODEC, AddBasicsPayload::land,
                    ByteBufCodecs.VAR_INT, AddBasicsPayload::howMany,
                    AddBasicsPayload::new);

    public static AddBasicsPayload of(InteractionHand hand, Basic land, int howMany) {
        return new AddBasicsPayload(hand == InteractionHand.OFF_HAND, land, howMany);
    }

    public InteractionHand hand() {
        return offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
