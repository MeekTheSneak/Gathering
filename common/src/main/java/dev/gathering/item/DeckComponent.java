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
 * <p>Name and description are the player's, not the importer's: a deck is a thing you title
 * and annotate, and both show on the item so a row of deckboxes on a shelf reads as a
 * collection rather than as five identical objects.
 *
 * @param entries mainboard, in decklist order
 * @param commanders the command zone, empty outside commander formats
 * @param sideboard the sideboard, empty in singleton formats
 */
public record DeckComponent(
        String name,
        String description,
        Optional<UUID> owner,
        List<CardComponent> entries,
        List<CardComponent> commanders,
        List<CardComponent> sideboard) {

    public static final Codec<DeckComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(DeckComponent::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(DeckComponent::description),
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(DeckComponent::owner),
            CardComponent.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(DeckComponent::entries),
            CardComponent.CODEC.listOf().optionalFieldOf("commanders", List.of()).forGetter(DeckComponent::commanders),
            CardComponent.CODEC.listOf().optionalFieldOf("sideboard", List.of()).forGetter(DeckComponent::sideboard))
            .apply(instance, DeckComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeckComponent::name,
            ByteBufCodecs.STRING_UTF8, DeckComponent::description,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), DeckComponent::owner,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckComponent::entries,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckComponent::commanders,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckComponent::sideboard,
            DeckComponent::new);

    public DeckComponent {
        description = description == null ? "" : description;
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

    public boolean hasDescription() {
        return !description.isBlank();
    }

    /** Every distinct printing in the deck, for asking the server what these cards are. */
    public java.util.List<UUID> distinctPrintings() {
        java.util.LinkedHashSet<UUID> printings = new java.util.LinkedHashSet<>();
        for (java.util.List<CardComponent> section : java.util.List.of(commanders, entries, sideboard)) {
            for (CardComponent card : section) {
                card.scryfallId().ifPresent(printings::add);
            }
        }
        return java.util.List.copyOf(printings);
    }
}
