package dev.gathering.core.collection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what with one collection.
 * <p>Three rights rather than one, because separating them is what buys the shapes people
 * actually build: a donation box is anyone-adds and owner-takes, a lending library is
 * trusted-take and owner-stocks, a display case is neither, and a locked cabinet is none of
 * them - not even to look in. One "trusted" flag would collapse all four into the same thing.
 * <p>Looking is open to begin with and can be closed. A collection is a thing you show off, and
 * being able to browse the playgroup's pool without asking anybody is most of what it is for,
 * so that is the default; but the owner asked to be able to lock one outright (2026-09-16), and
 * a cabinet in a shared base whose contents anybody can read is a cabinet whose owner knows
 * exactly what to take from it. Closed, it is the owner's and whoever has been let in.
 * <p>Whoever may take from it or add to it may look in it, whether or not they are on the
 * looking list: taking from a box you cannot see into is not a thing anybody could do, and a
 * right granted with a second right needed to use it is a right that does not work.
 * <p>Owner-only for touching to begin with, so sharing a collection is something somebody did
 * on purpose rather than the default a griefer finds first.
 * <p>Pure.
 */
public record CollectionRights(
        UUID owner, Everyone everyone, Set<UUID> mayLook, Set<UUID> mayTake, Set<UUID> mayAdd) {

    /**
     * What anybody at all may do, before any name is on any list.
     * <p>Three switches rather than one lock, because "anyone may look, only I may take" is the shape a
     * collection is usually in and it was the one thing the owner could not say out loud: the screen had a
     * lock and nothing else, so every arrangement but "mine" and "everybody's to read" had to be built one
     * name at a time. Looking open and the other two shut is what a collection starts as.
     *
     * @param looks whether anybody may see what is in it
     * @param takes whether anybody may take cards out - which is also what lets them break the block
     * @param adds  whether anybody may put cards in
     */
    public record Everyone(boolean looks, boolean takes, boolean adds) {

        /** A collection you may read and not touch, which is what one is for. */
        public static final Everyone LOOK = new Everyone(true, false, false);

        /** Shut: the owner and whoever they have let in. */
        public static final Everyone NOBODY = new Everyone(false, false, false);
    }

    /** What a collection nobody has claimed allows: looking, which is what an empty shelf is for. */
    public static final CollectionRights NOBODYS =
            new CollectionRights(null, Everyone.LOOK, Set.of(), Set.of(), Set.of());

    public CollectionRights {
        everyone = everyone == null ? Everyone.NOBODY : everyone;
        mayLook = unmodifiable(mayLook);
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
        return new CollectionRights(
                Objects.requireNonNull(owner, "owner"), Everyone.LOOK, Set.of(), Set.of(), Set.of());
    }

    /** The same rights, with what anybody at all may do set to this. */
    public CollectionRights allowingEveryone(Everyone what) {
        return new CollectionRights(owner, what, mayLook, mayTake, mayAdd);
    }

    /** Whether anybody at all may look, which is what "open" used to mean on its own. */
    public boolean open() {
        return everyone.looks();
    }

    /** The same rights, with looking open to everybody or shut to everybody not let in. */
    public CollectionRights openedToLook(boolean toEverybody) {
        return allowingEveryone(new Everyone(toEverybody, everyone.takes(), everyone.adds()));
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
        return everyone.takes() || (player != null && (isOwner(player) || mayTake.contains(player)));
    }

    /** Whether this player may put cards in. */
    public boolean mayAdd(UUID player) {
        return everyone.adds() || (player != null && (isOwner(player) || mayAdd.contains(player)));
    }

    /**
     * Whether this player may look in it.
     * <p>Everybody, while it is open. Closed, the owner and whoever has been let in - by name, or by
     * being allowed to take from it or add to it, neither of which anybody could do blind.
     */
    public boolean mayLook(UUID player) {
        if (everyone.looks() || everyone.takes() || everyone.adds()) {
            // Anybody allowed to touch it can see it: a box you cannot look into is a box you cannot
            // take from either.
            return true;
        }
        return player != null
                && (isOwner(player) || mayLook.contains(player) || mayTake.contains(player) || mayAdd.contains(player));
    }

    /** Whether anybody at all besides the owner has been let in. */
    public boolean isShared() {
        return !mayLook.isEmpty() || !mayTake.isEmpty() || !mayAdd.isEmpty();
    }

    /** Everybody who has been let in to do anything, in the order they were let in. */
    public Set<UUID> everybodyLetIn() {
        Set<UUID> all = new LinkedHashSet<>(mayLook);
        all.addAll(mayTake);
        all.addAll(mayAdd);
        return java.util.Collections.unmodifiableSet(all);
    }

    public CollectionRights allowingLook(UUID player) {
        return with(player, Right.LOOK, true);
    }

    public CollectionRights refusingLook(UUID player) {
        return with(player, Right.LOOK, false);
    }

    /** Off every list: the whole of what "this person is no longer let in" means. */
    public CollectionRights refusingEverything(UUID player) {
        return refusingLook(player).refusingTake(player).refusingAdd(player);
    }

    /** Which of the three lists a change is about. */
    private enum Right { LOOK, TAKE, ADD }

    public CollectionRights allowingTake(UUID player) {
        return with(player, Right.TAKE, true);
    }

    public CollectionRights refusingTake(UUID player) {
        return with(player, Right.TAKE, false);
    }

    public CollectionRights allowingAdd(UUID player) {
        return with(player, Right.ADD, true);
    }

    public CollectionRights refusingAdd(UUID player) {
        return with(player, Right.ADD, false);
    }

    /**
     * The same rights, transferred.
     * <p>The new owner comes off both lists: an owner who is also listed would keep the right
     * after being taken off it, which is the kind of thing nobody notices until it matters.
     */
    public CollectionRights ownedNowBy(UUID newOwner) {
        Objects.requireNonNull(newOwner, "newOwner");
        Set<UUID> look = new LinkedHashSet<>(mayLook);
        Set<UUID> take = new LinkedHashSet<>(mayTake);
        Set<UUID> add = new LinkedHashSet<>(mayAdd);
        look.remove(newOwner);
        take.remove(newOwner);
        add.remove(newOwner);
        return new CollectionRights(newOwner, everyone, look, take, add);
    }

    private CollectionRights with(UUID player, Right right, boolean allowed) {
        if (player == null || isOwner(player)) {
            // The owner's rights are not a list entry, so there is nothing to add and nothing
            // that could be taken away.
            return this;
        }
        Set<UUID> look = new LinkedHashSet<>(mayLook);
        Set<UUID> take = new LinkedHashSet<>(mayTake);
        Set<UUID> add = new LinkedHashSet<>(mayAdd);
        Set<UUID> which = switch (right) {
            case LOOK -> look;
            case TAKE -> take;
            case ADD -> add;
        };
        if (allowed) {
            which.add(player);
        } else {
            which.remove(player);
        }
        return new CollectionRights(owner, everyone, look, take, add);
    }
}
