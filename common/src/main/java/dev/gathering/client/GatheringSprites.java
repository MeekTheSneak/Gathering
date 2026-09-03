package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;

/**
 * Every rectangle the mod draws, as art rather than as arithmetic.
 * <p>Nothing in this mod paints a colored box. A panel, a scrim, a tint over a tapped card, a
 * progress bar, the ring round the card under the cursor - each one is a PNG under
 * {@code assets/gathering/textures/gui/sprites/<theme>/}, with a {@code .mcmeta} beside it
 * saying how it stretches. That is the difference between a look somebody can change and a
 * look somebody would have to recompile: repaint the file and the screens change.
 * <p>A {@linkplain GuiTheme theme} is one folder of those files. Screens name an
 * {@link Element}; this decides which folder it comes out of. A theme that has not drawn an
 * element inherits it from {@link GuiTheme#DEFAULT}, so a resource pack can repaint six things
 * and leave the other forty-nine alone - and so a theme can never be half-drawn on screen.
 * <p>Colors live in the PNGs, not here. The one exception is where a color is genuinely data
 * rather than decoration - the light a booster's rarity glows, the ring the pack's own color
 * draws - and those go through {@link #draw(GuiGraphics, Element, int, int, int, int, int)},
 * which tints a neutral sprite. A theme still owns that element's shape and weight.
 * <p>Client thread only.
 */
public final class GatheringSprites {

    /**
     * Every drawn element there is.
     * <p>Kept in step with {@code tools/gui_art.py}, which paints them, by
     * {@code tools/spritecheck.py}, which fails the build if either grows an element the
     * other does not have.
     */
    public enum Element {
        /** The standard raised panel: the background of a screen or a section of one. */
        PANEL("panel"),
        /** A recessed well, for text areas, card slots and scrolling lists. */
        PANEL_INSET("panel_inset"),
        /** Selection and hover on a row of a list. */
        ROW_HIGHLIGHT("row_highlight"),
        /** The decklist backdrop: flush left, tapering right. Stretched; the taper is the shape. */
        DECK_PANEL("deck_panel"),
        /** The channel a scrollbar runs down. */
        SCROLL_TRACK("scroll_track"),
        /** And the part of it that moves. */
        SCROLL_THUMB("scroll_thumb"),
        /** A button, at rest. */
        BUTTON("button"),
        /** The same button with the cursor on it, or the keyboard focus. */
        BUTTON_HOVER("button_hover"),
        /** And one that will not do anything, which has to look like it will not. */
        BUTTON_OFF("button_off"),
        /** And one with the mouse held down on it, which dips the way a real key does. */
        BUTTON_DOWN("button_down"),

        /**
         * The four directions, as shapes.
         * <p>Blitted at {@link #ARROW} pixels square in the middle of a button rather than
         * stretched to fit it: an arrow is what is written on the button, and a triangle
         * scaled to a twenty-four by eighteen box is a triangle nobody drew.
         */
        ARROW_LEFT("arrow_left"),
        ARROW_RIGHT("arrow_right"),
        ARROW_UP("arrow_up"),
        ARROW_DOWN("arrow_down"),

        /**
         * Over the board, when a sub-screen is open on top of it.
         * <p>Enough to push the board back behind the screen in front of it, not enough to
         * hide it: a graveyard read mid-turn should be a box with the game behind it rather
         * than a room somebody has walked into.
         */
        SCREEN_SCRIM("screen_scrim"),
        /** Behind a full screen of its own: the collection, the trade window. */
        SCREEN_BACKDROP("screen_backdrop"),
        /** Behind a booster being opened. */
        PACK_BACKDROP("pack_backdrop"),
        /** Behind the set-completion list. */
        SETS_BACKDROP("sets_backdrop"),
        /**
         * Behind a card held up full screen.
         * <p>Darker than the one a small panel sits on. Nothing behind it is being read -
         * that is the whole reason that form exists - and a card is a picture, which needs
         * the room around it to be quiet before it looks like anything.
         */
        INSPECT_BACKDROP("inspect_backdrop"),

