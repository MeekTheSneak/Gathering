package dev.gathering.core.collection;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.SetRelease;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How much of a set somebody has.
 * <p>The question every collector actually asks of a binder, and the one thing a pile of ten
 * thousand cards cannot answer by being looked at. A collection screen can already say what is
 * in it; this says what is <em>missing</em> from it, which is what makes the next pack worth
 * opening.
 * <p><b>What counts as the set.</b> The numbered cards, and only those. A modern release has
 * a few hundred numbered cards and then several hundred more sharing the same set code -
 * borderless, extended art, showcase, promos, the buy-a-box card - numbered above the printed
 * size. Nobody counts those against you: a player who owns one of every card the set says it
 * has owns the set. So they are counted separately, as extras, because owning forty of them is
 * worth saying and is not worth being told you are forty short of anything.
 * <p><b>One of each card, either finish.</b> A foil and a non-foil of the same card are the
 * same slot filled. A foil set is a different thing to collect and would be a different number
 * beside this one; claiming somebody is missing a card they are holding, because the copy they
 * are holding is shiny, would be the screen arguing with them.
 * <p><b>Variants of one number fill one slot.</b> A card numbered {@code 12a} and one numbered
 * {@code 12b} are two printings of card twelve, so owning either fills twelve - see
 * {@link SetRelease#leadingNumber}.
 * <p>Pure: this is arithmetic over what a player owns and what Scryfall says a set is, so it
 * is tested rather than played.
 */
public record SetCompletion(String code, String name, int owned, int size, int extras) {

    public SetCompletion {
        code = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        name = name == null || name.isBlank() ? code.toUpperCase(Locale.ROOT) : name;
        owned = Math.max(0, owned);
        size = Math.max(0, size);
        extras = Math.max(0, extras);
    }

    /** How many of the numbered cards are still missing. */
    public int missing() {
        return Math.max(0, size - owned);
    }

    /** Whether the set is finished. A set nobody knows the size of is never claimed finished. */
    public boolean isComplete() {
        return size > 0 && owned >= size;
    }

    /** How far along, from zero to one, for a bar to be drawn from. */
    public float share() {
        return size <= 0 ? 0f : Math.min(1f, owned / (float) size);
    }

    /**
     * Most finished first, then whoever has more of it, then by name.
     * <p>"What am I closest to finishing" is the question somebody opens this to ask. The
     * second key matters more than it looks: a two-card set nobody prints boosters for would
     * otherwise sit above the release somebody has spent a month on.
     */
    private static final Comparator<SetCompletion> CLOSEST_FIRST =
            Comparator.comparingDouble(SetCompletion::share).reversed()
                    .thenComparing(Comparator.comparingInt(SetCompletion::owned).reversed())
                    .thenComparing(SetCompletion::name);

    /**
     * Works out how much of each set is here.
     * <p>A set the list of sets says nothing about is left out rather than shown with an
     * unknown size. Scryfall lists every set there has ever been, so that is a set whose data
     * has not arrived rather than one that exists - and a row reading "14 of ?" answers
     * nothing while taking up the space of a row that does.
     *
     * @param owned the distinct cards in the collection, one entry per printing
     * @param sets  what a set is, by its code in lower case
     */
    public static List<SetCompletion> of(
            Collection<CardMetadata> owned, Map<String, SetRelease> sets) {
        if (owned == null || sets == null) {
            return List.of();
        }
        Map<String, Set<Integer>> numbered = new LinkedHashMap<>();
        Map<String, Integer> extras = new LinkedHashMap<>();
        for (CardMetadata card : owned) {
            if (card == null) {
                continue;
            }
            String code = card.setCode() == null ? "" : card.setCode().trim().toLowerCase(Locale.ROOT);
            SetRelease set = sets.get(code);
            if (set == null) {
                continue;
            }
            if (set.numbers(card.collectorNumber())) {
                numbered.computeIfAbsent(code, any -> new LinkedHashSet<>())
                        .add(SetRelease.leadingNumber(card.collectorNumber()));
            } else {
                extras.merge(code, 1, Integer::sum);
                // Still worth a row: somebody whose only cards from a set are its showcases
                // has something from it, and a set that vanished off the list because none of
                // its cards were numbered would look like a set they own nothing of.
                numbered.computeIfAbsent(code, any -> new LinkedHashSet<>());
            }
        }
        List<SetCompletion> rows = new ArrayList<>(numbered.size());
        numbered.forEach((code, filled) -> {
            SetRelease set = sets.get(code);
            rows.add(new SetCompletion(
                    code, set.name(), filled.size(), set.sizeOfTheSet(),
                    extras.getOrDefault(code, 0)));
        });
        rows.sort(CLOSEST_FIRST);
        return List.copyOf(rows);
    }
}
