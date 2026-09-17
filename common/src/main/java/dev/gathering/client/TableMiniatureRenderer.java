package dev.gathering.client;

import dev.gathering.core.ui.CounterText;
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
import dev.gathering.core.ui.FlatLayers;
import dev.gathering.core.ui.PileThickness;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.Shaking;
import dev.gathering.core.ui.SeatColor;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.BoardPresentation;
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
 * <p>What makes a table in a world worth more than a menu: you can see from across the room
 * that a game is happening and roughly how it is going, and walk over and watch. It draws the
 * <b>public</b> board and nothing else - the spectator view - so a face-down card is a card
 * back here for the reason it is one on the table it is modeled on.
 * <p><b>The same board the screen draws, through the same arithmetic.</b> Both go through
 * {@link TableSurface}: the same mats in the same places, cards the same size relative to
 * them. A second layout drifts every time either side changes, and it is this that makes
 * playing on the block a change of camera rather than a third layout.
 * <p>Client-only.
 */
public class TableMiniatureRenderer implements BlockEntityRenderer<TableBlockEntity> {

    /**
     * Piles, attachments and ids for the boards this draws, once per view rather than per
     * frame. Several, because one renderer draws every table in sight each frame and a cache
     * of one would be rebuilt for each in turn.
     */
    private final BoardPresentation.Memo presentations = new BoardPresentation.Memo(8);

    /**
     * Where a card sits on a mat, in surface units, asked of the same rule the seated board
     * asks - so the two views cannot come to different answers about the same card.
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
     * <p>The same numbers decide what a player is pointing at when they reach onto the table,
     * and a picture measured from one corner while the pointing is measured from another is
     * a table where cards are not where they look.
     */
    private static final float SURFACE_Y = (float) TableTop.SURFACE_HEIGHT;

    /**
     * And cards above everything printed on the mats, so a mat never z-fights the board on top of
     * it: this many steps up, one clear of the highest thing a mat has on it.
     */
    private static final int CARDS_ABOVE_THE_MAT = 8;

    private static final float MARGIN = (float) TableTop.MARGIN;

    /** Nothing on a table is worth a draw call past this. */
    private static final int MAX_CARDS = 256;

    /**
     * How far above the one below each card of a pile sits, in blocks, at the least.
     * <p>Two jobs. It makes a pile look like a pile from across the room, and it keeps two
     * cards on the same spot off exactly the same plane - coplanar quads z-fight, and a pile
     * of four flickering in the middle of somebody's board is worse than not drawing it. Seen
     * from further off it is more, by {@link FlatLayers#perCard}, so everything drawn on one
     * card stays under the next.
     */
    private static final float CARD_THICKNESS = 0.0015f;


    /** The line round a group of zones, and how thick it is drawn. */
    private static final int GROUP_EDGE_COLOR = 0x66FFFFFF;
    /**
     * As a share of the box's narrow side, which is a zone's width and not its height.
     * <p>Taken from the wrong side first: two per cent of a box half a block deep is a line
     * three pixels wide, and two per cent of one an eighth of a block across is a line most
     * of a pixel wide, which rounds to nothing on most rows and to one on the rest.
     */
    private static final float GROUP_EDGE_THICKNESS = 0.08f;

    /** An empty zone, and the line round it. Dark, because it is a recess in the mat. */
    private static final int SLOT_COLOR = 0x55000000;
    private static final int SLOT_EDGE_COLOR = 0x99000000;

    /** And the same zone with a card being held over it, waiting to be let go. */
    private static final int SLOT_AIMED = 0x664FA4CF;

    private static final float SLOT_EDGE_THICKNESS = 0.07f;

    /**
     * The foot of a pile, in steps: one over the slot's edge. Under it, a single card lying in a
     * slot seen from across the room went under the slot's dark recess.
     */
    private static final int ON_A_SLOT = 7;

    /** The gray sleeve every colored one is a tint of. One file; see {@link CardSleeves}. */
    private static final ResourceLocation PLAIN_SLEEVE = ResourceLocation.fromNamespaceAndPath(
            dev.gathering.Gathering.MOD_ID, "textures/card/sleeve.png");

    /** How much of a card the picture on a sleeve takes. The same fraction the screen uses. */
    private static final float EMBLEM_SPAN = 0.44f;

    /** Writing lying on the felt of the mats: a step over a button's edge. */
    private static final int ON_THE_FELT = 7;

    /**
     * How tall a line of writing is, in surface units, so it scales with the mat.
     * <p>Surface units run to {@link TableSurface#SPAN} across the whole table and a card is
     * a tenth of that, so this is about an eighth of a card's width - the same relation a
     * zone name has to a card on a printed playmat.
     */
    private static final double WRITING_HEIGHT = TableSurface.CARD_WIDTH_UNITS / 8.0;

    /** The patch a count is written on, so it reads over a card's art rather than into it. */
    private static final int COUNT_BACKING = 0xC0000000;

    private static final int WRITING_COLOR = 0xFFE8E4DC;

    /** And writing and lines drawn straight onto a light felt, where the light ones would not show. */
    private static final int WRITING_ON_LIGHT_FELT = 0xFF2B2622;
    private static final int GROUP_EDGE_ON_LIGHT_FELT = 0x77000000;

    /**
     * The colors for what is written and ruled straight onto the felt of the table being drawn: light
     * on a dark felt and dark on a light one. See {@link dev.gathering.core.ui.FeltContrast}.
     */
    private int feltWriting = WRITING_COLOR;
    private int feltEdge = GROUP_EDGE_COLOR;

    /** How much of a slot's width a line of writing may take up. */
    private static final float WRITING_ROOM = 0.86f;

    /** The halo under the card the cursor is on, and how far it sticks out past it. */
    private static final int RING_COLOR = 0xCC7FD4FF;
    private static final float RING_THICKNESS = 0.09f;

    /**
     * One step between two things lying flat on each other, in blocks, for the table being drawn.
     * <p>Small enough that nothing looks lifted off the felt and large enough that the depth
     * buffer never has to choose - which depends on how far away the table is, so it is worked
     * out per table per frame by {@link FlatLayers}. Every flat marking names the layer it is on
     * in these steps. It was a fixed ten-thousandth of a block, and from a few blocks away the
     * mat's buttons and the cards stacked on it flickered.
     */
    private float step = (float) FlatLayers.NEAREST_STEP;

    /** This many steps up. */
    private float layer(int steps) {
        return step * steps;
    }

    /** The felt of a mat, clear of the block's own top face. */
    private static final int MAT_FELT = 1;

