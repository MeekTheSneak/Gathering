package dev.gathering.platform;

import java.util.ServiceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a table is in the world, for tables that are not where their block position says.
 * <p>Create Aeronautics builds vehicles and physics objects out of ordinary blocks, and Sable,
 * underneath it, keeps those blocks in a region of their own far from where they appear and moves
 * that region about by a pose. A table on an airship has a block position out in that region: a
 * player standing at it is a long way from its coordinates, and anything that compares the two
 * directly - which side somebody clicked from, where to seat them, which way to point them, where
 * to put the camera - is comparing a place on the ship with a place in the world.
 * <p>So those comparisons ask here. Without a moving structure every answer is the point it was
 * given, which is what the {@link #FLAT} default is and what a loader with no such mod installed
 * uses. Entity distance checks are not routed through this: Sable already corrects those.
 * <p>Its own service rather than a method on {@link Platform}, which is kept to what only the loader
 * can answer.
 */
public interface WorldSpace {

    /** Blocks are where they say they are. */
    WorldSpace FLAT = new WorldSpace() {
        @Override
        public Vec3 toWorld(Level level, Vec3 point) {
            return point;
        }

        @Override
        public Vec3 toLocalOf(Level level, BlockPos anchor, Vec3 worldPoint) {
            return worldPoint;
        }

        @Override
        public Vec3 directionToLocalOf(Level level, BlockPos anchor, Vec3 worldDirection) {
            return worldDirection;
        }

        @Override
        public float yawOf(Level level, BlockPos anchor) {
            return 0f;
        }
    };

    /** Where a point given in a block's coordinates is in the world. */
    Vec3 toWorld(Level level, Vec3 point);

    /** Where a point in the world is in the coordinates of the structure this block is part of. */
    Vec3 toLocalOf(Level level, BlockPos anchor, Vec3 worldPoint);

    /** A direction in the world, turned into the coordinates of the structure this block is part of. */
    Vec3 directionToLocalOf(Level level, BlockPos anchor, Vec3 worldDirection);

    /**
     * How far the structure this block is part of has turned about the vertical, in degrees, the
     * way Minecraft measures yaw. Zero for a block that is part of no moving structure.
     */
    float yawOf(Level level, BlockPos anchor);

    /** A block position's center, in the world. */
    default Vec3 centerInWorld(Level level, BlockPos pos) {
        return toWorld(level, Vec3.atCenterOf(pos));
    }

    static WorldSpace get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private static final WorldSpace INSTANCE = ServiceLoader.load(WorldSpace.class).findFirst().orElse(FLAT);

        private Holder() {
        }
    }
}
