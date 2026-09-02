package dev.gathering.core.format;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The formats that ship, as one table.
 * <p>Commander is first because it is the one the mod is built around, but nothing in the
 * validator knows that. A server wanting Brawl or Duel Commander adds a row.
 */
public final class FormatPresets {

    public static final FormatPreset COMMANDER = new FormatPreset(
            "commander", "Commander", "commander",
            100, 100, 1, 40, CommanderRules.COMMANDER, 0);

    public static final FormatPreset OATHBREAKER = new FormatPreset(
            "oathbreaker", "Oathbreaker", "oathbreaker",
            60, 60, 1, 20, CommanderRules.OATHBREAKER, 0);

    public static final FormatPreset STANDARD = sixtyCard("standard", "Standard");
    public static final FormatPreset PIONEER = sixtyCard("pioneer", "Pioneer");
    public static final FormatPreset MODERN = sixtyCard("modern", "Modern");
    public static final FormatPreset LEGACY = sixtyCard("legacy", "Legacy");
    public static final FormatPreset PAUPER = sixtyCard("pauper", "Pauper");

    /**
     * Vintage needs no special handling at all.
     * <p>Its restricted list arrives as a per-card legality of {@code restricted}, which
     * carries its own copy ceiling of one, so the general copy-limit check handles it.
     */
    public static final FormatPreset VINTAGE = sixtyCard("vintage", "Vintage");

    /**
     * What a drafted or sealed pool is played as.
     * <p>Forty cards, no maximum, and no copy limit at all - which is not laxity but the
     * actual rule: in limited you may play every copy of a card you opened, and how many that
     * is was settled by the packs rather than by the format. What stops somebody playing
     * eight of a good uncommon is that they only drafted one, and that is a question about a
     * pool rather than about a format, so it is asked by {@link PoolCheck} instead.
     * <p>Its sideboard is unbounded for the same reason: in limited, everything you opened
     * and did not play is your sideboard, and how much that is was decided by the packs.
     * <p>Its legalities key is Vintage's. Limited has no ban list of its own - a card that
     * came out of a pack is legal in the pack it came out of - so the only thing worth
     * inheriting is the handful of cards that are not legal anywhere in paper at all.
     */
    public static final FormatPreset LIMITED = new FormatPreset(
            "limited", "Limited", "vintage", 40, -1, Integer.MAX_VALUE, 20, CommanderRules.NONE,
            Integer.MAX_VALUE);

    private static final Map<String, FormatPreset> BY_ID = index(
            COMMANDER, OATHBREAKER, STANDARD, PIONEER, MODERN, LEGACY, VINTAGE, PAUPER, LIMITED);

    private FormatPresets() {
    }

    public static List<FormatPreset> all() {
        return List.copyOf(BY_ID.values());
    }

    public static Optional<FormatPreset> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT).strip()));
    }

    /** The default when a table starts a formatted game without saying which. */
    public static FormatPreset defaultPreset() {
        return COMMANDER;
    }

    private static FormatPreset sixtyCard(String id, String displayName) {
        return new FormatPreset(id, displayName, id, 60, -1, 4, 20, CommanderRules.NONE, 15);
    }

    /**
     * The presets by id, in the order they are written above.
     * <p>Not {@code Map.copyOf}, whose iteration order is unspecified and in practice comes
     * out of a hash salted once per launch - so the format buttons came up in a different
     * order every time the game started, and nobody could ever learn where Commander was.
     * The order here is a decision: the ones most tables play, first.
     */
    private static Map<String, FormatPreset> index(FormatPreset... presets) {
        Map<String, FormatPreset> byId = new LinkedHashMap<>();
        for (FormatPreset preset : presets) {
            byId.put(preset.id(), preset);
        }
        return java.util.Collections.unmodifiableMap(byId);
    }
}
