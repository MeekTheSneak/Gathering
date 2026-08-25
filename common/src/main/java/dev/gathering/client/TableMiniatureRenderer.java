package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.CommandSlots;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.Shaking;
import dev.gathering.core.ui.SeatColour;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableTop;
import dev.gathering.core.ui.TableVerb;
import dev.gathering.core.table.TableCluster;
import dev.gathering.item.CardComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The game, laid out small on the table it is being played on.
 *
 * <p>This is what makes a table in a world worth more than a menu: you can see from across
 * the room that a game is happening and roughly how it is going, and you can walk over and
 * watch. It draws the <b>public</b> board and nothing else - the spectator view, the same one
 * anybody standing at the table would get - so a face-down card is a card back here for the
 * same reason it is one on the table it is modelled on.
 *
 * <p><b>The same board the screen draws, through the same arithmetic.</b> This used to squash
 * each seat's region into a band of its own, which was a second layout: a card sat in one
 * place on your screen and somewhere else on the block, and the two drifted every time either
 * changed. Both now go through {@link TableSurface} - the same mats, in the same places, with
 * cards the same size relative to them - so the block is a small copy of the screen rather
 * than an impression of it. That is worth having on its own, and it is what makes moving the
 * board onto the block itself a change of camera rather than a third layout.
 *
 * <p>Client-only.
 */
public class TableMiniatureRenderer implements BlockEntityRenderer<TableBlockEntity> {

    /**
     * Where a card sits on a mat, in surface units, asked of the same rule the seated board
     * asks - so the two views cannot come to different answers about the same card.
     *
     * <p>Kept rather than built per frame, and rebuilt only when the table changes shape.
     */
    private SurfaceBoard onBlock;
    private int onBlockSeats = -1;

    private SurfaceBoard placementFor(int seats) {
        if (onBlock == null || onBlockSeats != seats) {
            onBlock = new SurfaceBoard(TableCluster.assumedSeating(seats));
            onBlockSeats = seats;
        }
        return onBlock;
    }

    /**
     * Where the surface is, taken from {@link TableTop} rather than restated here.
     *
     * <p>The same numbers decide what a player is pointing at when they reach onto the table,
     * and a picture measured from one corner while the pointing is measured from another is
     * a table where cards are not where they look.
     */
    private static final float SURFACE_Y = (float) TableTop.SURFACE_HEIGHT;

    /** And cards just above the mats, so a mat never z-fights the board on top of it. */
    private static final float CARD_Y = SURFACE_Y + 0.0008f;

    private static final float MARGIN = (float) TableTop.MARGIN;

    /** Nothing on a table is worth a draw call past this. */
    private static final int MAX_CARDS = 256;

    /**
     * How far above the one below each card of a pile sits, in blocks.
     *
     * <p>Two jobs. It makes a pile look like a pile from across the room, and it keeps two
     * cards on the same spot off exactly the same plane - coplanar quads z-fight, and a pile
     * of four flickering in the middle of somebody's board is worse than not drawing it.
     */
    private static final float STACK_LIFT = 0.0015f;


    /** The line round a group of zones, and how thick it is drawn. */
    private static final int GROUP_EDGE_COLOUR = 0x66FFFFFF;
    /**
     * As a share of the box's narrow side, which is a zone's width and not its height.
     *
     * <p>Taken from the wrong side first: two per cent of a box half a block deep is a line
     * three pixels wide, and two per cent of one an eighth of a block across is a line most
     * of a pixel wide, which rounds to nothing on most rows and to one on the rest.
     */
    private static final float GROUP_EDGE_THICKNESS = 0.08f;

    /** An empty zone, and the line round it. Dark, because it is a recess in the mat. */
    private static final int SLOT_COLOUR = 0x55000000;
    private static final int SLOT_EDGE_COLOUR = 0x99000000;

    /** And the same zone with a card being held over it, waiting to be let go. */
    private static final int SLOT_AIMED = 0x664FA4CF;

    private static final float SLOT_EDGE_THICKNESS = 0.07f;

    /** The card on a zone sits just above the slot it is in, so the two do not z-fight. */
    private static final float SLOT_LIFT = 0.0006f;

    /** Zone names and counts, just clear of the sleeve drawn in the slot. */
    private static final float WRITING_LIFT = 0.0012f;

    /**
     * How tall a line of writing is, in surface units, so it scales with the mat.
     *
     * <p>Surface units run to {@link TableSurface#SPAN} across the whole table and a card is
     * a tenth of that, so this is about an eighth of a card's width - the same relation a
     * zone name has to a card on a printed playmat.
     */
    private static final double WRITING_HEIGHT = TableSurface.CARD_WIDTH_UNITS / 8.0;

    /** The patch a count is written on, so it reads over a card's art rather than into it. */
    private static final int COUNT_BACKING = 0xC0000000;

