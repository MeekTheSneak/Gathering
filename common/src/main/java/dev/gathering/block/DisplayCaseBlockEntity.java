package dev.gathering.block;

import dev.gathering.item.CardComponent;
import dev.gathering.item.GatheringContent;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one card a display case is showing, and whose case it is.
 * <p>Everything in here is public by definition: a card in a case is a card being shown to a room, and
 * the case is glass. That is why it is the only place in this mod where a card's identity is sent to
 * every client in sight - the card goes in face up and is stored face up, so there is no hidden
 * identity here to leak. A face-down card is never accepted; see {@link DisplayCaseBlock}.
 */
public class DisplayCaseBlockEntity extends BlockEntity {

    /** The block entity's own id, which both loaders register it under. */
    public static final String ID = "display_case";

    private static final String CARD_KEY = "Card";
    private static final String OWNER_KEY = "Owner";

    private CardComponent card;
    private UUID owner;

    public DisplayCaseBlockEntity(BlockPos pos, BlockState state) {
        super(GatheringContent.DISPLAY_CASE_ENTITY.get(), pos, state);
    }

    /** What is on show, if anything. */
    public Optional<CardComponent> card() {
        return Optional.ofNullable(card);
    }

    public boolean isEmpty() {
        return card == null;
    }

    /** Whose case it is: whoever put it down, and nobody after that unless they break it. */
    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public boolean isOwner(UUID player) {
        return owner == null || owner.equals(player);
    }

    /** Claims an unclaimed case. A case that already belongs to somebody keeps them. */
    public void claimFor(UUID player) {
        if (player != null && owner == null) {
            owner = player;
            setChanged();
        }
    }

    /**
     * Puts a card on show, face up whatever way round it arrived.
     * <p>Face up because a case is glass and what is in it is sent to everybody who can see the block.
     * A face-down card put in here would be a hidden identity on every client in the room.
     */
    public void show(CardComponent showing) {
        this.card = showing == null ? null : showing.faceUp();
        changed();
    }

    /** Takes the card back out, if there is one. */
    public Optional<CardComponent> take() {
        Optional<CardComponent> taken = card();
        this.card = null;
        changed();
        return taken;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            // The card is drawn in the world, so every client that can see the block has to be told.
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null;
        card = tag.contains(CARD_KEY)
                ? CardComponent.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(CARD_KEY))
                        .result().map(CardComponent::faceUp).orElse(null)
                : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(OWNER_KEY, owner);
        }
        if (card != null) {
            CardComponent.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, card)
                    .result().ifPresent(written -> tag.put(CARD_KEY, written));
        }
    }

    /**
     * What a client is told about a case it can see: the card, which is the whole point of the block.
     * <p>The owner travels with it too, and that is not a leak - a case says whose it is when somebody
     * tries to take from it, so the id is already public to anybody who clicks one.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
