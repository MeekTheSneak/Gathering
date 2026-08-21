package dev.gathering.core.format;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The formats that ship, as one table.
 *
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
     *
     * <p>Its restricted list arrives as a per-card legality of {@code restricted}, which
     * carries its own copy ceiling of one, so the general copy-limit check handles it.
     */
    public static final FormatPreset VINTAGE = sixtyCard("vintage", "Vintage");

    private static final Map<String, FormatPreset> BY_ID = index(
            COMMANDER, OATHBREAKER, STANDARD, PIONEER, MODERN, LEGACY, VINTAGE, PAUPER);

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

    private static Map<String, FormatPreset> index(FormatPreset... presets) {
        Map<String, FormatPreset> byId = new LinkedHashMap<>();
        for (FormatPreset preset : presets) {
            byId.put(preset.id(), preset);
        }
        return Map.copyOf(byId);
    }
}
