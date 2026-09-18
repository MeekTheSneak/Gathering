package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.ui.CardShape;
import dev.gathering.core.ui.CardText;
import dev.gathering.core.ui.CardTextFit;
import dev.gathering.core.ui.InspectLayout;
import dev.gathering.core.story.CardStory;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.StrengthBadge;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * One card, drawn: its printed face and its oracle text.
 * <p>The only place a card is drawn for reading, in three sizes for three places - beside the
 * cursor, into a box a screen set aside, and filling the screen. They share every pixel of the
 * drawing and differ only in geometry, so a card looks like itself wherever you meet it.
 * <p>Oracle text rather than printed text throughout: errata accuracy is the reason to show
 * text at all rather than just the image.
 * <p>Everything here draws from what this client already has. It never blocks and never asks
 * the server for anything, so it can run every frame.
 * <p>Client-only.
 */
public final class CardInspectPanel {

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM_TEXT = 0xFF9A9690;

    /**
     * What somebody wrote on a card, across the top of it, and the band it is written on.
     * <p>Pale blue rather than the card's own ink so a note reads as somebody's handwriting
     * and not as printed text, and on a backing because a card's own name is light on dark as
     * often as it is dark on light and a note has to be readable over either.
     */
    private static final int WRITING_TEXT = 0xFFBFD8FF;

    private static final int GAP = 8;

    /**
     * The panel's own nine-slice border, which is eight in every look there is.
     * <p>Not a measurement of one theme: the sprite is thirty-two with a border of eight, so
     * everything at that depth or deeper is the stretched field and everything shallower is
     * frame, whatever the theme draws there.
     */
    private static final int FRAME = 8;

    /**
     * How far in from the panel's edge the words start.
     * <p>Past the frame, not up against it. This used to be eight, which is exactly the frame's
     * thickness - so the first pixel of every line was the first pixel of the field, and any look
     * whose border reaches in at all had its text sitting on the border.
     * <p>Then it was that plus six, which is enough for the looks whose panel this mod draws and not
     * for the four built around a frame somebody drew: those reach further in, and the owner
     * reported the text crowding the edge on Ember. The panel is sized from its text, so widening
     * this makes the box bigger rather than the words smaller, which is the way round to want.
     */
    private static final int PADDING = FRAME + 10;
    private static final int SIDEBAR_WIDTH = 180;
    private static final float FULL_SCREEN_HEIGHT_FRACTION = 0.82f;

    /**
     * How tall the art is in the cursor panel, as a share of the screen, and the bounds it
     * may not leave.
     * <p>Relative because a GUI-scaled screen is anywhere between about 240 and 700 units
     * tall depending on the player's GUI scale, and a fixed size that reads as a small
     * preview on one is most of the screen on another.
     */
    // Big enough to actually read. A screen whose own preview says everything the card says
    // keeps renderAtCursor out, and one showing only the picture gets the words alone, so a
    // panel with art is over inventories, the table and lists, where the card is small.
    //
    // Two thirds of a GUI-scale-3 window is a little over two hundred pixels of card. The text
    // column is sized from the art, so this is the one knob.
    private static final float CURSOR_ART_FRACTION = 0.66f;
    private static final int CURSOR_ART_MAX = 400;
    private static final int CURSOR_ART_MIN = 96;

    /** The art may shrink this far to give a wordy card's text somewhere to go. */
    private static final int CURSOR_ART_FLOOR = 56;

    /** Oracle text wrapped much narrower than this stops being worth reading. */
    private static final int CURSOR_TEXT_WIDTH = 190;

    /** Vanilla's own tooltip offsets, so the panel lands where the tooltip it replaces was. */
    private static final int CURSOR_OFFSET_X = 12;
    private static final int CURSOR_OFFSET_Y = -12;
    private static final int SCREEN_EDGE = 6;

    /**
     * The most of the window's height the panel beside the cursor takes while the whole text
     * fits in that. A wordy card may have the rest.
     */
    private static final float MOST_OF_THE_WINDOW = 0.8f;

    /**
     * How far in front of everything else a panel drawn over a screen sits.
     * <p>Slot items are drawn at depth 150 and above, so a panel drawn afterwards at depth
     * zero still comes out behind them - the item pokes through the card you are trying to
     * read. Vanilla puts tooltips at 400 for the same reason, and this sits with them.
     */
    private static final float OVER_ITEMS = 400f;

    private CardInspectPanel() {
    }

    /**
     * The card beside the cursor, as an enclosed panel that follows it.
     * <p>Positioned the way a tooltip is - down and to the right, flipping to the other side
     * near an edge - because that is the movement a player's hand already expects, and
     * because this panel is drawn over the vanilla tooltip and should sit where it sat.
     */
    public static void renderBeside(
            GuiGraphics graphics, CardSummary summary, boolean foil, boolean flipped,
            CardStory story, String strength, int anchorX, int anchorY,
            int screenWidth, int screenHeight) {
        renderBeside(graphics, summary, foil, flipped, story, strength, anchorX, anchorY,
                screenWidth, screenHeight, true);
    }

