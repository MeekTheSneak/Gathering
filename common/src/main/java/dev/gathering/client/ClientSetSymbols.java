package dev.gathering.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.gathering.Gathering;
import dev.gathering.core.card.SetCode;
import dev.gathering.core.svg.SetSymbol;
import dev.gathering.core.svg.SvgException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set symbols, fetched by this client from Scryfall and drawn at whatever size and color a
 * pack needs.
 * <p>The same architecture as card art next door and for the same reason: the mod ships no
 * pictures of any set, never relays them over its own network, and never asks a server for
 * one. Every client goes and gets the symbol itself.
 * <p>Two things are different from card art, and both make this simpler. A symbol is shapes
 * rather than pixels, so one download serves every size: what is kept is the outline, and a
 * texture is drawn from it whenever a size or a color is first wanted. And there are a
 * handful of sets in play at once rather than hundreds of cards, so nothing here needs an
 * eviction policy that card art cannot do without.
 * <p>Never fetches, reads or rasterizes on the render thread. Only the upload comes back to
 * the client thread, because that is where a texture may be registered.
 * <p>Client-only. Nothing on a server may reference this class.
 */
public final class ClientSetSymbols {

    /** Where the symbols live. Built from a checked set code, never from anything relayed. */
    private static final String SYMBOLS = "https://svgs.scryfall.io/sets/";

    private static final String CACHE_DIRECTORY = "symbol-cache";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** A symbol file is a couple of kilobytes. Anything this size is not one. */
    private static final int MOST_BYTES = 2 * 1024 * 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");
    private static final ClientSetSymbols INSTANCE = new ClientSetSymbols();

    private final ExecutorService fetchers =
            Executors.newFixedThreadPool(1, daemonThreads("gathering-set-symbols"));
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** The outlines, once read. Written from a fetcher, read from the render thread. */
    private final Map<String, SetSymbol> outlines = new ConcurrentHashMap<>();

    /**
     * Textures already drawn, by set, color and size. Client thread only.
     * <p>Bounded, and thrown away whole rather than one at a time when it fills. A symbol is
     * a few kilobytes and there are a handful in play, so this is a backstop against a
     * session that wanders through hundreds of sets rather than a working eviction policy -
     * and redrawing one costs nothing, because the outline it is drawn from is still here.
     */
    private final Map<String, ResourceLocation> drawn = new java.util.HashMap<>();

    /** As many drawn symbols as are kept before the lot are released and drawn again. */
    private static final int MOST_DRAWN = 64;

    /** Sizes and colors that would not draw. Client thread only, like {@link #drawn}. */
    private final Set<String> undrawable = new java.util.HashSet<>();

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger textureCounter = new AtomicInteger();

    private String userAgent = Gathering.MOD_NAME + " (Minecraft client)";

    private ClientSetSymbols() {
    }

    public static ClientSetSymbols get() {
        return INSTANCE;
    }

    /** Set once at client init so fetches identify themselves, as the guidelines ask. */
    public void identifyAs(String agent) {
        this.userAgent = agent;
    }

    /**
     * The symbol for a set, in this color and this many pixels across, if it is ready.
     * <p>Returns empty and starts fetching otherwise, so a caller draws a plain wrapper this
     * frame and a wrapper with a symbol on it a moment later. Never blocks.
     *
     * @param color packed ARGB; the symbol is a silhouette and this is what it is printed in
     */
    public Optional<ResourceLocation> symbol(String setCode, int color, int size) {
        String code = checked(setCode);
        if (code == null || failed.contains(code)) {
            return Optional.empty();
        }
        String key = code + "/" + Integer.toHexString(color) + "/" + size;
        if (undrawable.contains(key)) {
            return Optional.empty();
        }
        ResourceLocation ready = drawn.get(key);
        if (ready != null) {
            return Optional.of(ready);
        }
        SetSymbol outline = outlines.get(code);
        if (outline == null) {
            if (inFlight.add(code)) {
                fetchers.execute(() -> fetch(code));
            }
            return Optional.empty();
        }
        return draw(key, outline, color, size);
    }

    /** Whether this set has been tried and will not be tried again this session. */
    public boolean hasFailed(String setCode) {
        String code = checked(setCode);
        return code != null && failed.contains(code);
    }

