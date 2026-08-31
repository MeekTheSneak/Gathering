package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Every rectangle the mod draws, as art rather than as arithmetic.
 *
 * <p>Nothing in this mod paints a colored box. A panel, a scrim, a tint over a tapped card, a
 * progress bar, the ring round the card under the cursor - each one is a PNG under
 * {@code assets/gathering/textures/gui/sprites/<theme>/}, with a {@code .mcmeta} beside it
 * saying how it stretches. That is the difference between a look somebody can change and a
 * look somebody would have to recompile: repaint the file and the screens change.
 *
 * <p>A {@linkplain GuiTheme theme} is one folder of those files. Screens name an
 * {@link Element}; this decides which folder it comes out of. A theme that has not drawn an
 * element inherits it from {@link GuiTheme#DEFAULT}, so a resource pack can repaint six things
 * and leave the other forty-nine alone - and so a theme can never be half-drawn on screen.
 *
 * <p>Colors live in the PNGs, not here. The one exception is where a color is genuinely data
 * rather than decoration - the light a booster's rarity glows, the ring the pack's own color
 * draws - and those go through {@link #draw(GuiGraphics, Element, int, int, int, int, int)},
 * which tints a neutral sprite. A theme still owns that element's shape and weight.
 *
 * <p>Client thread only.
 */
public final class GatheringSprites {

    /**
     * Every drawn element there is.
     *
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

        /**
         * Over the board, when a sub-screen is open on top of it.
         *
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
         *
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
        ZONE_BORDER("zone_border"),
        /**
         * Round somebody's half of the table, and round the controls that belong to it.
         *
         * <p>Drawn in the seat's own color, which is what makes four identical rectangles
         * four boards - so this one is painted neutral and tinted, unlike everything else
         * here, whose color is the artist's.
         */
        SEAT_RING("seat_ring"),
        /** Round whatever the cursor is on. */
        FOCUS_RING("focus_ring"),
        /** Round something merely pointed at, in the quieter of the two. */
        HOVER_RING("hover_ring"),
        /** Round a button that is switched on. Thicker: it has to read against a button's own edge. */
        CHOSEN_RING("chosen_ring"),
        /** The rubber band dragged out to pick several cards at once. */
        SELECT_BOX("select_box"),
        /**
         * The pile a card is being dragged at.
         *
         * <p>Drawn behind the slot rather than over it, so whatever is already sitting there
         * stays readable: the question being answered is "which box", and the box is the
         * thing that has to change. Two rings and a wash rather than one thin line, because
         * this is answering "which of five" among slots that all have borders already.
         */
        AIMED_PILE("aimed_pile"),
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
         *
         * <p>Warm against the board's greens and cools, because it is the same kind of fact
         * as a power and toughness somebody typed: a thing a person did on purpose. It has to
         * be the one warm thing at the bottom of the window or it is not a warning.
         */
        EXPOSED_BAND("exposed_band"),
        /**
         * Under one line of a card's counters.
         *
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
         *
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
         *
         * <p>Softer and wider than the line a resting card casts, because it is further away,
         * and the whole footprint rather than an edge, because it is doing a second job: it
         * is where the card will come down. A drag that shows nothing until it is let go asks
         * the player to aim at something they cannot see.
         */
        CARD_CAST("card_cast"),
        /**
         * The outline of where a held card would land.
         *
         * <p>The same amber {@link #DRAG_LANDING} uses in the scry box, because it is the
         * same sentence: this is where the thing in your hand goes if you let go now.
         */
        CARD_FOOTPRINT("card_footprint"),
        /** Over a tapped card. */
        TAPPED_TINT("tapped_tint"),
        /**
         * Over a card that will not untap.
         *
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
         *
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
         *
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
         *
         * <p>Yellow for a rare, orange for a mythic, which is the whole point of it - so this
         * is the second of the two neutral sprites, painted white and tinted like
         * {@link #SEAT_RING}.
         */
        RARITY_RING("rarity_ring"),

        /** How far there is to go. */
        BAR_TRACK("bar_track"),
        /** How far it has got. */
        BAR_FILL("bar_fill"),
        /** And how it looks when it is all the way, which is a set somebody finished. */
        BAR_DONE("bar_done");

        private final String name;

        Element(String name) {
            this.name = name;
        }

        /** The file name this element is painted in, the same in every theme. */
        public String fileName() {
            return name;
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
     *
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blitSprite(of(element), x, y, width, height);
    }

    /**
     * Draws one element into a box, in a color the game worked out rather than the artist.
     *
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
        graphics.blitSprite(of(element), x, y, width, height);
        graphics.setColor(1f, 1f, 1f, 1f);
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