    private static final int WRITING_COLOUR = 0xFFE8E4DC;

    /** How much of a slot's width a line of writing may take up. */
    private static final float WRITING_ROOM = 0.86f;

    /** The halo under the card the cursor is on, and how far it sticks out past it. */
    private static final int RING_COLOUR = 0xCC7FD4FF;
    private static final float RING_THICKNESS = 0.09f;

    /** Just under the card, so the halo shows around the edges and not through the art. */
    private static final float RING_DROP = 0.0003f;

    /** A taken seat's mat, and the darker line around it. Read from above, in a lit room. */
    private static final int MAT_COLOUR = 0x30000000;

    /** The wash over the mat a card in the air would land on. */
    private static final int MAT_LANDING = 0x334FA4CF;

    /**
     * How solid a mat's border is drawn.
     *
     * <p>The border carries the seat's own colour, which is the whole of how four boards laid
     * out on one surface are told apart. Everything else about them is identical.
     */
    private static final int MAT_EDGE_ALPHA = 0xCC;

    /** And how solid a free chair's is: there, and clearly not a board in play. */
    private static final int FREE_SEAT_ALPHA = 0x44;

    /** How thick the line around a mat is, as a fraction of the mat's shorter side. */
    private static final float MAT_EDGE_THICKNESS = 0.035f;

    public TableMiniatureRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            TableBlockEntity table, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = table.getBlockPos();
        GameView board = ClientTableState.viewOf(pos).orElse(null);
        if (board == null || board.seats().isEmpty()) {
            return;
        }

        // The one moment the projection the world was drawn with is still set. The seated
        // view has no use for it; the view that plays on this block cannot work out what the
        // cursor is over without it.
        TablePointer.capture(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera());

        TableSurface surface = TableSurface.forSeatCount(board.seats().size());
        SurfaceBoard placement = placementFor(board.seats().size());
        float span = (float) TableTop.SPAN_BLOCKS;

        poseStack.pushPose();
        poseStack.translate(MARGIN, SURFACE_Y, MARGIN);

