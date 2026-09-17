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
 * The cards a display case is showing, and whose case it is.
 * <p>Four of them, standing in a row behind the glass. It held one when it was first built and the owner
 * asked for a case that shows a handful and stands beside a shop counter (2026-09-16) - which is what a
 * case in a card shop is: a row of the good ones, at the height you lean over to look at them.
 * <p>Everything in here is public by definition: a card in a case is a card being shown to a room, and
 * the case is glass. That is why it is the only place in this mod where a card's identity is sent to
 * every client in sight - cards go in face up and are stored face up, so there is no hidden identity
 * here to leak. A face-down card is turned over on the way in; see {@link DisplayCaseBlock}.
 */
public class DisplayCaseBlockEntity extends BlockEntity {

    /** The block entity's own id, which both loaders register it under. */
    public static final String ID = "display_case";

    /** How many a case shows. Four across the front of a block is a row you can read at a glance. */
    public static final int HOLDS = dev.gathering.core.ui.DisplayCaseRow.HOLDS;

    private static final String CARDS_KEY = "Cards";
    private static final String CARD_KEY = "Card";
    private static final String OWNER_KEY = "Owner";
    private static final String LOCKED_KEY = "Locked";

    private final java.util.List<CardComponent> cards = new java.util.ArrayList<>();
    private UUID owner;
    private boolean locked;

    /**
     * The card as an item, which is what the renderer draws it from.
     * <p>Built when the card changes rather than every frame: a case is drawn sixty times a second for as
     * long as somebody is looking at it, and a stack built per frame is a stack built per frame.
     */
    private final java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();

    public DisplayCaseBlockEntity(BlockPos pos, BlockState state) {
        super(GatheringContent.DISPLAY_CASE_ENTITY.get(), pos, state);
    }

    /** What is on show, in the order it was put in. */
    public java.util.List<CardComponent> cards() {
        return java.util.List.copyOf(cards);
    }

    /**
     * Whether the glass is shut, so nothing goes in or comes out.
     * <p>Against the owner's own hand as much as anybody else's: a case is a thing people walk past,
     * and an empty-handed right-click on the way past took the card out of it. Everything in this
     * block is already the owner's alone, so a lock that only shut other people out would do nothing.
     */
    public boolean isLocked() {
        return locked;
    }

    /** Shuts the glass, or opens it. The caller decides who is allowed to ask. */
    public void setLocked(boolean shut) {
        if (locked != shut) {
            locked = shut;
            changed();
        }
    }

    /** The first card, for everything that only wants to know whether there is one. */
    public Optional<CardComponent> card() {
        return cards.isEmpty() ? Optional.empty() : Optional.of(cards.get(0));
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public boolean isFull() {
        return cards.size() >= HOLDS;
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
     *
     * @return whether there was room for it
     */
    public boolean show(CardComponent showing) {
        if (showing == null || isFull()) {
            return false;
        }
        cards.add(showing.faceUp());
        changed();
        return true;
    }

    /** The cards as items, for whatever is drawing them. Empty when the case is. */
    public java.util.List<net.minecraft.world.item.ItemStack> asStacks() {
        return stacks;
    }

    /** Takes the last card put in back out, which is the one nearest the hand reaching in. */
    public Optional<CardComponent> take() {
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        CardComponent taken = cards.remove(cards.size() - 1);
        changed();
        return Optional.of(taken);
    }

    private void changed() {
        stacks.clear();
        for (CardComponent card : cards) {
            stacks.add(dev.gathering.item.CardItem.of(card));
        }
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
        locked = tag.getBoolean(LOCKED_KEY);
        cards.clear();
        // The one-card key first, so a case put down before it held four keeps what is in it.
        if (tag.contains(CARD_KEY)) {
            CardComponent.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(CARD_KEY))
                    .result().map(CardComponent::faceUp).ifPresent(cards::add);
        }
        net.minecraft.nbt.ListTag written = tag.getList(CARDS_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < written.size() && cards.size() < HOLDS; index++) {
            CardComponent.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, written.get(index))
                    .result().map(CardComponent::faceUp).ifPresent(cards::add);
        }
        stacks.clear();
        for (CardComponent card : cards) {
            stacks.add(dev.gathering.item.CardItem.of(card));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(OWNER_KEY, owner);
        }
        if (locked) {
            tag.putBoolean(LOCKED_KEY, true);
        }
        if (!cards.isEmpty()) {
            net.minecraft.nbt.ListTag written = new net.minecraft.nbt.ListTag();
            for (CardComponent card : cards) {
                CardComponent.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, card)
                        .result().ifPresent(written::add);
            }
            tag.put(CARDS_KEY, written);
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
