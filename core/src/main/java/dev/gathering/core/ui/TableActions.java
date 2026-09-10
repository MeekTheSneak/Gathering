package dev.gathering.core.ui;

import dev.gathering.core.ui.TableActionSpec.Category;
import dev.gathering.core.ui.TableActionSpec.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every verb the table offers, in one list.
 * <p>The list a menu builds, the list a palette searches and the list a key can be bound to
 * were three lists. This is one, and the ids in it are the words the menus already use, so
 * nothing had to be renamed and no second set of labels exists to fall out of step with the
 * first.
 * <p>Open on purpose. A verb that is not in here still works from its menu exactly as it did -
 * this is what is <em>searchable and bindable</em>, not what is possible. Adding one is a line
 * here plus a line in the client's dispatch table, and the check that those two agree is a
 * test rather than a hope.
 * <p>Pure. Nothing here knows what a card is, only what a verb is called and what it wants
 * pointed at.
 */
public final class TableActions {

    private TableActions() {
    }

    private static TableActionSpec of(
            String id, Category category, Target target, boolean many, String... aliases) {
        return new TableActionSpec(id, category, target, many, List.of(aliases));
    }

    /**
     * The catalogue, in the order it reads best.
     * <p>Order matters twice: it is the order the palette lists things in when nothing has
     * been typed, and it is a rough statement of how often a verb is wanted. The six the
     * tutorial teaches come first within their drawers.
     */
    private static final List<TableActionSpec> ALL = List.of(
            // The turn, and the two things everybody does at the start of one.
            of("draw", Category.LIBRARY, Target.NONE, false, "pick up"),
            of("pass_turn", Category.TABLE, Target.NONE, false, "end turn", "next"),
            of("untap_all", Category.TABLE, Target.NONE, false, "straighten all", "unturn"),
            of("shuffle", Category.LIBRARY, Target.NONE, false, "randomize", "randomise"),
            of("mulligan", Category.HAND, Target.NONE, false, "new hand", "redraw"),

            // One card on the felt.
            of("tap", Category.CARD, Target.SELECTION, true, "turn sideways", "use"),
            of("untap", Category.CARD, Target.SELECTION, true, "straighten", "unturn"),
            of("play", Category.HAND, Target.CARD, false, "cast", "put down"),
            of("play_face_down", Category.HAND, Target.CARD, false, "morph", "face down"),
            of("counters", Category.CARD, Target.SELECTION, true, "markers", "loyalty"),
            of("add_counter", Category.CARD, Target.SELECTION, true, "plus one", "+1/+1"),
            of("remove_counter", Category.CARD, Target.SELECTION, true, "minus one", "-1/-1"),
            of("loyalty_up", Category.CARD, Target.CARD, false, "planeswalker plus"),
            of("loyalty_down", Category.CARD, Target.CARD, false, "planeswalker minus"),
            of("strength", Category.CARD, Target.CARD, false, "power toughness", "p/t"),
            of("turn_over", Category.CARD, Target.SELECTION, true, "flip", "transform"),
            of("turn_face_down", Category.CARD, Target.SELECTION, true, "hide", "face down"),
            of("turn_face_up", Category.CARD, Target.SELECTION, true, "show", "face up"),
            of("turn_left", Category.CARD, Target.SELECTION, true, "rotate left"),
            of("turn_right", Category.CARD, Target.SELECTION, true, "rotate right"),
            of("straighten", Category.CARD, Target.SELECTION, true, "unrotate", "square up"),
            of("freeze", Category.CARD, Target.SELECTION, true, "does not untap"),
            of("thaw", Category.CARD, Target.SELECTION, true, "untaps again"),
            of("attach", Category.CARD, Target.CARD, false, "equip", "aura", "onto"),
            of("detach", Category.CARD, Target.CARD, false, "unequip", "take off"),
            of("write", Category.CARD, Target.CARD, false, "note", "annotate"),
            of("ping", Category.CARD, Target.CARD, false, "point", "look at this"),
            of("copy", Category.CARD, Target.CARD, false, "token copy", "duplicate"),

            // Where a card goes.
            of("to_graveyard", Category.SELECTION, Target.SELECTION, true, "bin", "yard", "gy"),
            of("to_exile", Category.SELECTION, Target.SELECTION, true, "remove from game"),
            of("to_hand", Category.SELECTION, Target.SELECTION, true, "bounce", "pick up"),
            of("to_battlefield", Category.SELECTION, Target.SELECTION, true, "onto the table"),
            of("to_command", Category.SELECTION, Target.SELECTION, true, "command zone"),
            of("to_library_top", Category.SELECTION, Target.SELECTION, true, "on top of deck"),
            of("to_library_bottom", Category.SELECTION, Target.SELECTION, true, "under the deck"),
            of("to_library_bottom_random", Category.SELECTION, Target.SELECTION, true,
                    "under the deck in no order"),
            of("remove_token", Category.SELECTION, Target.SELECTION, true, "delete token"),

            // The hand.
            of("sort_hand", Category.HAND, Target.NONE, false, "tidy", "order by cost"),
            of("discard_at_random", Category.HAND, Target.NONE, false, "random discard"),
            of("show_hand", Category.HAND, Target.NONE, false, "reveal hand"),
            of("hide_hand", Category.HAND, Target.NONE, false, "stop revealing hand"),

            // The library and the piles beside it.
            of("draw_many", Category.LIBRARY, Target.NONE, false, "draw several"),
            of("search", Category.LIBRARY, Target.NONE, false, "tutor", "look through deck"),
            of("scry", Category.LIBRARY, Target.NONE, false),
            of("surveil", Category.LIBRARY, Target.NONE, false),
            of("mill", Category.LIBRARY, Target.NONE, false, "grind"),
            of("reveal", Category.LIBRARY, Target.NONE, false, "reveal top"),
            of("reveal_until_type", Category.LIBRARY, Target.NONE, false, "cascade style reveal"),
            of("exile_top", Category.LIBRARY, Target.NONE, false, "impulse"),
            of("cascade", Category.LIBRARY, Target.NONE, false),
            of("fetch_basic", Category.LIBRARY, Target.NONE, false, "basic land", "fetchland"),
            of("open_pile", Category.LIBRARY, Target.PILE, false, "look through", "browse"),
            of("stop_revealing", Category.LIBRARY, Target.NONE, false),
            of("see_revealed", Category.LIBRARY, Target.NONE, false),

            // The game, and the table it is on.
            of("make_token", Category.TABLE, Target.NONE, false, "create token", "new token"),
            of("make_emblem", Category.TABLE, Target.NONE, false, "emblem"),
            of("note_card", Category.TABLE, Target.NONE, false, "write a card", "blank card"),
            of("gain_life", Category.TABLE, Target.SEAT, false, "life up"),
            of("lose_life", Category.TABLE, Target.SEAT, false, "life down"),
            of("my_counters", Category.TABLE, Target.SEAT, false, "commander damage", "poison"),
            of("roll_die", Category.TABLE, Target.NONE, false, "dice", "d20"),
            of("flip_coin", Category.TABLE, Target.NONE, false, "coin"),
            of("say", Category.TABLE, Target.NONE, false, "talk", "chat", "message"),
            of("bring_in_dungeon", Category.TABLE, Target.NONE, false, "venture"),
            of("undo", Category.TABLE, Target.NONE, false, "take back"),
            of("concede", Category.TABLE, Target.NONE, false, "give up", "scoop"),
            of("leave_table", Category.TABLE, Target.NONE, false, "stand up", "get up"),

            // Nothing anybody else can see. Picking a theme is not here: its menu row reads
            // "Look: Walnut" and names the theme it will cycle to, so it is a label with the
            // answer inside it rather than a verb, and a palette row saying "Look: %s" would
            // be worse than the menu row it came from.
            of("show_log", Category.VIEW, Target.NONE, false, "history", "what happened"),
            of("hide_log", Category.VIEW, Target.NONE, false),
            of("show_everything", Category.VIEW, Target.NONE, false, "zoom out", "whole table"));

