package dev.gathering.block;

import dev.gathering.core.table.Side;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a table remembers.
 *
 * <p>One per table, on the corner that owns it. It holds who has taken which of its edges,
 * which is a table's share of a cluster's seating: a seat is identified by the table it is at
 * and the edge it is on, so storing the claim on that table means it is saved with that table
 * and comes back with it.
 *
 * <p>Seats are registrations rather than chairs. Nobody is sitting anywhere - the design has
 * seated players walking around, tending a furnace and heckling over a shoulder - so a claim
 * is a name against an edge and nothing more. It survives logging out, because the design
 * says leaving does not drop your seat.
 *
 * <p>The session itself will live here too, for the same reason: a block entity is the one
 * thing in Minecraft saved with the world at the position it belongs to.
 */
public class TableBlockEntity extends BlockEntity {

    public static final String ID = "table";

    private static final String FELT_KEY = "felt";
    private static final String SEATS_KEY = "seats";
    private static final String SIDE_KEY = "side";
    private static final String PLAYER_KEY = "player";

    private final Map<Side, UUID> claims = new EnumMap<>(Side.class);

    /** Empty for the felt's own colour; a dye replaces it. */
    private DyeColor felt;

    public TableBlockEntity(BlockPos pos, BlockState state) {
        super(dev.gathering.item.GatheringContent.TABLE_ENTITY.get(), pos, state);
    }

    public Optional<DyeColor> felt() {
        return Optional.ofNullable(felt);
    }

    /**
     * Dyes the felt, and says whether anything changed.
     *
     * <p>Colour is a property of the table rather than of its blockstate, which is what keeps
     * a dyeable surface from multiplying the blockstate count by sixteen for something that
     * is never asked about except when drawing.
     */
    public boolean dye(DyeColor colour) {
        if (colour == felt) {
            return false;
        }
        felt = colour;
        setChanged();
        if (level != null) {
            // Colour lives in the block entity, so a blockstate change will not tell the
            // client about it; this is what does.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    public Optional<UUID> occupantOf(Side side) {
        return Optional.ofNullable(claims.get(side));
    }

    public boolean hasAnyOccupant() {
        return !claims.isEmpty();
    }

    /** Takes an edge for a player, or does nothing if somebody else already has it. */
    public boolean claim(Side side, UUID player) {
        if (claims.containsKey(side)) {
            return false;
        }
        claims.put(side, player);
        setChanged();
        return true;
    }

    /** Gives up an edge, but only for the player who took it. */
    public boolean release(Side side, UUID player) {
        if (!player.equals(claims.get(side))) {
            return false;
        }
        claims.remove(side);
        setChanged();
        return true;
    }

    /** Whichever edge of this table the player holds, if any. */
    public Optional<Side> sideHeldBy(UUID player) {
        return claims.entrySet().stream()
                .filter(entry -> entry.getValue().equals(player))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * What a joining client is told.
     *
     * <p>Only the felt colour: seat claims are player identity and nothing on a client draws
     * them yet, so sending them would be data leaving the server for no reason.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (felt != null) {
            tag.putString(FELT_KEY, felt.getSerializedName());
        }
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        felt = tag.contains(FELT_KEY) ? DyeColor.byName(tag.getString(FELT_KEY), null) : null;
        claims.clear();
        ListTag seats = tag.getList(SEATS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < seats.size(); index++) {
            CompoundTag seat = seats.getCompound(index);
            // Written by name rather than by ordinal: an ordinal is a number that means
            // something different the moment the enum gains a value, and this is a save file.
            Side side = sideNamed(seat.getString(SIDE_KEY));
            if (side != null && seat.hasUUID(PLAYER_KEY)) {
                claims.put(side, seat.getUUID(PLAYER_KEY));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (felt != null) {
            // By name, like the seat sides and for the same reason: this is a save file.
            tag.putString(FELT_KEY, felt.getSerializedName());
        }
        ListTag seats = new ListTag();
        claims.forEach((side, player) -> {
            CompoundTag seat = new CompoundTag();
            seat.putString(SIDE_KEY, side.name());
            seat.putUUID(PLAYER_KEY, player);
            seats.add(seat);
        });
        tag.put(SEATS_KEY, seats);
    }

    private static Side sideNamed(String name) {
        for (Side side : Side.values()) {
            if (side.name().equals(name)) {
                return side;
            }
        }
        // An unreadable seat is an empty seat, never a failed world load.
        return null;
    }
}
