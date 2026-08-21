package dev.gathering.network;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.ImageTier;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One readable face of a card, as it travels to an entitled client.
 *
 * <p>Two image URLs, not image bytes: {@code small} for the table miniatures and
 * {@code normal} for the zoom overlay. Every client fetches from those itself and caches on
 * its own disk, so no card art ever crosses this mod's network or ships in its jar.
 */
public record CardFaceSummary(
        String name,
        String manaCost,
        String typeLine,
        String oracleText,
        String smallImage,
        String normalImage) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CardFaceSummary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CardFaceSummary::name,
            ByteBufCodecs.STRING_UTF8, CardFaceSummary::manaCost,
            ByteBufCodecs.STRING_UTF8, CardFaceSummary::typeLine,
            ByteBufCodecs.STRING_UTF8, CardFaceSummary::oracleText,
            ByteBufCodecs.STRING_UTF8, CardFaceSummary::smallImage,
            ByteBufCodecs.STRING_UTF8, CardFaceSummary::normalImage,
            CardFaceSummary::new);

    public CardFaceSummary {
        name = orEmpty(name);
        manaCost = orEmpty(manaCost);
        typeLine = orEmpty(typeLine);
        oracleText = orEmpty(oracleText);
        smallImage = orEmpty(smallImage);
        normalImage = orEmpty(normalImage);
    }

    public static CardFaceSummary of(CardFace face) {
        return new CardFaceSummary(
                face.name(),
                face.manaCost(),
                face.typeLine(),
                face.oracleText(),
                imageAt(face, ImageTier.SMALL),
                imageAt(face, ImageTier.NORMAL));
    }

    /** The tier the overlay reads from, falling back to the table tier if that is all there is. */
    public Optional<String> readableImage() {
        if (!normalImage.isEmpty()) {
            return Optional.of(normalImage);
        }
        return smallImage.isEmpty() ? Optional.empty() : Optional.of(smallImage);
    }

    private static String imageAt(CardFace face, ImageTier tier) {
        return face.imageUris() == null ? "" : face.imageUris().bestFor(tier).orElse("");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
