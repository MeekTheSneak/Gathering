package dev.gathering.core.table;

/**
 * An edge of a table, in world compass terms.
 * <p>Its own type rather than a Minecraft {@code Direction}, because this module compiles
 * against no Minecraft at all - which is what lets the cluster arithmetic be checked over
 * every shape a player can build rather than over the one somebody happened to place.
 */
public enum Side {

    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int stepX;
    private final int stepZ;

    Side(int stepX, int stepZ) {
        this.stepX = stepX;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepZ() {
        return stepZ;
    }

    public Side opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
