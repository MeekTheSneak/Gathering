package dev.gathering.client;

import java.util.Locale;

/**
 * Which set of GUI art the mod draws itself with.
 *
 * <p>Every element of every screen is a texture, and a theme is one folder of them. The
 * sprite for a panel under the slate theme is {@code gathering:slate/panel}; under the felt
 * theme it is {@code gathering:felt/panel}. Nothing in the code knows which - screens ask
 * {@link GatheringSprites} for an element and get back whichever folder is in force.
 *
 * <p>That is why this is an enum of folder names and nothing else. A theme carries no colors,
 * no sizes and no rules: if a theme could change anything that is not a PNG, then repainting
 * the PNGs would stop being enough to change how the mod looks, which is the whole point.
 *
 * <p>{@link #FELT} is the one that must be complete. A theme is allowed to leave an element
 * out and inherit it, so a pack can reskin six things without drawing the other forty; see
 * {@link GatheringSprites#of}.
 */
public enum GuiTheme {

    /** The dark green table the design brief describes. The default, and the fallback. */
    FELT("felt"),

    /** Cold and high contrast: grey stone, a colder accent, for readability over prettiness. */
    SLATE("slate"),

    /** Warm: wood, cream and brass, the look of a card table rather than a screen. */
    WALNUT("walnut");

    /** The one every other theme falls back to, and the only one that has to be complete. */
    public static final GuiTheme DEFAULT = FELT;

    private final String folder;

    GuiTheme(String folder) {
        this.folder = folder;
    }

    /** The folder under {@code textures/gui/sprites} this theme's art lives in. */
    public String folder() {
        return folder;
    }

    /** What a player sees this called. Translated, so a theme's name is editable like any text. */
    public String translationKey() {
        return "theme.gathering." + folder;
    }

    /** The next one round, for a button that cycles rather than opening a list of three. */
    public GuiTheme next() {
        GuiTheme[] all = ALL;
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * The theme with this folder name, or the default.
     *
     * <p>Never throws. This reads a name out of a config file a person typed into, and a
     * misspelled theme is a reason to draw the default one, not a reason to refuse to start.
     */
    public static GuiTheme named(String folder) {
        if (folder != null) {
            String wanted = folder.trim().toLowerCase(Locale.ROOT);
            for (GuiTheme theme : ALL) {
                if (theme.folder.equals(wanted)) {
                    return theme;
                }
            }
        }
        return DEFAULT;
    }

    /** Every theme, once. {@code values()} clones its array and this is read while drawing. */
    private static final GuiTheme[] ALL = values();

    /** Every theme, for anything that has to walk them. Do not modify. */
    public static GuiTheme[] all() {
        return ALL;
    }
}
