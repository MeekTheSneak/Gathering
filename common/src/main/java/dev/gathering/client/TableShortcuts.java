package dev.gathering.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gathering.core.ui.TableActions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Which key does which verb, and the player's answer to that question rather than mine.
 * <p>The keys used to be written into the board twice: once as a {@code switch} on a GLFW
 * constant and once as the literal {@code "E"} the menu printed beside "Tap". Both were mine
 * to choose and neither could be changed. That is fine until somebody's keyboard puts Q and E
 * somewhere else, or they hold the mouse in the other hand, or they already have E bound to
 * something they care about more - and then the whole table is unusable and there is nothing
 * on any screen that would let them fix it.
 * <p>So each verb that has a key has a real {@link KeyMapping}, registered by both loaders and
 * therefore sitting in Minecraft's own Controls screen with everything else. What the menu
 * prints beside a verb is what that mapping is bound to <em>now</em>, asked every time it is
 * drawn. Rebinding tap to Z makes the menu say Z, and the key list say Z, and a tutorial step
 * say Z, because all three ask here.
 * <p>The mapping's name is the menu's own translation key, which is not a trick: it means the
 * Controls screen and the card menu call the verb the same thing in every language, and that
 * there is one string to translate rather than two that can disagree.
 * <p>Client-only.
 */
public final class TableShortcuts {

    /**
     * The keys as they shipped, matched to Tabletop Simulator's where it has an equivalent.
     * <p>Defaults, now, rather than the law. A key that does something else in TTS is one
     * people press by accident all evening, which is why these are what they are - but the
     * player who disagrees can now say so.
     */
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();

    static {
        // The number row, in the order the Magic table binds it.
        DEFAULTS.put("pass_turn", GLFW.GLFW_KEY_0);
        DEFAULTS.put("untap_all", GLFW.GLFW_KEY_1);
        DEFAULTS.put("draw", GLFW.GLFW_KEY_2);
        DEFAULTS.put("scry", GLFW.GLFW_KEY_3);
        DEFAULTS.put("mill", GLFW.GLFW_KEY_4);
        DEFAULTS.put("reveal", GLFW.GLFW_KEY_5);
        DEFAULTS.put("surveil", GLFW.GLFW_KEY_6);
        DEFAULTS.put("to_exile", GLFW.GLFW_KEY_7);
        DEFAULTS.put("to_graveyard", GLFW.GLFW_KEY_8);
        DEFAULTS.put("to_library_bottom_random", GLFW.GLFW_KEY_9);
        // The letters, which are TTS's.
        DEFAULTS.put("untap", GLFW.GLFW_KEY_Q);
        DEFAULTS.put("tap", GLFW.GLFW_KEY_E);
        DEFAULTS.put("shuffle", GLFW.GLFW_KEY_R);
        DEFAULTS.put("turn_over", GLFW.GLFW_KEY_F);
        DEFAULTS.put("show_log", GLFW.GLFW_KEY_L);
        DEFAULTS.put("show_everything", GLFW.GLFW_KEY_HOME);
        // The way in for everything that has no key. Slash, because it is what every search
        // box in every game is opened with, and because it is not a letter - so it cannot be
        // the first character of a verb somebody is about to type into the box it opens.
        DEFAULTS.put("palette", GLFW.GLFW_KEY_SLASH);
        // Talking to the table is deliberately not here: it opens on the player's own chat
        // key, whatever they have bound it to, because talking to the table and talking to
        // the server are the same act and a second key for it would be a second answer to a
        // question Minecraft has already asked them.
    }

    /**
     * How to ask this loader which key a mapping currently sits on.
     * <p>The one thing vanilla will not answer: {@code KeyMapping#getKey} is a NeoForge
     * addition and Fabric has a helper of its own, so the loader supplies the lookup exactly
     * as it does for the read key - see {@link ZoomKeyState}. Until one does, the default key
     * is the honest answer: it is what the mapping was built with.
     */
    private static volatile java.util.function.Function<KeyMapping, InputConstants.Key> boundKey =
            KeyMapping::getDefaultKey;

    /** Bound at client init to whichever lookup this loader offers. */
    public static void bindKeyLookup(
            java.util.function.Function<KeyMapping, InputConstants.Key> lookup) {
        if (lookup != null) {
            boundKey = lookup;
        }
    }

