package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: an answer for this player, now, wherever they are looking.
 * <p>Its own payload rather than the action bar, for the same reason the table's chat has one:
 * the action bar is drawn under whatever screen is open, and almost everything the server
 * refuses is refused in answer to a button on a screen. Sent as a line, the client decides
 * where it can be seen - on the screen while there is one, over the hotbar when there is not.
 * <p>Not chat. Chat is the log, kept and scrolled back through; this is the answer to the
 * click that has just happened, replaced by the next one and gone in a few seconds.
 *
 * @param line what to say
 */
public record NoticePayload(Component line) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NoticePayload> TYPE = GatheringPayloads.type("notice");

    /**
     * Trusted, because the server wrote it.
     * <p>The untrusted reading of a component caps how deeply one may nest, for text that came from
     * a player. Every line here is the mod's own translatable key, and what a player has typed only
     * ever reaches it as an <em>argument</em> - a tournament's name, say, which arrives as one line
     * of at most forty characters through {@code PlayerText.oneLine}. So the nesting is this mod's
     * own however the argument was written, which is what the cap is for. It is not true that none
     * of it is anybody's typing, and this used to say so.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_STREAM_CODEC, NoticePayload::line,
                    NoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
