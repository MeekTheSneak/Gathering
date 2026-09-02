package dev.gathering.sound;

import dev.gathering.Gathering;
import dev.gathering.registry.Registered;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * The noises a table makes.
 * <p>Three, and each is the sound of a thing a player does often enough to learn by ear:
 * cards drawn off a deck, a deck shuffled, and a card coming off the top to be looked at or
 * binned. A table you can follow without watching it is worth as much as one you can follow
 * without reading it, and in a game of four, three of the boards are always somewhere other
 * than where you are looking.
 * <p>Made at the table rather than in the listener's ear, so a game two rooms away is quiet
 * and the one at the next table is not. That is also what keeps them honest: a sound says a
 * library was shuffled, which the log says out loud anyway, and says nothing about what is
 * in it.
 */
public final class GatheringSounds {

    public static final String DRAW_ID = "draw_card";
    public static final String SHUFFLE_ID = "shuffle";
    public static final String SCRY_ID = "scry";

    /** A card coming off the top of a library into somebody's hand. */
    public static final Registered<SoundEvent> DRAW = new Registered<>(DRAW_ID);

    /** A library being shuffled. */
    public static final Registered<SoundEvent> SHUFFLE = new Registered<>(SHUFFLE_ID);

    /** A card coming off the top to be looked at, milled, or revealed. */
    public static final Registered<SoundEvent> SCRY = new Registered<>(SCRY_ID);

    private GatheringSounds() {
    }

    /**
     * All of them, in one list, so a loader registers them by walking it.
     * <p>Both loaders register the same three under the same names because they read the
     * same list; a second list in the second loader is how one of them ends up with a sound
     * the other has not got.
     */
    public static List<Registered<SoundEvent>> all() {
        return List.of(DRAW, SHUFFLE, SCRY);
    }

    /** The event to register for one of these, built the same way on either loader. */
    public static SoundEvent create(String id) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, id));
    }
}