        /** The table top itself, under everything. */
        TABLE_FELT("table_felt"),
        /** Somebody's half of the table. Lighter than the felt, so it reads as their space. */
        SEAT_MAT("seat_mat"),
        /** Your own half of it, in the accent, so your board is the one you find first. */
        SEAT_MAT_MINE("seat_mat_mine"),
        /** The line across a mat marking off the row nearest its player. */
        SEAT_DIVIDER("seat_divider"),
        /** Round a zone or a group of slots: a marking on the mat, not a piece of interface. */
        ZONE_BORDER("zone_border", WhenCramped.LEFT_OFF),
        /**
         * Round somebody's half of the table, and round the controls that belong to it.
         * <p>Drawn in the seat's own color, which is what makes four identical rectangles
         * four boards - so this one is painted neutral and tinted, unlike everything else
         * here, whose color is the artist's.
         */
        SEAT_RING("seat_ring", WhenCramped.LEFT_OFF),
        /** Round whatever the cursor is on. */
        FOCUS_RING("focus_ring", WhenCramped.LEFT_OFF),
        /** Round something merely pointed at, in the quieter of the two. */
        HOVER_RING("hover_ring", WhenCramped.LEFT_OFF),
        /** Round a button that is switched on. Thicker: it has to read against a button's own edge. */
        CHOSEN_RING("chosen_ring", WhenCramped.LEFT_OFF),
        /** The rubber band dragged out to pick several cards at once. */
        SELECT_BOX("select_box", WhenCramped.LEFT_OFF),
        /**
         * The pile a card is being dragged at.
         * <p>Drawn behind the slot rather than over it, so whatever is already sitting there
         * stays readable: the question being answered is "which box", and the box is the
         * thing that has to change. Two rings and a wash rather than one thin line, because
         * this is answering "which of five" among slots that all have borders already.
         */
        AIMED_PILE("aimed_pile", WhenCramped.LEFT_OFF),
        /** Under a life total, so it reads against the table rather than into it. */
        LIFE_BACKING("life_backing"),
        /** Behind what the table has been saying. Quiet: it is not part of the board. */
        TALK_BACKDROP("talk_backdrop"),
        /** Behind the line being typed to it. */
        TALK_TYPING("talk_typing"),
        /** Under the number on a pile, saying how many cards are in it. */
        PILE_BADGE("pile_badge"),
        /**
         * Across a hand that is open to somebody.
         * <p>Warm against the board's greens and cools, because it is the same kind of fact
         * as a power and toughness somebody typed: a thing a person did on purpose. It has to
         * be the one warm thing at the bottom of the window or it is not a warning.
         */
        EXPOSED_BAND("exposed_band"),
        /**
         * Under one line of a card's counters.
         * <p>Darker than the tints used elsewhere, because this one lands wherever a card's
         * rules box happens to be light, and pale text on pale card stock is text nobody
         * reads at a glance - which for the number saying how big a creature is now is the
         * whole point.
         */
        COUNTER_BAND("counter_band"),
        /** Under a commander's tax, which is written over card art rather than empty felt. */
        TAX_BACKING("tax_backing"),
        /** The same under the cursor: darker still, so a button looks like one. */
        TAX_LIT("tax_lit"),
        /** Under a number written over card art. */
        GHOST_TINT("ghost_tint"),

