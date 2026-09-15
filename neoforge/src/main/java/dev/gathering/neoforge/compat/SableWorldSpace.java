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
 * <p>Through Sable's companion library, which Sable carries in its own jar. Only made when that library is
 * installed - see GatheringNeoForge - so a server without Sable never loads this class or anything it names.
 */
public final class SableWorldSpace implements WorldSpace {

    /** Makes this the world space in use. Called only once the companion library is known to be installed. */
    public static void install() {
        WorldSpace.use(new SableWorldSpace());
    }

    @Override
    public Vec3 toWorld(Level level, Vec3 point) {
        if (level == null || point == null) {
            return point;
        }
        if (level.isClientSide()) {
            SubLevelAccess structure = SableCompanion.INSTANCE.getContaining(level, point);
            return structure == null ? point : pose(level, structure).transformPosition(point);
        }
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, point);
    }

    @Override
    public Vec3 toLocalOf(Level level, BlockPos anchor, Vec3 worldPoint) {
        SubLevelAccess structure = containing(level, anchor);
        return structure == null || worldPoint == null ? worldPoint : pose(level, structure).transformPositionInverse(worldPoint);
    }

    @Override
    public Vec3 directionToLocalOf(Level level, BlockPos anchor, Vec3 worldDirection) {
        SubLevelAccess structure = containing(level, anchor);
        return structure == null || worldDirection == null
                ? worldDirection : pose(level, structure).transformNormalInverse(worldDirection);
    }

    @Override
    public float yawOf(Level level, BlockPos anchor) {
        SubLevelAccess structure = containing(level, anchor);
        if (structure == null) {
            return 0f;
        }
        // Where the structure's own south ends up, flattened onto the ground and measured the way
        // an entity's yaw is: zero facing south, positive turning towards west.
        Quaterniondc orientation = pose(level, structure).orientation();
        Vector3d south = orientation.transform(new Vector3d(0, 0, 1));
        return (float) Math.toDegrees(Math.atan2(-south.x, south.z));
    }

    /**
     * Where the structure is, for whoever is asking. A server's answer is the structure's logical
     * pose, where it is this tick. A client's is where it is drawn this frame, between the last tick
     * and this one: the camera over a table, the pointer on its felt and the marker over a seat are
     * all drawn with the structure, and put by the logical pose they sat up to a tick ahead of a
     * moving ship and shook as it went.
     */
    private static dev.ryanhcode.sable.companion.math.Pose3dc pose(Level level, SubLevelAccess structure) {
        return level.isClientSide() && structure instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess drawn
                ? drawn.renderPose()
                : structure.logicalPose();
    }

    private static SubLevelAccess containing(Level level, BlockPos anchor) {
        return level == null || anchor == null ? null : SableCompanion.INSTANCE.getContaining(level, anchor);
    }
}