    private static final Map<String, TableActionSpec> BY_ID = index();

    private static Map<String, TableActionSpec> index() {
        Map<String, TableActionSpec> found = new LinkedHashMap<>();
        for (TableActionSpec spec : ALL) {
            if (found.put(spec.id(), spec) != null) {
                throw new IllegalStateException("two actions share the id " + spec.id());
            }
        }
        return Map.copyOf(found);
    }

    /** Everything, in catalogue order. */
    public static List<TableActionSpec> all() {
        return ALL;
    }

    /** One by id, or empty for a verb this catalogue does not carry. */
    public static Optional<TableActionSpec> byId(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    /** Whether the catalogue carries this verb. */
    public static boolean has(String id) {
        return id != null && BY_ID.containsKey(id);
    }

    /** Everything in one drawer, in catalogue order. */
    public static List<TableActionSpec> inCategory(Category category) {
        List<TableActionSpec> found = new ArrayList<>();
        for (TableActionSpec spec : ALL) {
            if (spec.category() == category) {
                found.add(spec);
            }
        }
        return List.copyOf(found);
    }

    /**
     * How well a typed query matches one word, as a score - higher is better, zero is no match.
     * <p>Exact first, then a prefix, then a word inside it, then anywhere at all. The order is
     * the whole point: somebody typing "tap" wants Tap, not "Set power/toughness" because it
     * happens to contain the letters. Case and surrounding space are ignored.
     */
    public static int score(String query, String word) {
        if (query == null || word == null) {
            return 0;
        }
        String looking = query.strip().toLowerCase(Locale.ROOT);
        String against = word.strip().toLowerCase(Locale.ROOT);
        if (looking.isEmpty() || against.isEmpty()) {
            return 0;
        }
        if (against.equals(looking)) {
            return 1000;
        }
        if (against.startsWith(looking)) {
            return 800;
        }
        // A word inside it, which is what makes "graveyard" find "To graveyard" without also
        // making "a" find everything: the match has to start a word.
        int at = against.indexOf(looking);
        while (at > 0) {
            char before = against.charAt(at - 1);
            if (before == ' ' || before == '_' || before == '/' || before == '-') {
                return 600;
            }
            at = against.indexOf(looking, at + 1);
        }
        return against.contains(looking) ? 300 : 0;
    }

    /**
     * The best score any of these words gets against the query.
     * <p>What a palette row is worth: its label in the player's own language, then whatever
     * aliases the catalogue and the translation offer. Aliases are worth a little less than
     * the label so that a label match always sorts first.
     *
     * @param label   the action's name in the player's language, which is the primary text
     * @param aliases every other word it may be found by
     */
    public static int rank(String query, String label, List<String> aliases) {
        int best = score(query, label);
        if (aliases != null) {
            for (String alias : aliases) {
                best = Math.max(best, score(query, alias) - 50);
            }
        }
        return Math.max(0, best);
    }
}