        /**
         * The two edges a card lying on the felt casts.
         * <p>Nothing here has thickness, so without it a card lying across another one is two
         * flat pictures sharing an edge and you cannot tell which is on top. A contact shadow
         * rather than a drop shadow, which is the difference between a card lying on the felt
         * and a card hovering over it: what a card resting on a table casts is a hard line
         * right where its edge meets the cloth, one pixel, no daylight under it, and dark to
         * make up for being thin.
         */
        CARD_SHADOW("card_shadow"),
        /**
         * The shadow a card held over the table casts on it.
         * <p>Softer and wider than the line a resting card casts, because it is further away,
         * and the whole footprint rather than an edge, because it is doing a second job: it
         * is where the card will come down. A drag that shows nothing until it is let go asks
         * the player to aim at something they cannot see.
         */
        CARD_CAST("card_cast"),
        /**
         * The outline of where a held card would land.
         * <p>The same amber {@link #DRAG_LANDING} uses in the scry box, because it is the
         * same sentence: this is where the thing in your hand goes if you let go now.
         */
        CARD_FOOTPRINT("card_footprint", WhenCramped.LEFT_OFF),
        /** Over a tapped card. */
        TAPPED_TINT("tapped_tint"),
        /**
         * Over a card that will not untap.
         * <p>A pale blue wash, with the rime of {@link #FROZEN_EDGE} caked along the top and
         * bottom rather than a ring round the whole card - because a ring is what the cursor
         * draws. Nearly white, so it separates from the cursor's accent as well as from the
         * warm gold of a written power and toughness: three marks on one card have to be
         * three things or a player reads them as one.
         */
        FROZEN_TINT("frozen_tint"),
        /** And the rime along its top and bottom. */
        FROZEN_EDGE("frozen_edge"),
        /** Under a note written across a card. */
        NAME_BACKDROP("name_backdrop"),
        /** Where art has not arrived yet. */
        CARD_PLACEHOLDER("card_placeholder"),
        /**
         * Under a written power and toughness.
         * <p>Warm rather than the cool gray the rest of the board uses, because it is the one
         * number on a card somebody put there by hand, and the difference between "printed"
         * and "we agreed this" should be visible from across the table.
         */
        STRENGTH_BADGE("strength_badge"),
        /** Blank card stock, for a card somebody wrote themselves. */
        PAPER_BLANK("paper_blank"),
        /** The same stock in an emblem's colors. */
        PAPER_EMBLEM("paper_emblem"),

        /** Every other row of a long list, so the eye can follow one across. */
        ROW_ODD("row_odd"),
        /** The row under the cursor. */
        ROW_HOVER("row_hover"),
        /** The line between groups of a menu. */
        MENU_RULE("menu_rule"),
        /** Over a card that has been chosen, so a pick of two reads at a glance. */
        CHOSEN_FILL("chosen_fill"),
        /** The bar marking the gap a dragged card would drop into. */
        DRAG_LANDING("drag_landing"),
        /**
         * Over a card the player has said they do not want on top.
         * <p>Grayed rather than moved: a card that jumped to another row every time somebody
         * changed their mind would make a scry of three a puzzle about where things went.
         */
        SENT_AWAY("sent_away"),
        /** Under a filter that is switched on. */
        FILTER_ON("filter_on"),
        /** Beside a card somebody is chasing, wherever that card is shown. */
        WANTED_MARK("wanted_mark"),

        /** The torn edge of a booster wrapper. */
        PACK_WRAPPER_EDGE("pack_wrapper_edge"),
        /** One mote of the light coming out of it. Tinted by the product's own color. */
        PACK_SPARK("pack_spark"),
        /**
         * Round the card a pack was opened for.
         * <p>Yellow for a rare, orange for a mythic, which is the whole point of it - so this
         * is the second of the two neutral sprites, painted white and tinted like
         * {@link #SEAT_RING}.
         */
        RARITY_RING("rarity_ring", WhenCramped.LEFT_OFF),

        /**
         * Five frames of a dashed ring with the lit dash travelling round it, cut off
         * BDragon1727's sheet, for a card whose art has not arrived.
         * <p>Frames rather than one sprite turned by the renderer: rotating pixel art by
         * anything but a right angle resamples it. Pick one with {@link #spinner}.
         */
        SPINNER_0("spinner_0"),
        SPINNER_1("spinner_1"),
        SPINNER_2("spinner_2"),
        SPINNER_3("spinner_3"),
        SPINNER_4("spinner_4"),

        /**
         * How far there is to go, and how far it has got.
         * <p>A hollow box with its ends cut on the diagonal, and a solid bar that runs
         * inside it - cut off BDragon1727's sheet, so the fill is drawn inside the track's
         * wall rather than over the top of it.
         */
        BAR_TRACK("bar_track"),
        /** How far it has got. */
        BAR_FILL("bar_fill"),
        /** And how it looks when it is all the way, which is a set somebody finished. */
        BAR_DONE("bar_done"),

