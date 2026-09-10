package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.ui.ActionSearch;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TableActions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Type a few letters, press Enter, and the thing happens.
 * <p>Seventy verbs live on four menus, and finding one you have not used in a week means
 * remembering which menu it was on. This is the other way in: the name, or a word you would
 * have called it, and the row is there.
 * <p><b>It is a search over the menus themselves, not a second list beside them.</b> Opening
 * it builds exactly the rows a right-click would have offered - the card's menu for whatever
 * the cursor or the selection is on, the library's, and the felt's - and pressing a row runs
 * that row's own {@link Runnable}. So "a verb does the same thing here as it does from its
 * menu" is not a rule somebody has to keep; there is only one body and this calls it. A verb
 * the menus would not offer right now is not here either, which is the same guarantee read
 * the other way round.
 * <p>It also settles the search's safety on its own. A menu row built for one particular card
 * or one particular player carries that name in its label and is built without a catalogue id
 * - the tokens a card makes, the hands turned toward you - and this carries only rows that
 * have one. Nothing a player cannot already see can be found by typing part of it, because
 * nothing a player can see is in here either: what is searched is the verbs.
 * <p>Client-only.
 */
public final class ActionPalette {

    /** How wide the panel is, and what it will not go past either way. */
    private static final double SHARE_OF_WIDTH = 0.42;
    private static final int WIDEST = 240;
    private static final int NARROWEST = 150;

    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 11;
    private static final int GAP = 4;

    /**
     * How many rows are on screen at once.
     * <p>Enough that an unsearched palette shows a useful slice of the catalogue, few enough
     * that the panel does not become the board. Past this it scrolls with the highlight,
     * which is what every list does and needs no scrollbar to explain.
     */
    private static final int VISIBLE_ROWS = 9;

    /** How much can be typed. Longer than any verb's name, and short enough to draw. */
    private static final int LONGEST_QUERY = 32;

    private static final int TITLE = 0xFFF3EEE4;
    private static final int TYPED = 0xFFFFFFFF;
    private static final int HINT = 0xFF8A8378;
    private static final int ROW = 0xFFE8E4DC;
    private static final int ROW_PICKED = 0xFFFFFFFF;
    private static final int KEY = 0xFF8A8378;
    private static final int NOTHING = 0xFF9C9384;
    private static final int HIGHLIGHT = 0x40FFFFFF;

    /**
     * One thing the palette can do, and the menu row it came from.
     *
     * @param label    the verb's name, exactly as its menu prints it
     * @param shortcut the key it is on right now, or null
     * @param action   the menu row's own body. Not a copy of it
     */
    public record Choice(String id, Component label, Component shortcut, Runnable action) {
    }

    private final List<Choice> everything;
    private final List<ActionSearch.Row> searchable;
    private final Component acting;

    private final StringBuilder typed = new StringBuilder();
    private List<Choice> showing;
    private int picked;
    private int firstShown;

    /**
     * Whether the key that opened the palette may still be on its way to {@code charTyped}.
     * <p>A key press and the character it produces are two events, and the press that opens
     * this is followed by its own letter in the same round of input. Without this the box
     * opens with the palette key already typed into it - which the chat line hit first and
     * fixed the same way.
     * <p>But this palette has two ways in, and the chat line has one. Opened from the felt's
     * menu there is no key and no letter coming, so a flag that waited for one would swallow
     * the first letter the player actually typed. So it is cleared by the first frame drawn
     * instead: the letter, if there is one, arrives in the same round of input as the press,
     * which is before anything is drawn again. One frame is exactly the window.
     */
    private boolean swallowingTheOpeningKey = true;

    private boolean finished;

    /**
     * Where the cursor was last frame, so the mouse only moves the highlight when it moves.
     * <p>The palette opens in the middle of the window, which is where the cursor usually
     * already is - so a highlight that simply followed whatever the cursor was over would be
     * dragged off the first row the instant the panel appeared, and Enter after a search
     * would take a row nobody chose. A mouse that has not moved has not said anything.
     */
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    private ActionPalette(List<Choice> everything, Component acting) {
        this.everything = List.copyOf(everything);
        this.acting = acting;
        List<ActionSearch.Row> rows = new ArrayList<>();
        for (Choice choice : everything) {
            rows.add(new ActionSearch.Row(
                    choice.id(), choice.label().getString(), aliasesOf(choice.id())));
        }
        this.searchable = List.copyOf(rows);
        this.showing = List.copyOf(everything);
    }

