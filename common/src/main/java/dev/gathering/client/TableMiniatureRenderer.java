package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.item.CardComponent;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
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
 * <p>Each seat gets a band of the surface and its permanents run along it. Cards are drawn at
 * a size that fits the band, so a busy board is drawn smaller rather than spilling off the
 * table - the table never grows in world footprint.
 *
 * <p>Client-only.
 */
public class TableMiniatureRenderer implements BlockEntityRenderer<TableBlockEntity> {

    /** Just above the felt, so the cards are on the table rather than in it. */
    private static final float SURFACE_Y = 15.02f / 16f;

    /** The table is two blocks across; the miniature stays inside a margin of that. */
    private static final float MARGIN = 0.12f;

    private static final float CARD_ASPECT = 488f / 680f;

    /** Nothing on a table is worth a draw call past this. */
    private static final int MAX_CARDS = 256;

    /** How deep a card is drawn within its seat's band, leaving room to spread out in it. */
    private static final float BAND_CARD_DEPTH = 0.42f;

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

        // Two blocks square, inset a little. Bands run north to south, one per seat, so the
        // seat you are standing behind is the band nearest you.
        float span = 2f - MARGIN * 2f;
        int bands = board.seats().size();
        float bandDepth = span / bands;

        poseStack.pushPose();
        poseStack.translate(MARGIN, SURFACE_Y, MARGIN);

        int drawn = 0;
        for (int index = 0; index < bands; index++) {
            SeatView seat = board.seats().get(index);
            List<CardView> cards = seat.zone(Zone.BATTLEFIELD).cards();
            if (cards.isEmpty()) {
                continue;
            }
            drawn += drawBand(poseStack, buffers, packedLight, cards,
                    index * bandDepth, bandDepth, span, MAX_CARDS - drawn);
            if (drawn >= MAX_CARDS) {
                break;
            }
        }
        poseStack.popPose();
    }

    /**
     * One seat's permanents, along its band.
     *
     * <p>Laid out where their owner actually put them: the seat's region is squashed into the
     * band and every card keeps its place and its angle within it. A board somebody has
     * arranged into lands at the back and creatures at the front reads as that from across the
     * room, which is the entire point of the block showing anything at all.
     */
    private int drawBand(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            List<CardView> cards, float bandZ, float bandDepth, float span, int budget) {
        float cardDepth = bandDepth * BAND_CARD_DEPTH;
        float cardWidth = cardDepth * CARD_ASPECT;
        if (cardDepth <= 0f || cardWidth <= 0f || cardWidth > span) {
            return 0;
        }
        float acrossRoom = Math.max(0f, span - cardWidth);
        float downRoom = Math.max(0f, bandDepth - cardDepth);

        int drawn = 0;
        for (CardView card : cards) {
            if (drawn >= budget) {
                break;
            }
            // A card with no position of its own is one the game has not put down yet; it is
            // still somebody's permanent, so it goes at the front of the band rather than
            // nowhere.
            TablePosition where = card.placedAt().orElse(TablePosition.ORIGIN);
            float across = (float) where.acrossFraction();
            float down = (float) where.downFraction();

            draw(poseStack, buffers, packedLight, textureFor(card),
                    across * acrossRoom, bandZ + down * downRoom, cardWidth, cardDepth,
                    where.rotation(), isTapped(card));
            drawn++;
        }
        return drawn;
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
            int angle, boolean tapped) {
        poseStack.pushPose();
        poseStack.translate(x + width / 2f, 0f, z + depth / 2f);
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
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0f, 1f, 0f);
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

    /** Tables are read from across the room, so they render well past the default range. */
    @Override
    public int getViewDistance() {
        return 64;
    }
}
