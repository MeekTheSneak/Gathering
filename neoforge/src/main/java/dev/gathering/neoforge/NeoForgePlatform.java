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

    @Override
    public boolean canReceive(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.resources.ResourceLocation payload) {
        // NeoForge negotiates channels when the connection opens and throws rather than
        // dropping anything sent down one that was never agreed, so this is asked before
        // every send. See dev.gathering.network.Sending.
        return player != null && payload != null && player.connection.hasChannel(payload);
    }
}
