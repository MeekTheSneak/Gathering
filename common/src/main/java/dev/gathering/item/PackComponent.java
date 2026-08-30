package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.card.SetCode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a sealed pack is: which set, and which of that set's products.
 *
 * <p>Two strings and nothing else, for the same reason a card item is a pointer. What is
 * inside a pack is not decided when the pack is made - it is decided when the pack is opened,
 * out of the set's published collation, by a seed nobody has seen. A pack carrying its
 * contents would be a pack somebody could read before opening it, and there is only one
 * moment a booster is for.
 *
 * @param setCode which set, lower case as Scryfall writes it
 * @param kind    which of its products - "play", "collector", "draft" - as the published
 *                collation names them
 */
public record PackComponent(String setCode, String kind) {

    public static final Codec<PackComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("set").forGetter(PackComponent::setCode),
            Codec.STRING.optionalFieldOf("kind", "").forGetter(PackComponent::kind))
            .apply(instance, PackComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PackComponent::setCode,
                    ByteBufCodecs.STRING_UTF8, PackComponent::kind,
                    PackComponent::new);

    public PackComponent {
        // Checked here rather than wherever it is next used. This arrives off a stack, and a
        // stack's data component is a thing an operator can write by hand - after which the
        // set code is on its way into a URL and a file name.
        setCode = SetCode.of(setCode).orElse("");
        kind = kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The set code an Archive Pack carries.
     *
     * <p>A word rather than a real code, and it lives here rather than beside the code that
     * fills the pack because both the client and the server have to recognise one: the
     * client so it does not go looking for a set symbol that does not exist, the server so
     * it does not go looking for a set. Seven letters, which no real set code is.
     */
    public static final String ARCHIVE = "archive";

    /** Whether this is a pack of anything at all. */
    public boolean isReal() {
        return !setCode.isEmpty();
    }

    /** Whether this is the Archive Pack rather than one of a set's real products. */
    public boolean isArchive() {
        return ARCHIVE.equals(setCode);
    }

    /** What to call this pack when two of them have to be told apart. */
    public String id() {
        return setCode + (kind.isEmpty() ? "" : ":" + kind);
    }
}
