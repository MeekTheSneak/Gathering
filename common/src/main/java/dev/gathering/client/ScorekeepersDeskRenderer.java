package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gathering.block.ScorekeepersDeskBlock;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The tournament a Scorekeeper's Desk runs, floating over it, and where it has got to - "sign up
 * here", the round, who won - so a room with a desk in it says where to go without anybody asking.
 */
public class ScorekeepersDeskRenderer implements BlockEntityRenderer<ScorekeepersDeskBlockEntity> {

    public ScorekeepersDeskRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ScorekeepersDeskBlockEntity desk, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        ScorekeepersDeskBlockEntity.Label label = desk.label();
        if (!label.isShown()) {
            return;
        }
        // High enough to clear a Display Link or a lantern put on top of the desk.
        List<Component> lines = desk.linesFor(ScorekeepersDeskRenderer::lines);
        if (desk.getLevel() == Minecraft.getInstance().level) {
            FloatingLabel.draw(poseStack, buffers, lines, 0.5, 2.4, 0.5);
        } else {
            // Somewhere other than the world being played - a Ponder scene - whose camera is not the
            // player's: turned to face the player's camera it is edge-on or backwards there, so it
            // faces the way the desk does, as a sign's words do.
            Direction facing = desk.getBlockState().getValue(ScorekeepersDeskBlock.FACING);
            FloatingLabel.draw(poseStack, buffers, lines, 0.5, 2.4, 0.5,
                    Axis.YP.rotationDegrees(-facing.toYRot()));
        }
    }

    /**
     * Tall and wide enough for the label over the desk, so it is not culled with the desk's own cube -
     * looking up at the label from beside the desk, or with the desk just below the window's edge.
     * <p>Not an {@code @Override}: NeoForge's extension, which Fabric does not have - it culls by
     * chunk section. A {@code BlockEntity} parameter, as the table renderer explains: the erased
     * signature is the one that is called.
     */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(net.minecraft.world.level.block.entity.BlockEntity desk) {
        return new net.minecraft.world.phys.AABB(desk.getBlockPos()).expandTowards(0, 2, 0).inflate(1.5, 0.5, 1.5);
    }

    /** The heading and the line under it. Visible for tests and the scene. */
    public static List<Component> lines(ScorekeepersDeskBlockEntity.Label label) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(label.name()));
        String key = "label.gathering.desk." + label.phase();
        lines.add(switch (label.phase()) {
            case "swiss" -> Component.translatable(key, label.round(), label.rounds());
            case "finished" -> label.winner().isEmpty()
                    ? Component.translatable("label.gathering.desk.over")
                    : Component.translatable(key, label.winner());
            default -> Component.translatable(key);
        });
        return lines;
    }
}
