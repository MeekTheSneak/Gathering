package dev.gathering.block;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.persistence.SessionCipher;
import dev.gathering.core.game.persistence.StoredSession;
import dev.gathering.core.table.Side;
import dev.gathering.server.SessionKeyring;
import java.io.IOException;
import javax.crypto.SecretKey;
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

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    public static final String ID = "table";

    private static final String FELT_KEY = "felt";
    private static final String SEATS_KEY = "seats";
    private static final String SESSION_OPEN_KEY = "session_open";
    private static final String SESSION_SEALED_KEY = "session_sealed";
    private static final String STARTING_LIFE_KEY = "starting_life";
    private static final String SIDE_KEY = "side";
    private static final String PLAYER_KEY = "player";

    private final Map<Side, UUID> claims = new EnumMap<>(Side.class);

    /** Empty for the felt's own colour; a dye replaces it. */
    private DyeColor felt;

    /**
     * The game on this cluster, if there is one and this is the table holding it.
     *
     * <p>Restored from {@link #stored} the first time somebody asks rather than at load,
     * because reading it needs the session key and a key that cannot be read must never be
     * the reason a world fails to load.
     */
    private GameSession session;

    private StoredSession stored;
    private int startingLife;
    private boolean restoreFailed;

    public TableBlockEntity(BlockPos pos, BlockState state) {
        super(dev.gathering.item.GatheringContent.TABLE_ENTITY.get(), pos, state);
    }

    /**
     * The game on this table, opening it from storage if this is the first time anybody has
     * asked since the world loaded.
     */
    public Optional<GameSession> session() {
        if (session == null && stored != null && !restoreFailed) {
            restoreFailed = true;
            SessionKeyring.key().ifPresent(key -> {
                try {
                    session = stored.restore(key);
                    restoreFailed = false;
                } catch (IOException | SessionCipher.SealedStreamException e) {
                    // The table keeps the bytes: an unopenable session is still somebody's
                    // game, and overwriting it with nothing would be the one irreversible
                    // thing to do about it.
                    LOGGER.error("The session at {} will not open: {}", worldPosition, e.getMessage());
                }
            });
        }
        return Optional.ofNullable(session);
    }

    public boolean hasSession() {
        return session != null || stored != null;
    }

    public void beginSession(GameSession newSession, int life) {
        this.session = newSession;
        this.startingLife = life;
        this.stored = null;
        this.restoreFailed = false;
        setChanged();
    }

    public void endSession() {
        this.session = null;
        this.stored = null;
        this.restoreFailed = false;
        setChanged();
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

        session = null;
        restoreFailed = false;
        startingLife = tag.getInt(STARTING_LIFE_KEY);
        stored = tag.contains(SESSION_OPEN_KEY)
                ? new StoredSession(tag.getByteArray(SESSION_OPEN_KEY), tag.getByteArray(SESSION_SEALED_KEY))
                : null;

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
        writeSession(tag);
        ListTag seats = new ListTag();
        claims.forEach((side, player) -> {
            CompoundTag seat = new CompoundTag();
            seat.putString(SIDE_KEY, side.name());
            seat.putUUID(PLAYER_KEY, player);
            seats.add(seat);
        });
        tag.put(SEATS_KEY, seats);
    }

    /**
     * Writes the game down, sealed.
     *
     * <p>A session that could not be opened is written back exactly as it was found. It is
     * still somebody's game, and replacing it with nothing because this server could not read
     * it is the one irreversible thing available here.
     */
    private void writeSession(CompoundTag tag) {
        StoredSession toWrite = stored;
        if (session != null) {
            Optional<SecretKey> key = SessionKeyring.key();
            if (key.isEmpty()) {
                LOGGER.error("No session key, so the game at {} cannot be saved", worldPosition);
                return;
            }
            try {
                toWrite = StoredSession.of(session, startingLife, key.get());
            } catch (IOException e) {
                LOGGER.error("Could not write the game at {}: {}", worldPosition, e.getMessage());
                return;
            }
        }
        if (toWrite == null) {
            return;
        }
        tag.putByteArray(SESSION_OPEN_KEY, toWrite.openPart());
        tag.putByteArray(SESSION_SEALED_KEY, toWrite.sealedPart());
        tag.putInt(STARTING_LIFE_KEY, startingLife);
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