    /**
     * The same, with or without the picture.
     * <p>Without, for a screen already showing the card's picture somewhere of its own: the
     * words are what it is missing, and a second picture chasing the cursor is not.
     */
    public static void renderBeside(
            GuiGraphics graphics, CardSummary summary, boolean foil, boolean flipped,
            CardStory story, String strength, int anchorX, int anchorY,
            int screenWidth, int screenHeight, boolean withArt) {
        told = story == null ? CardStory.NONE : story;
        overwritten = strength;
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, OVER_ITEMS);
        try {
            drawBeside(graphics, summary, foil, flipped, anchorX, anchorY, screenWidth, screenHeight,
                    withArt);
        } finally {
            graphics.pose().popPose();
            told = CardStory.NONE;
            overwritten = null;
        }
    }

    private static void drawBeside(
            GuiGraphics graphics, CardSummary summary, boolean foil, boolean flipped,
            int anchorX, int anchorY, int screenWidth, int screenHeight, boolean withArt) {
        // Art per printed side, text per face: a split card is one picture and two rules
        // boxes. The side is the one the card is showing - a transformed permanent read from
        // the table used to be described by its front, which is a different card.
        CardFaceSummary shown = summary.sideShown(flipped);
        List<CardFaceSummary> faces = summary.faces();
        Font font = Minecraft.getInstance().font;
        float asked = GuiText.askedScale();

        int artHeight = withArt
                ? Mth.clamp(Math.round(screenHeight * CURSOR_ART_FRACTION), CURSOR_ART_MIN, CURSOR_ART_MAX)
                : 0;
        int content = Math.max(withArt ? CardShape.widthFor(artHeight) : 0, CURSOR_TEXT_WIDTH);

        // A narrow screen, or a double-faced card, can want more width than there is.
        int available = Math.max(1, screenWidth - SCREEN_EDGE * 2 - PADDING * 2);
        if (content > available) {
            float scale = (float) available / content;
            artHeight = withArt ? Math.max(1, Math.round(artHeight * scale)) : 0;
            content = available;
        }

        // Room first, and only then smaller letters. Reported by the owner: "the alt screen
        // that opens when hovering over a card should have the entire card's text. It needs to
        // not be cut off." The panel used to stop at eight tenths of the window and drop
        // whatever was left over the edge, so a wordy card lost its last ability and a
        // double-faced one could lose its whole back.
        //
        // In order: under the art at the height that leaves the board in view, under it at the
        // window's height, beside it and widening, and under it as wide as the window. The first
        // that holds every word at the asked size is the one drawn. Where none does, the
        // roomiest is, and the text shrinks or takes columns to fit it - see CardTextFit.
        int tallest = Math.max(1, screenHeight - SCREEN_EDGE * 2);
        // Not the whole window when it need not be. The panel is read *while* looking at the
        // board, so one that reaches from the top of the screen to the bottom has answered the
        // question and taken away the reason for asking it.
        int comfortable = Math.min(tallest, Math.round(screenHeight * MOST_OF_THE_WINDOW));
        int underArt = withArt ? CURSOR_ART_FLOOR + GAP : 0;

        List<Shape> tried = new ArrayList<>();
        tried.add(Shape.under(fitFor(font, faces, content, comfortable - PADDING * 2 - underArt),
                content, comfortable));
        tried.add(Shape.under(fitFor(font, faces, content, tallest - PADDING * 2 - underArt),
                content, tallest));
        if (withArt) {
            int besideArt = Math.max(1, Math.min(artHeight, tallest - PADDING * 2));
            int widest = available - CardShape.widthFor(besideArt) - GAP;
            for (int wide = CURSOR_TEXT_WIDTH; wide <= widest && !whole(tried, asked); ) {
                tried.add(Shape.beside(fitFor(font, faces, wide, tallest - PADDING * 2),
                        besideArt, wide));
                wide = wide == widest ? widest + 1 : Math.min(widest, wide * 4 / 3);
            }
        }
        if (!whole(tried, asked)) {
            tried.add(Shape.under(fitFor(font, faces, available, tallest - PADDING * 2 - underArt),
                    available, tallest));
        }
        Shape shape = best(tried, asked);
        CardTextFit fit = shape.fit();

        int artShown;
        int width;
        int height;
        if (shape.beside()) {
            artShown = shape.art();
            width = PADDING * 2 + CardShape.widthFor(artShown) + GAP + shape.textWidth();
            height = PADDING * 2 + Math.max(artShown, fit.height());
        } else {
            // A wordy card takes the room out of the art rather than off the end of the text.
            artShown = withArt
                    ? Math.max(CURSOR_ART_FLOOR,
                            Math.min(artHeight, shape.ceiling() - PADDING * 2 - GAP - fit.height()))
                    : 0;
            width = PADDING * 2 + shape.textWidth();
            height = PADDING * 2 + (withArt ? artShown + GAP : 0) + fit.height();
        }
        height = Math.min(height, tallest);
        int artWidth = CardShape.widthFor(artShown);

        int x = anchorX + CURSOR_OFFSET_X;
        if (x + width > screenWidth - SCREEN_EDGE) {
            x = anchorX - CURSOR_OFFSET_X - width;
        }
        int y = anchorY + CURSOR_OFFSET_Y;
        if (y + height > screenHeight - SCREEN_EDGE) {
            y = screenHeight - SCREEN_EDGE - height;
        }
        x = Math.max(SCREEN_EDGE, x);
        y = Math.max(SCREEN_EDGE, y);

        GatheringSprites.panel(graphics, x, y, width, height);

        // The shine, on the small panel too. It moves with the cursor rather than with a
        // turn, because this panel is already following the cursor and turning it as well
        // would be two things answering one hand. Same drawing either way, so a foil
        // looks like a foil wherever it is met.
        if (shape.beside()) {
            drawFace(graphics, shown, Holding.inspected(foil, grainOf(summary)),
                    x + PADDING, y + PADDING, artWidth, artShown, true);
            drawFitted(graphics, font, faces, fit,
                    x + PADDING + artWidth + GAP, y + PADDING, shape.textWidth(), fit.height());
            return;
        }
        int textTop = y + PADDING;
        if (withArt) {
            drawFace(graphics, shown, Holding.inspected(foil, grainOf(summary)),
                    x + PADDING + (shape.textWidth() - artWidth) / 2, y + PADDING,
                    artWidth, artShown, true);
            textTop += artShown + GAP;
        }
        drawFitted(graphics, font, faces, fit, x + PADDING, textTop, shape.textWidth(), fit.height());
    }

    /**
     * One way of laying out the panel beside the cursor, and how the text came out in it.
     *
     * @param beside    whether the text is beside the art rather than under it
     * @param art       the art's height, when beside
     * @param textWidth the text's column, across
     * @param ceiling   the tallest the panel may be, when under
     */
    private record Shape(CardTextFit fit, boolean beside, int art, int textWidth, int ceiling) {

        static Shape under(CardTextFit fit, int textWidth, int ceiling) {
            return new Shape(fit, false, 0, textWidth, ceiling);
        }

        static Shape beside(CardTextFit fit, int art, int textWidth) {
            return new Shape(fit, true, art, textWidth, 0);
        }
    }

    private static boolean whole(List<Shape> tried, float asked) {
        return tried.stream().anyMatch(shape -> shape.fit().isWhole(asked));
    }

    /**
     * The first shape that holds all of it at the asked size, or else the one where it came out
     * largest: all of it before some of it, bigger letters before smaller, fewer columns before
     * more. Ties go to the earlier shape, which is the smaller panel.
     */
    private static Shape best(List<Shape> tried, float asked) {
        Shape best = tried.get(0);
        for (Shape shape : tried) {
            if (shape.fit().isWhole(asked)) {
                return shape;
            }
            if (better(shape.fit(), best.fit())) {
                best = shape;
            }
        }
        return best;
    }

    private static boolean better(CardTextFit one, CardTextFit other) {
        if (one.fits() != other.fits()) {
            return one.fits();
        }
        if (one.scale() != other.scale()) {
            return one.scale() > other.scale();
        }
        return one.columns() < other.columns();
    }

    /**
     * The printed face, or both of them, filling a box a screen has set aside.
     * <p>The art keeps its proportions and is centered in whatever is left, so a box that is
     * not exactly card-shaped - or a double-faced card sharing one box - letterboxes rather
     * than stretching. A stretched card looks like a rendering bug; a centered one looks like
     * a layout.
     */
    public static void renderArt(
            GuiGraphics graphics, CardSummary summary, int x, int y, int width, int height) {
        renderArt(graphics, summary, false, x, y, width, height);
    }

    /**
     * The same, showing the side this card is currently sitting on.
     * <p>One side, not all of them. A transform card drawn as both its printed sides side by
     * side comes out as two half-size cards, which is not a thing that exists - a card lies
     * on a table one way up. Which way up is the caller's, because it is state about that
     * particular card rather than about the printing.
     */
    public static void renderArt(GuiGraphics graphics, CardSummary summary, boolean flipped,
            int x, int y, int width, int height) {
        renderArt(graphics, summary, flipped, x, y, width, height, Holding.FLAT);
    }

    /**
     * The same, turned, and with its shine if it is a foil.
     * <p>For a screen that is showing a card as a thing rather than as an entry in a list -
     * the cards coming out of a booster, so far. It goes through the same door every other
     * card does, so a foil pulled out of a pack sparkles by exactly the machinery a foil held
     * up to read does, rather than by a second copy of it.
     *
     * @param yaw   how far the card is turned sideways, in degrees
     * @param pitch and up and down
     * @param grain a stable number about the printing, so its sparkle is its own
     */
    public static void renderArtTurned(
            GuiGraphics graphics, CardSummary summary, boolean flipped,
            int x, int y, int width, int height,
            float yaw, float pitch, boolean foil) {
        renderArt(graphics, summary, flipped, x, y, width, height,
                new Holding(foil, grainOf(summary), yaw, pitch,
                        shineFrom(yaw, MOST_YAW), shineFrom(pitch, MOST_PITCH)));
    }

    /**
     * Where the light is on a card turned this far.
     * <p>The shine follows the turn rather than the cursor, because a card in a grid is
     * turned by how near the cursor is to it rather than by being held - so the only thing
     * that knows where the light should be is the angle itself.
     */
    private static float shineFrom(float angle, float most) {
        return most <= 0f ? 0f : Math.max(-1f, Math.min(1f, angle / most));
    }

    /** The most a card in a grid is turned, which is what its shine is measured against. */
    private static final float MOST_YAW = 9f;
    private static final float MOST_PITCH = 5.5f;

    private static void renderArt(GuiGraphics graphics, CardSummary summary, boolean flipped,
            int x, int y, int width, int height, Holding held) {
        CardFaceSummary shown = summary.sideShown(flipped);
        if (width <= 0 || height <= 0) {
            return;
        }

        int artWidth = width;
        int artHeight = CardShape.heightFor(artWidth);
        if (artHeight > height) {
            artHeight = height;
            artWidth = CardShape.widthFor(artHeight);
        }
        if (artWidth <= 0 || artHeight <= 0) {
            return;
        }

        int row = x + (width - artWidth) / 2;
        int top = y + (height - artHeight) / 2;
        drawFace(graphics, shown, held, row, top, artWidth, artHeight);
    }

    /**
     * Everything the card says, filling a box a screen has set aside.
     * <p>No background of its own: the screen has already framed the space, and a second
     * panel inside the first is just a smaller hole.
     */
    public static void renderText(
            GuiGraphics graphics, CardSummary summary, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        drawFitted(graphics, font, summary.faces(),
                fitFor(font, summary.faces(), width, height), x, y, width, height);
    }

    /**
     * The whole window: the card down the left, everything it says beside it.
     * <p>Two columns rather than a card with a caption, and the card drawn as large as it
     * can be: the picture is the card, and one shrunk to make room for its own text is a
     * reading tool with the priority backwards.
     * <p>The card turns with the mouse, which out here is the player's head - what a hand does
     * without being asked, and the only way a foil can exist at all, because a sheen that
     * never moves is a sticker. See {@link CardTilt} and {@link FoilSheen}.
     *
     * @param foil whether this particular copy is a foil, which is a fact about the card in
     *     somebody's hand rather than about the printing - so it comes from the caller and
     *     never from the metadata
     */
    public static void renderFullScreen(
            GuiGraphics graphics, CardSummary summary, boolean foil, boolean flipped,
            CardStory story, String strength, int screenWidth, int screenHeight) {
        told = story == null ? CardStory.NONE : story;
        overwritten = strength;
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, OVER_ITEMS);
        try {
            drawFullScreen(graphics, summary, foil, flipped, screenWidth, screenHeight);
        } finally {
            graphics.pose().popPose();
            told = CardStory.NONE;
            overwritten = null;
        }
    }

    private static void drawFullScreen(
            GuiGraphics graphics, CardSummary summary, boolean foil, boolean flipped,
            int screenWidth, int screenHeight) {
        GatheringSprites.draw(graphics, Element.INSPECT_BACKDROP,
                0, 0, screenWidth, screenHeight);

        InspectLayout layout = InspectLayout.of(screenWidth, screenHeight);
        Rect card = layout.card();
        Rect words = layout.text();
        if (card.isEmpty()) {
            return;
        }
        drawFace(graphics, summary.sideShown(flipped), Holding.inspected(foil, grainOf(summary)),
                card.x(), card.y(), card.width(), card.height(), true);
        drawTextPanel(graphics, summary.faces(), words.x(), words.y(), words.width(), words.height());
    }

    /**
     * A number to sparkle by: this printing and no other.
     * <p>Taken from the Scryfall id, so a foil glitters the same way every time it is picked
     * up and two foils on the same table do not glitter alike. It is not a secret and it is
     * not sent anywhere - the client already holds it, because it is what it fetched the
     * picture with.
     */
    private static long grainOf(CardSummary summary) {
        return summary.scryfallId().getMostSignificantBits()
                ^ summary.scryfallId().getLeastSignificantBits();
    }

    /**
     * How a particular copy of a card is being held: foil or not, and turned how far.
     * <p>Carried rather than read from {@link CardTilt} where the card is drawn, because most
     * of the cards this class draws are rows in a list and a list that leaned every time
     * somebody inspected something else would be a list with a fault in it. Only the two
     * screens that are actually about one card ask for {@link #inspected}; everything else
     * takes {@link #FLAT} and is drawn exactly as it always was.
     */
    private record Holding(
            boolean foil, long grain, float yaw, float pitch, float shineX, float shineY) {

        /** A card lying flat with nothing special about it. */
        static final Holding FLAT = new Holding(false, 0L, 0f, 0f, 0f, 0f);

        /** The card somebody is looking at, turned however they are holding it. */
        static Holding inspected(boolean foil, long grain) {
            return new Holding(foil, grain, CardTilt.yaw(), CardTilt.pitch(),
                    CardTilt.shine(), CardTilt.shineDown());
        }
    }

    /** A printed face lying flat, with no shine on it. For every list of cards. */
    private static void drawFace(
            GuiGraphics graphics, CardFaceSummary face, int x, int y, int width, int height) {
        drawFace(graphics, face, Holding.FLAT, x, y, width, height);
    }

    /**
     * A printed face, turned the way it is being held, with its shine if it is a foil.
     * <p><b>The only place a foil is ever drawn.</b> The shine goes on inside the branch that
     * has just been handed a printed face's texture, so there is no arrangement of callers
     * that can put one on anything else: a sleeve is a different texture drawn by different
     * code with no path here, and a card whose art has not arrived gets the placeholder below
     * and no shine, because a sheen over a "fetching" box is a shine on a box.
     * <p>Both printed sides of a double-faced card arrive here, so turning one over shows a
     * foil on its back too. That is a face rather than a sleeve, and it was foil when it was
     * printed.
     */
    private static void drawFace(
            GuiGraphics graphics, CardFaceSummary face, Holding held,
            int x, int y, int width, int height) {
        drawFace(graphics, face, held, x, y, width, height, false);
    }

    /**
     * The same, saying whether this card may have the crisp tier fetched for it.
     * <p>Only a card being read - the full-screen one, and the panel that follows the cursor -
     * ever asks. Reported from a real session: "GUI mode when you zoom in gets extremely
     * laggy, like almost crashes." The tier was chosen by how large the card was being drawn
     * and by nothing else, so zooming a board in past the threshold asked for a three-megabyte
     * texture for every permanent on it at once, one upload each, mid-frame. The comment
     * beside the tier already said it was meant for "the read overlay and the pack, and
     * nothing on a board" - it just had no way to tell which it was in.
     */
    private static void drawFace(
            GuiGraphics graphics, CardFaceSummary face, Holding held,
            int x, int y, int width, int height, boolean beingRead) {
        // The best picture there is once the card is being drawn larger than the ordinary
        // tier's own resolution, and the ordinary one everywhere else. Reported as "the mod
        // pretty much exclusively uses the low quality scryfall image pull": it read 488x680
        // everywhere, which is right for a card an inch tall on a board and wrong for one
        // filling the window, where it was being upscaled past itself and the rules text went
        // soft exactly when somebody was trying to read it.
        //
        // Decided by the size rather than by a setting, so nobody has to know the tiers exist
        // - and only by the size, because a board of sixty permanents at the crisp tier is
        // sixty textures four times the area for no gain at all.
        boolean crisp = beingRead && height >= CRISP_ABOVE;
        Optional<String> url = crisp ? face.bestImage() : face.readableImage();
        Optional<ResourceLocation> texture = url.flatMap(ClientCardImages.get()::texture);
        if (texture.isEmpty() && crisp) {
            // The crisp one has not arrived yet. Show the ordinary one rather than a
            // "fetching" box over a card this client can already draw: the swap when it lands
            // is a card getting sharper, which is a better half-second than an empty frame.
            texture = face.readableImage().flatMap(ClientCardImages.get()::texture);
        }

        if (texture.isPresent()) {
            TiltedFace.draw(graphics, texture.get(), new Rect(x, y, width, height),
                    held.yaw(), held.pitch(), held.foil(), held.grain(),
                    held.shineX(), held.shineY());
            return;
        }

        // No art yet. Say which, rather than showing an empty rectangle that looks broken.
        GatheringSprites.draw(graphics, Element.CARD_PLACEHOLDER, x, y, width, height);
        Font font = Minecraft.getInstance().font;
        // Fitted to the box, not drawn at whatever width the sentence happens to be. A row of
        // small cards with no art came out as four sentences overlapping each other and the
        // cards either side, which reads as the interface being broken rather than as the art
        // not having arrived.
        int room = Math.max(1, width - PADDING * 2);
        GuiText.drawCentered(graphics, font, Component.literal(face.name()),
                x + width / 2, y + PADDING, room, TEXT);

        boolean stillTrying = url.isPresent() && !ClientCardImages.get().hasFailed(url.get());
        Component message = url.isEmpty()
                ? Component.translatable("overlay.gathering.no_image")
                : ClientCardImages.get().hasFailed(url.get())
                        ? Component.translatable("overlay.gathering.image_unavailable")
                        : Component.translatable("overlay.gathering.fetching_image");
        // Only if the whole of it fits. Half a sentence in a box the size of a postage stamp
        // says less than the empty box does, and the card's own name is already there.
        int lines = GuiText.linesNeeded(font, message, room);
        int needs = lines * (font.lineHeight + 1);
        // The ring goes above the words, and only while there is still a fetch to be waiting
        // on: a sentence cannot tell anybody whether it is still trying, and "Fetching art..."
        // sitting still is the same picture as "Fetching art..." having given up. It gets the
        // room first, and is left out entirely on a card too small to hold both.
        boolean turning = stillTrying
                && GatheringSprites.SPINNER + 2 + needs <= height - PADDING * 2 - font.lineHeight
                && GatheringSprites.SPINNER <= room;
        if (turning) {
            needs += GatheringSprites.SPINNER + 2;
        }
        if (needs <= height - PADDING * 2 - font.lineHeight) {
            int top = y + (height - needs) / 2;
            if (turning) {
                GatheringSprites.spinner(graphics, x, top, width, GatheringSprites.SPINNER);
                top += GatheringSprites.SPINNER + 2;
            }
            for (var row : font.split(message, room)) {
                // Drawn as the wrapped sequence rather than turned back into a string:
                // FormattedCharSequence has no readable toString, and the one it does have
                // would have put an object identity on the card.
                graphics.drawString(font, row, x + (width - font.width(row)) / 2, top,
                        DIM_TEXT, false);
                top += font.lineHeight + 1;
            }
        }
    }

    /**
     * Everything the card says, in a panel beside it.
     * <p>The mod's own panel rather than a filled rectangle with a line round it. Every other
     * box in the interface is drawn with this one, and the full-window read is the largest
     * thing on screen when it is up - a box that looked like nothing else in the mod would be
     * the most visible thing that did.
     */
    private static void drawTextPanel(
            GuiGraphics graphics, List<CardFaceSummary> faces, int x, int y, int width, int height) {
        GatheringSprites.panel(graphics, x, y, width, height);

        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(1, width - PADDING * 2);
        int textHeight = Math.max(1, height - PADDING * 2);
        drawFitted(graphics, font, faces, fitFor(font, faces, textWidth, textHeight),
                x + PADDING, y + PADDING, textWidth, textHeight);
    }

    /**
     * Everything a card says, fitted to a box and drawn there whole: columns flowed the way
     * {@link CardTextFit} measured them, at the size it chose, the credit pinned to the bottom.
     */
    private static void drawFitted(GuiGraphics graphics, Font font, List<CardFaceSummary> faces,
            CardTextFit fit, int x, int y, int width, int height) {
        float scale = fit.scale();
        List<Line> lines = describe(font, faces, fit.columnWrap(), fit.withStory());
        int[] heights = heightsOf(font, lines);
        int[] starts = CardTextFit.flow(heights, fit.capacity(), fit.columns());
        if (starts.length == 0) {
            // Only when no arrangement holds all of it - a window too small for any text. The
            // most that fits, from the top.
            starts = new int[] {0};
        }
        int across = (width - CardTextFit.COLUMN_GAP * (fit.columns() - 1)) / fit.columns();
        for (int column = 0; column < starts.length; column++) {
            int end = column + 1 < starts.length ? starts[column + 1] : lines.size();
            drawScaled(graphics, font, lines.subList(starts[column], end),
                    x + column * (across + CardTextFit.COLUMN_GAP), y, scale, fit.capacity());
        }

        List<Line> credit = credit(font, fit.creditWrap());
        int creditHeight = 0;
        for (int line : heightsOf(font, credit)) {
            creditHeight += line;
        }
        drawScaled(graphics, font, credit,
                x, y + height - CardTextFit.scaled(creditHeight, scale), scale, Integer.MAX_VALUE);
    }

    /**
     * The fit for this card's text in a box this size, remembered.
     * <p>Finding it wraps the whole text once per size tried, and the panel beside the cursor
     * asks about several boxes every frame. The answer only changes with the card, the box and
     * the text size, so it is kept for as long as those stay put.
     */
    private static CardTextFit fitFor(Font font, List<CardFaceSummary> faces, int width, int height) {
        float asked = GuiText.askedScale();
        FitKey key = new FitKey(faces, told, overwritten, width, height, asked,
                Minecraft.getInstance().getLanguageManager().getSelected());
        CardTextFit known = FITS.get(key);
        if (known != null) {
            return known;
        }
        CardTextFit fit = CardTextFit.of(width, height, asked, new CardTextFit.Measure() {
            @Override
            public int[] lines(int wrapWidth, boolean withStory) {
                return heightsOf(font, describe(font, faces, wrapWidth, withStory));
            }

            @Override
            public int[] credit(int wrapWidth) {
                return heightsOf(font, CardInspectPanel.credit(font, wrapWidth));
            }
        });
        FITS.put(key, fit);
        return fit;
    }

    private record FitKey(List<CardFaceSummary> faces, CardStory story, String strength,
            int width, int height, float scale, String language) {
    }

    /** A few frames' worth: the panel beside the cursor tries up to about eight boxes. */
    private static final int FITS_KEPT = 32;

    // statecheck: arithmetic about card text and box sizes, keyed by all of it, never a world's
    private static final java.util.Map<FitKey, CardTextFit> FITS =
            new java.util.LinkedHashMap<>(FITS_KEPT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<FitKey, CardTextFit> eldest) {
                    return size() > FITS_KEPT;
                }
            };

    /**
     * Everything worth saying about a card, wrapped to a width.
     * <p>Measured up front so a panel that has to size itself around the text can ask how
     * tall it will be before drawing any of it.
     */
    private static List<Line> describe(
            Font font, List<CardFaceSummary> faces, int width, boolean withStory) {
        List<Line> lines = new ArrayList<>();
        for (int index = 0; index < faces.size(); index++) {
            CardFaceSummary face = faces.get(index);
            if (index > 0) {
                rule(lines);
            }
            wrap(lines, font, header(face), width, TEXT);
            wrap(lines, font, face.typeLine(), width, DIM_TEXT);
            rule(lines);
            wrap(lines, font, face.oracleText(), width, TEXT);
            strengthLine(lines, font, face, width);
        }
        if (withStory) {
            tell(lines, font, width);
        }
        return lines;
    }

    /**
     * The power and toughness, under the rules text, where a card prints it.
     * <p>Carried on the wire summary beside the name, cost, type line and rules text, without
     * which a player reading a creature sees everything except the number the combat turns on.
     * <p>What somebody wrote over the top wins and says so, because that is the number the
     * table is playing with. Nothing is drawn for a card with neither, which is most of them.
     */
    private static void strengthLine(List<Line> lines, Font font, CardFaceSummary face, int width) {
        String written = overwritten;
        boolean changed = written != null && !written.isBlank();
        String strength = changed ? written : face.strength();
        if (strength.isEmpty()) {
            return;
        }
        rule(lines);
        wrap(lines, font, strength, width, changed ? STORY_TEXT : TEXT);
    }

    /**
     * A power and toughness a player has written over the printed one, while it is being drawn.
     * <p>Held the way {@link #told} is and for the same reason: the method that builds these
     * lines is also how the panel measures itself, and it is reached from callers that know
     * about a card summary and nothing about a card in play. Set on the way in and cleared on
     * the way out, on the one thread that draws.
     */
    private static String overwritten;

    /**
     * Which card's history the panel is drawing, for as long as it is drawing it.
     * <p>Held here rather than threaded through {@code describe} because that method is also
     * how a panel measures itself, and it is reached from half a dozen places that know
     * nothing about stories. Set on the way in and cleared on the way out, on the one thread
     * that draws - so a card with no story can never be handed the last one's.
     */
    private static CardStory told = CardStory.NONE;

    /** Where a card has been, under everything it says. */
    private static void tell(List<Line> lines, Font font, int width) {
        if (told.isEmpty()) {
            return;
        }
        rule(lines);
        wrap(lines, font,
                Component.translatable("story.gathering.heading").getString(), width, STORY_TEXT);
        if (told.hasGaps()) {
            // Said rather than swallowed: a card that changed hands twenty times and shows
            // three of them is a card whose history has a hole in it, and a hole nobody is
            // told about is a lie about where it has been.
            wrap(lines, font,
                    Component.translatable("story.gathering.and_more", told.forgotten()).getString(),
                    width, DIM_TEXT);
        }
        for (CardStory.Chapter chapter : told.chapters()) {
            wrap(lines, font, sentenceFor(chapter), width, DIM_TEXT);
        }
    }

    /**
     * A card's history as the lines somebody reads, oldest first, with the gap said where chapters
     * were forgotten. For the card overview, which lists it rather than fitting it under a panel.
     */
    public static List<String> historyOf(CardStory story) {
        if (story == null || story.isEmpty()) {
            return List.of();
        }
        List<String> said = new java.util.ArrayList<>();
        if (story.hasGaps()) {
            said.add(Component.translatable("story.gathering.and_more", story.forgotten()).getString());
        }
        for (CardStory.Chapter chapter : story.chapters()) {
            said.add(sentenceFor(chapter));
        }
        return List.copyOf(said);
    }

    /** One chapter, as a line somebody reads. Every word of it translated. */
    private static String sentenceFor(CardStory.Chapter chapter) {
        Component said = chapter.how().hasSomebodyBefore() && !chapter.from().isEmpty()
                ? Component.translatable(chapter.how().translationKey() + ".from",
                        chapter.who(), chapter.from())
                : Component.translatable(chapter.how().translationKey(),
                        chapter.who(), chapter.what());
        return chapter.day().isEmpty()
                ? said.getString()
                : Component.translatable("story.gathering.on_day", said, chapter.day()).getString();
    }

    /** The color a card's history is written in: warm, because somebody did all of it. */
    private static final int STORY_TEXT = 0xFFD9A441;

    /**
     * The Scryfall credit, which every panel showing card data carries.
     * <p>Kept out of {@link #describe} and pinned to the bottom of the text area by each
     * caller, with its height reserved. Flowed in with the oracle text it would be the first
     * thing a wordy card pushes off the end of the panel, and the one line here that is not
     * allowed to be optional is this one.
     */
    private static List<Line> credit(Font font, int width) {
        List<Line> lines = new ArrayList<>();
        wrap(lines, font, Gathering.SCRYFALL_ATTRIBUTION, width, DIM_TEXT);
        return lines;
    }

    /** A gap, unless one is already there - two rules in a row is just a bigger hole. */
    private static void rule(List<Line> into) {
        if (!into.isEmpty() && into.get(into.size() - 1).text() != null) {
            into.add(Line.rule());
        }
    }

    private static void wrap(List<Line> into, Font font, String text, int width, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String paragraph : text.split("\n")) {
            // Through ManaText, so {T} and {1}{W} arrive as symbols - and, because they are
            // just styled characters, wrap and measure like any other text.
            for (FormattedCharSequence sequence : font.split(ManaText.of(paragraph), width)) {
                into.add(new Line(sequence, color));
            }
        }
    }

    private static int[] heightsOf(Font font, List<Line> lines) {
        int[] heights = new int[lines.size()];
        for (int index = 0; index < heights.length; index++) {
            heights[index] = lines.get(index).height(font);
        }
        return heights;
    }

    /**
     * Lines drawn down from a point at one size.
     * <p>The bottom is in the same unscaled units the lines are measured in, and only ever
     * reached when no fit holds the whole text.
     */
    private static void drawScaled(GuiGraphics graphics, Font font, List<Line> lines,
            int x, int y, float scale, int bottom) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        int line = 0;
        for (Line item : lines) {
            int height = item.height(font);
            if (line + height > bottom) {
                break;
            }
            if (item.text() != null) {
                graphics.drawString(font, item.text(), 0, line, item.color(), false);
            }
            line += height;
        }
        graphics.pose().popPose();
    }

    private static String header(CardFaceSummary face) {
        return face.manaCost().isEmpty() ? face.name() : face.name() + "   " + face.manaCost();
    }

    /** One drawn line, or - with no text - the gap that separates two blocks of them. */
    private record Line(FormattedCharSequence text, int color) {

        private static final int RULE_HEIGHT = 4;

        static Line rule() {
            return new Line(null, 0);
        }

        int height(Font font) {
            return text == null ? RULE_HEIGHT : font.lineHeight + 1;
        }
    }

    /**
     * Writes a card's note across the top of the art it is drawn on.
     * <p>One method rather than one per screen. The felt draws notes and so does every pile
     * screen opened off it, and a note that read one way on the table and another way in a
     * graveyard would be two features wearing the same name. It was two copies until one of
     * them grew a guard about slots too short to write in and the other did not.
     * <p>At the top because counters go along the bottom and the two must not fight over the
     * same band: a card with three counters and a note is a card somebody is keeping careful
     * track of, which is exactly when both have to be readable at once.
     */
    public static void drawNote(GuiGraphics graphics, Font font, String note, Rect art) {
        if (note == null || note.isBlank()) {
            return;
        }
        // Against the card rather than against the screen - see CardText. A note pinned to
        // the font's own size grew against the card as the board was zoomed out, until it was
        // a band of letters with a picture behind it; below the floor nothing is written at
        // all, which is the part of the old rule worth keeping.
        if (!CardText.worthDrawing(art.height(), CardText.NOTE, font.lineHeight)) {
            return;
        }
        float scale = CardText.scaleFor(art.height(), CardText.NOTE, font.lineHeight);
        int line = CardText.lineAt(scale, font.lineHeight);
        GatheringSprites.draw(graphics, Element.NAME_BACKDROP,
                art.x(), art.y() + 1, art.width(), line);
        GuiText.drawTrimmedAt(graphics, font, Component.literal(note),
                art.x() + 2, art.y() + 2, art.width() - 4,
                Math.round(LEAST_NOTE_WIDTH * scale), scale, WRITING_TEXT);
    }

    /** Narrower than this and a note is an ellipsis, so nothing is drawn at all. */
    private static final int LEAST_NOTE_WIDTH = 22;

    /**
     * How tall a card has to be drawn before it is worth the crisp tier.
     * <p>The ordinary tier is 680 pixels tall, so anything drawn under about a third of that
     * is throwing detail away rather than missing it. Above this the card is being scaled up
     * toward its own resolution and past it, which is where the softness the players
     * reported actually lives - the read overlay and the pack, and nothing on a board.
     */
    private static final int CRISP_ABOVE = 220;

    private static final int STRENGTH_TEXT = 0xFFFFE6B0;

    /**
     * The power and toughness somebody wrote on it, in the corner where the printed ones are.
     * <p>Where the card already puts them, so a board reads the same printed or written - and
     * here rather than in either screen, so the same card in a graveyard and on the felt cannot
     * come to look like different features. Nothing is worked out: what is drawn is exactly
     * what somebody typed. See {@link dev.gathering.core.game.CardStrength}, and section 16.
     *
     * @return the line anything stacking up the card may start from, which is above this
     *     when there is something written and the card's own bottom edge when there is not
     */
    public static int drawStrength(GuiGraphics graphics, Font font, String strength, Rect art) {
        int floor = art.bottom() - 2;
        if (strength == null || strength.isBlank() || art.height() < font.lineHeight + 4) {
            return floor;
        }
        Component numbers = Component.literal(strength);
        int room = Math.max(1, art.width() - 4);
        if (!StrengthBadge.fitsOn(room)) {
            // A card drawn this small has nowhere to put numbers, and a badge wider than the
            // card is worse than none.
            return floor;
        }
        // Measured from the text after it has been fitted rather than clamped to the card and
        // left to be overrun - which is what "the box does not render large enough to contain
        // the numbers" was. See StrengthBadge, where the rule lives and is checked.
        StrengthBadge.Fit badge = StrengthBadge.of(font.width(numbers), font.lineHeight, room);
        int left = art.right() - 2 - badge.width();
        int top = floor - badge.height();
        GatheringSprites.draw(graphics, Element.STRENGTH_BADGE,
                left, top, badge.width(), badge.height());
        GuiText.drawCenteredAt(graphics, font, numbers, left + badge.textX(),
                top + badge.textY(), badge.scale(), STRENGTH_TEXT);
        return top - 1;
    }
}
