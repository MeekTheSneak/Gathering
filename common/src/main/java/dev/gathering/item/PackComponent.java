package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.card.SetCode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a sealed pack is: which set, and which of that set's products.
 * <p>Two strings and nothing else, for the same reason a card item is a pointer. What is
 * inside a pack is not decided when the pack is made - it is decided when the pack is opened,
 * out of the set's published collation, by a seed nobody has seen. A pack carrying its
 * contents would be a pack somebody could read before opening it, and there is only one
 * moment a booster is for.
 *
 * @param setCode which set, lower case as Scryfall writes it
 * @param kind    which of its products - "play", "collector", "draft" - as the published
 *                collation names them
 * @param color  one letter of WUBRG, or blank for a pack that is not about a color. A
 *                product sold one color at a time - Jumpstart is the one this was written
 *                for - has as many arrangements per color as it has themes, and a pack that
 *                said only "jumpstart" would open as any of them. This narrows <em>which</em>
 *                arrangements the seed chooses between; it does not choose one. What is inside
 *                is still decided at the moment of opening, by a seed nobody has seen
 */
public record PackComponent(String setCode, String kind, String color) {

    public static final Codec<PackComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("set").forGetter(PackComponent::setCode),
            Codec.STRING.optionalFieldOf("kind", "").forGetter(PackComponent::kind),
            // Optional, so every pack written before colors existed reads back as one that
            // is not about a color - which is what it was.
            Codec.STRING.optionalFieldOf("color", "").forGetter(PackComponent::color))
            .apply(instance, PackComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PackComponent::setCode,
                    ByteBufCodecs.STRING_UTF8, PackComponent::kind,
                    ByteBufCodecs.STRING_UTF8, PackComponent::color,
                    PackComponent::new);

    /** A pack of a set's product, about no color in particular, which is almost all of them. */
    public PackComponent(String setCode, String kind) {
        this(setCode, kind, "");
    }

    public PackComponent {
        // Checked here rather than wherever it is next used. This arrives off a stack, and a
        // stack's data component is a thing an operator can write by hand - after which the
        // set code is on its way into a URL and a file name.
        setCode = SetCode.of(setCode).orElse("");
        kind = kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT);
        // One letter of WUBRG or nothing at all. Checked here for the same reason the set code
        // is: this arrives off a stack an operator can write by hand, and everything
        // downstream is entitled to assume it is one of six things.
        String wanted = color == null ? "" : color.trim().toUpperCase(java.util.Locale.ROOT);
        color = wanted.length() == 1
                && dev.gathering.core.booster.BoosterColors.WUBRG.indexOf(wanted.charAt(0)) >= 0
                ? wanted
                : "";
    }

    /** Whether this pack is sold as one color. */
    public boolean hasColor() {
        return !color.isEmpty();
    }

    /**
     * The set code an Archive Pack carries.
     * <p>A word rather than a real code, and it lives here rather than beside the code that
     * fills the pack because both the client and the server have to recognize one: the
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
        return setCode
                + (kind.isEmpty() ? "" : ":" + kind)
                + (color.isEmpty() ? "" : ":" + color);
    }
}
