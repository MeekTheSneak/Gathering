package dev.gathering.client;

import dev.gathering.core.game.Zone;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * What a zone is called, in one place.
 *
 * <p>Three separate things name a zone at the player - the screen that opens a pile, the
 * seated board, and the board drawn on the block - and a zone that is "Graveyard" in one and
 * "Yard" in another is two rules where there should be one. The key is derived from the enum
 * so a zone added later is named or missing, never named twice.
 *
 * <p>Client-only.
 */
public final class ZoneText {

    private ZoneText() {
    }

    /**
     * The player-facing name of a zone.
     *
     * <p>Built once per zone. Both boards name every zone on every mat every frame, and a
     * translatable component is resolved when it is drawn rather than when it is made, so
     * making a fresh one each time buys nothing but garbage.
     */
    public static Component name(Zone zone) {
        return NAMES[zone.ordinal()];
    }

    /**
     * Built up front rather than on demand, so there is no question about who may ask first.
     *
     * <p>A translatable component holds a key and resolves it when it is drawn, so building
     * these before a language is loaded is not early - it is the only sensible time.
     */
    private static final Component[] NAMES = java.util.Arrays.stream(Zone.values())
            .map(zone -> (Component) Component.translatable(
                    "zone.gathering." + zone.name().toLowerCase(Locale.ROOT)))
            .toArray(Component[]::new);
}