        /**
         * A box of pips that fill one at a time: two caps, a lit cell and a dim one.
         * <p>In pieces rather than whole, because a match is best of one, three or five and
         * a nine-slice cannot repeat a cell - its middle stretches, and five pips stretched
         * would be one long smudge. Drawn with {@link #pips}.
         */
        PIP_LEFT("pip_left"),
        PIP_FULL("pip_full"),
        PIP_EMPTY("pip_empty"),
        PIP_RIGHT("pip_right"),

        /**
         * A mana curve's columns, which are the same idea standing up.
         * <p>Their own elements rather than the bar's, because his bar only reads one way
         * round: the shear leans and the light runs along the top. These are the mod's own,
         * built to be drawn either way.
         */
        CURVE_TRACK("curve_track"),
        CURVE_FILL("curve_fill");

        /** What an element does when its box has no room left for its border. */
        public enum WhenCramped {
            /**
             * Drawn as one squashed picture instead of a frame round a middle.
             * <p>For an element that <em>is</em> the surface. A mat left off is a seat with no
             * board under it, and a mat nine-sliced into a box too small for its border is a
             * smear of corners - so it is stretched instead, which is the picture somebody
             * painted, made small.
             */
            SQUASHED,
            /**
             * Left off.
             * <p>For a marking over something else. A zone's outline smeared into its own
             * corners reads as a fault; the same zone as a clean dark recess reads as a zone.
             */
            LEFT_OFF
        }

        private final String name;
        private final WhenCramped cramped;

        Element(String name) {
            this(name, WhenCramped.SQUASHED);
        }

        /**
         * An element that is a line round something else rather than a surface of its own.
         * <p>Which matters only where there is no room for its border, and only for art that
         * has one - what that border is is the artist's, and is read off the sprite as it is
         * drawn rather than written down here, because four of the fourteen shipped looks
         * draw a panel at sixteen pixels where the other ten draw it at eight.
         */
        Element(String name, WhenCramped cramped) {
            this.name = name;
            this.cramped = cramped;
        }

        /** The file name this element is painted in, the same in every theme. */
        public String fileName() {
            return name;
        }

        /** What this one does in a box with no room for its border. */
        public WhenCramped whenCramped() {
            return cramped;
        }

        /** Every element, once. {@code values()} clones its array and this is walked per theme. */
        private static final Element[] ALL = values();

        /** Every element, for anything that has to walk them. Do not modify. */
        public static Element[] all() {
            return ALL;
        }
    }

    private GatheringSprites() {
    }

    /**
     * Which file an element is coming out of right now.
     * <p>The theme in force, unless it has not drawn this one, in which case the default
     * theme's. Resolved on every call rather than remembered: the answer changes when a
     * resource pack is added or removed, and both the lookup and the comparison are cheaper
     * than the draw call that follows. Nothing is allocated - every file name a theme could
     * name was worked out when the theme was read.
     */
    public static ResourceLocation of(Element element) {
        GuiTheme theme = GuiThemes.active();
        ResourceLocation wanted = theme.spriteOf(element);
        if (theme.id().equals(GuiThemes.DEFAULT)) {
            return wanted;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return wanted;
        }
        // Null rather than the missing sprite before the atlas has been stitched, which is
        // not a state a screen can draw in - but this is asked once per drawn rectangle and
        // an exception here would take the whole frame down rather than one panel with it.
        TextureAtlasSprite drawn = client.getGuiSprites().getSprite(wanted);
        if (drawn == null
                || drawn.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            return GuiThemes.byId(GuiThemes.DEFAULT.toString()).spriteOf(element);
        }
        return wanted;
    }