    // ----------------------------------------------------------------- fetch

    private void fetch(String code) {
        try {
            String svg = readCached(code).orElseGet(() -> download(code));
            if (svg == null || svg.isBlank()) {
                failed.add(code);
                return;
            }
            outlines.put(code, SetSymbol.read(svg));
        } catch (SvgException | RuntimeException notASymbol) {
            // One line per set rather than a flood: a set is only ever attempted once this
            // session. The cached file goes with it, so a restart tries the network again
            // rather than reading the same unreadable bytes for ever.
            LOGGER.warn("Could not read the set symbol for {}: {}", code, notASymbol.toString());
            discardCached(code);
            failed.add(code);
        } finally {
            inFlight.remove(code);
        }
    }

    private String download(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SYMBOLS + code + ".svg"))
                    .timeout(TIMEOUT)
                    .header("User-Agent", userAgent)
                    .header("Accept", "image/svg+xml")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                LOGGER.warn("The symbol for {} came back as HTTP {}", code, response.statusCode());
                return null;
            }
            String body = response.body();
            if (body != null && body.length() > MOST_BYTES) {
                LOGGER.warn("The symbol for {} came back far too large to be one", code);
                return null;
            }
            writeCache(code, body);
            return body;
        } catch (IOException couldNotFetch) {
            LOGGER.warn("Could not fetch the set symbol for {}: {}", code, couldNotFetch.toString());
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ------------------------------------------------------------------ draw

    /** Client thread only: registering a texture touches GL. */
    private Optional<ResourceLocation> draw(String key, SetSymbol outline, int color, int size) {
        int across = Math.max(1, size);
        try {
            byte[] mask = outline.mask(across);
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, across, across, false);
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;
            int tint = (color >>> 24) & 0xFF;
            for (int y = 0; y < across; y++) {
                for (int x = 0; x < across; x++) {
                    int alpha = (mask[y * across + x] & 0xFF) * tint / 255;
                    // NativeImage packs ABGR, not ARGB.
                    image.setPixelRGBA(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
                }
            }
            if (drawn.size() >= MOST_DRAWN) {
                for (ResourceLocation old : drawn.values()) {
                    Minecraft.getInstance().getTextureManager().release(old);
                }
                drawn.clear();
            }
            ResourceLocation id = Gathering.id("set_symbol/" + textureCounter.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            drawn.put(key, id);
            return Optional.of(id);
        } catch (SvgException | RuntimeException couldNotDraw) {
            // Remembered, because this is the render thread: without it a symbol that will
            // not rasterize is rasterized again on every frame the pack is on screen, for
            // the whole session.
            LOGGER.warn("Could not draw the set symbol {}: {}", key, couldNotDraw.toString());
            undrawable.add(key);
            return Optional.empty();
        }
    }

    // ----------------------------------------------------------------- cache

    private Optional<String> readCached(String code) {
        Path file = cacheFile(code);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private void discardCached(String code) {
        Path file = cacheFile(code);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException couldNotDelete) {
            LOGGER.debug("Could not discard the cached set symbol for {}", code, couldNotDelete);
        }
    }

    private void writeCache(String code, String svg) {
        Path file = cacheFile(code);
        if (file == null || svg == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            // Written beside and moved into place. A client killed part way through a write
            // would otherwise leave half a symbol on disk, and half a symbol reads as a
            // symbol that will not parse - for good, because a set is only tried once.
            Path beside = Files.createTempFile(file.getParent(), code + "-", ".part");
            try {
                Files.writeString(beside, svg, StandardCharsets.UTF_8);
                Files.move(beside, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(beside);
            }
        } catch (IOException couldNotWrite) {
            // A cache we cannot write is slower, not broken.
            LOGGER.debug("Could not cache the set symbol for {}", code, couldNotWrite);
        }
    }

    private Path cacheFile(String code) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(Gathering.MOD_ID)
                .resolve(CACHE_DIRECTORY)
                .resolve(code + ".svg");
    }

    /** A set code, or null. What counts as one is {@link SetCode}'s to say, in one place. */
    private static String checked(String setCode) {
        return SetCode.of(setCode).orElse(null);
    }

    private static java.util.concurrent.ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
