package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    /**
     * How many cards one deck may hold, across every section.
     *
     * <p>A cube is the largest real thing anyone builds and sits near 540, so this is well
     * clear of legitimate use. It exists because a deck component travels over the wire on
     * every held-item sync: without a bound, a hostile or merely broken client could hand a
     * server an arbitrarily long list to allocate. The stream codec enforces it on decode.
     */
    public static final int MAX_CARDS = 1024;

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
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARDS)), DeckComponent::entries,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARDS)), DeckComponent::commanders,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARDS)), DeckComponent::sideboard,
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
    public List<UUID> distinctPrintings() {
        LinkedHashSet<UUID> printings = new LinkedHashSet<>();
        for (List<CardComponent> section : List.of(commanders, entries, sideboard)) {
            for (CardComponent card : section) {
                card.scryfallId().ifPresent(printings::add);
            }
        }
        return List.copyOf(printings);
    }

    /** Which pile a card sits in. Editing is always section-explicit, never a search. */
    public enum Section {
        COMMANDERS,
        MAINBOARD,
        SIDEBOARD;

        /**
         * Where a card goes when somebody moves it without saying where to.
         *
         * <p>The deck and the sideboard swap, which is what building a deck is. A commander
         * goes to the deck: a card in the command zone that is not wanted there is wanted in
         * the ninety-nine far more often than it is wanted out of the box altogether.
         *
         * <p>Never itself, or the gesture that means "move this" would sometimes mean
         * nothing at all - and a click that silently does nothing is the worst kind.
         */
        public Section across() {
            return switch (this) {
                case MAINBOARD -> SIDEBOARD;
                case SIDEBOARD, COMMANDERS -> MAINBOARD;
            };
        }

        public static final StreamCodec<ByteBuf, Section> STREAM_CODEC =
                ByteBufCodecs.idMapper(Section::byId, Section::ordinal);

        private static Section byId(int id) {
            Section[] sections = values();
            if (id < 0 || id >= sections.length) {
                throw new DecoderException("Unknown deck section id " + id);
            }
            return sections[id];
        }
    }

    public List<CardComponent> section(Section section) {
        return switch (section) {
            case COMMANDERS -> commanders;
            case MAINBOARD -> entries;
            case SIDEBOARD -> sideboard;
        };
    }

    private DeckComponent withSection(Section section, List<CardComponent> cards) {
        return switch (section) {
            case COMMANDERS -> new DeckComponent(name, description, owner, entries, cards, sideboard);
            case MAINBOARD -> new DeckComponent(name, description, owner, cards, commanders, sideboard);
            case SIDEBOARD -> new DeckComponent(name, description, owner, entries, commanders, cards);
        };
    }

    /**
     * The deck with one copy of {@code card} taken out of {@code section}.
     *
     * <p>Empty when that section holds no such card. That is not an error worth shouting
     * about: it is what a stale click looks like when the client asks to remove a card the
     * server has already removed, and the right answer is to do nothing.
     */
    public Optional<DeckComponent> withoutOne(Section section, CardComponent card) {
        List<CardComponent> cards = new ArrayList<>(section(section));
        return cards.remove(card) ? Optional.of(withSection(section, cards)) : Optional.empty();
    }

    /** The deck with {@code card} added to {@code section}, or empty if the deck is full. */
    public Optional<DeckComponent> withAdded(Section section, CardComponent card) {
        if (totalCards() >= MAX_CARDS) {
            return Optional.empty();
        }
        return Optional.of(insert(section, card));
    }

    /**
     * The deck with one copy of {@code card} moved between sections.
     *
     * <p>The size limit does not apply: a move cannot make a deck larger.
     */
    public Optional<DeckComponent> moved(Section from, Section to, CardComponent card) {
        if (from == to) {
            return Optional.empty();
        }
        return withoutOne(from, card).map(deck -> deck.insert(to, card));
    }

    private DeckComponent insert(Section section, CardComponent card) {
        List<CardComponent> cards = new ArrayList<>(section(section));
        cards.add(card);
        return withSection(section, cards);
    }
}
