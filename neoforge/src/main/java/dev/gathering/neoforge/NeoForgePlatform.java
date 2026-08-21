package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** The NeoForge answer to the four questions {@link Platform} asks. Discovered by ServiceLoader. */
public final class NeoForgePlatform implements Platform {

    @Override
    public Path dataDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(Gathering.MOD_ID);
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion() {
        return ModList.get()
                .getModContainerById(Gathering.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("0.0.0");
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }
}
