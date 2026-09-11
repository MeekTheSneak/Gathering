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

    /**
     * How loud this player wants the table, or nothing at all.
     * <p>Both settings answered in one place, because both paths out of this class go through
     * it and a toggle honored by one of them is a toggle that half works. Zero means silence,
     * and silence is arranged by not asking the sound engine rather than by asking it for a
     * sound at no volume - a sound at zero is still a sound being scheduled, and the point of
     * turning them off is that nothing happens.
     * <p>The player's own category slider still applies on top of this: these are the noise of
     * people handling cards, and they play under PLAYERS like people do. This is the mod's own
     * slider for somebody who wants the game loud and the table quiet.
     */
    private static float wantedVolume() {
        if (!ClientSettings.tableSounds()) {
            return 0f;
        }
        return VOLUME * Math.clamp(ClientSettings.tableSoundVolume(), 0, 100) / 100f;
    }

    /**
     * One of the table's noises, here, now, at whatever the settings currently say.
     * <p>For the settings screen. A volume you cannot hear while you are setting it is a
     * volume you set twice: once by guessing, and again after going back to a table to find
     * out what you chose.
     * <p>At the player rather than at a table, because the point is to be heard rather than to
     * be located, and there may be no table anywhere near. Silent when the sounds are off,
     * which is itself the answer to "what does off sound like".
     */
    static void preview() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        at(client.player.blockPosition(), dev.gathering.sound.GatheringSounds.SHUFFLE);
    }

    /**
     * The same, for one of the game's own sounds rather than one of the mod's.
     * <p>The mod has three sounds and they are audio files in its resource pack, which is the
     * owner's to add to. A gesture that wants a noise the mod has not got uses vanilla's,
     * which needs nothing added and is already a sound every player knows.
     */
    static void vanillaAt(BlockPos table, SoundEvent sound) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || table == null || sound == null) {
            return;
        }
        float volume = wantedVolume();
        if (volume <= 0f) {
            return;
        }
        client.execute(() -> {
            if (client.level != null) {
                client.level.playLocalSound(
                        table, sound, SoundSource.PLAYERS, volume, 1f, false);
            }
        });
    }

    static void at(BlockPos table, Registered<SoundEvent> sound) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || table == null || !sound.isBound()) {
            return;
        }
        float volume = wantedVolume();
        if (volume <= 0f) {
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
                    table, sound.get(), SoundSource.PLAYERS, volume, pitch, false);
        });
    }
}
