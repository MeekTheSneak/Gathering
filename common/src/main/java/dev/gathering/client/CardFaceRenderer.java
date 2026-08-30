package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardSummary;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws a card as the actual card, rather than as a generic icon.
 *
 * <p>A card item in your hand shows its own printed face, fetched from Scryfall and cached
 * by this client. Turn it over and it shows the other side: the back face for a double-faced
 * card, and the card back for everything else.
 *
 * <p><b>The card back is ours.</b> The mod ships no Wizards artwork - that is a pillar of the
 * project, not an oversight - so the back is our own design, and it is the seed of the sleeve
 * system: a card's back is the sleeve it sits in, dyeable and eventually player-supplied.
 *
 * <p>Both loaders call this; only the plumbing that reaches it differs.
 *
 * <p>Client-only.
 */
public final class CardFaceRenderer {

    /** Our own card back. Never a Wizards one. */
    public static final ResourceLocation CARD_BACK =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "textures/card/back.png");

    /** Printed proportions, 2.5 by 3.5 inches, in the 1/16-block units item models use. */
    private static final float WIDTH = 0.64f;
    private static final float HEIGHT = WIDTH * (3.5f / 2.5f);
    private static final float HALF_THICKNESS = 0.002f;

    private static final int FULL_BRIGHT_UNUSED = OverlayTexture.NO_OVERLAY;

    private CardFaceRenderer() {
    }

    /**
     * Renders the card held in this stack.
     *
     * <p>Always draws something card-shaped. A stack with no card on it - the blank from the
     * creative tab - shows as a card back, which is what a blank card is; drawing nothing
     * would make the item invisible in the creative menu.
     */
    public static void render(
            ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Optional<CardComponent> card = CardItem.cardOf(stack);
        ResourceLocation facing = card.map(c -> textureForSide(c, c.flipped())).orElse(CARD_BACK);

        poseStack.pushPose();
        // Item models are drawn in a 1x1 space with the origin at a corner; center the card.
        poseStack.translate(0.5f, 0.5f, 0.5f);

        Matrix4f pose = poseStack.last().pose();
        drawFace(buffers, pose, facing, packedLight, HALF_THICKNESS);
        // A foil in the hand catches the light the same way one being read does. Only over a
        // printed face: the reverse of every card is its sleeve, and a sleeve is not foil -
        // nor is the sleeve a card falls back to while its picture is still being fetched.
        card.filter(CardComponent::foil)
                .filter(held -> !facing.equals(CARD_BACK))
                .ifPresent(held -> drawSheen(buffers, pose, held));
        // The reverse is always the sleeve, never the printed face behind it. Think of every
        // card as sleeved: the back of a sleeve is opaque.
        //
        // This is not only a look. A face-down card has to be unreadable from *every* angle,
        // or an opponent can walk round behind it and read what the visibility rules went to
        // such lengths to withhold. Making the two sides complementary would put the card's
        // face on the back of a face-down card, which is a hole in the one security property
        // this mod has.
        drawFace(buffers, pose, CARD_BACK, packedLight, -HALF_THICKNESS);

        poseStack.popPose();
    }

    /**
     * The texture for one side of a card.
     *
     * <p>A double-faced card genuinely has two printed sides, so turning one over shows its
     * other face. Everything else has a front and a back, and its back is the sleeve it sits
     * in - our own artwork, never Wizards'.
     *
     * <p>Art that has not been fetched yet falls back to the back rather than to nothing, so a
     * hand of cards looks like a hand of cards while it loads.
     *
     * <p>Note that {@code flipped} currently means two things at once: "show me the other
     * printed side" for a double-faced card, and "this card is face down" for everything
     * else. That is the right shape for a card in hand. When the table arrives and face-down
     * becomes a zone-level fact with a marker attached, the two will want separating.
     *
     * @param reverseSide false for the printed front, true for whatever is on the other side
     */
    static ResourceLocation textureForSide(CardComponent card, boolean reverseSide) {
        Optional<CardSummary> summary = ClientCardCache.get().summary(card);
        if (summary.isEmpty()) {
            return CARD_BACK;
        }

        CardSummary details = summary.get();
        Optional<CardFaceSummary> face = reverseSide
                ? details.back()
                : Optional.of(details.front());
        if (face.isEmpty()) {
            // No other side: an ordinary card, so this side is its sleeve.
            return CARD_BACK;
        }

        return face.get().readableImage()
                .flatMap(url -> ClientCardImages.get().texture(url))
                .orElse(CARD_BACK);
    }

    /**
     * The holographic layer over a foil's printed face.
     *
     * <p>Reported as a foil in the hand rendering as a flat picture. The shine already
     * existed for a card being read on a screen; what it had no way to know out here is which
     * way the card is turned, because there is no cursor and there are many cards at once -
     * a hand, a slot, an item frame across the room. So it is read off the card's own pose:
     * how far each of its axes has tilted toward the camera is exactly the rake the sheen
     * wants, per card, with nothing shared between them.
     *
     * <p>Untextured translucent quads on the main target, which is what {@code debugQuads}
     * is - vanilla's name for the state rather than a debugging tool. Drawn just in front of
     * the face so it lies on the picture rather than fighting it.
     */
    private static void drawSheen(MultiBufferSource buffers, Matrix4f pose, CardComponent card) {
        FoilSheen.paintFlat(
                buffers.getBuffer(RenderType.debugQuads()), pose,
                WIDTH / 2f, HEIGHT / 2f, HALF_THICKNESS * SHEEN_LIFT,
                // The z parts of the card's own axes once posed: zero when an axis lies
                // across the view, one when it points at the eye. Turning the card, or
                // walking round it, moves both - which is the whole of what a foil answers to.
                pose.m20(), pose.m21(),
                grainOf(card), SHEEN_COLUMNS, SHEEN_ROWS);
    }

    /**
     * A number that belongs to this printing, so two foils on a table do not glitter alike.
     *
     * <p>The same one the reading screen uses, from the same id, so a card picked up off the
     * felt has the grain it had a moment ago on the board.
     */
    private static long grainOf(CardComponent card) {
        return card.scryfallId()
                .map(id -> id.getMostSignificantBits() ^ id.getLeastSignificantBits())
                .orElseGet(() -> card.customId().map(String::hashCode).orElse(0).longValue());
    }

    /** How far in front of the face the sheen lies, in half-thicknesses. Enough not to fight. */
    private static final float SHEEN_LIFT = 1.5f;

    /**
     * How finely the sheen is cut up on a card held in the hand.
     *
     * <p>Far coarser than the reading screen's, which is twenty-six by thirty-six: this card
     * is an inch of screen, and a full inventory of foils at the reading mesh would be tens
     * of thousands of quads a frame for a shimmer nobody can resolve.
     */
    private static final int SHEEN_COLUMNS = 7;
    private static final int SHEEN_ROWS = 10;

    /**
     * One textured quad.
     *
     * <p>Two of them back to back with a hair of separation, rather than one double-sided
     * quad, so the card has a front and a back and neither z-fights with the other.
     */
    private static void drawFace(
            MultiBufferSource buffers, Matrix4f pose, ResourceLocation texture, int packedLight, float z) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));

        float left = -WIDTH / 2f;
        float right = WIDTH / 2f;
        float bottom = -HEIGHT / 2f;
        float top = HEIGHT / 2f;
        float normal = z >= 0 ? 1f : -1f;

        if (z >= 0) {
            vertex(consumer, pose, left, bottom, z, 0f, 1f, packedLight, normal);
            vertex(consumer, pose, right, bottom, z, 1f, 1f, packedLight, normal);
            vertex(consumer, pose, right, top, z, 1f, 0f, packedLight, normal);
            vertex(consumer, pose, left, top, z, 0f, 0f, packedLight, normal);
        } else {
            // Wound the other way so the back face is not culled, and mirrored so the back
            // reads the right way round rather than as a mirror image.
            vertex(consumer, pose, right, bottom, z, 0f, 1f, packedLight, normal);
            vertex(consumer, pose, left, bottom, z, 1f, 1f, packedLight, normal);
            vertex(consumer, pose, left, top, z, 1f, 0f, packedLight, normal);
            vertex(consumer, pose, right, top, z, 0f, 0f, packedLight, normal);
        }
    }

    private static void vertex(
            VertexConsumer consumer, Matrix4f pose,
            float x, float y, float z, float u, float v, int packedLight, float normal) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(FULL_BRIGHT_UNUSED)
                .setLight(packedLight)
                .setNormal(0f, 0f, normal);
    }
}
