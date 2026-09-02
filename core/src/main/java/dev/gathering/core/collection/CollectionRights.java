package dev.gathering.core.collection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what with one collection.
 * <p>Looking is public and touching is permissioned. A collection is a thing you show off, and
 * being able to browse the playgroup's pool without asking anybody is most of what it is for -
 * so reading is not a right at all, it is just what a collection is. Taking and adding are.
 * <p>Two rights rather than one, because separating them is what buys the shapes people
 * actually build: a donation box is anyone-adds and owner-takes, a lending library is
 * trusted-take and owner-stocks, a display case is neither. One "trusted" flag would collapse
 * all three into the same thing.
 * <p>Owner-only to begin with, so sharing a collection is something somebody did on purpose
 * rather than the default a griefer finds first.
 * <p>Pure.
 */
public record CollectionRights(UUID owner, Set<UUID> mayTake, Set<UUID> mayAdd) {

    /** What a collection nobody has claimed allows, which is nothing. */
    public static final CollectionRights NOBODYS =
            new CollectionRights(null, Set.of(), Set.of());

    public CollectionRights {
        mayTake = unmodifiable(mayTake);
        mayAdd = unmodifiable(mayAdd);
    }

    /** Kept in the order they were let in, so a list of names does not wander about. */
    private static Set<UUID> unmodifiable(Set<UUID> players) {
        if (players == null || players.isEmpty()) {
            return Set.of();
        }
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(players));
    }

    /** A fresh collection, belonging to whoever put it down and to nobody else. */
    public static CollectionRights ownedBy(UUID owner) {
        return new CollectionRights(Objects.requireNonNull(owner, "owner"), Set.of(), Set.of());
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    /**
     * Whether this player may take cards out.
     * <p>Breaking the block needs this too: a collection you cannot take from is a collection
     * you cannot walk off with either.
     */
    public boolean mayTake(UUID player) {
        return player != null && (isOwner(player) || mayTake.contains(player));
    }

    /** Whether this player may put cards in. */
    public boolean mayAdd(UUID player) {
        return player != null && (isOwner(player) || mayAdd.contains(player));
    }

    /**
     * Whether this player may read it, which is everybody.
     * <p>Written out rather than left implicit, because every caller asking "may they?" should
     * be asking this object, and one that has to know reading is free is a caller that will
     * one day guess wrong about it.
     */
    public boolean mayLook(UUID player) {
        return true;
    }

    /** Whether anybody at all besides the owner has been let in. */
    public boolean isShared() {
        return !mayTake.isEmpty() || !mayAdd.isEmpty();
    }

    public CollectionRights allowingTake(UUID player) {
        return with(player, true, true);
    }

    public CollectionRights refusingTake(UUID player) {
        return with(player, true, false);
    }

    public CollectionRights allowingAdd(UUID player) {
        return with(player, false, true);
    }

    public CollectionRights refusingAdd(UUID player) {
        return with(player, false, false);
    }

    /**
     * The same rights, transferred.
     * <p>The new owner comes off both lists: an owner who is also listed would keep the right
     * after being taken off it, which is the kind of thing nobody notices until it matters.
     */
    public CollectionRights ownedNowBy(UUID newOwner) {
        Objects.requireNonNull(newOwner, "newOwner");
        Set<UUID> take = new LinkedHashSet<>(mayTake);
        Set<UUID> add = new LinkedHashSet<>(mayAdd);
        take.remove(newOwner);
        add.remove(newOwner);
        return new CollectionRights(newOwner, take, add);
    }

    private CollectionRights with(UUID player, boolean taking, boolean allowed) {
        if (player == null || isOwner(player)) {
            // The owner's rights are not a list entry, so there is nothing to add and nothing
            // that could be taken away.
            return this;
        }
        Set<UUID> take = new LinkedHashSet<>(mayTake);
        Set<UUID> add = new LinkedHashSet<>(mayAdd);
        Set<UUID> which = taking ? take : add;
        if (allowed) {
            which.add(player);
        } else {
            which.remove(player);
        }
        return new CollectionRights(owner, take, add);
    }
}
