package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.table.TableCluster;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.core.ui.TableSurface;
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

    /** Just above the felt, so the mats are on the table rather than in it. */
    private static final float SURFACE_Y = 15.02f / 16f;

    /** And cards just above the mats, so a mat never z-fights the board on top of it. */
    private static final float CARD_Y = SURFACE_Y + 0.0008f;

    /** The table is two blocks across; the miniature stays inside a margin of that. */
    private static final float MARGIN = 0.12f;

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

    /** A taken seat's mat, and the darker line around it. Read from above, in a lit room. */
    private static final int MAT_COLOUR = 0x30000000;
    private static final int MAT_EDGE_COLOUR = 0x60000000;

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

        TableSurface surface = TableSurface.forSeats(
                TableCluster.assumedSeating(board.seats().size()));
        float span = 2f - MARGIN * 2f;

        poseStack.pushPose();
        poseStack.translate(MARGIN, SURFACE_Y, MARGIN);

        // Mats first and all of them, then the cards. A mat is drawn for a seat somebody has
        // actually taken: a playmat appearing when a player sits down is how a table shows
        // that it now has a game in it, and an empty seat's mat would say the opposite.
        for (int index = 0; index < board.seats().size(); index++) {
            if (board.seats().get(index).occupant().isPresent()) {
                drawMat(poseStack, buffers, surface.matOf(index), span);
            }
        }
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(MARGIN, CARD_Y, MARGIN);
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
    private void drawMat(PoseStack poseStack, MultiBufferSource buffers, Rect mat, float span) {
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
        flat(consumer, pose, left, top, right, top + edge, MAT_EDGE_COLOUR);
        flat(consumer, pose, left, bottom - edge, right, bottom, MAT_EDGE_COLOUR);
        flat(consumer, pose, left, top, left + edge, bottom, MAT_EDGE_COLOUR);
        flat(consumer, pose, right - edge, top, right, bottom, MAT_EDGE_COLOUR);
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
            // A card with no position of its own is one the game has not put down yet; it is
            // still somebody's permanent, so it goes at the corner of the mat rather than
            // nowhere.
            TablePosition where = card.placedAt().orElse(TablePosition.ORIGIN);

            draw(poseStack, buffers, packedLight, textureFor(card),
                    onSurface(surface.surfaceX(seatIndex, where.x()), span),
                    onSurface(surface.surfaceY(seatIndex, where.y()), span),
                    cardWidth, cardDepth,
                    where.rotation(), isTapped(card), depths.get(index) * STACK_LIFT);
            drawn++;
        }
        return drawn;
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
