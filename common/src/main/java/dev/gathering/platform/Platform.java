package dev.gathering.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The whole of what the mod needs to ask the loader.
 *
 * <p>Deliberately tiny. If this interface grows, loader-specific behaviour is leaking into
 * common code and the answer is almost always to move the logic down into the pure core
 * rather than to add a method here.
 *
 * <p>Loaded through {@link ServiceLoader}, so {@code common} names it without importing
 * either loader.
 */
public interface Platform {

    /** Where the server keeps this mod's own data - the card metadata cache lives under here. */
    Path dataDirectory();

    Path configDirectory();

    /** Reported to Scryfall in the User-Agent, as their guidelines ask. */
    String modVersion();

    String loaderName();

    /**
     * Whether one of this mod's payloads may be sent to this player at all.
     *
     * <p>Here rather than in common code because only the loader knows: each one negotiates
     * its own channels when a connection opens, and each one throws rather than dropping a
     * packet sent down a channel that was never agreed. Asked through
     * {@link dev.gathering.network.Sending}, which is the only thing that sends.
     */
    boolean canReceive(ServerPlayer player, ResourceLocation payload);

    static Platform get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private static final Platform INSTANCE = ServiceLoader.load(Platform.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Platform service is registered. Each loader module must provide one via "
                                + "META-INF/services/dev.gathering.platform.Platform"));

        private Holder() {
        }
    }
}
