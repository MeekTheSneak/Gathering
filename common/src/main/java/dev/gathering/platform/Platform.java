package dev.gathering.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

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