        // Mats first and all of them, then the cards. A mat is drawn for a seat somebody has
        // actually taken: a playmat appearing when a player sits down is how a table shows
        // that it now has a game in it, and an empty seat's mat would say the opposite.
        for (int index = 0; index < board.seats().size(); index++) {
            // A board rather than an occupant: a seat somebody walked away from still holds
            // their cards, and a mat carrying a library and a graveyard is a board.
            boolean taken = board.seats().get(index).hasABoard();
            // A free chair keeps its outline and loses everything else - the same answer the
            // seated screen gives, because the two are the same board.
            drawMat(poseStack, buffers, surface.matOf(index), span,
                    SeatColour.at(index, taken ? MAT_EDGE_ALPHA : FREE_SEAT_ALPHA), taken,
                    taken && ClientTableHighlight.isLandingOn(board.seats().get(index).seat()));
            if (taken) {
                // The same buttons the seated board prints, in the same places, because they
                // are the same mat. A player who learns where their untap button is in one
                // view has learned where it is in the other.
                drawVerbs(poseStack, buffers, packedLight, surface,
                        board.seats().get(index).seat(), index, span);
                // The line marking off the row nearest its player, where lands go. On the mat
                // rather than above it: it is a marking printed on the felt, not a thing
                // sitting on top of the felt.
                Rect divider = surface.matDivider(index, Zone.pilesFor(table.hasCommandZone()));
                if (!divider.isEmpty()) {
                    flat(buffers.getBuffer(RenderType.debugQuads()), poseStack.last().pose(),
                            onSurface(divider.x(), span), onSurface(divider.y(), span),
                            onSurface(divider.right(), span), onSurface(divider.bottom(), span),
                            GROUP_EDGE_COLOUR);
                }
            }
        }
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(MARGIN, CARD_Y, MARGIN);
        int piles = Zone.pilesFor(table.hasCommandZone());
        for (int index = 0; index < board.seats().size(); index++) {
            // Same rule as the mats: a seat nobody has taken shows nothing at all. Drawing
            // its zones but not its mat left four empty boxes floating on bare felt, which
            // reads as a fault rather than as a free chair.
            if (board.seats().get(index).hasABoard()) {
                drawPiles(poseStack, buffers, packedLight, board.seats().get(index),
                        surface, pos, index, span, piles);
                drawLife(poseStack, buffers, packedLight, board.seats().get(index),
                        surface, index, span);
            }
        }
        int drawn = 0;
        for (int index = 0; index < board.seats().size() && drawn < MAX_CARDS; index++) {
            drawn += drawSeat(poseStack, buffers, packedLight, board.seats().get(index),
                    surface, placement, index, span, MAX_CARDS - drawn);
        }
        drawFlights(poseStack, buffers, packedLight, board, placement, pos, piles, span);
        poseStack.popPose();
    }

    /**
     * The cards crossing this table on their way somewhere.
     *
     * <p>The same flights the seated board draws, from the same two places - a flight is kept
     * as where it is going from and to rather than as a pair of rectangles, so the one that is
     * pixels on a window is a place on the felt here without either view knowing about the
     * other.
     *
     * <p>Above the cards lying on the table rather than among them, because a card in the air
     * is in the air. Everyone watching sees it, seated or not: that is the point.
     */
    private void drawFlights(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, GameView board,
            SurfaceBoard placement, BlockPos table, int piles, float span) {
        long now = ClientCardFlights.now();
        for (ClientCardFlights.Flight flight : ClientCardFlights.at(table, now)) {
            Rect where = FlightPath.at(placement, table, piles, flight, now);
            if (where.isEmpty()) {
                continue;
            }
            CardView card = flight.move().card()
                    .flatMap(id -> cardIn(board, id))
                    .orElse(null);
            ResourceLocation texture = card == null || card.isFaceDown()
                    ? CardFaceRenderer.CARD_BACK
                    : textureFor(card);
            draw(poseStack, buffers, packedLight, texture,
                    onSurface(where.x(), span), onSurface(where.y(), span),
                    onSurface(where.width(), span), onSurface(where.height(), span),
                    surface(board).facingDegrees(flight.move().to().seat().index()),
                    false, IN_THE_AIR);
        }
    }

    /**
     * A card in the air sits above everything lying on the table, including a stack.
     *
     * <p>One step above the deepest a stack is ever drawn, which is what
     * {@link TableStacking#shownDepth(int)} answers - not the deepest a stack can be, which
     * has no limit.
     */
    private static final float IN_THE_AIR = STACK_LIFT * (TableStacking.MAX_DEPTH + 1);

    private static TableSurface surface(GameView board) {
        return TableSurface.forSeatCount(board.seats().size());
    }

    /** This card as this client knows it, wherever on the board it currently is. */
    private static java.util.Optional<CardView> cardIn(
            GameView board, dev.gathering.core.game.CardInstanceId id) {
        for (SeatView seat : board.seats()) {
            for (Zone zone : Zone.values()) {
                ZoneView contents = seat.zones().get(zone);
                if (contents == null) {
                    continue;
                }
                for (CardView card : contents.cards()) {
                    if (card instanceof CardView.Visible visible && visible.id().equals(id)) {
                        return java.util.Optional.of(card);
                    }
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * One seat's playmat: a tinted rectangle with a darker line around it.
     *
     * <p>Four quads for the line rather than an outline draw, because a line width in world
     * space is a pixel width on screen and a mat you can only see the edge of from two blocks
     * away is not an edge.
     */
    private void drawMat(
            PoseStack poseStack, MultiBufferSource buffers, Rect mat, float span, int border,
            boolean filled, boolean landing) {
        if (mat.isEmpty()) {
            return;
        }
        float left = onSurface(mat.x(), span);
        float top = onSurface(mat.y(), span);
        float right = onSurface(mat.right(), span);
        float bottom = onSurface(mat.bottom(), span);
        float edge = Math.min(right - left, bottom - top) * MAT_EDGE_THICKNESS;

        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();

        if (filled) {
            flat(consumer, pose, left, top, right, bottom, MAT_COLOUR);
        }
        if (landing) {
            // The side of the table a card in the air would come down on. Lit across the
            // whole mat rather than only where a zone is, because most of a mat is felt and
            // dropping a card on felt is most of what anybody does with one.
            flat(consumer, pose, left, top, right, bottom, MAT_LANDING);
        }
        flat(consumer, pose, left, top, right, top + edge, border);
        flat(consumer, pose, left, bottom - edge, right, bottom, border);
        flat(consumer, pose, left, top, left + edge, bottom, border);
        flat(consumer, pose, right - edge, top, right, bottom, border);
    }

    /**
     * The run of verb buttons printed down a seat's own mat.
     *
     * <p>Drawn, not clickable here: the board on the block is pointed at with a ray and the
     * screen that owns that ray is the one that listens. What this has to do is make sure the
     * player can see there is something to point at.
     */
    private void drawVerbs(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            TableSurface surface, SeatId seat, int seatIndex, float span) {
        int count = TableVerb.count();
        drawGroup(poseStack, buffers, surface.verbGroup(seatIndex, count), span);
        float lineHeight = onSurface(WRITING_HEIGHT, span);
        for (int index = 0; index < count; index++) {
            Rect slot = surface.verbSlot(seatIndex, index, count);
            if (slot.isEmpty()) {
                continue;
            }
            float x = onSurface(slot.x(), span);
            float z = onSurface(slot.y(), span);
            float width = onSurface(slot.width(), span);
            float depth = onSurface(slot.height(), span);
            drawSlot(poseStack, buffers, x, z, width, depth,
                    ClientTableHighlight.isPointedAtVerb(seat, index));
            writing(poseStack, buffers, packedLight,
                    Component.translatable(TableVerb.values()[index].key()),
                    x + width / 2f, z + depth / 2f, lineHeight, width * WRITING_ROOM,
                    surface.facingDegrees(seatIndex), 0);
        }
    }

    /**
     * A seat's zones, in a column down the outer edge of its own mat.
     *
     * <p>The same ones in the same places as the seated screen puts them, because they are the
     * same zones: a player who learns where their graveyard is in one view has learned where
     * it is in the other. A pile shows its top card if the whole table is entitled to see it,
     * and a sleeve if not - which for a library is always.
     *
     * <p>Two boxes: the three a hand is in and out of all game, and the command zone past a
     * gap. A format with no commanders draws three and no second box.
     */
    private void drawPiles(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, SeatView seat,
            TableSurface surface, BlockPos table, int seatIndex, float span, int count) {
        drawGroup(poseStack, buffers,
                surface.pileGroup(seatIndex, 0, Zone.PILES_WITHOUT_A_COMMAND_ZONE - 1, count), span);
        if (count > Zone.PILES_WITHOUT_A_COMMAND_ZONE) {
            drawGroup(poseStack, buffers, surface.pileGroup(
                    seatIndex, Zone.PILES_WITHOUT_A_COMMAND_ZONE, count - 1, count), span);
        }
        for (int index = 0; index < count; index++) {
            Rect slot = surface.pileSlot(seatIndex, index, count);
            if (slot.isEmpty()) {
                continue;
            }
            // A pile somebody has just shuffled rattles where it stands. Nothing about a
            // shuffle shows on the board - see ClientTableNews - so this is the only account
            // of it anybody watching the table itself gets.
            slot = shakenIfStirred(table, seat.seat(), Zone.PILES.get(index), slot);
            float x = onSurface(slot.x(), span);
            float z = onSurface(slot.y(), span);
            float width = onSurface(slot.width(), span);
            float depth = onSurface(slot.height(), span);

            // The slot is drawn whether or not there is anything in it. A zone you can only
            // see once it has cards in it is a zone nobody can aim at, and aiming at it is now
            // how cards get put there.
            boolean aimed = ClientTableHighlight.isAimedAt(seat.seat(), index);
            drawSlot(poseStack, buffers, x, z, width, depth, aimed);

            ZoneView contents = seat.zones().get(Zone.PILES.get(index));
            int held = contents == null ? 0 : showing(contents);
            int angle = surface.facingDegrees(seatIndex);
            if (held > 0) {
                CardView top = topOf(contents);
                ResourceLocation texture = top == null || top.isFaceDown()
                        ? CardFaceRenderer.CARD_BACK
                        : textureFor(top);
                draw(poseStack, buffers, packedLight, texture, x, z, width, depth,
                        angle, false, SLOT_LIFT);
            }

            // The board played on the block said nothing about which box was which or how
            // much was in any of them, so the one view meant for playing was the one view a
            // player could not read their own deck size off. Written on the felt rather than
            // floated over it: it is a marking on the mat, like the line the lands sit behind.
            Rect named = surface.pileLabel(seatIndex, index, count);
            // The count is written at the same size as the name beside it. It used to take
            // its size from a constant that came out about half as tall, which made the one
            // number a player reads most often - how much library they have left - the
            // smallest thing on the board.
            float lineHeight = onSurface(surface.pileCountHeight(seatIndex, index, count), span);
            // A command slot holds one commander, so a number counting cards there says "1"
            // all game. It says that commander's tax instead - the number a Commander deck
            // actually reads off that box - written in the band a press on it lands in, so
            // the two views agree about where the number is as well as what it says.
            Rect taxBand = CommandSlots.commanderIn(seat, Zone.PILES.get(index)) == null
                    ? Rect.NONE
                    : TableSurface.taxBand(slot);
            if (taxBand.isEmpty()) {
                // The count sits over whatever card is showing there, so it is written on a
                // dark patch rather than straight onto the art - a white number over a pale
                // card is a number nobody can read.
                writing(poseStack, buffers, packedLight,
                        Component.literal(Integer.toString(held)),
                        x + width / 2f, z + lineHeight * 0.6f,
                        lineHeight, width * WRITING_ROOM, angle, held > 0 ? COUNT_BACKING : 0);
            } else {
                CardInstanceId commander =
                        CommandSlots.commanderIn(seat, Zone.PILES.get(index));
                int tax = CommandSlots.taxFor(seat.commanderTax().getOrDefault(commander, 0));
                // Mirrored on the way out. The block lays the surface down with its y axis
                // running the other way from the screen's, which is why a count written a
                // line below the top of a slot in surface units comes out along the foot of
                // it here - and why a band measured against the surface's bottom edge came
                // out across the top of the card. Measured from the far edge instead, the
                // tax lands at the same end of the slot as every count beside it.
                float bandZ = z + onSurface(slot.bottom() - taxBand.centreY(), span);
                // Written to the band rather than to a count's line, because that is what it
                // has: a count is a badge in a corner and gets a corner's worth of room, and
                // a tax is a band across the whole slot. Given the count's height it came out
                // half the height of its own backing, which on a table seen from across the
                // room is the difference between a number and a smudge.
                writing(poseStack, buffers, packedLight, Component.literal("+" + tax),
                        x + width / 2f, bandZ, onSurface(taxBand.height(), span),
                        onSurface(taxBand.width(), span) * WRITING_ROOM, angle, COUNT_BACKING);
            }
            // The name goes on the felt beside it, in the space the seated board writes it
            // in, so the two views read the same.
            if (!named.isEmpty()) {
                // Flush against the slot column, the same way the seated board writes them,
                // so a name sits the same distance from the box it names however short the
                // word is. Which side the column is on is read off the two rectangles.
                Component zoneName = ZoneText.name(Zone.PILES.get(index));
                float nameHeight = onSurface(named.height(), span);
                float room = onSurface(named.width(), span);
                float half = writtenWidth(zoneName, nameHeight, room) / 2f;
                float middle = named.x() < slot.x()
                        ? onSurface(named.right(), span) - half
                        : onSurface(named.x(), span) + half;
                writing(poseStack, buffers, packedLight, zoneName,
                        middle, onSurface(named.centreY(), span), nameHeight, room, angle, 0);
            }
        }
    }

    /**
     * A pile rattling because it has just been shuffled, in surface units.
     *
     * <p>The same shake the seated board draws, from the same clock, so the two views agree
     * about which library is being shuffled and for how long.
     */
    private Rect shakenIfStirred(BlockPos table, SeatId seat, Zone zone, Rect slot) {
        long shaking = ClientTableNews.shakingFor(table, seat, zone, ClientCardFlights.now());
        if (shaking < 0) {
            return slot;
        }
        int reach = Math.max(1, slot.width() / SHAKE_OF_A_SLOT);
        int seed = seat.index() * Zone.values().length + zone.ordinal();
        return new Rect(
                slot.x() + Shaking.wobble(seed, shaking, reach),
                slot.y() + Shaking.wobble(seed + 7, shaking, reach),
                slot.width(), slot.height());
    }

    /** How far a shuffled pile rattles, as a fraction of its own width. */
    private static final int SHAKE_OF_A_SLOT = 8;

    /**
     * How far a line of writing shrinks to fit the room it has.
     *
     * <p>A zone name is longer than a zone is wide, so the line shrinks rather than running
     * out over the felt and off the edge of the mat. It shrinks as far as it has to, unlike
     * the seated board, which drops a name it cannot write whole: this writing is in the
     * world, so a player who cannot read it can walk towards it.
     */
    private static float writingScale(Component text, float lineHeight, float maxWidth) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int drawn = font.width(text);
        return drawn <= 0 ? 0f : Math.min(lineHeight / font.lineHeight, maxWidth / drawn);
    }

    /** How wide that line comes out, in surface units, so a caller can put an end of it. */
    private static float writtenWidth(Component text, float lineHeight, float maxWidth) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        return font.width(text) * writingScale(text, lineHeight, maxWidth);
    }

    /**
     * A line of writing lying flat on the surface, centred on a point and turned to face the
     * seat it belongs to.
     *
     * <p>The font draws into the XY plane facing the camera, so it is tipped a quarter turn
     * about X to lie down with its face to the sky, and scaled from font pixels into surface
     * units so a label keeps its size against the mat rather than against the screen.
     */
    private void writing(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Component text, float centreX, float centreZ, float lineHeight, float maxWidth,
            int angle, int backing) {
        if (lineHeight <= 0f) {
            return;
        }
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int drawn = font.width(text);
        if (drawn <= 0) {
            return;
        }
        float scale = writingScale(text, lineHeight, maxWidth);
        poseStack.pushPose();
        poseStack.translate(centreX, WRITING_LIFT, centreZ);
        if (Math.floorMod(angle, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-angle));
        }
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, -drawn / 2f, -font.lineHeight / 2f, WRITING_COLOUR,
                false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL,
                backing, packedLight);
        poseStack.popPose();
    }

    /**
     * The card showing on top of a pile: the first one not currently following somebody's
     * cursor, or nothing when the client was sent no cards for this pile at all.
     *
     * <p>Nothing is not the same as empty. A library's cards are sent to nobody, so an empty
     * list there means a face-down stack - which is why the caller counts separately.
     */
    private static CardView topOf(ZoneView contents) {
        for (CardView card : contents.cards()) {
            if (!(card instanceof CardView.Visible visible)
                    || !ClientTableHighlight.isInTheAir(visible.id())) {
                return card;
            }
        }
        return null;
    }

    /**
     * How many cards a pile has that are not in the air, which is what a viewer counts.
     *
     * <p>Stops at the first card anybody could have picked up. Only the top card of a pile can
     * be lifted off it, so once the first one this client was sent has been looked at there is
     * nothing further down that could be following a cursor - and scanning a fifty-card
     * graveyard every frame to find that out is a scan for an answer already known.
     */
    private static int showing(ZoneView contents) {
        for (CardView card : contents.cards()) {
            if (card instanceof CardView.Visible visible) {
                return ClientTableHighlight.isInTheAir(visible.id())
                        ? contents.count() - 1
                        : contents.count();
            }
        }
        return contents.count();
    }

    /**
     * The line round a group of zones: a marking on the mat, drawn as four thin quads.
     *
     * <p>Empty in the middle. The slots inside it draw their own recesses, and a filled box
     * behind them would put a second shade of dark under every zone.
     */
    /**
     * A seat's life total, on the table past the far edge of its own board.
     *
     * <p>The same box the seated screen draws, in the same place, because it is the same
     * table - and pressed through the same screen, which casts its ray at this board and
     * finds the box by the arithmetic that put it here.
     */
    private void drawLife(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            SeatView seat, TableSurface surface, int seatIndex, float span) {
        Rect box = surface.lifeBox(seatIndex);
        if (box.isEmpty()) {
            return;
        }
        float left = onSurface(box.x(), span);
        float top = onSurface(box.y(), span);
        float right = onSurface(box.right(), span);
        float bottom = onSurface(box.bottom(), span);
        flat(buffers.getBuffer(RenderType.debugQuads()), poseStack.last().pose(),
                left, top, right, bottom, LIFE_BACKING);
        drawGroup(poseStack, buffers, box, span);
        // Turned to face its own player, like everything else printed for one seat, so both
        // players read their own total the right way up.
        int angle = surface.facingDegrees(seatIndex);
        float lineHeight = (bottom - top) * LIFE_WRITING;
        float across = right - left;
        // Kept for the number itself; the ends are placed from the shared rule below.
        // In what the two ends leave, from the same rule the seated board uses.
        Rect middle = TableSurface.lifeMiddle(box);
        writing(poseStack, buffers, packedLight,
                Component.literal(Integer.toString(seat.life())),
                onSurface(middle.centreX(), span), (top + bottom) / 2f, lineHeight,
                onSurface(middle.width(), span), angle, 0);
        // The same minus and plus the seated board prints on the ends, because the ends are
        // buttons here too - the screen casts its ray at this board and presses them. A pair
        // of buttons marked in one view and bare in the other is a pair nobody finds twice.
        //
        // Which end is which comes from the same function the press does. Written out here
        // instead, from this board's own idea of which way round the mat is, it disagreed
        // with the press for every seat facing the other way: the sign was turned round with
        // the mat and the press was not, so the end marked plus took a life off.
        // The same question the screen asks before it decides which end a press means. This
        // board turns each seat's own writing rather than the felt, so it says so.
        boolean turned = surface.lifeIsTurned(seatIndex, false);
        drawLifeEnd(poseStack, buffers, packedLight, box, turned, -1, lineHeight, angle, span);
        drawLifeEnd(poseStack, buffers, packedLight, box, turned, 1, lineHeight, angle, span);
    }

    private void drawLifeEnd(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Rect box, boolean turned, int way, float lineHeight, int angle, float span) {
        Rect end = TableSurface.lifeEnd(box, turned, way);
        if (end.isEmpty()) {
            return;
        }
        writing(poseStack, buffers, packedLight, Component.literal(way < 0 ? "-" : "+"),
                onSurface(end.centreX(), span), onSurface(end.centreY(), span),
                lineHeight, onSurface(end.width(), span) * LIFE_END_WRITING, angle, 0);
    }

    /** What a life total is written on, so it reads against the table rather than into it. */
    private static final int LIFE_BACKING = 0xC0101418;

    /** How much of its box a life total is written at, leaving room for the two ends. */
    private static final float LIFE_WRITING = 0.7f;

    /** How much of an end's room its sign is allowed, so it clears the number beside it. */
    private static final float LIFE_END_WRITING = 0.5f;

    private void drawGroup(
            PoseStack poseStack, MultiBufferSource buffers, Rect group, float span) {
        if (group.isEmpty()) {
            return;
        }
        float left = onSurface(group.x(), span);
        float top = onSurface(group.y(), span);
        float right = onSurface(group.right(), span);
        float bottom = onSurface(group.bottom(), span);
        float edge = Math.min(right - left, bottom - top) * GROUP_EDGE_THICKNESS;

        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();
        flat(consumer, pose, left, top, right, top + edge, GROUP_EDGE_COLOUR);
        flat(consumer, pose, left, bottom - edge, right, bottom, GROUP_EDGE_COLOUR);
        flat(consumer, pose, left, top, left + edge, bottom, GROUP_EDGE_COLOUR);
        flat(consumer, pose, right - edge, top, right, bottom, GROUP_EDGE_COLOUR);
    }

    /**
     * An empty zone: a recess in the mat with a border, the shape of the card that goes in it.
     *
     * <p>Four quads for the border rather than an outline draw, for the same reason the mats
     * use them - a line width in world space is a pixel width on screen, and a zone you can
     * only make out with your face against the table is not marked.
     */
    private void drawSlot(
            PoseStack poseStack, MultiBufferSource buffers,
            float x, float z, float width, float depth, boolean aimed) {
        float edge = Math.min(width, depth) * SLOT_EDGE_THICKNESS;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();
        int border = aimed ? RING_COLOUR : SLOT_EDGE_COLOUR;

        flat(consumer, pose, x, z, x + width, z + depth, aimed ? SLOT_AIMED : SLOT_COLOUR);
        flat(consumer, pose, x, z, x + width, z + edge, border);
        flat(consumer, pose, x, z + depth - edge, x + width, z + depth, border);
        flat(consumer, pose, x, z, x + edge, z + depth, border);
        flat(consumer, pose, x + width - edge, z, x + width, z + depth, border);
    }

    /**
     * One seat's permanents, on its own mat.
     *
     * <p>Laid out where their owner actually put them, at the size the screen draws them
     * relative to the mat. A board somebody has arranged into lands at the back and creatures
     * at the front reads as that from across the room, which is the entire point of the block
     * showing anything at all.
     */
    private int drawSeat(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, SeatView seat,
            TableSurface surface, SurfaceBoard placement, int seatIndex, float span, int budget) {
        List<CardView> cards = seat.zone(Zone.BATTLEFIELD).cards();
        if (cards.isEmpty()) {
            return 0;
        }
        float cardWidth = (float) (surface.cardWidthOn(seatIndex) / TableSurface.SPAN * span);
        float cardDepth = (float) (surface.cardHeightOn(seatIndex) / TableSurface.SPAN * span);
        if (cardWidth <= 0f || cardDepth <= 0f) {
            return 0;
        }

        List<TablePosition> spots = new ArrayList<>(cards.size());
        for (CardView card : cards) {
            spots.add(card.placedAt().orElse(null));
        }
        List<Integer> depths = TableStacking.depths(spots);

        int drawn = 0;
        for (int index = 0; index < cards.size() && drawn < budget; index++) {
            CardView card = cards.get(index);
            if (card instanceof CardView.Visible inHand
                    && ClientTableHighlight.isInTheAir(inHand.id())) {
                // Following somebody's cursor. It has not moved yet - the server has not been
                // told - so the board still lists it here, and drawing it would leave a copy
                // lying on the felt while its twin follows the cursor.
                continue;
            }
            // A card with no position of its own is one the game has not put down yet; it is
            // still somebody's permanent, so it goes at the corner of the mat rather than
            // nowhere.
            TablePosition where = card.placedAt().orElse(TablePosition.ORIGIN);

            // Two things at once, and both are needed. The lift keeps cards on the same spot
            // off the same plane, because coplanar quads z-fight and a pile of four flickering
            // in the middle of a board is worse than not drawing it. The lean is what makes a
            // pile read as a pile from directly above, where a stack that only went upwards
            // would be one card.
            float lean = onSurface(
                    TableStacking.offsetFor(depths.get(index), (int) surface.cardWidthOn(seatIndex)),
                    span);
            // A position is the card's middle, not its corner - see BoardPlacement - and
            // draw() is given a corner. Adding half a card without taking half a card off
            // first put every card on this board half a card down and right of where the
            // seated board draws the same card, so a permanent sitting on the edge of a mat
            // in one view was off the mat in the other.
            Rect placed = placement.rectOf(seat.seat(), where);
            float x = onSurface(placed.x(), span) + lean;
            float z = onSurface(placed.y(), span) + lean;
            float lift = TableStacking.shownDepth(depths.get(index)) * STACK_LIFT;

            if (card instanceof CardView.Visible visible && ClientTableHighlight.isLit(visible.id())) {
                // Under the card rather than over it: a ring drawn on top would cover the art
                // it is pointing at, and a card is a picture before it is a token.
                drawRing(poseStack, buffers, x, z, cardWidth, cardDepth,
                        where.rotation() + surface.facingDegrees(seatIndex), isTapped(card), lift);
            }
            // Turned with the board, so a card lying face up in front of its owner reads the
            // right way up to them and upside down from the chair opposite - which is what a
            // card on a table between two people does.
            draw(poseStack, buffers, packedLight, textureFor(card), x, z, cardWidth, cardDepth,
                    where.rotation() + surface.facingDegrees(seatIndex), isTapped(card), lift);
            drawn++;
        }
        return drawn;
    }

    /**
     * A halo just larger than a card, marking the one the cursor is on.
     *
     * <p>Turned with the card, so an angled card gets an angled ring rather than a square one
     * that gives away that the two are drawn by different code.
     */
    private void drawRing(
            PoseStack poseStack, MultiBufferSource buffers, float x, float z,
            float width, float depth, int angle, boolean tapped, float lift) {
        float grow = Math.max(width, depth) * RING_THICKNESS;
        poseStack.pushPose();
        poseStack.translate(x + width / 2f, lift - RING_DROP, z + depth / 2f);
        int turned = angle + (tapped ? TablePosition.QUARTER_TURN : 0);
        if (Math.floorMod(turned, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-turned));
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        flat(consumer, poseStack.last().pose(),
                -width / 2f - grow, -depth / 2f - grow,
                width / 2f + grow, depth / 2f + grow, RING_COLOUR);
        poseStack.popPose();
    }

    /** A point on the shared surface, in blocks across the table's own footprint. */
    private static float onSurface(double surfaceUnits, float span) {
        return (float) (surfaceUnits / TableSurface.SPAN * span);
    }

    /**
     * One card, lying on the surface at whatever angle it was left at.
     *
     * <p>A tapped card gets its quarter turn on top of that angle, which is the whole reason
     * tapping is legible across a table in paper - and why it has to be a turn here too rather
     * than a tint nobody can see from six blocks away.
     *
     * <p>The turn is negated because a card turned clockwise on the seated screen has to look
     * turned clockwise from above, and looking down at the surface flips the sense of a
     * rotation about the vertical.
     */
    private void draw(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            ResourceLocation texture, float x, float z, float width, float depth,
            int angle, boolean tapped, float lift) {
        poseStack.pushPose();
        poseStack.translate(x + width / 2f, lift, z + depth / 2f);
        int turned = angle + (tapped ? TablePosition.QUARTER_TURN : 0);
        if (Math.floorMod(turned, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-turned));
        }

        float halfWidth = width / 2f;
        float halfDepth = depth / 2f;
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));

        // Wound anticlockwise seen from above, so the face points at the sky.
        vertex(consumer, pose, -halfWidth, -halfDepth, 0f, 0f, packedLight);
        vertex(consumer, pose, -halfWidth, halfDepth, 0f, 1f, packedLight);
        vertex(consumer, pose, halfWidth, halfDepth, 1f, 1f, packedLight);
        vertex(consumer, pose, halfWidth, -halfDepth, 1f, 0f, packedLight);

        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer consumer, Matrix4f pose, float x, float z, float u, float v, int light) {
        consumer.addVertex(pose, x, 0f, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0f, 1f, 0f);
    }

    /** A flat coloured rectangle on the surface, wound to face the sky. */
    private static void flat(
            VertexConsumer consumer, Matrix4f pose,
            float left, float top, float right, float bottom, int argb) {
        consumer.addVertex(pose, left, 0f, top).setColor(argb);
        consumer.addVertex(pose, left, 0f, bottom).setColor(argb);
        consumer.addVertex(pose, right, 0f, bottom).setColor(argb);
        consumer.addVertex(pose, right, 0f, top).setColor(argb);
    }

    /**
     * What a card looks like from here.
     *
     * <p>The small tier, because this is a miniature and the overlay is where a card is read.
     * A card whose art has not arrived, and every anonymous one, is a card back - which for
     * the anonymous ones is not a fallback but the correct picture.
     */
    private static ResourceLocation textureFor(CardView card) {
        if (!(card instanceof CardView.Visible visible)) {
            return CardFaceRenderer.CARD_BACK;
        }
        Optional<dev.gathering.network.CardSummary> summary =
                ClientCardCache.get().summary(CardComponent.of(visible.identity()));
        return summary
                .map(found -> found.front().smallImage())
                .filter(url -> !url.isEmpty())
                .flatMap(url -> ClientCardImages.get().texture(url))
                .orElse(CardFaceRenderer.CARD_BACK);
    }

    private static boolean isTapped(CardView card) {
        return switch (card) {
            case CardView.Visible visible -> visible.tapped();
            case CardView.Anonymous anonymous -> anonymous.tapped();
        };
    }
}
