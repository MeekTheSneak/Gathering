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

    private static final String SEATS_KEY = "seats";
    private static final String SIDE_KEY = "side";
    private static final String PLAYER_KEY = "player";

    private final Map<Side, UUID> claims = new EnumMap<>(Side.class);

    public TableBlockEntity(BlockPos pos, BlockState state) {
        super(dev.gathering.item.GatheringContent.TABLE_ENTITY.get(), pos, state);
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

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
