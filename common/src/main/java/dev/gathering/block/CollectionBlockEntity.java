package dev.gathering.block;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.GatheringContent;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a collection lives.
 *
 * <p>One shared inventory rather than a per-player one. A private view would make this a
 * convenience; one shared pile makes it social infrastructure, which is the point - a
 * playgroup pools a collection, a gym leader keeps their decks in the clubhouse, a server
 * runs a lending library.
 *
 * <p>Counts rather than stacks, so a real collection fits. Ten thousand cards is a few
 * hundred entries here and would be four hundred double chests anywhere else.
 *
 * <p>Nothing about who owns it or what is in it is sent to a client with the block. A
 * collection is public to read, but reading it is a screen somebody opened on purpose, and
 * pushing every card in it to every player who walks past would be a lot of bytes for a
 * question nobody asked.
 */
public class CollectionBlockEntity extends BlockEntity {

    public static final String ID = "collection";

    private static final String CARDS_KEY = "Cards";
    private static final String COUNT_KEY = "Count";
    private static final String PRINTING_KEY = "Printing";
    private static final String CUSTOM_KEY = "Custom";
    private static final String FOIL_KEY = "Foil";
    private static final String OWNER_KEY = "Owner";
    private static final String MAY_TAKE_KEY = "MayTake";
    private static final String MAY_ADD_KEY = "MayAdd";
    private static final String LABEL_KEY = "Label";

    private CardTally cards = CardTally.EMPTY;
    private CollectionRights rights = CollectionRights.NOBODYS;
    private String label = "";

    public CollectionBlockEntity(BlockPos pos, BlockState state) {
        super(GatheringContent.COLLECTION_ENTITY.get(), pos, state);
    }

    public CardTally cards() {
        return cards;
    }

    public CollectionRights rights() {
        return rights;
    }

    /** What this one is called, or blank. A playgroup ends up with several. */
    public String label() {
        return label;
    }

    public void setLabel(String newLabel) {
        this.label = newLabel == null ? "" : newLabel;
        setChanged();
    }

    /** Claimed by whoever put it down, once. A collection already owned is left alone. */
    public void claimFor(UUID player) {
        if (player == null || rights.owner() != null) {
            return;
        }
        rights = CollectionRights.ownedBy(player);
        setChanged();
    }

    public void setRights(CollectionRights newRights) {
        this.rights = newRights == null ? CollectionRights.NOBODYS : newRights;
        setChanged();
    }

    /**
     * Puts cards in.
     *
     * <p>Says nothing about whether they were allowed to; that is asked before this is
     * called, by whoever has the player.
     */
    public void put(CardIdentity card, int howMany) {
        CardTally now = cards.plus(card, howMany);
        if (now != cards) {
            cards = now;
            setChanged();
        }
    }

    public void putAll(CardTally more) {
        CardTally now = cards.plus(more);
        if (!now.equals(cards)) {
            cards = now;
            setChanged();
        }
    }

    /** Takes cards out, and says how many actually came. */
    public int take(CardIdentity card, int howMany) {
        CardTally.Taking taken = cards.take(card, howMany);
        if (taken.took() > 0) {
            cards = taken.left();
            setChanged();
        }
        return taken.took();
    }

    /** Everything, out at once - what breaking the block does. */
    public CardTally emptyOut() {
        CardTally held = cards;
        if (!held.isEmpty()) {
            cards = CardTally.EMPTY;
            setChanged();
        }
        return held;
    }

    public void setCards(CardTally newCards) {
        this.cards = CardTally.orEmpty(newCards);
        setChanged();
    }

    // ------------------------------------------------------------ on the disk

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards = readCards(tag);
        label = tag.getString(LABEL_KEY);
        UUID owner = tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null;
        rights = new CollectionRights(owner, readPlayers(tag, MAY_TAKE_KEY),
                readPlayers(tag, MAY_ADD_KEY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeCards(tag);
        if (!label.isEmpty()) {
            tag.putString(LABEL_KEY, label);
        }
        if (rights.owner() != null) {
            tag.putUUID(OWNER_KEY, rights.owner());
        }
        writePlayers(tag, MAY_TAKE_KEY, rights.mayTake());
        writePlayers(tag, MAY_ADD_KEY, rights.mayAdd());
    }

    /**
     * Reads the tally back.
     *
     * <p>An entry that will not read is dropped and the rest are kept. Losing one card of ten
     * thousand to a corrupted byte is a bad day; losing all ten thousand because of it is the
     * end of somebody's server.
     */
    private static CardTally readCards(CompoundTag tag) {
        ListTag stored = tag.getList(CARDS_KEY, Tag.TAG_COMPOUND);
        CardTally.Builder building = CardTally.builder();
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag entry = stored.getCompound(index);
            int count = entry.getInt(COUNT_KEY);
            if (count <= 0) {
                continue;
            }
            boolean foil = entry.getBoolean(FOIL_KEY);
            if (entry.hasUUID(PRINTING_KEY)) {
                building.add(CardIdentity.ofPrinting(entry.getUUID(PRINTING_KEY), foil), count);
            } else if (!entry.getString(CUSTOM_KEY).isBlank()) {
                building.add(CardIdentity.ofCustom(entry.getString(CUSTOM_KEY), foil), count);
            }
        }
        return building.build();
    }

    private void writeCards(CompoundTag tag) {
        ListTag stored = new ListTag();
        for (Map.Entry<CardIdentity, Integer> entry : cards.counts().entrySet()) {
            CardIdentity card = entry.getKey();
            CompoundTag written = new CompoundTag();
            card.printing().ifPresent(printing -> written.putUUID(PRINTING_KEY, printing));
            card.custom().ifPresent(custom -> written.putString(CUSTOM_KEY, custom));
            if (card.foil()) {
                written.putBoolean(FOIL_KEY, true);
            }
            written.putInt(COUNT_KEY, entry.getValue());
            stored.add(written);
        }
        tag.put(CARDS_KEY, stored);
    }

    private static Set<UUID> readPlayers(CompoundTag tag, String key) {
        ListTag stored = tag.getList(key, Tag.TAG_INT_ARRAY);
        Set<UUID> players = new LinkedHashSet<>();
        for (int index = 0; index < stored.size(); index++) {
            players.add(net.minecraft.nbt.NbtUtils.loadUUID(stored.get(index)));
        }
        return players;
    }

    private static void writePlayers(CompoundTag tag, String key, Set<UUID> players) {
        if (players.isEmpty()) {
            return;
        }
        ListTag stored = new ListTag();
        for (UUID player : players) {
            stored.add(net.minecraft.nbt.NbtUtils.createUUID(player));
        }
        tag.put(key, stored);
    }

}
