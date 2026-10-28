package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a trophy is engraved with, and what color it was cast in.
 * <p>A tournament is an afternoon, and until now it left nothing behind: the standings were read and
 * the desk moved on. This is the part somebody keeps. It names the event, the day it was played and
 * whoever won it, so a shelf of them is a shop's history rather than a row of identical cups.
 * <p>The tint is the object's own and is decided once, when the trophy is made. Two trophies from
 * two tournaments are two colors; the same trophy is the same color for ever, in every hand it
 * passes through, because it travels on the item rather than being worked out from anything.
 * <p>Everything here is text somebody typed or a name the server knows, so it is cleaned and bounded
 * where it is made. Nothing about it is hidden from anybody: a trophy is meant to be shown.
 */
public record TrophyComponent(String event, String day, String winner, int tint) {

    /** As long as a tournament's own name may be, which is where these come from. */
    public static final int LONGEST = 48;

    /** A trophy nobody has engraved: the tint still stands, so it is a cup of some color. */
    public static final TrophyComponent BLANK = new TrophyComponent("", "", "", 0xC8A24A);

    public static final Codec<TrophyComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("event", "").forGetter(TrophyComponent::event),
            Codec.STRING.optionalFieldOf("day", "").forGetter(TrophyComponent::day),
            Codec.STRING.optionalFieldOf("winner", "").forGetter(TrophyComponent::winner),
            Codec.INT.optionalFieldOf("tint", BLANK.tint()).forGetter(TrophyComponent::tint))
            .apply(instance, TrophyComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrophyComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, TrophyComponent::event,
                    ByteBufCodecs.STRING_UTF8, TrophyComponent::day,
                    ByteBufCodecs.STRING_UTF8, TrophyComponent::winner,
                    ByteBufCodecs.INT, TrophyComponent::tint,
                    TrophyComponent::new);

    public TrophyComponent {
        // Bounded here rather than wherever it is next used: this arrives off a stack, and a stack's
        // component is a thing an operator can write by hand.
        event = oneLine(event);
        day = oneLine(day);
        winner = oneLine(winner);
        tint = tint & 0xFFFFFF;
    }

    private static String oneLine(String said) {
        String cleaned = dev.gathering.core.game.PlayerText.oneLine(said, LONGEST);
        return cleaned == null ? "" : cleaned;
    }

    /** Whether this says what it was won for, rather than being a cup somebody was given. */
    public boolean isEngraved() {
        return !event.isEmpty() || !winner.isEmpty();
    }
}
