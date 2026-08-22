package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.core.text.ManaSymbols;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Card text with its mana and tap symbols drawn as symbols.
 *
 * <p>Braced codes become private-use characters styled with the mod's symbol font, which
 * means the result is an ordinary {@link Component}: the game's own text layout wraps it,
 * measures it and draws it, symbols included, with no separate layout pass to write and
 * nothing that could disagree with how the rest of the text is handled.
 *
 * <p>Client-only.
 */
public final class ManaText {

    /** {@code assets/gathering/font/mana.json}, generated alongside the glyph textures. */
    public static final ResourceLocation FONT =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "mana");

    private ManaText() {
    }

    public static Component of(String text) {
        List<ManaSymbols.Segment> segments = ManaSymbols.segments(text);
        if (segments.isEmpty()) {
            return Component.empty();
        }
        if (segments.size() == 1 && !segments.get(0).symbols()) {
            return Component.literal(segments.get(0).text());
        }

        MutableComponent built = Component.empty();
        for (ManaSymbols.Segment segment : segments) {
            MutableComponent piece = Component.literal(segment.text());
            if (segment.symbols()) {
                piece = piece.withStyle(style -> style.withFont(FONT));
            }
            built.append(piece);
        }
        return built;
    }
}
