package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The Fabric answer to the four questions {@link Platform} asks. Discovered by ServiceLoader. */
public final class FabricPlatform implements Platform {

    @Override
    public Path dataDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(Gathering.MOD_ID);
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(Gathering.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }
}