    /**
     * Every word this verb can be found by, besides its name.
     * <p>The catalogue's English mnemonics, plus whatever the translation adds under the
     * verb's alias key. A language with nothing to add has no line and gets the catalogue's,
     * which is better than nothing to search by at all.
     */
    private static List<String> aliasesOf(String id) {
        List<String> found = new ArrayList<>(
                TableActions.byId(id).map(spec -> spec.aliases()).orElse(List.of()));
        String key = "alias.gathering.table." + id;
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) {
            for (String word : translated.split(",")) {
                String trimmed = word.strip().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    found.add(trimmed);
                }
            }
        }
        return List.copyOf(found);
    }

    /**
     * Builds a palette out of the menu rows a player could reach right now.
     * <p>Rows with no catalogue id are dropped, and so are rules and rows the menu itself
     * shows greyed out. A verb offered by two menus - draw is on the library's and on the
     * felt's - is kept once, from whichever offered it first.
     *
     * @param acting what the card verbs here will act on, said in words. Null when nothing is
     *               pointed at, which is not an error: most of the catalogue needs no target
     */
    public static ActionPalette over(List<ContextMenu.Entry> entries, Component acting) {
        Map<String, Choice> kept = new LinkedHashMap<>();
        for (ContextMenu.Entry entry : entries) {
            if (entry.id() == null || !entry.enabled()) {
                continue;
            }
            kept.putIfAbsent(entry.id(), new Choice(
                    entry.id(), entry.label(), entry.shortcut(), entry.action()));
        }
        return new ActionPalette(List.copyOf(kept.values()), acting);
    }

    /** Whether it has been used or dismissed, and the board may drop it. */
    public boolean finished() {
        return finished;
    }

    /** What has been typed so far, for the scripted run. */
    String queryForTesting() {
        return typed.toString();
    }

    /** Which verbs are showing, for the scripted run. */
    List<String> showingForTesting() {
        return showing.stream().map(Choice::id).toList();
    }

    /**
     * Answers a key press, and always says yes.
     * <p>Always, which is the point: while this is open every key belongs to it. A letter
     * that fell through to the board would tap a card halfway through the word "attach", and
     * this is the one screen in the mod where somebody is certain to be typing words made of
     * the same letters the board binds.
     */
    public boolean keyPressed(int key, int scanCode) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> finished = true;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> take();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (typed.length() > 0) {
                    typed.deleteCharAt(typed.length() - 1);
                    research();
                }
            }
            case GLFW.GLFW_KEY_UP -> move(-1);
            case GLFW.GLFW_KEY_DOWN -> move(1);
            case GLFW.GLFW_KEY_TAB -> move(net.minecraft.client.gui.screens.Screen.hasShiftDown()
                    ? -1 : 1);
            case GLFW.GLFW_KEY_V -> {
                if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                    append(net.minecraft.client.Minecraft.getInstance()
                            .keyboardHandler.getClipboard());
                }
            }
            default -> {
                // A letter on its way to charTyped, or a key with no meaning here. Swallowed
                // either way, because the board is not listening while this is.
            }
        }
        return true;
    }

    /** Takes a typed letter, and always says yes, for the same reason. */
    public boolean charTyped(char letter) {
        if (swallowingTheOpeningKey) {
            swallowingTheOpeningKey = false;
            return true;
        }
        append(String.valueOf(letter));
        return true;
    }

    private void append(String more) {
        if (more == null) {
            return;
        }
        for (int index = 0; index < more.length() && typed.length() < LONGEST_QUERY; index++) {
            char letter = more.charAt(index);
            if (letter >= ' ' && letter != 127) {
                typed.append(letter);
            }
        }
        research();
    }

    /**
     * Runs the query again and puts the highlight back on the first row.
     * <p>Back to the top on every keystroke, on purpose. A highlight that stayed where it was
     * would be on a different verb after each letter, and Enter would do whatever happened to
     * be under it - which is how a search box comes to do something nobody asked for.
     */
    private void research() {
        List<ActionSearch.Row> found = ActionSearch.matching(typed.toString(), searchable);
        Map<String, Choice> byId = new LinkedHashMap<>();
        for (Choice choice : everything) {
            byId.put(choice.id(), choice);
        }
        List<Choice> next = new ArrayList<>();
        for (ActionSearch.Row row : found) {
            Choice choice = byId.get(row.id());
            if (choice != null) {
                next.add(choice);
            }
        }
        showing = List.copyOf(next);
        picked = 0;
        firstShown = 0;
    }

    private void move(int by) {
        if (showing.isEmpty()) {
            return;
        }
        picked = Math.floorMod(picked + by, showing.size());
        // Scroll only as far as it takes to bring the highlight back on screen, so a list
        // being walked with the arrow keys moves a row at a time rather than a page.
        firstShown = Math.min(firstShown, picked);
        firstShown = Math.max(firstShown, picked - VISIBLE_ROWS + 1);
    }

    /** Does the highlighted verb, if there is one, and closes either way. */
    private void take() {
        if (picked >= 0 && picked < showing.size()) {
            Choice choice = showing.get(picked);
            finished = true;
            GatheringButtons.clickSound();
            choice.action().run();
            return;
        }
        // Enter on a search that found nothing is somebody giving up, not somebody asking for
        // the first row of a list that is not there.
        finished = true;
    }

    /** Where the panel sits: the middle of the window, over the felt. */
    public Rect at(Font font, int screenWidth, int screenHeight, int topEdge) {
        int width = Math.clamp((int) (screenWidth * SHARE_OF_WIDTH), NARROWEST, WIDEST);
        width = Math.min(width, Math.max(NARROWEST, screenWidth - PADDING * 2));
        int height = heightOf(font);
        int top = Math.max(topEdge + PADDING, (screenHeight - height) / 3);
        return new Rect((screenWidth - width) / 2, top, width,
                Math.min(height, Math.max(40, screenHeight - topEdge - PADDING * 2)));
    }

    private int heightOf(Font font) {
        int rows = Math.max(1, Math.min(VISIBLE_ROWS, showing.size()));
        int high = PADDING * 2 + font.lineHeight + GAP + rows * ROW_HEIGHT;
        if (acting != null) {
            high += font.lineHeight;
        }
        return high;
    }

    /** Which row is under this point, or -1. */
    private int rowAt(Font font, Rect where, double mouseX, double mouseY) {
        if (!where.contains((int) mouseX, (int) mouseY)) {
            return -1;
        }
        int top = firstRowY(font, where);
        int index = (int) ((mouseY - top) / ROW_HEIGHT);
        int at = firstShown + index;
        return index >= 0 && index < VISIBLE_ROWS && at < showing.size() ? at : -1;
    }

    private int firstRowY(Font font, Rect where) {
        int y = where.y() + PADDING + font.lineHeight + GAP;
        if (acting != null) {
            y += font.lineHeight;
        }
        return y;
    }

    /**
     * Answers a click, and always says yes.
     * <p>Clicking a row takes it; clicking anywhere else closes the palette. Both are true
     * everywhere else in the game, and a click that fell through to the felt would move a
     * card the player was only trying to stop looking at a list of verbs.
     */
    public boolean mouseClicked(Font font, Rect where, double mouseX, double mouseY) {
        int at = rowAt(font, where, mouseX, mouseY);
        if (at >= 0) {
            picked = at;
            take();
            return true;
        }
        if (!where.contains((int) mouseX, (int) mouseY)) {
            finished = true;
        }
        return true;
    }

    /**
     * Follows the cursor, so the mouse and the arrow keys agree about what Enter will do.
     * <p>Only once the cursor has actually moved. See {@link #lastMouseX}.
     */
    public void mouseMoved(Font font, Rect where, double mouseX, double mouseY) {
        boolean moved = Double.isNaN(lastMouseX)
                || lastMouseX != mouseX || lastMouseY != mouseY;
        boolean first = Double.isNaN(lastMouseX);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (first || !moved) {
            return;
        }
        int at = rowAt(font, where, mouseX, mouseY);
        if (at >= 0) {
            picked = at;
        }
    }

    public void render(GuiGraphics graphics, Font font, Rect where) {
        // Anything the opening key was going to produce has arrived by now. See the field.
        swallowingTheOpeningKey = false;

        GatheringSprites.draw(graphics, Element.PANEL, where.x(), where.y(),
                where.width(), where.height());
        int room = where.width() - PADDING * 2;
        int left = where.x() + PADDING;
        int y = where.y() + PADDING;

        // What has been typed, or what to do about that. A blank box with a cursor in it says
        // nothing; a line of grey saying what it wants says everything it needs to.
        if (typed.isEmpty()) {
            GuiText.draw(graphics, font,
                    Component.translatable("screen.gathering.palette.hint"), left, y, room, HINT);
        } else {
            GuiText.draw(graphics, font, Component.literal(typed + "_"), left, y, room, TYPED);
        }
        y += font.lineHeight;

        // What the card verbs on this list will act on, when anything is pointed at. Said in
        // words - "3 cards" - and never by name: a face-down card has a name this client is
        // not allowed to print, and a summary that named the target would be the one place
        // in the mod where it did.
        if (acting != null) {
            GuiText.draw(graphics, font, acting, left, y, room, TITLE);
            y += font.lineHeight;
        }
        y += GAP;

        if (showing.isEmpty()) {
            GuiText.draw(graphics, font,
                    Component.translatable("screen.gathering.palette.nothing"),
                    left, y, room, NOTHING);
            return;
        }

        int last = Math.min(showing.size(), firstShown + VISIBLE_ROWS);
        for (int index = firstShown; index < last; index++) {
            Choice choice = showing.get(index);
            int rowY = y + (index - firstShown) * ROW_HEIGHT;
            if (index == picked) {
                graphics.fill(where.x() + 2, rowY - 1,
                        where.x() + where.width() - 2, rowY + ROW_HEIGHT - 2, HIGHLIGHT);
            }
            int keyRoom = 0;
            if (choice.shortcut() != null) {
                keyRoom = font.width(choice.shortcut()) + GAP;
                GuiText.drawFlushRight(graphics, font, choice.shortcut(),
                        where.x() + where.width() - PADDING, rowY, 1f, KEY);
            }
            GuiText.draw(graphics, font, choice.label(), left, rowY,
                    Math.max(1, room - keyRoom), index == picked ? ROW_PICKED : ROW);
        }
    }
}
