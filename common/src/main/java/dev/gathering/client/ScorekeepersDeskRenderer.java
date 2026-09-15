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
        if (desk.getLevel() == Minecraft.getInstance().level) {
            FloatingLabel.draw(poseStack, buffers, lines(label), 0.5, 2.4, 0.5);
        } else {
            // Somewhere other than the world being played - a Ponder scene - whose camera is not the
            // player's: turned to face the player's camera it is edge-on or backwards there, so it
            // faces the way the desk does, as a sign's words do.
            Direction facing = desk.getBlockState().getValue(ScorekeepersDeskBlock.FACING);
            FloatingLabel.draw(poseStack, buffers, lines(label), 0.5, 2.4, 0.5,
                    Axis.YP.rotationDegrees(-facing.toYRot()));
        }
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