    /** The wash over the side of the table a card is coming down on. */
    private static final int MAT_WASH = 2;

    /** The mat's border, over both. */
    private static final int MAT_BORDER = 3;

    /** And anything printed on the felt over that, like the line marking off the land row. */
    private static final int MAT_MARKING = 4;

    /**
     * A slot's recess, over all of the mat's own layers: a button down a mat lies on its felt,
     * and the two on one plane were the buttons flickering.
     */
    private static final int SLOT_RECESS = 5;

    /** And the slot's edge, over its recess. */
    private static final int SLOT_EDGE = 6;

    /** A taken seat's mat, and the darker line around it. Read from above, in a lit room. */
    private static final int MAT_COLOR = 0x30000000;

    /** The wash over the mat a card in the air would land on. */
    private static final int MAT_LANDING = 0x334FA4CF;

    /**
     * How solid a mat's border is drawn.
     * <p>The border carries the seat's own color, which is the whole of how four boards laid
     * out on one surface are told apart. Everything else about them is identical.
     */
    private static final int MAT_EDGE_ALPHA = 0xCC;

    /** And how solid a free chair's is: there, and clearly not a board in play. */
    private static final int FREE_SEAT_ALPHA = 0x44;

    /** How thick the line around a mat is, as a fraction of the mat's shorter side. */
    private static final float MAT_EDGE_THICKNESS = 0.035f;

    public TableMiniatureRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * How far from its own block this renderer draws, so the board is not culled away.
     * <p>The default is the block's own cube; the board is drawn across the whole cluster
     * from the corner block that holds the session. Zoomed right in, the corner block leaves
     * the frustum while the middle of the board fills the window, and the whole board vanishes
     * with it.
     * <p>Sized to the largest cluster rather than this one, both ways from the corner. It is
     * asked during culling, so a generous box costs nothing and a box from the live board
     * would be a question put to the session mid-cull.
     * <p>Not an {@code @Override}: this is NeoForge's own extension to the renderer interface
     * and the same class is loaded on Fabric, where nothing calls it and vanilla culls by
     * chunk section instead.
     * <p>The parameter is a {@code BlockEntity} because that is what NeoForge's generic method is
     * after erasure, and the JVM matches the whole signature, not the name. This class is compiled
     * without NeoForge, so nothing generates the bridge that a {@code TableBlockEntity} parameter
     * would need: written that way it was never called, and the board was culled with its corner
     * block all along. The pack scene asks through NeoForge's interface to keep it so.
     */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(net.minecraft.world.level.block.entity.BlockEntity table) {
        int reach = TableCluster.MAX_TABLES * dev.gathering.core.table.TableCell.BLOCKS_PER_TABLE;
        return new net.minecraft.world.phys.AABB(table.getBlockPos()).inflate(reach, 1, reach);
    }

