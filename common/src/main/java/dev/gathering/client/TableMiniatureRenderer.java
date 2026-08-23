package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.SeatColour;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableTop;
import dev.gathering.item.CardComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
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

    /** The halo under the card the cursor is on, and how far it sticks out past it. */
    private static final int RING_COLOUR = 0xCC7FD4FF;
    private static final float RING_THICKNESS = 0.09f;

    /** Just under the card, so the halo shows around the edges and not through the art. */
    private static final float RING_DROP = 0.0003f;

    /** A taken seat's mat, and the darker line around it. Read from above, in a lit room. */
    private static final int MAT_COLOUR = 0x30000000;

    /**
     * How solid a mat's border is drawn.
     *
     * <p>The border carries the seat's own colour, which is the whole of how four boards laid
     * out on one surface are told apart. Everything else about them is identical.
     */
    private static final int MAT_EDGE_ALPHA = 0xCC;

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
        float span = (float) TableTop.SPAN_BLOCKS;

        poseStack.pushPose();
        poseStack.translate(MARGIN, SURFACE_Y, MARGIN);

        // Mats first and all of them, then the cards. A mat is drawn for a seat somebody has
        // actually taken: a playmat appearing when a player sits down is how a table shows
        // that it now has a game in it, and an empty seat's mat would say the opposite.
        for (int index = 0; index < board.seats().size(); index++) {
            if (board.seats().get(index).occupant().isPresent()) {
                drawMat(poseStack, buffers, surface.matOf(index), span,
                        SeatColour.at(index, MAT_EDGE_ALPHA));
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
        for (int index = 0; index < board.seats().size(); index++) {
            // Same rule as the mats: a seat nobody has taken shows nothing at all. Drawing
            // its zones but not its mat left four empty boxes floating on bare felt, which
            // reads as a fault rather than as a free chair.
            if (board.seats().get(index).occupant().isPresent()) {
                drawPiles(poseStack, buffers, packedLight, board.seats().get(index),
                        surface, index, span, Zone.pilesFor(table.hasCommandZone()));
            }
        }
        int drawn = 0;
        for (int index = 0; index < board.seats().size() && drawn < MAX_CARDS; index++) {
            drawn += drawSeat(poseStack, buffers, packedLight, board.seats().get(index),
                    surface, index, span, MAX_CARDS - drawn);
        }
        poseStack.popPose();
    }

    /**
     * One seat's playmat: a tinted rectangle with a darker line around it.
     *
     * <p>Four quads for the line rather than an outline draw, because a line width in world
     * space is a pixel width on screen and a mat you can only see the edge of from two blocks
     * away is not an edge.
     */
    private void drawMat(
            PoseStack poseStack, MultiBufferSource buffers, Rect mat, float span, int border) {
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

        flat(consumer, pose, left, top, right, bottom, MAT_COLOUR);
        flat(consumer, pose, left, top, right, top + edge, border);
        flat(consumer, pose, left, bottom - edge, right, bottom, border);
        flat(consumer, pose, left, top, left + edge, bottom, border);
        flat(consumer, pose, right - edge, top, right, bottom, border);
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
            TableSurface surface, int seatIndex, float span, int count) {
        drawGroup(poseStack, buffers,
                surface.pileGroup(seatIndex, 0, Zone.PILES_WITHOUT_A_COMMAND_ZONE - 1, count), span);
        if (count > Zone.PILES_WITHOUT_A_COMMAND_ZONE) {
            drawGroup(poseStack, buffers,
                    surface.pileGroup(seatIndex, count - 1, count - 1, count), span);
        }
        for (int index = 0; index < count; index++) {
            Rect slot = surface.pileSlot(seatIndex, index, count);
            if (slot.isEmpty()) {
                continue;
            }
            float x = onSurface(slot.x(), span);
            float z = onSurface(slot.y(), span);
            float width = onSurface(slot.width(), span);
            float depth = onSurface(slot.height(), span);

            // The slot is drawn whether or not there is anything in it. A zone you can only
            // see once it has cards in it is a zone nobody can aim at, and aiming at it is now
            // how cards get put there.
            boolean aimed = ClientTableHighlight.isAimedAt(seat.seat(), index);
            drawSlot(poseStack, buffers, x, z, width, depth, aimed);

            ZoneView contents = seat.zone(Zone.PILES.get(index));
            if (contents == null || showing(contents) == 0) {
                continue;
            }
            CardView top = topOf(contents);
            ResourceLocation texture = top == null || top.isFaceDown()
                    ? CardFaceRenderer.CARD_BACK
                    : textureFor(top);
            draw(poseStack, buffers, packedLight, texture, x, z, width, depth,
                    surface.facingDegrees(seatIndex), false, SLOT_LIFT);
        }
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

    /** How many cards a pile has that are not in the air, which is what a viewer counts. */
    private static int showing(ZoneView contents) {
        for (CardView card : contents.cards()) {
            if (card instanceof CardView.Visible visible
                    && ClientTableHighlight.isInTheAir(visible.id())) {
                return contents.count() - 1;
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
            TableSurface surface, int seatIndex, float span, int budget) {
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
            float x = onSurface(surface.surfaceX(seatIndex, where.x()), span) + lean;
            float z = onSurface(surface.surfaceY(seatIndex, where.y()), span) + lean;
            float lift = depths.get(index) * STACK_LIFT;

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
