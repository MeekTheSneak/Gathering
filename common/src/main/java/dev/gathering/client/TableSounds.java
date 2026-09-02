package dev.gathering.client;

import dev.gathering.registry.Registered;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Makes a table's noises, at the table.
 * <p>At the table and not in the listener's ear, so a game across the room is quiet and the
 * one you are sitting at is not - which is the whole point of having them: in a game of four,
 * three of the boards are always somewhere other than where you are looking, and a shuffle you
 * can hear is a shuffle you do not have to read about.
 * <p>Under the players' own volume slider, because that is what these are: the noise of people
 * handling cards. Anybody who does not want it has the slider.
 * <p>Client-only. Asked for from the network thread, made on the client thread.
 */
final class TableSounds {

    /**
     * Quiet. These fire on every draw of every game in earshot, and a sound at full volume
     * that happens forty times a turn is a sound people turn off rather than enjoy.
     */
    private static final float VOLUME = 0.55f;

    /** Slightly varied, so a run of draws is a hand riffling rather than a machine. */
    private static final float PITCH_SPREAD = 0.12f;

    private TableSounds() {
    }

    static void at(BlockPos table, Registered<SoundEvent> sound) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || table == null || !sound.isBound()) {
            return;
        }
        // Handed to the client thread rather than made here: a board arrives on the network
        // thread, and the sound engine is not somewhere two threads may both be at once.
        client.execute(() -> {
            if (client.level == null) {
                return;
            }
            float pitch = 1f + (client.level.random.nextFloat() - 0.5f) * 2f * PITCH_SPREAD;
            client.level.playLocalSound(
                    table, sound.get(), SoundSource.PLAYERS, VOLUME, pitch, false);
        });
    }
}
