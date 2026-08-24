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

    /** The player-facing name of a zone. */
    public static Component name(Zone zone) {
        return Component.translatable("zone.gathering." + zone.name().toLowerCase(Locale.ROOT));
    }
}
