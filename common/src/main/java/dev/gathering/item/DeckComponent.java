package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A sleeved deck: its name, who imported it, and its cards in order.
 *
 * <p>Import produces one of these bound to the importing player. Sections are kept as
 * separate lists rather than flags on each entry because the pre-game validator asks about
 * them by section and never about individual cards.
 *
 * @param entries mainboard, in decklist order
 * @param commanders the command zone, empty outside commander formats
 * @param sideboard the sideboard, empty in singleton formats
 */
public record DeckComponent(
        String name,
        Optional<UUID> owner,
        List<CardComponent> entries,
        List<CardComponent> commanders,
        List<CardComponent> sideboard) {

    public static final Codec<DeckComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(DeckComponent::name),
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(DeckComponent::owner),
            CardComponent.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(DeckComponent::entries),
            CardComponent.CODEC.listOf().optionalFieldOf("commanders", List.of()).forGetter(DeckComponent::commanders),
            CardComponent.CODEC.listOf().optionalFieldOf("sideboard", List.of()).forGetter(DeckComponent::sideboard))
            .apply(instance, DeckComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeckComponent::name,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), DeckComponent::owner,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckComponent::entries,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckComponent::commanders,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckComponent::sideboard,
            DeckComponent::new);

    public DeckComponent {
        entries = List.copyOf(entries);
        commanders = List.copyOf(commanders);
        sideboard = List.copyOf(sideboard);
    }

    /** Physical cards in the deck proper - mainboard plus command zone, never the sideboard. */
    public int deckSize() {
        return entries.size() + commanders.size();
    }

    public int totalCards() {
        return deckSize() + sideboard.size();
    }

    public boolean isEmpty() {
        return totalCards() == 0;
    }
}
