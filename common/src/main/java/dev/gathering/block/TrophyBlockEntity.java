package dev.gathering.block;

import dev.gathering.item.GatheringContent;
import dev.gathering.item.TrophyComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a trophy on a shelf is engraved with, and what color it was cast in.
 * <p>The same three lines and the same color the item carries, kept here from the moment it is put
 * down until the moment it is broken, when they go back onto the stack. There is no other copy: a
 * block that worked the engraving out from anything would be a trophy that changes its mind.
 * <p>Sent to every client that can see the block, which is the whole point - the color is what the
 * thing looks like, and a trophy is meant to be shown. Nothing here is hidden from anybody.
 */
public class TrophyBlockEntity extends BlockEntity {

    /** The block entity's own id, which both loaders register it under. */
    public static final String ID = "trophy";

    private static final String WON_KEY = "Won";

    private TrophyComponent won = TrophyComponent.BLANK;

    public TrophyBlockEntity(BlockPos pos, BlockState state) {
        super(GatheringContent.TROPHY_ENTITY.get(), pos, state);
    }

    /** What it says, and what color it is. Never null: an unengraved cup is still a color. */
    public TrophyComponent engraved() {
        return won;
    }

    /** Takes the engraving off the stack that was placed. */
    public void engrave(TrophyComponent engraving) {
        if (engraving == null || engraving.equals(won)) {
            return;
        }
        won = engraving;
        setChanged();
        if (level != null) {
            // The color is drawn in the world, so every client that can see the block has to be told.
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        won = tag.contains(WON_KEY)
                ? TrophyComponent.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(WON_KEY))
                        .result().orElse(TrophyComponent.BLANK)
                : TrophyComponent.BLANK;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        TrophyComponent.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, won)
                .result().ifPresent(written -> tag.put(WON_KEY, written));
    }

    /** What a client is told about a trophy it can see: what it says and what color it is. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
