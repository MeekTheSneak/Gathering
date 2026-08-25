package dev.gathering.network;

import dev.gathering.core.card.Rarity;
import dev.gathering.core.collection.CollectionSearch;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a player is looking for in a collection, on the wire.
 *
 * <p>Searching happens on the server. The client has no card details for a collection it does
 * not own - and sending it ten thousand card names so it could search them itself would be a
 * megabyte for a screen showing forty rows. So the question crosses instead of the answer,
 * and what comes back is one page.
 *
 * <p>Every field is checked here rather than trusted. It is typed by a player, and the length
 * caps exist so a client cannot make the server search a megabyte-long word.
 */
public record CollectionQuery(
        String text, String setCode, String colours, String type, Rarity rarity,
        CollectionSearch.Sort sort) {

    /** Longer than any card name, and short enough that nothing is worth doing with it. */
    public static final int MOST_CHARACTERS = 96;

    public static final CollectionQuery EVERYTHING =
            new CollectionQuery("", "", "", "", null, CollectionSearch.Sort.NAME);

    public CollectionQuery {
        text = clipped(text);
        setCode = clipped(setCode);
        colours = clipped(colours).toUpperCase(Locale.ROOT);
        type = clipped(type);
        sort = sort == null ? CollectionSearch.Sort.NAME : sort;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionQuery> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MOST_CHARACTERS), CollectionQuery::text,
                    ByteBufCodecs.stringUtf8(MOST_CHARACTERS), CollectionQuery::setCode,
                    ByteBufCodecs.stringUtf8(MOST_CHARACTERS), CollectionQuery::colours,
                    ByteBufCodecs.stringUtf8(MOST_CHARACTERS), CollectionQuery::type,
                    ByteBufCodecs.idMapper(
                            id -> id <= 0 || id > Rarity.values().length
                                    ? null
                                    : Rarity.values()[id - 1],
                            rarity -> rarity == null ? 0 : rarity.ordinal() + 1),
                    CollectionQuery::rarity,
                    ByteBufCodecs.idMapper(
                            id -> id >= 0 && id < CollectionSearch.Sort.values().length
                                    ? CollectionSearch.Sort.values()[id]
                                    : CollectionSearch.Sort.NAME,
                            CollectionSearch.Sort::ordinal), CollectionQuery::sort,
                    CollectionQuery::new);

    /** The search this asks for. */
    public CollectionSearch.Query asSearch(boolean descending) {
        Set<String> wanted = new LinkedHashSet<>();
        for (char colour : colours.toCharArray()) {
            if (colour != ' ') {
                wanted.add(String.valueOf(colour));
            }
        }
        return new CollectionSearch.Query(text, setCode, wanted, rarity, type, sort, descending);
    }

    public CollectionQuery searchingFor(String newText) {
        return new CollectionQuery(newText, setCode, colours, type, rarity, sort);
    }

    public CollectionQuery orderedBy(CollectionSearch.Sort newSort) {
        return new CollectionQuery(text, setCode, colours, type, rarity, newSort);
    }

    public CollectionQuery inColours(String newColours) {
        return new CollectionQuery(text, setCode, newColours, type, rarity, sort);
    }

    public CollectionQuery ofRarity(Rarity newRarity) {
        return new CollectionQuery(text, setCode, colours, type, newRarity, sort);
    }

    private static String clipped(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MOST_CHARACTERS
                ? trimmed.substring(0, MOST_CHARACTERS)
                : trimmed;
    }
}