    /** Draws one element into a box. */
    public static void draw(GuiGraphics graphics, Element element, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        ResourceLocation sprite = of(element);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Resolved once and passed along. Everything here wants the same stitched sprite, and
        // this runs for every rectangle on every frame of a screen that may have two hundred
        // of them.
        TextureAtlasSprite drawn = drawn(sprite);
        if (drawn == null || hasRoomForItsBorder(drawn, width, height)) {
            graphics.blitSprite(sprite, x, y, width, height);
            return;
        }
        if (element.whenCramped() == Element.WhenCramped.LEFT_OFF) {
            return;
        }
        graphics.blit(x, y, 0, width, height, drawn);
    }

    /**
     * Whether a box this size has room for this sprite's border to be itself.
     * <p>A nine-slice keeps its border at a fixed size and tiles what is between them. Give
     * it a box no wider than its two edges and there is nothing between them: the game tiles
     * what it can and runs the corners together, and what comes out is not the picture
     * anybody painted. Reported from a real session - "GUI borders also scale with zoom, that
     * makes certain zones (like graveyard or exile) look really bad when zoomed out on
     * certain themes like future sight" - the zone slots on a board scrolled right out
     * being the first boxes small enough for it to show.
     * <p>The border is read off the sprite that is about to be drawn rather than written down
     * anywhere: it is a number in a resource pack, and the fourteen shipped looks do not
     * agree on it - four draw a panel at sixteen pixels where the other ten draw it at eight.
     * A number in the mod would have been wrong for one of those groups.
     * <p>Stricter than the game's own rule, which only refuses a nine-slice with no middle
     * at all. See {@link #across}.
     */
    private static boolean hasRoomForItsBorder(TextureAtlasSprite drawn, int width, int height) {
        GuiSpriteScaling.NineSlice nine = sliced(drawn);
        // Null where it is stretched or tiled, or before the atlas is stitched: nothing to
        // run out of room for, so it is drawn the way it always was.
        return nine == null
                || (width >= across(nine.border()) && height >= along(nine.border()));
    }

