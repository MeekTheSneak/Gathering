package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.card.SetCode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a sealed box is: which set, which product, and what it said on the front.
 *
 * <p>A pointer, like a pack. What is in a display box is what the published data says is in
 * that product, looked up when somebody opens it, so a box carries an id rather than
 * thirty-six boosters - and a box in a chest is one slot rather than a slot per pack.
 *
 * <p>The name is carried because it is the only part a client can read on its own. Everything
 * else about the product lives in a catalogue the server holds and the client has never seen,
 * so a box with no name on it would be a nameless brown box on every shelf and in every hand.
 *
 * @param setCode   which set, lower case as Scryfall writes it
 * @param productId the published id for the product, as its own data names it
 * @param name      what it was called on the shelf
 */
public record SealedComponent(String setCode, String productId, String name) {

    /** As long as a product name may be. The longest real one is nowhere near this. */
    public static final int MOST_NAME_CHARACTERS = 96;

    /** As long as a published product id may be. They are uuids, so this is generous. */
    public static final int MOST_ID_CHARACTERS = 64;

    public static final Codec<SealedComponent> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("set").forGetter(SealedComponent::setCode),
                    Codec.STRING.fieldOf("product").forGetter(SealedComponent::productId),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(SealedComponent::name))
            .apply(instance, SealedComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SealedComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MOST_ID_CHARACTERS), SealedComponent::setCode,
                    ByteBufCodecs.stringUtf8(MOST_ID_CHARACTERS), SealedComponent::productId,
                    ByteBufCodecs.stringUtf8(MOST_NAME_CHARACTERS), SealedComponent::name,
                    SealedComponent::new);

    public SealedComponent {
        // Checked here rather than wherever it is next used. This arrives off a stack, and a
        // stack's data component is a thing an operator can write by hand - after which the
        // set code is on its way into a URL and a file name.
        setCode = SetCode.of(setCode).orElse("");
        productId = trimmed(productId, MOST_ID_CHARACTERS);
        name = trimmed(name, MOST_NAME_CHARACTERS);
    }

    /** Whether this is a box of anything at all. */
    public boolean isReal() {
        return !setCode.isEmpty() && !productId.isEmpty();
    }

    private static String trimmed(String text, int longest) {
        if (text == null) {
            return "";
        }
        String tidy = text.trim();
        return tidy.length() > longest ? tidy.substring(0, longest) : tidy;
    }
}