    /**
     * Which key that verb is on right now, or null for one with no key.
     * <p>For anything that has to press it rather than print it - which is the scripted run,
     * and is the only way that run can check that the prompts and the dispatch agree.
     */
    public static InputConstants.Key keyFor(String actionId) {
        KeyMapping mapping = MAPPINGS.get(actionId);
        if (mapping == null || mapping.isUnbound()) {
            return null;
        }
        InputConstants.Key key = boundKey.apply(mapping);
        return key == null || key.getValue() == GLFW.GLFW_KEY_UNKNOWN ? null : key;
    }

    private static final Map<String, KeyMapping> MAPPINGS = build();

    private static Map<String, KeyMapping> build() {
        Map<String, KeyMapping> made = new LinkedHashMap<>();
        DEFAULTS.forEach((id, key) -> {
            if (!TableActions.has(id)) {
                throw new IllegalStateException("no such action to bind a key to: " + id);
            }
            made.put(id, new KeyMapping(
                    // The menu's own key. One string, two screens, and they cannot disagree.
                    TableActions.byId(id).orElseThrow().labelKey(),
                    InputConstants.Type.KEYSYM,
                    key,
                    "key.categories." + dev.gathering.Gathering.MOD_ID));
        });
        return Map.copyOf(made);
    }

    private TableShortcuts() {
    }

    /**
     * Every mapping, for a loader to register.
     * <p>Both loaders call this and neither has a list of its own, which is the point: a verb
     * added here appears in the Controls screen on NeoForge and on Fabric without anybody
     * remembering to add it twice.
     */
    public static List<KeyMapping> all() {
        return List.copyOf(MAPPINGS.values());
    }

    /** Whether the catalogue offers a key for this verb at all. */
    public static boolean bindable(String actionId) {
        return MAPPINGS.containsKey(actionId);
    }

    /**
     * Which verb this press means, or null for a press that is not one of ours.
     * <p>Asked from inside a screen, so it goes through {@code matches} rather than
     * {@code isDown}: a key mapping reads as permanently up inside any screen, which is the
     * trap {@link ZoomKeyState} exists for on the other side.
     * <p>An unbound mapping matches nothing. Vanilla's {@code matches} already refuses
     * {@code GLFW_KEY_UNKNOWN}, and this is the one place it would matter.
     */
    public static String actionFor(int key, int scanCode) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            return null;
        }
        for (Map.Entry<String, KeyMapping> entry : MAPPINGS.entrySet()) {
            KeyMapping mapping = entry.getValue();
            if (!mapping.isUnbound() && mapping.matches(key, scanCode)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Whether this press is the key currently bound to that verb. */
    public static boolean matches(String actionId, int key, int scanCode) {
        KeyMapping mapping = MAPPINGS.get(actionId);
        return mapping != null && !mapping.isUnbound()
                && key != GLFW.GLFW_KEY_UNKNOWN && mapping.matches(key, scanCode);
    }

    /**
     * What to print beside this verb: the key it is on right now.
     * <p>Null rather than a blank for a verb with no key and for one the player has unbound,
     * because a menu row with an empty column where a key should be reads as a missing string.
     * Callers that have somewhere to say "not bound" use {@link #labelOrUnbound}.
     */
    public static Component label(String actionId) {
        KeyMapping mapping = MAPPINGS.get(actionId);
        return mapping == null || mapping.isUnbound()
                ? null
                : mapping.getTranslatedKeyMessage();
    }

    /**
     * The same, saying so out loud when there is no key.
     * <p>For the key list and for a tutorial prompt, both of which are telling somebody how to
     * do a thing: "not bound" is the answer they need, and an omission is not.
     */
    public static Component labelOrUnbound(String actionId) {
        Component bound = label(actionId);
        return bound == null
                ? Component.translatable("screen.gathering.table.key_unbound")
                : bound;
    }

    /**
     * Puts a verb's current key into a sentence about it.
     * <p>The whole reason the key list has a {@code %s} in half its lines: "2 - draw a card"
     * is a sentence somebody can act on, and it has to keep being true after they rebind it.
     */
    public static Component inASentence(String sentenceKey, String actionId) {
        return Component.translatable(sentenceKey, labelOrUnbound(actionId));
    }

    /** Every verb that has a key, in the order they were declared. For the key list. */
    public static List<String> bound() {
        return new ArrayList<>(MAPPINGS.keySet());
    }
}