    /**
     * The nine-slice this sprite is drawn as, or null where it is stretched or tiled.
     * <p>Null rather than an exception all the way down: this is asked once per drawn
     * rectangle, and a frame that will not draw at all is worse than a frame drawn with one
     * panel sliced the wrong way.
     */
    private static GuiSpriteScaling.NineSlice sliced(TextureAtlasSprite drawn) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || drawn == null) {
            return null;
        }
        return client.getGuiSprites().getSpriteScaling(drawn)
                instanceof GuiSpriteScaling.NineSlice nine ? nine : null;
    }

    /** The stitched sprite behind a name, or null before the atlas has one. */
    private static TextureAtlasSprite drawn(ResourceLocation sprite) {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.getGuiSprites().getSprite(sprite);
    }

    /**
     * How wide a box has to be for this border: its two edges, and a middle as wide as one.
     * <p>The game refuses only a nine-slice with no middle at all. That is not enough for a
     * board somebody has scrolled out: a card there is about seventeen pixels across, and a
     * border of eight leaves one pixel of middle - a solid block of border with a hairline of
     * picture in it.
     */
    private static int across(GuiSpriteScaling.NineSlice.Border border) {
        return dev.gathering.core.ui.SpriteFrames.smallestFor(border.left(), border.right());
    }

    /** And how tall, the same way. */
    private static int along(GuiSpriteScaling.NineSlice.Border border) {
        return dev.gathering.core.ui.SpriteFrames.smallestFor(border.top(), border.bottom());
    }

    /**
     * Draws one element into a box, in a color the game worked out rather than the artist.
     * <p>For the few things whose color is information - the light a booster's rarity gives
     * off, the ring in a product's own color. Everything else takes its color from its PNG,
     * which is the point of this class.
     *
     * @param color 0xAARRGGBB
     */
    public static void draw(
            GuiGraphics graphics, Element element, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f);
        draw(graphics, element, x, y, width, height);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * How big a spinner is drawn, and how long one turn takes.
     * <p>Forty-four is the size the ring was drawn at. Halving a ring of dashes turns every
     * dash into a smudge, so it is blitted whole and simply left off a card with no room for
     * it - which the caller already checks, because the words underneath need room too.
     */
    public static final int SPINNER = 44;
    private static final long SPINNER_TURN_MILLIS = 640L;

    /**
     * The frames in order, held once.
     * <p>Rather than {@code Element.values()[...]} at the draw: an enum's {@code values()}
     * clones its array every call, and this one is called on every frame of a screen that may
     * have twenty cards on it still fetching.
     */
    private static final Element[] SPINNER_FRAMES = {
        Element.SPINNER_0, Element.SPINNER_1, Element.SPINNER_2, Element.SPINNER_3,
        Element.SPINNER_4,
    };

    /**
     * The frame of the spinner that belongs to right now, in the middle of the given box.
     * <p>Off the wall clock rather than off a tick count, because this is drawn on screens
     * that are open while the game is paused - and a spinner that stops whenever the world
     * does says the fetch has stopped too.
     */
    public static void spinner(GuiGraphics graphics, int x, int y, int width, int height) {
        int frame = (int) (net.minecraft.Util.getMillis() % SPINNER_TURN_MILLIS
                * SPINNER_FRAMES.length / SPINNER_TURN_MILLIS);
        draw(graphics, SPINNER_FRAMES[frame],
                x + (width - SPINNER) / 2, y + (height - SPINNER) / 2, SPINNER, SPINNER);
    }

    /**
     * How big an arrow is drawn: nine across the point, twelve along the base.
     * <p>Not square, because the sprite is not. It is cut off BDragon1727's sheet at nine by
     * twelve, and up and down are that turned a quarter - so which way round these two go
     * depends on which arrow it is. Drawing every one of them nine by nine squashed the two
     * that are taller than they are wide.
     */
    public static final int ARROW_ACROSS = 9;
    public static final int ARROW_ALONG = 12;

    /** How big a pip box's pieces are: two caps, and one cell per game of the match. */
    public static final int PIP_HIGH = 10;
    private static final int PIP_LEFT_WIDE = 3;
    private static final int PIP_CELL_WIDE = 5;
    private static final int PIP_RIGHT_WIDE = 2;

    /** How wide a box of this many pips comes out, so a caller can middle it. */
    public static int pipsWide(int total) {
        return PIP_LEFT_WIDE + PIP_CELL_WIDE * Math.max(0, total) + PIP_RIGHT_WIDE;
    }

    /**
     * A row of pips, the first {@code filled} of them lit.
     * <p>Built out of four sprites rather than drawn from one, so a match of any length gets
     * a box that really is that many cells wide.
     */
    public static void pips(GuiGraphics graphics, int x, int y, int filled, int total) {
        draw(graphics, Element.PIP_LEFT, x, y, PIP_LEFT_WIDE, PIP_HIGH);
        int at = x + PIP_LEFT_WIDE;
        for (int index = 0; index < total; index++) {
            draw(graphics, index < filled ? Element.PIP_FULL : Element.PIP_EMPTY,
                    at, y, PIP_CELL_WIDE, PIP_HIGH);
            at += PIP_CELL_WIDE;
        }
        draw(graphics, Element.PIP_RIGHT, at, y, PIP_RIGHT_WIDE, PIP_HIGH);
    }

    /** One of the four arrows, at its own size, in the middle of the given box. */
    public static void arrow(GuiGraphics graphics, Element which, int x, int y, int width, int height) {
        boolean upright = which == Element.ARROW_LEFT || which == Element.ARROW_RIGHT;
        int wide = upright ? ARROW_ACROSS : ARROW_ALONG;
        int tall = upright ? ARROW_ALONG : ARROW_ACROSS;
        draw(graphics, which, x + (width - wide) / 2, y + (height - tall) / 2, wide, tall);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, Element.PANEL, x, y, width, height);
    }

    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, Element.PANEL_INSET, x, y, width, height);
    }

    public static void highlight(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, Element.ROW_HIGHLIGHT, x, y, width, height);
    }

    public static void deckPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, Element.DECK_PANEL, x, y, width, height);
    }

    public static void scrollTrack(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, Element.SCROLL_TRACK, x, y, width, height);
    }

    public static void scrollThumb(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, Element.SCROLL_THUMB, x, y, width, height);
    }
}