    @Override
    public void render(
            TableBlockEntity table, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = table.getBlockPos();
        boolean inTheWorld = table.getLevel() == net.minecraft.client.Minecraft.getInstance().level;
        if (table.eventTable() > 0) {
            drawEventLabel(table, poseStack, buffers, packedLight, inTheWorld);
        }
        if (!inTheWorld) {
            // A table drawn somewhere other than the world being played - a Ponder scene, a Create
            // schematic's preview. What is known about tables is known by position in that world, so
            // here it would be some real table's game drawn on a stranger, and the capture below would
            // leave the picker with a projection the world was never drawn with.
            return;
        }
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
        step = (float) FlatLayers.step(distanceToTheFarSide(pos, span));

        // A turned table - a line running north to south, or a lone table seating east and west - has
        // its board laid out the usual way and turned a quarter clockwise onto its blocks: the same
        // turn TableTop makes for the pointer and the camera, so what is drawn is what is pointed at.
        boolean turned = dev.gathering.block.TableClusters.at(table.getLevel(), pos).turned();
        boolean lightFelt = dev.gathering.core.ui.FeltContrast.isLight(
                table.felt().map(dye -> dye.getTextureDiffuseColor() & 0xFFFFFF).orElse(TableColors.UNDYED));
        feltWriting = lightFelt ? WRITING_ON_LIGHT_FELT : WRITING_COLOR;
        feltEdge = lightFelt ? GROUP_EDGE_ON_LIGHT_FELT : GROUP_EDGE_COLOR;
        if (turned) {
            turnedBoardsDrawn++;
        }

        poseStack.pushPose();
        onTheSurface(poseStack, SURFACE_Y, span, turned);

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
                    SeatColor.at(index, taken ? MAT_EDGE_ALPHA : FREE_SEAT_ALPHA), taken,
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
                            feltEdge, layer(MAT_MARKING));
                }
            }
        }
        poseStack.popPose();

        poseStack.pushPose();
        onTheSurface(poseStack, SURFACE_Y + layer(CARDS_ABOVE_THE_MAT), span, turned);
        int piles = Zone.pilesFor(table.hasCommandZone());
        tallestPile = 0f;
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
        BoardPresentation shown = presentations.of(board);
        for (int index = 0; index < board.seats().size() && drawn < MAX_CARDS; index++) {
            drawn += drawSeat(poseStack, buffers, packedLight, board.seats().get(index),
                    shown.mats().get(index), surface, placement, index, span, MAX_CARDS - drawn);
        }
        drawFlights(poseStack, buffers, packedLight, board, placement, pos, piles, span);
        poseStack.popPose();
    }

    /** How many times a turned table's board has been drawn. For the scripted harness. */
    static int turnedBoardsDrawn;

    /**
     * Moves the pose onto the table's surface at this height, with the surface's x along the pose's x
     * and its y along the pose's z - turned a quarter clockwise for a turned table, whose surface x
     * runs south and whose surface y runs west from its north-east corner.
     */
    private static void onTheSurface(PoseStack poseStack, float height, float span, boolean turned) {
        if (!turned) {
            poseStack.translate(MARGIN, height, MARGIN);
            return;
        }
        // One table deep across the turn, which is the table's own span.
        poseStack.translate(MARGIN + span, height, MARGIN);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90f));
    }

    /**
     * The cards crossing this table on their way somewhere.
     * <p>The same flights the seated board draws, from the same two places - a flight is kept
     * as where it is going from and to rather than as a pair of rectangles, so the one that is
     * pixels on a window is a place on the felt here without either view knowing about the
     * other.
     * <p>Above the cards lying on the table rather than among them, because a card in the air
     * is in the air. Everyone watching sees it, seated or not: that is the point.
     */
    private void drawFlights(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, GameView board,
            SurfaceBoard placement, BlockPos table, int piles, float span) {
        long now = ClientCardFlights.now();
        for (ClientCardFlights.Flight flight : ClientCardFlights.at(table, now)) {
            Rect where = FlightPath.at(placement, piles, flight, now);
            if (where.isEmpty()) {
                continue;
            }
            CardView card = flight.move().card()
                    .flatMap(id -> cardIn(board, id))
                    .orElse(null);
            drawSleeved(poseStack, buffers, packedLight, card,
                    CardSleeves.of(board, flight.move().to().seat()),
                    onSurface(where.x(), span), onSurface(where.y(), span),
                    onSurface(where.width(), span), onSurface(where.height(), span),
                    surface(board).facingDegrees(flight.move().to().seat().index()),
                    // Over the tallest pile rather than through it: a card coming off the top of
                    // a sixty-card library leaves from the top.
                    false, inTheAir() + tallestPile);
        }
    }

    /**
     * A card in the air sits above everything lying on the table, including a stack: this far over
     * the tallest thing on the table being drawn, which {@link #tallestPile} keeps.
     */
    private float inTheAir() {
        return perCard() + layer(ON_A_SLOT + PILE_TOP_LAYERS + 1);
    }

    /** How many layers a pile's top card has over it: its sleeve's picture, two rings, a backing, a count. */
    private static final int PILE_TOP_LAYERS = 5;

    /** How far over the card under it a card in a stack sits, for the table being drawn. */
    private float perCard() {
        return (float) FlatLayers.perCard(step, CARD_THICKNESS);
    }

    /**
     * How far the camera is from the far side of the table being drawn, in blocks: the part of
     * it the depth buffer has least precision for.
     */
    private static double distanceToTheFarSide(BlockPos table, float span) {
        net.minecraft.world.phys.Vec3 eye = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera().getPosition();
        double middleX = table.getX() + MARGIN + span / 2.0;
        double middleZ = table.getZ() + MARGIN + span / 2.0;
        double middleY = table.getY() + SURFACE_Y;
        double toTheMiddle = Math.sqrt(eye.distanceToSqr(middleX, middleY, middleZ));
        return toTheMiddle + span * Math.sqrt(0.5);
    }

    /** The tallest pile on the table being drawn, so a card in the air clears it. */
    private float tallestPile;

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

        // Off the block's own top face first of all, then a layer each. The felt, the wash
        // over the side a card is coming down on, and the border are three things lying on
        // one another, and on one plane the depth buffer chose between them per frame.
        if (filled) {
            flat(consumer, pose, left, top, right, bottom, MAT_COLOR, layer(MAT_FELT));
        }
        if (landing) {
            // The side of the table a card in the air would come down on. Lit across the
            // whole mat rather than only where a zone is, because most of a mat is felt and
            // dropping a card on felt is most of what anybody does with one.
            flat(consumer, pose, left, top, right, bottom, MAT_LANDING, layer(MAT_WASH));
        }
        frame(consumer, pose, left, top, right, bottom, edge, border, layer(MAT_BORDER));
    }

    /**
     * The four button names, built once.
     * <p>This is the block entity renderer: it runs for every table in the world, for every seated
     * mat on it, on every frame. A fresh component per button per seat per table per frame is a lot
     * of garbage for four words that never change - and the table screen already keeps exactly this
     * array, for exactly this reason.
     */
    private static final Component[] VERB_NAMES = buildVerbNames();

    /** And the verbs themselves, because {@code values()} clones the array on every call. */
    private static final TableVerb[] VERBS = TableVerb.values();

    private static Component[] buildVerbNames() {
        TableVerb[] verbs = TableVerb.values();
        Component[] names = new Component[verbs.length];
        for (int index = 0; index < verbs.length; index++) {
            names[index] = Component.translatable(verbs[index].key());
        }
        return names;
    }

    /**
     * The run of verb buttons printed down a seat's own mat.
     * <p>Drawn, not clickable here: the board on the block is pointed at with a ray and the
     * screen that owns that ray is the one that listens. What this has to do is make sure the
     * player can see there is something to point at.
     */
    private void drawVerbs(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            TableSurface surface, SeatId seat, int seatIndex, float span) {
        int count = VERBS.length;
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
                    VERB_NAMES[index],
                    x + width / 2f, z + depth / 2f, lineHeight, width * WRITING_ROOM,
                    surface.facingDegrees(seatIndex), 0, layer(ON_THE_FELT), feltWriting);
        }
    }

    /**
     * A seat's zones, in a column down the outer edge of its own mat.
     * <p>The same ones in the same places as the seated screen, so learning where a graveyard
     * is in one view learns it in the other. A pile shows its top card where the whole table
     * is entitled to see it and a sleeve if not - which for a library is always.
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
            // As tall as the cards in it, so a deck stands on the table like a deck and a
            // graveyard of three is a few cards thick. How many is already written on it; the
            // height says roughly the same thing to somebody across the room.
            float standing = onSurface((float) pileHeight(contents, slot), span);
            tallestPile = Math.max(tallestPile, standing);
            if (held > 0) {
                drawPileSides(poseStack, buffers, packedLight, seat.sleeve(), held,
                        x, z, width, depth, angle, standing);
                CardView top = topOf(contents);
                drawSleeved(poseStack, buffers, packedLight, top, seat.sleeve(),
                        x, z, width, depth, angle, false, layer(ON_A_SLOT) + standing);
                if (aimed) {
                    // The recess under a pile lights up when a card is held over it, and a tall
                    // pile covers its own recess - so the top of the pile lights up as well.
                    drawAimedTop(poseStack, buffers, x, z, width, depth, layer(ON_A_SLOT) + standing);
                }
            }

            // The glow a pile gets when cards land in it, the same one the seated board draws,
            // around the top of the pile so a tall one does not hide it.
            float arrived = dev.gathering.core.ui.Arrival.strength(
                    ClientCardFlights.arrivedSince(table, seat.seat(), Zone.PILES.get(index), ClientCardFlights.now()),
                    ClientSettings.reducedMotion());
            if (arrived > 0f) {
                drawArrival(poseStack, buffers, x, z, width, depth, layer(ON_A_SLOT) + standing,
                        (dev.gathering.core.ui.Arrival.alpha(arrived) << 24)
                                | (SeatColor.at(seatIndex, 0xFF) & 0x00FFFFFF));
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
                        lineHeight, width * WRITING_ROOM, angle, held > 0 ? COUNT_BACKING : 0,
                        layer(ON_A_SLOT + PILE_TOP_LAYERS - 1) + standing);
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
                float bandZ = z + onSurface(slot.bottom() - taxBand.centerY(), span);
                // Written to the band rather than to a count's line, because that is what it
                // has: a count is a badge in a corner and gets a corner's worth of room, and
                // a tax is a band across the whole slot. Given the count's height it came out
                // half the height of its own backing, which on a table seen from across the
                // room is the difference between a number and a smudge.
                writing(poseStack, buffers, packedLight, Component.literal("+" + tax),
                        x + width / 2f, bandZ, onSurface(taxBand.height(), span),
                        onSurface(taxBand.width(), span) * WRITING_ROOM, angle, COUNT_BACKING,
                        layer(ON_A_SLOT + PILE_TOP_LAYERS - 1) + standing);
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
                        middle, onSurface(named.centerY(), span), nameHeight, room, angle, 0, layer(ON_THE_FELT), feltWriting);
            }
        }
    }

    /**
     * A pile rattling because it has just been shuffled, in surface units.
     * <p>The same shake the seated board draws, from the same clock, so the two views agree
     * about which library is being shuffled and for how long.
     */
    private Rect shakenIfStirred(BlockPos table, SeatId seat, Zone zone, Rect slot) {
        return Shaking.shaken(slot, seat, zone,
                ClientTableNews.shakingFor(table, seat, zone, ClientCardFlights.now()));
    }

    /**
     * How far a line of writing shrinks to fit the room it has.
     * <p>A zone name is longer than a zone is wide, so the line shrinks rather than running
     * out over the felt and off the edge of the mat. It shrinks as far as it has to, unlike
     * the seated board, which drops a name it cannot write whole: this writing is in the
     * world, so a player who cannot read it can walk toward it.
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
     * A line of writing lying flat on the surface, centered on a point and turned to face the
     * seat it belongs to.
     * <p>The font draws into the XY plane facing the camera, so it is tipped a quarter turn
     * about X to lie down with its face to the sky, and scaled from font pixels into surface
     * units so a label keeps its size against the mat rather than against the screen.
     */
    private void writing(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Component text, float centerX, float centerZ, float lineHeight, float maxWidth,
            int angle, int backing) {
        writing(poseStack, buffers, packedLight, text, centerX, centerZ, lineHeight, maxWidth,
                angle, backing, layer(ON_THE_FELT));
    }

    /** The same, this far above the felt - on the top of a pile, say. */
    private void writing(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Component text, float centerX, float centerZ, float lineHeight, float maxWidth,
            int angle, int backing, float lift) {
        writing(poseStack, buffers, packedLight, text, centerX, centerZ, lineHeight, maxWidth,
                angle, backing, lift, WRITING_COLOR);
    }

    /** The same, in this color. */
    private void writing(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Component text, float centerX, float centerZ, float lineHeight, float maxWidth,
            int angle, int backing, float lift, int color) {
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
        poseStack.translate(centerX, lift, centerZ);
        if (Math.floorMod(angle, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-angle));
        }
        if (backing != 0) {
            // The backing drawn here, a whole step under the writing, rather than by the font:
            // the font puts its own a hundredth of a font pixel behind the letters, which on a
            // line an eighth of a block tall is nothing the depth buffer can see, and the count
            // on a pile flickered in and out of its dark patch.
            float half = (drawn / 2f + 1f) * scale;
            flat(buffers.getBuffer(RenderType.debugQuads()), poseStack.last().pose(),
                    -half, (-font.lineHeight / 2f - 1f) * scale, half, (font.lineHeight / 2f) * scale,
                    backing, 0f);
            poseStack.translate(0f, layer(1), 0f);
        }
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, -drawn / 2f, -font.lineHeight / 2f, color,
                false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL,
                0, packedLight);
        poseStack.popPose();
    }

    /**
     * The card showing on top of a pile: the first one not currently following somebody's
     * cursor, or nothing when the client was sent no cards for this pile at all.
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
     * How tall a pile stands, in surface units: the one answer the drawing and the pointer share,
     * so a player aiming at the top of a deck aims where it is drawn.
     */
    static double pileHeight(ZoneView contents, Rect slot) {
        int held = contents == null ? 0 : showing(contents);
        return PileThickness.of(held, Math.min(slot.width(), slot.height()));
    }

    /**
     * How many cards a pile has that are not in the air, which is what a viewer counts.
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
     * A seat's life total, on the table past the far edge of its own board.
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
                left, top, right, bottom, LIFE_BACKING, layer(1));
        // In its own seat's color, the same way the mat is. Two boards facing each other
        // put their counters in the same strip of table between them, back to back; drawn in
        // the one gray every other marking uses, the pair read as a single control and
        // neither said which board it belonged to.
        drawGroup(poseStack, buffers, box, span,
                dev.gathering.core.ui.SeatColor.at(seatIndex, 0xAA), layer(2));
        // Turned to face its own player, like everything else printed for one seat, so both
        // players read their own total the right way up.
        int angle = surface.facingDegrees(seatIndex);
        float lineHeight = (bottom - top) * LIFE_WRITING;
        // In what the two ends leave, from the same rule the seated board uses.
        Rect middle = TableSurface.lifeMiddle(box);
        Component life = Component.literal(Integer.toString(seat.life()));
        if (dev.gathering.core.game.LossReminders.lifeIsAtALoss(seat.life())) {
            // Red at zero and below, as on the seated board.
            life = life.copy().withColor(0xE06C6C);
        }
        writing(poseStack, buffers, packedLight,
                life,
                onSurface(middle.centerX(), span), (top + bottom) / 2f, lineHeight,
                onSurface(middle.width(), span), angle, 0);
        // The same minus and plus the seated board prints, because the ends are buttons here
        // too - the screen casts its ray at this board and presses them.
        //
        // Which end is which comes from the same function the press uses, asked with this
        // board's own answer to whether the seat is turned. Worked out here instead it
        // disagreed with the press for every seat facing the other way, and the end marked
        // plus took a life off.
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
                onSurface(end.centerX(), span), onSurface(end.centerY(), span),
                lineHeight, onSurface(end.width(), span) * LIFE_END_WRITING, angle, 0);
    }

    /** What a life total is written on, so it reads against the table rather than into it. */
    private static final int LIFE_BACKING = 0xC0101418;

    /** How much of its box a life total is written at, leaving room for the two ends. */
    private static final float LIFE_WRITING = 0.7f;

    /** How much of an end's room its sign is allowed, so it clears the number beside it. */
    private static final float LIFE_END_WRITING = 0.5f;

    /**
     * The line round a group of zones: a marking on the mat, drawn as four thin quads.
     * <p>Empty in the middle. The slots inside it draw their own recesses, and a filled box
     * behind them would put a second shade of dark under every zone.
     */
    private void drawGroup(
            PoseStack poseStack, MultiBufferSource buffers, Rect group, float span) {
        drawGroup(poseStack, buffers, group, span, feltEdge, layer(MAT_MARKING));
    }

    private void drawGroup(
            PoseStack poseStack, MultiBufferSource buffers, Rect group, float span, int color,
            float lift) {
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
        frame(consumer, pose, left, top, right, bottom, edge, color, lift);
    }

    /**
     * An empty zone: a recess in the mat with a border, the shape of the card that goes in it.
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
        int border = aimed ? RING_COLOR : SLOT_EDGE_COLOR;

        // The recess under its own border, for the reason the mat's felt is under the mat's.
        flat(consumer, pose, x, z, x + width, z + depth,
                aimed ? SLOT_AIMED : SLOT_COLOR, layer(SLOT_RECESS));
        frame(consumer, pose, x, z, x + width, z + depth, edge, border, layer(SLOT_EDGE));
    }

    /**
     * One seat's permanents, on its own mat.
     * <p>Laid out where their owner actually put them, at the size the screen draws them
     * relative to the mat. A board somebody has arranged into lands at the back and creatures
     * at the front reads as that from across the room, which is the entire point of the block
     * showing anything at all.
     */
    private int drawSeat(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, SeatView seat,
            BoardPresentation.Mat mat, TableSurface surface, SurfaceBoard placement,
            int seatIndex, float span, int budget) {
        List<CardView> cards = mat.cards();
        if (cards.isEmpty()) {
            return 0;
        }
        float cardWidth = (float) (surface.cardWidthOn(seatIndex) / TableSurface.SPAN * span);
        float cardDepth = (float) (surface.cardHeightOn(seatIndex) / TableSurface.SPAN * span);
        if (cardWidth <= 0f || cardDepth <= 0f) {
            return 0;
        }

        // Worked out once per board rather than per frame, and by the rule the seated board
        // uses. This used to count an attached card at its own recorded spot, which the seated
        // board stopped doing because it made a lone creature with an aura read as a pile - so
        // the block leaned and lifted a card the seated view drew flat.
        TableStacking.Piles piles = mat.piles();
        java.util.Map<CardInstanceId, List<CardView>> attachments = mat.attachments();

        // The top card of each stack, by its bottom card, so a buried card knows what is lying on it.
        java.util.Map<Integer, Integer> topOfStack = new java.util.HashMap<>();
        for (int index = 0; index < cards.size(); index++) {
            topOfStack.put(piles.baseOf(index), index);
        }

        int drawn = 0;
        for (int index = 0; index < cards.size() && drawn < budget; index++) {
            CardView card = cards.get(index);
            if (card.host().isPresent()) {
                // Drawn on whatever it is sitting on, below, rather than wherever it was last
                // put down on its own.
                continue;
            }
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

            // A stack stands as tall as its cards, each one a card's thickness over the one under
            // it and squared up on it, the way cards stacked on a real table are. It used to lean
            // each card up and to the left by a sliver and lift it a hair, which from across the
            // room was one card, and flickered: the owner asked for every stack to grow the way a
            // deck does. The seated board, which has no height to show, still leans them.
            // A position is the card's middle, not its corner - see BoardPlacement - and
            // draw() is given a corner. Adding half a card without taking half a card off
            // first put every card on this board half a card down and right of where the
            // seated board draws the same card, so a permanent sitting on the edge of a mat
            // in one view was off the mat in the other.
            Rect placed = placement.rectOf(seat.seat(), where);
            float x = onSurface(placed.x(), span);
            float z = onSurface(placed.y(), span);
            boolean stacked = piles.pileSize(index) > 1;
            float thickness = stacked ? oneCardThick(cardWidth) : 0f;
            float lift = Math.min(piles.depth(index), PileThickness.TALLEST) * thickness + thickness;
            tallestPile = Math.max(tallestPile, lift);

            if (card instanceof CardView.Visible visible && ClientTableHighlight.isLit(visible.id())) {
                // Under the card rather than over it: a ring drawn on top would cover the art
                // it is pointing at, and a card is a picture before it is a token.
                drawRing(poseStack, buffers, x, z, cardWidth, cardDepth,
                        where.rotation() + surface.facingDegrees(seatIndex), isTapped(card), lift);
            }
            // Turned with the board, so a card lying face up in front of its owner reads the
            // right way up to them and upside down from the chair opposite - which is what a
            // card on a table between two people does.
            int angle = where.rotation() + surface.facingDegrees(seatIndex);
            if (stacked) {
                drawCardEdges(poseStack, buffers, packedLight, seat.sleeve(), x, z, cardWidth, cardDepth,
                        angle + (isTapped(card) ? TablePosition.QUARTER_TURN : 0), lift - thickness, lift,
                        piles.depth(index) % 2 == 0);
            }
            // A card with another lying square on it shows nothing but its edges, so its face is not
            // drawn: a card's thickness apart, two faces fight for the same pixels from across the room.
            // One turned differently from the card on top of it still shows its corners, and is drawn.
            int top = topOfStack.getOrDefault(piles.baseOf(index), index);
            boolean covered = piles.isBuried(index) && top != index
                    && cards.get(top).placedAt().map(TablePosition::rotation).orElse(0) == where.rotation()
                    && isTapped(cards.get(top)) == isTapped(card);
            if (!covered) {
                drawSleeved(poseStack, buffers, packedLight, card, seat.sleeve(),
                        x, z, cardWidth, cardDepth, angle, isTapped(card), lift);
            }
            drawn++;
            // What is attached to a card is fanned out beside it, so it shows whatever is on top.
            drawn += drawAttached(poseStack, buffers, packedLight, attachments, card,
                    seat.sleeve(), placed, angle, lift, span, budget - drawn);
            if (!covered) {
                // On the card rather than at a height of their own: the writing on a card further
                // down a stack was drawn at one fixed height, under every card piled on top of it.
                writeOn(poseStack, buffers, packedLight, card, x, z, cardWidth, cardDepth, angle, lift + layer(2));
                markUp(poseStack, buffers, packedLight, card, x, z, cardWidth, cardDepth, angle, lift + layer(2));
            }
        }
        return drawn;
    }

    /**
     * The cards sitting on this one, fanned down its side the way the seated board fans them.
     * <p>Through {@link dev.gathering.core.ui.TableAttachments}, so both views put an aura in
     * the same spot. Fanned right instead when the host is near the left edge: half of
     * somebody's equipment drawn off the table is half of it invisible.
     * <p>Each is lifted a little above its host. Two quads on one plane z-fight, and an aura
     * flickering on and off its creature reads as a fault rather than as an aura.
     */
    private int drawAttached(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            java.util.Map<CardInstanceId, List<CardView>> attachments, CardView host,
            dev.gathering.core.card.Sleeve sleeve,
            Rect hostRect, int angle, float lift, float span, int budget) {
        List<CardView> attached = dev.gathering.core.ui.TableAttachments.on(attachments, host);
        if (attached.isEmpty() || budget <= 0) {
            return 0;
        }
        boolean left = dev.gathering.core.ui.TableAttachments.fansLeft(hostRect, WHOLE_SURFACE);
        int drawn = 0;
        for (int slot = 0; slot < attached.size() && drawn < budget; slot++) {
            CardView card = attached.get(slot);
            if (card instanceof CardView.Visible inHand
                    && ClientTableHighlight.isInTheAir(inHand.id())) {
                continue;
            }
            Rect at = left
                    ? dev.gathering.core.ui.TableAttachments.slot(hostRect, slot)
                    : dev.gathering.core.ui.TableAttachments.slotOnTheRight(hostRect, slot);
            drawSleeved(poseStack, buffers, packedLight, card, sleeve,
                    onSurface(at.x(), span), onSurface(at.y(), span),
                    onSurface(at.width(), span), onSurface(at.height(), span),
                    angle, isTapped(card), lift + perCard() * (slot + 1));
            drawn++;
        }
        return drawn;
    }

    /** The whole surface, as the bounds the fan decides which side to run down against. */
    private static final Rect WHOLE_SURFACE =
            new Rect(0, 0, (int) TableSurface.SPAN, (int) TableSurface.SPAN);

    /**
     * What somebody has written on this card, lying across it.
     * <p>Drawn here as well as on the screen: a face-down card labeled "morph - Brine
     * Elemental" is a blank card back to everybody standing at the table otherwise, which is
     * the one case the label exists for.
     * <p>Not on blank stock, for the reason the seated board leaves it alone: there the
     * writing <em>is</em> the card, and a second copy is the same sentence twice.
     */
    private void writeOn(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CardView card,
            float x, float z, float cardWidth, float cardDepth, int angle, float lift) {
        String note = card.writtenOn().orElse(null);
        if (note == null || note.isBlank() || PaperFace.isPaper(card)) {
            return;
        }
        writing(poseStack, buffers, packedLight, Component.literal(note),
                x + cardWidth / 2f, z + cardDepth / 2f,
                cardDepth * NOTE_HEIGHT, cardWidth * WRITING_ROOM, angle, COUNT_BACKING, lift);
    }

    /** How tall a note written on a card is drawn, against the card's own height. */
    private static final float NOTE_HEIGHT = 0.14f;

    /**
     * The numbers and counters on a card, the way the seated board writes them.
     * <p>A board on a block was drawing what somebody had written across a card and nothing
     * else, so a planeswalker's loyalty and a creature's counters were visible from a chair
     * and invisible from the same table seen from above. Which counters are shown and what
     * each is called is {@link CounterText}'s, so the two views cannot come to disagree; only
     * where they go is decided here.
     * <p>The corner number sits where a card prints its own, along the bottom edge, and the
     * counters stack up from just above it. Counted from the bottom because that is the end
     * of a card nothing else has claimed - the note goes across the top.
     */
    private void markUp(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CardView card,
            float x, float z, float cardWidth, float cardDepth, int angle, float lift) {
        java.util.List<CounterText.Line> counters = CounterText.linesOn(card);
        String corner = CounterText.cornerNumber(card);
        if (counters.isEmpty() && corner == null) {
            return;
        }
        float line = cardDepth * MARK_HEIGHT;
        float room = cardWidth * WRITING_ROOM;
        float middleX = x + cardWidth / 2f;
        // Down the card from its middle, in the card's own frame, so a turned card's numbers
        // turn with it. The seat's own facing is already in the angle.
        float fromMiddle = cardDepth / 2f - line / 2f - cardDepth * MARK_MARGIN;
        if (corner != null) {
            markAt(poseStack, buffers, packedLight, Component.literal(corner),
                    middleX, z + cardDepth / 2f, fromMiddle, line, room, angle, lift);
            fromMiddle -= line;
        }
        for (CounterText.Line counter : counters) {
            Component text = Component.literal(counter.count() == null
                    ? counter.name()
                    : counter.name() + " " + counter.count());
            markAt(poseStack, buffers, packedLight, text,
                    middleX, z + cardDepth / 2f, fromMiddle, line, room, angle, lift);
            fromMiddle -= line;
            if (fromMiddle < -cardDepth / 2f) {
                // Off the far end of the card. A stack of counters taller than the card it is
                // on is a card whose art has been replaced by a list.
                return;
            }
        }
    }

    /**
     * One mark, this far down the card from its middle.
     * <p>Placed in the card's own frame rather than the surface's, because a card lying at an
     * angle has to have its numbers along its own bottom edge and not along the table's.
     */
    private void markAt(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, Component text,
            float middleX, float middleZ, float fromMiddle, float line, float room, int angle, float lift) {
        double radians = Math.toRadians(angle);
        float across = (float) (Math.sin(radians) * fromMiddle);
        float down = (float) (Math.cos(radians) * fromMiddle);
        writing(poseStack, buffers, packedLight, text,
                middleX - across, middleZ + down, line, room, angle, COUNT_BACKING, lift);
    }

    /** How tall one number or counter is drawn, against the card's own height. */
    private static final float MARK_HEIGHT = 0.13f;

    /** And how far the lowest one sits off the card's bottom edge. */
    private static final float MARK_MARGIN = 0.03f;

    /**
     * A halo just larger than a card, marking the one the cursor is on.
     * <p>Turned with the card, so an angled card gets an angled ring rather than a square one
     * that gives away that the two are drawn by different code.
     */
    private void drawRing(
            PoseStack poseStack, MultiBufferSource buffers, float x, float z,
            float width, float depth, int angle, boolean tapped, float lift) {
        float grow = Math.max(width, depth) * RING_THICKNESS;
        poseStack.pushPose();
        // Just under the card, so the halo shows around the edges and not through the art.
        poseStack.translate(x + width / 2f, lift - layer(1), z + depth / 2f);
        int turned = angle + (tapped ? TablePosition.QUARTER_TURN : 0);
        if (Math.floorMod(turned, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-turned));
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        flat(consumer, poseStack.last().pose(),
                -width / 2f - grow, -depth / 2f - grow,
                width / 2f + grow, depth / 2f + grow, RING_COLOR);
        poseStack.popPose();
    }

    /** Closer than this to a table's middle, in blocks, and its event label is not drawn. */
    private static final double NEAR_ENOUGH_TO_BE_SEATED = 3.0;

    /** A tournament table's number floating over it, with who is playing and the time left. */
    private void drawEventLabel(TableBlockEntity table, PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            boolean inTheWorld) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(net.minecraft.network.chat.Component.translatable("label.gathering.event.table", table.eventTable()));
        if (!table.eventLine().isEmpty()) {
            lines.add(net.minecraft.network.chat.Component.literal(table.eventLine()));
        }
        if (table.eventEnds() > 0 && client.level != null) {
            long left = Math.max(0, table.eventEnds() - client.level.getGameTime()) / 20;
            lines.add(net.minecraft.network.chat.Component.literal(
                    String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60)));
        }
        // Not for the people sitting at it. The label is how a table is found across a hall; a
        // player already at the table has found it, and from a chair the label is a banner
        // across the top of their view.
        if (!inTheWorld) {
            // Not the player's camera there, so not turned to it: facing north, as the scene is set.
            FloatingLabel.draw(poseStack, buffers, lines, 1.0, 2.4, 1.0, com.mojang.math.Axis.YP.rotationDegrees(180));
            return;
        }
        net.minecraft.world.phys.Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
        BlockPos at = table.getBlockPos();
        double dx = eye.x - (at.getX() + 1.0);
        double dz = eye.z - (at.getZ() + 1.0);
        if (dx * dx + dz * dz < NEAR_ENOUGH_TO_BE_SEATED * NEAR_ENOUGH_TO_BE_SEATED) {
            return;
        }
        FloatingLabel.draw(poseStack, buffers, lines, at, 1.0, 2.4, 1.0);
    }

    /** A point on the shared surface, in blocks across the table's own footprint. */
    private static float onSurface(double surfaceUnits, float span) {
        return (float) (surfaceUnits / TableSurface.SPAN * span);
    }

    /** How much darker each band down a pile's side is than the one above it, alternately. */
    private static final float BAND_SHADE = 0.84f;

    /** The foot of a pile, where it meets the felt, darker again: that edge is in its own shadow. */
    private static final float FOOT_SHADE = 0.62f;

    /**
     * The four sides of a pile, from the felt up to where its top card lies.
     * <p>In the owner's sleeve color, because the edge of a sleeved deck is the sleeves, and a
     * printed sleeve's edge is its dark stock. Banded every few cards, darker and lighter by
     * turns, which is what a deck's side looks like from a step away: layers rather than a
     * painted block. Lit like the cards on it, so a pile in a dark room is not a glowing brick.
     * <p>Nothing about which cards are in it: the sides of a library are the same whatever it
     * holds, and its count is already written on the table.
     */
    private void drawPileSides(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            dev.gathering.core.card.Sleeve sleeve, int cards,
            float x, float z, float width, float depth, int angle, float height) {
        if (height <= 0f) {
            return;
        }
        dev.gathering.core.card.Sleeve drawn =
                sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
        int color = drawn.isPrinted() ? PRINTED_STOCK : drawn.tint();
        poseStack.pushPose();
        poseStack.translate(x + width / 2f, layer(ON_A_SLOT), z + depth / 2f);
        if (Math.floorMod(angle, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-angle));
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(PLAIN_SLEEVE));
        float halfWidth = width / 2f;
        float halfDepth = depth / 2f;
        int bands = PileThickness.bands(cards);
        for (int band = 0; band < bands; band++) {
            float bottom = height * band / bands;
            float top = height * (band + 1) / bands;
            float shade = band == 0 && bands > 1 ? FOOT_SHADE : band % 2 == 0 ? BAND_SHADE : 1f;
            int tint = shaded(color, shade);
            for (int side = 0; side < PileThickness.SIDES; side++) {
                side(consumer, pose, PileThickness.sideCorners(side, halfWidth, halfDepth, bottom, top),
                        PileThickness.sideNormal(side), tint, packedLight);
            }
        }
        poseStack.popPose();
    }

    /**
     * How thick one card of a stack on the felt is drawn, in blocks: a real card's thickness, the
     * same as a card in a pile in a zone, so a stack is as tall as the cards in it wherever it is
     * seen from. It was the depth step when that was more, which grows with distance - so a stack
     * grew taller as the camera went further off, and did not match the deck beside it.
     */
    private static float oneCardThick(float cardWidth) {
        return (float) PileThickness.of(1, cardWidth);
    }

    /**
     * The four edges of one card in a stack on the felt, from the top of the card under it to its own
     * face, at the card's own angle - so a tapped card in a stack shows its edges crosswise.
     * Every other card a shade darker, so the cards of a stack show as cards rather than a block.
     */
    private void drawCardEdges(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            dev.gathering.core.card.Sleeve sleeve, float x, float z, float width, float depth,
            int angle, float bottom, float top, boolean darker) {
        dev.gathering.core.card.Sleeve drawn =
                sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
        int tint = shaded(drawn.isPrinted() ? PRINTED_STOCK : drawn.tint(), darker ? BAND_SHADE : 1f);
        poseStack.pushPose();
        poseStack.translate(x + width / 2f, 0f, z + depth / 2f);
        if (Math.floorMod(angle, 360) != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-angle));
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(PLAIN_SLEEVE));
        for (int side = 0; side < PileThickness.SIDES; side++) {
            side(consumer, pose, PileThickness.sideCorners(side, width / 2f, depth / 2f, bottom, top),
                    PileThickness.sideNormal(side), tint, packedLight);
        }
        poseStack.popPose();
    }

    /** The edge color of a sleeve with a picture printed on it: dark card stock. */
    private static final int PRINTED_STOCK = 0x2E2A26;

    /** One upright face of a pile, its corners already in the order that faces it outward. */
    private static void side(
            VertexConsumer consumer, PoseStack.Pose pose, double[][] corners, int[] normal, int tint, int light) {
        // A patch from the middle of the sleeve texture, which is its plain cloth, stretched
        // along the side: the border and the pattern belong to the face.
        float[][] uv = {{0.45f, 0.55f}, {0.55f, 0.55f}, {0.55f, 0.45f}, {0.45f, 0.45f}};
        for (int corner = 0; corner < corners.length; corner++) {
            sideVertex(consumer, pose, (float) corners[corner][0], (float) corners[corner][1], (float) corners[corner][2],
                    uv[corner][0], uv[corner][1], normal[0], normal[1], tint, light);
        }
    }

    private static void sideVertex(
            VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
            float normalX, float normalZ, int tint, int light) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, 0f, normalZ);
    }

    private static int shaded(int rgb, float shade) {
        int red = Math.round(((rgb >> 16) & 0xFF) * shade);
        int green = Math.round(((rgb >> 8) & 0xFF) * shade);
        int blue = Math.round((rgb & 0xFF) * shade);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /**
     * A pile's arrival glow on the block: a band just outside its edge, at the height of its top.
     * Outside rather than over, so the card on top keeps its face.
     */
    private void drawArrival(
            PoseStack poseStack, MultiBufferSource buffers,
            float x, float z, float width, float depth, float lift, int argb) {
        float edge = Math.min(width, depth) * SLOT_EDGE_THICKNESS * 1.6f;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();
        float above = lift + layer(3);
        flat(consumer, pose, x - edge, z - edge, x + width + edge, z, argb, above);
        flat(consumer, pose, x - edge, z + depth, x + width + edge, z + depth + edge, argb, above);
        flat(consumer, pose, x - edge, z, x, z + depth, argb, above);
        flat(consumer, pose, x + width, z, x + width + edge, z + depth, argb, above);
    }

    /** The aimed-at ring, drawn on the top of a pile rather than on the felt under it. */
    private void drawAimedTop(
            PoseStack poseStack, MultiBufferSource buffers,
            float x, float z, float width, float depth, float lift) {
        float edge = Math.min(width, depth) * SLOT_EDGE_THICKNESS;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();
        float above = lift + layer(2);
        frame(consumer, pose, x, z, x + width, z + depth, edge, RING_COLOR, above);
    }

    /**
     * One card, lying on the surface at whatever angle it was left at.
     * <p>A tapped card gets its quarter turn on top of that angle: that is why tapping is
     * legible across a paper table, and why it is a turn here rather than a tint nobody can
     * see from six blocks away.
     * <p>The turn is negated because looking down at the surface flips the sense of a rotation
     * about the vertical, and a card turned clockwise on the screen must look it from above.
     */
    private void drawSleeved(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CardView card,
            dev.gathering.core.card.Sleeve sleeve, float x, float z, float width, float depth,
            int angle, boolean tapped, float lift) {
        if (card != null && !card.isFaceDown() && card instanceof CardView.Visible) {
            draw(poseStack, buffers, packedLight, textureFor(card), x, z, width, depth,
                    angle, tapped, lift);
            return;
        }
        dev.gathering.core.card.Sleeve drawn =
                sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
        if (drawn.isPrinted()) {
            draw(poseStack, buffers, packedLight, CardFaceRenderer.CARD_BACK, x, z, width, depth,
                    angle, tapped, lift);
            return;
        }
        draw(poseStack, buffers, packedLight, PLAIN_SLEEVE, x, z, width, depth,
                angle, tapped, lift, 0xFF000000 | drawn.tint());
        if (!drawn.hasEmblem()) {
            return;
        }
        float span = width * EMBLEM_SPAN;
        draw(poseStack, buffers, packedLight, CardSleeves.emblem(drawn),
                x + (width - span) / 2f, z + (depth - span) / 2f, span, span,
                angle, tapped, lift + layer(1));
    }

    private void draw(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            ResourceLocation texture, float x, float z, float width, float depth,
            int angle, boolean tapped, float lift) {
        draw(poseStack, buffers, packedLight, texture, x, z, width, depth, angle, tapped, lift,
                0xFFFFFFFF);
    }

    private void draw(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            ResourceLocation texture, float x, float z, float width, float depth,
            int angle, boolean tapped, float lift, int tint) {
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

        // Wound counterclockwise seen from above, so the face points at the sky.
        vertex(consumer, pose, -halfWidth, -halfDepth, 0f, 0f, packedLight, tint);
        vertex(consumer, pose, -halfWidth, halfDepth, 0f, 1f, packedLight, tint);
        vertex(consumer, pose, halfWidth, halfDepth, 1f, 1f, packedLight, tint);
        vertex(consumer, pose, halfWidth, -halfDepth, 1f, 0f, packedLight, tint);

        poseStack.popPose();
    }

    /**
     * A card as it sits on the block: its own face, or the sleeves its owner brought.
     * <p>Two quads for a sleeve with something printed on it, because the picture is
     * Minecraft's own item art and it is drawn in its own colors on top of a tinted sleeve -
     * one texture cannot be both.
     */
    private static void vertex(
            VertexConsumer consumer, Matrix4f pose, float x, float z, float u, float v, int light,
            int tint) {
        consumer.addVertex(pose, x, 0f, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0f, 1f, 0f);
    }

    /** A frame of four flat strips that cover it once each; see {@link dev.gathering.core.ui.FrameStrips}. */
    private static void frame(
            VertexConsumer consumer, Matrix4f pose,
            float left, float top, float right, float bottom, float edge, int argb, float lift) {
        for (float[] strip : dev.gathering.core.ui.FrameStrips.of(left, top, right, bottom, edge)) {
            flat(consumer, pose, strip[0], strip[1], strip[2], strip[3], argb, lift);
        }
    }

    /** A flat colored rectangle on the surface, wound to face the sky. */
    private static void flat(
            VertexConsumer consumer, Matrix4f pose,
            float left, float top, float right, float bottom, int argb) {
        flat(consumer, pose, left, top, right, bottom, argb, 0f);
    }

    /**
     * The same, this far above the plane the pose is on.
     * <p>Two quads on one plane z-fight, and the depth buffer picks a different winner from
     * one frame to the next as the camera moves - which is what "the border of a playmat
     * flickers like it's inside the table" is, and what the life box did against its own
     * backing. Everything drawn flat says which layer it is on instead. The steps are a
     * hair each: they have to separate the quads and must not lift a marking off the felt.
     */
    private static void flat(
            VertexConsumer consumer, Matrix4f pose,
            float left, float top, float right, float bottom, int argb, float lift) {
        consumer.addVertex(pose, left, lift, top).setColor(argb);
        consumer.addVertex(pose, left, lift, bottom).setColor(argb);
        consumer.addVertex(pose, right, lift, bottom).setColor(argb);
        consumer.addVertex(pose, right, lift, top).setColor(argb);
    }

    /**
     * What a card looks like from here.
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
        // Whichever side the table has it turned to. A transformed permanent drawn front-up
        // on the block and back-up on the screen is two boards disagreeing about one card.
        return summary
                .map(found -> found.sideShown(visible.turnedOver()).smallImage())
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
