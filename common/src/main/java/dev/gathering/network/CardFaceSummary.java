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
        String strength,
        String smallImage,
        String normalImage) {

    /**
     * Written out by hand rather than composed.
     *
     * <p>{@link StreamCodec#composite} stops at six components in this version and this record
     * has seven, so the choice was a nested sub-record purely to satisfy an arity limit, or
     * this. Seven strings in a fixed order is not the kind of code that hides a bug, and the
     * order below is the record's own, top to bottom, which is the only thing to keep right.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CardFaceSummary> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, face) -> {
                        buffer.writeUtf(face.name());
                        buffer.writeUtf(face.manaCost());
                        buffer.writeUtf(face.typeLine());
                        buffer.writeUtf(face.oracleText());
                        buffer.writeUtf(face.strength());
                        buffer.writeUtf(face.smallImage());
                        buffer.writeUtf(face.normalImage());
                    },
                    buffer -> new CardFaceSummary(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf()));

    public CardFaceSummary {
        name = orEmpty(name);
        manaCost = orEmpty(manaCost);
        typeLine = orEmpty(typeLine);
        oracleText = orEmpty(oracleText);
        strength = orEmpty(strength);
        smallImage = orEmpty(smallImage);
        normalImage = orEmpty(normalImage);
    }

    public static CardFaceSummary of(CardFace face) {
        return new CardFaceSummary(
                face.name(),
                face.manaCost(),
                face.typeLine(),
                face.oracleText(),
                strengthOf(face),
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

    /**
     * The printed power and toughness as one string, or empty for a card that has neither.
     *
     * <p>Both or neither: a face with a power and no toughness is not something Scryfall
     * publishes, and "3/" on a card would be worse than nothing at all. One field rather than
     * two because every reader wants the pair - it is drawn in the corner of a card the way it
     * is printed there - and because the power and toughness a player writes over the top is
     * already one string, so the two now agree about what they are.
     */
    private static String strengthOf(CardFace face) {
        String power = orEmpty(face.power());
        String toughness = orEmpty(face.toughness());
        return power.isEmpty() || toughness.isEmpty() ? "" : power + "/" + toughness;
    }

    private static String imageAt(CardFace face, ImageTier tier) {
        return face.imageUris() == null ? "" : face.imageUris().bestFor(tier).orElse("");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
