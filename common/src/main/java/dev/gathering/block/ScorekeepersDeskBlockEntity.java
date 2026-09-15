package dev.gathering.block;

import dev.gathering.item.GatheringContent;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Which tournament a Scorekeeper's Desk is the desk of. Nothing else is kept here: the event keeps the rest. */
public class ScorekeepersDeskBlockEntity extends BlockEntity {

    public static final String ID = "scorekeepers_desk";
    private static final String EVENT_KEY = "event";

    private UUID event;

    public ScorekeepersDeskBlockEntity(BlockPos pos, BlockState state) {
        super(GatheringContent.SCOREKEEPERS_DESK_ENTITY.get(), pos, state);
    }

    public Optional<UUID> event() {
        return Optional.ofNullable(event);
    }

    public void runs(UUID tournament) {
        if (tournament == null ? event != null : !tournament.equals(event)) {
            event = tournament;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (event != null) {
            tag.putUUID(EVENT_KEY, event);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        event = tag.hasUUID(EVENT_KEY) ? tag.getUUID(EVENT_KEY) : null;
    }
}
