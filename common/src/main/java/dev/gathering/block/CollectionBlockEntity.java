package dev.gathering.block;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.core.story.CardStory;
import dev.gathering.item.GatheringContent;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final String STORIED_KEY = "Storied";
    private static final String STORY_KEY = "Story";

    private CardTally cards = CardTally.EMPTY;

    /**
     * The copies in here that have a history, beside the count rather than in it.
     *
     * <p>A collection stores counts - forty Forests is one entry with a forty on it, which is
     * what makes ten thousand cards fit - and a count cannot hold a story. So the handful of
     * cards that have one are kept as themselves alongside, and they are still counted in the
     * tally: the box holds three of a card whether or not one of them was won off somebody.
     *
     * <p>Ordinary copies leave first. The one somebody won in an ante game stays at the bottom
     * of the box until it is the only one left, which is both what a person does with a card
     * like that and the safe way round: a trophy traded away by accident is the one loss here
     * that could not be undone.
     */
    private List<StoriedCard> storied = List.of();
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

    /** One card in here that remembers something, and what it remembers. */
    public record StoriedCard(CardIdentity card, CardStory story) {
    }

    /** The ones with a history, oldest first. */
    public List<StoriedCard> storied() {
        return storied;
    }

    /**
     * Puts one card in, keeping whatever it remembers.
     *
     * <p>The count goes up either way; the story is kept beside it. A collection that quietly
     * dropped a card's history the moment it was put away would be the one place in the mod
     * that destroys something a player cannot get back.
     */
    public void putStoried(CardIdentity card, CardStory story) {
        put(card, 1);
        if (story == null || story.isEmpty() || storied.size() >= MOST_STORIED) {
            return;
        }
        List<StoriedCard> more = new java.util.ArrayList<>(storied);
        more.add(new StoriedCard(card, story));
        storied = List.copyOf(more);
        setChanged();
    }

    /**
     * How many stories one box keeps.
     *
     * <p>A bound rather than a rule anybody will meet: these are cards won, traded for and
     * pulled as rares, so a box with a thousand of them is a box somebody has played a great
     * deal with. Past it the count still rises and the story is what is lost, because a card
     * that stopped being storable would be a card somebody could not put away.
     */
    private static final int MOST_STORIED = 1024;

    /**
     * Takes the story of one copy of this card, if a copy in here has one.
     *
     * <p>Asked for only once the plain copies are gone, so what comes out of a box is an
     * ordinary card until there are no ordinary ones left. The most recently put away comes
     * out first: it is the one somebody is most likely to be looking for.
     */
    public CardStory takeStory(CardIdentity card) {
        for (int index = storied.size() - 1; index >= 0; index--) {
            if (!storied.get(index).card().equals(card)) {
                continue;
            }
            List<StoriedCard> fewer = new java.util.ArrayList<>(storied);
            CardStory story = fewer.remove(index).story();
            storied = List.copyOf(fewer);
            setChanged();
            return story;
        }
        return CardStory.NONE;
    }

    /** How many copies of this card in here have a history. */
    public int storiedCount(CardIdentity card) {
        int found = 0;
        for (StoriedCard one : storied) {
            if (one.card().equals(card)) {
                found++;
            }
        }
        return found;
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
            prune(card);
            setChanged();
        }
        return taken.took();
    }

    /**
     * Drops stories the box no longer has copies to hang them on.
     *
     * <p>Here rather than at each place cards leave, because they leave several ways - one at
     * a time into a hand, a hundred at a time into a deck being sleeved - and a story left
     * behind by a copy that has gone would be a card's history attached to nothing, handed to
     * whoever took the next copy of that printing out. One rule, at the one door every
     * departure goes through.
     *
     * <p>The oldest go first, so what is left is the most recent history the box holds.
     */
    private void prune(CardIdentity card) {
        int copies = cards.of(card);
        int stories = storiedCount(card);
        if (stories <= copies) {
            return;
        }
        List<StoriedCard> kept = new java.util.ArrayList<>(storied);
        int dropping = stories - copies;
        for (int index = 0; index < kept.size() && dropping > 0; ) {
            if (kept.get(index).card().equals(card)) {
                kept.remove(index);
                dropping--;
            } else {
                index++;
            }
        }
        storied = List.copyOf(kept);
    }

    // ------------------------------------------------------------ on the disk

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards = readCards(tag);
        storied = readStoried(tag);
        label = tag.getString(LABEL_KEY);
        UUID owner = tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null;
        rights = new CollectionRights(owner, readPlayers(tag, MAY_TAKE_KEY),
                readPlayers(tag, MAY_ADD_KEY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeCards(tag);
        writeStoried(tag);
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
     * The cards in here that remember something, off the disk.
     *
     * <p>Through the same codec the component on an item uses, so a card put away and taken
     * out again is the same card - one written format for one thing, rather than two that can
     * come to disagree about what a story is.
     */
    private static List<StoriedCard> readStoried(CompoundTag tag) {
        if (!tag.contains(STORIED_KEY, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag stored = tag.getList(STORIED_KEY, Tag.TAG_COMPOUND);
        List<StoriedCard> found = new java.util.ArrayList<>(stored.size());
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag entry = stored.getCompound(index);
            boolean foil = entry.getBoolean(FOIL_KEY);
            CardIdentity card = entry.hasUUID(PRINTING_KEY)
                    ? CardIdentity.ofPrinting(entry.getUUID(PRINTING_KEY), foil)
                    : entry.contains(CUSTOM_KEY)
                            ? CardIdentity.ofCustom(entry.getString(CUSTOM_KEY), foil)
                            : null;
            if (card == null) {
                continue;
            }
            dev.gathering.item.StoryComponent.CODEC
                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get(STORY_KEY))
                    .result()
                    // A story that will not parse is a card that has forgotten, never a
                    // collection that will not load: somebody's box is not worth losing over
                    // a chapter an older version wrote differently.
                    .ifPresent(story -> found.add(new StoriedCard(card, story.story())));
        }
        return List.copyOf(found);
    }

    private void writeStoried(CompoundTag tag) {
        if (storied.isEmpty()) {
            return;
        }
        ListTag stored = new ListTag();
        for (StoriedCard one : storied) {
            CompoundTag written = new CompoundTag();
            one.card().printing().ifPresentOrElse(
                    printing -> written.putUUID(PRINTING_KEY, printing),
                    () -> one.card().custom().ifPresent(
                            custom -> written.putString(CUSTOM_KEY, custom)));
            if (one.card().foil()) {
                written.putBoolean(FOIL_KEY, true);
            }
            dev.gathering.item.StoryComponent.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                            dev.gathering.item.StoryComponent.of(one.story()))
                    .result()
                    .ifPresent(story -> written.put(STORY_KEY, story));
            stored.add(written);
        }
        tag.put(STORIED_KEY, stored);
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
