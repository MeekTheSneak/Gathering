package dev.gathering.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The noise a booster makes while it is being opened.
 * <p>There was none. A pack was torn across in silence, which is the one sense in which the ceremony was
 * not a ceremony at all - the owner asked for the tear to feel better (2026-09-16) and a tear you cannot
 * hear is half a tear. Paper catches and gives in short bursts, so the crinkle is not a note held down for
 * the length of a drag: it fires once every short stretch of the tear, rising in pitch as the wrapper
 * tightens, and the whole thing finishes on a rip.
 * <p>Vanilla sounds, deliberately. Every sound this mod ships is the owner's and there is no pack sound
 * among them; a page turning is the closest paper in the game, and putting one in the jar is not this
 * project's call to make.
 * <p>Under the player's own table-sound setting, because that setting is what it says it is - the noise of
 * somebody handling cards - and a pack is the loudest handling of cards there is.
 * <p>Client-only.
 */
final class PackSounds {

    /** How much of the tear goes by between one crinkle and the next. */
    private static final float EVERY = 0.07f;

    /** The pitch at the corner and at the far end: paper tightens as the tear runs out of slack. */
    private static final float FROM_PITCH = 0.85f;
    private static final float TO_PITCH = 1.45f;

    /** Quiet: this fires a dozen times across one pack. */
    private static final float CRINKLE = 0.35f;

    /** The rip at the end, which happens once and is allowed to be heard. */
    private static final float RIP = 0.7f;

    private PackSounds() {
    }

    /**
     * Crinkles if the tear has gone far enough since the last one.
     *
     * @param soundedAt how far along the tear was when it last made a noise
     * @param torn      how far along it is now
     * @return how far along it was when it last made a noise, for the next call
     */
    static float tearing(float soundedAt, float torn) {
        if (torn - soundedAt < EVERY) {
            return soundedAt;
        }
        play(SoundEvents.BOOK_PAGE_TURN, FROM_PITCH + (TO_PITCH - FROM_PITCH) * Math.min(1f, torn), CRINKLE);
        return torn;
    }

    /** The wrapper coming apart. */
    static void opened() {
        play(SoundEvents.WOOL_BREAK, TO_PITCH, RIP);
    }

    /** Taking hold of the corner, which is the one thing about this nobody is told how to do. */
    static void gripped() {
        play(SoundEvents.BOOK_PAGE_TURN, FROM_PITCH, CRINKLE);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        float wanted = volume * ClientSettings.tableSoundVolume() / 100f;
        if (!ClientSettings.tableSounds() || wanted <= 0f) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound, pitch, wanted));
    }
}
