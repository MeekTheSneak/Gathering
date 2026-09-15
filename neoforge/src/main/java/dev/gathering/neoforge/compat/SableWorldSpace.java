package dev.gathering.neoforge.compat;

import dev.gathering.platform.WorldSpace;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Tables on Sable's moving structures - Create Aeronautics' vehicles and physics objects.
 * <p>Through Sable's companion library, which is bundled and answers with plain world positions
 * when Sable itself is not installed, so this is the right answer on every NeoForge server.
 */
public final class SableWorldSpace implements WorldSpace {

    @Override
    public Vec3 toWorld(Level level, Vec3 point) {
        return level == null || point == null ? point : SableCompanion.INSTANCE.projectOutOfSubLevel(level, point);
    }

    @Override
    public Vec3 toLocalOf(Level level, BlockPos anchor, Vec3 worldPoint) {
        SubLevelAccess structure = containing(level, anchor);
        return structure == null || worldPoint == null ? worldPoint : structure.logicalPose().transformPositionInverse(worldPoint);
    }

    @Override
    public Vec3 directionToLocalOf(Level level, BlockPos anchor, Vec3 worldDirection) {
        SubLevelAccess structure = containing(level, anchor);
        return structure == null || worldDirection == null
                ? worldDirection : structure.logicalPose().transformNormalInverse(worldDirection);
    }

    @Override
    public float yawOf(Level level, BlockPos anchor) {
        SubLevelAccess structure = containing(level, anchor);
        if (structure == null) {
            return 0f;
        }
        // Where the structure's own south ends up, flattened onto the ground and measured the way
        // an entity's yaw is: zero facing south, positive turning towards west.
        Quaterniondc orientation = structure.logicalPose().orientation();
        Vector3d south = orientation.transform(new Vector3d(0, 0, 1));
        return (float) Math.toDegrees(Math.atan2(-south.x, south.z));
    }

    private static SubLevelAccess containing(Level level, BlockPos anchor) {
        return level == null || anchor == null ? null : SableCompanion.INSTANCE.getContaining(level, anchor);
    }
}
