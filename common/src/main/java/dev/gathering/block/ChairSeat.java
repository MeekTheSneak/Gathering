package dev.gathering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What somebody sitting in a chair is riding: nothing anybody sees, at the height of the seat.
 * <p>Minecraft seats a player by putting them on something - a boat, a minecart, a horse - and a
 * chair has to be something to be sat on in the same way, or the player stands on it. So a chair
 * that is sat in has one of these in it for as long as somebody is.
 * <p>Never saved. A seat outliving the person in it would be an invisible thing in a chair after a
 * restart that the next player could not sit down through; the seat at the table is the table's own
 * record and comes back on its own. One with nobody in it, or no chair under it, removes itself.
 */
public final class ChairSeat extends Entity {

    public static final String ID = "chair_seat";

    /**
     * How far above a standing player's feet the hip joint is drawn, in blocks: legs twelve model
     * pixels long, and the player drawn at fifteen sixteenths.
     */
    public static final double HIP_HEIGHT = 12.0 / 16.0 * 15.0 / 16.0;

    /** Half a thigh's thickness, in blocks: a sitter's hip joint is this far above what they sit on. */
    public static final double HALF_A_THIGH = 2.0 / 16.0 * 15.0 / 16.0;

    /**
     * How high in the chair's block this seat is, so the sitter's thighs lie on the seat.
     * <p>A rider is put with their feet {@link net.minecraft.world.entity.player.Player#DEFAULT_VEHICLE_ATTACHMENT}
     * below what they ride, and drawn with their legs out in front from the hip. The seat was
     * first at the seat's height less a guess, which put the hips a quarter of a block down
     * inside the chair: the owner saw a player sunk into it.
     */
    public static final double RIDDEN_AT = ChairBlock.SEAT_HEIGHT + HALF_A_THIGH - HIP_HEIGHT
            + net.minecraft.world.entity.player.Player.DEFAULT_VEHICLE_ATTACHMENT.y;

    /** The table whose seat this chair took, or null for a chair that is only a chair. */
    private BlockPos tableOrigin;

    public ChairSeat(EntityType<? extends ChairSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A seat in the chair at this position, for a table's seat or none. */
    public static ChairSeat in(Level level, BlockPos chair, BlockPos tableOrigin) {
        ChairSeat seat = new ChairSeat(dev.gathering.item.GatheringContent.CHAIR_SEAT.get(), level);
        seat.setPos(chair.getX() + 0.5, chair.getY() + RIDDEN_AT, chair.getZ() + 0.5);
        seat.tableOrigin = tableOrigin == null ? null : tableOrigin.immutable();
        return seat;
    }

    /** Marks this chair as holding its sitter's seat at the table here, once the table has given it. */
    void holdsTheSeatAt(BlockPos tableOrigin) {
        this.tableOrigin = tableOrigin == null ? null : tableOrigin.immutable();
    }

    /** The table this chair's sitter holds a seat at, or null. */
    public BlockPos tableOrigin() {
        return tableOrigin;
    }

    /** The chair this seat is in. */
    public BlockPos chair() {
        return BlockPos.containing(getX(), getY() - RIDDEN_AT + 0.5, getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()
                && (getPassengers().isEmpty() || !(level().getBlockState(chair()).getBlock() instanceof ChairBlock))) {
            discard();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    /** Getting out of a chair is standing up: see {@link Chairs#gotUp}. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide() && passenger instanceof ServerPlayer player) {
            Chairs.gotUp(player, this);
        }
    }

    /**
     * Stood up beside the chair rather than on top of it: behind it if there is room, else to either side,
     * else diagonally behind, and on the chair only when none of those will take a player.
     * <p>Room is asked of the world the way a minecart asks it - a floor to stand on, a block up or down,
     * and the player's own box clear of everything. It always said "behind" once, and an audit put a
     * wall there and the player's body in the wall.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos chair = chair();
        net.minecraft.core.Direction facing = level().getBlockState(chair).getBlock() instanceof ChairBlock
                ? level().getBlockState(chair).getValue(ChairBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        net.minecraft.core.Direction back = facing.getOpposite();
        net.minecraft.core.Direction left = facing.getCounterClockWise();
        net.minecraft.core.Direction right = facing.getClockWise();
        BlockPos[] ways = {
                chair.relative(back), chair.relative(left), chair.relative(right),
                chair.relative(back).relative(left), chair.relative(back).relative(right)};
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (net.minecraft.world.entity.Pose pose : passenger.getDismountPoses()) {
            net.minecraft.world.entity.EntityDimensions size = passenger.getDimensions(pose);
            float half = Math.min(size.width(), 1.0F) / 2.0F;
            for (int up : new int[] {0, 1, -1}) {
                for (BlockPos way : ways) {
                    probe.set(way.getX(), way.getY() + up, way.getZ());
                    double floor = level().getBlockFloorHeight(
                            net.minecraft.world.entity.vehicle.DismountHelper.nonClimbableShape(level(), probe),
                            () -> net.minecraft.world.entity.vehicle.DismountHelper.nonClimbableShape(level(), probe.below()));
                    if (!net.minecraft.world.entity.vehicle.DismountHelper.isBlockFloorValid(floor)) {
                        continue;
                    }
                    Vec3 standing = Vec3.upFromBottomCenterOf(probe, floor);
                    net.minecraft.world.phys.AABB body = new net.minecraft.world.phys.AABB(
                            -half, 0.0, -half, half, size.height(), half).move(standing);
                    if (net.minecraft.world.entity.vehicle.DismountHelper.canDismountTo(level(), passenger, body)) {
                        passenger.setPose(pose);
                        return standing;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
