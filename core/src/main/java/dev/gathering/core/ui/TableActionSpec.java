package dev.gathering.core.ui;

import java.util.List;
import java.util.Locale;

/**
 * One thing a player can ask the table to do, described once.
 * <p>The description, not the doing. What a card menu prints, what the palette searches, what
 * the key list shows and what a tutorial step points at were four separate pieces of writing
 * about the same verb, and four pieces of writing about one thing drift: the menu learned to
 * take its key from the number row after a menu row said "To graveyard 7" while 7 exiled. This
 * is that fix generalized - the name, the category, the words somebody might search for and
 * whether it works on a selection all live here, and everything that shows a verb to a player
 * reads them.
 * <p>Deliberately not the handler. Which method runs is a Minecraft question and belongs where
 * the Minecraft is; this is a pure description that a test can hold in its hand.
 * <p>The id doubles as the language key's tail, so an action reuses the menu string that was
 * already written for it rather than needing a second one that says the same thing.
 *
 * @param id             stable, lower case with underscores, and the same word the menu entry
 *                       already uses - {@code draw}, {@code to_graveyard}, {@code untap_all}
 * @param category       which drawer of the palette it belongs in
 * @param target         what it needs pointed at before it means anything
 * @param worksOnMany    whether pointing it at a selection does it to all of them. False does
 *                       not mean the action is unavailable while a selection is up; it means
 *                       the selection is not what it acts on
 * @param aliases        extra words to find it by, lower case. English mnemonics for a player
 *                       who knows the game by another name - "straighten" for untap, "bin" for
 *                       the graveyard. The translated label is always searched as well, and is
 *                       the part a translator controls; these are a supplement, not the
 *                       primary text
 */
public record TableActionSpec(
        String id,
        Category category,
        Target target,
        boolean worksOnMany,
        List<String> aliases) {

    /** Which drawer of the palette a verb belongs in. */
    public enum Category {

        /** Done to one card that is already on the table. */
        CARD,

        /** Done to everything picked out at once. */
        SELECTION,

        /** About the cards you are holding. */
        HAND,

        /** About your library, and the piles beside it. */
        LIBRARY,

        /** About the game rather than about any card: the turn, the log, leaving. */
        TABLE,

        /** Changes what you can see and nothing that anybody else can. */
        VIEW;

        private final String key = "category.gathering." + name().toLowerCase(Locale.ROOT);

        /** The key its name is written under. Built once; the palette asks every frame. */
        public String key() {
            return key;
        }
    }

    /** What has to be pointed at before a verb means anything. */
    public enum Target {

        /** Nothing. Pressing it is the whole gesture. */
        NONE,

        /** One card on the table, under the cursor or the only one selected. */
        CARD,

        /** Whatever is picked out, which may be one card. */
        SELECTION,

        /** One of the piles down the side: the library, the graveyard, exile. */
        PILE,

        /** A seat, which is usually your own. */
        SEAT
    }

    public TableActionSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an action needs an id");
        }
        category = category == null ? Category.TABLE : category;
        target = target == null ? Target.NONE : target;
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    /**
     * The key its name is written under.
     * <p>The menu's own key, on purpose. Every one of these verbs already has a line in the
     * language file because it is already on a menu, and writing a second line saying the same
     * thing is how a palette comes to call something by a name the menu does not use.
     */
    public String labelKey() {
        return "menu.gathering.table." + id;
    }

    /**
     * Where a translator may add words to find this by in their own language.
     * <p>Optional and usually absent: an entry under this prefix is a comma-separated list,
     * and a language that has none simply has no line. English aliases live in {@link #aliases}
     * because they are part of the catalogue rather than part of the translation, but a
     * language where the obvious search word is not the label needs somewhere to say so.
     */
    public String aliasKey() {
        return "alias.gathering.table." + id;
    }

    /** Whether this verb needs something pointed at before it can be pressed. */
    public boolean needsATarget() {
        return target != Target.NONE;
    }
}
