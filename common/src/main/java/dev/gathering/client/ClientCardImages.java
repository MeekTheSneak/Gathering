package dev.gathering.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.gathering.Gathering;
import dev.gathering.core.image.CardImageDecoder;
import java.io.IOException;
import dev.gathering.core.net.ArtHosts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
 * Card art, fetched by this client from Scryfall and cached on this client's disk.
 * <p>This is the architecture that removes the image-sync problem class entirely: the mod
 * ships no card images, never relays image bytes over its own network, and never asks a
 * server for art. A card summary carries a URL; every client goes and gets it.
 * <p>Two rules, both load-bearing:
 * <ul>
 *   <li><b>Never fetch or register a texture on the render thread.</b> HTTP and disk reads
 *       happen on a small daemon pool; only the GL upload comes back to the client thread.</li>
 *   <li><b>Resident textures are capped.</b> A four-player Commander game touches roughly
 *       450 distinct cards, so an unbounded cache is a VRAM leak with extra steps. The disk
 *       cache keeps everything; VRAM keeps the last {@value #MAX_RESIDENT_TEXTURES}.</li>
 * </ul>
 * <p>Client-only. Nothing on a server may reference this class.
 */
public final class ClientCardImages {

    /**
     * How many card textures stay on the GPU before the least recently used is dropped.
     * <p>256 of them is 324 MiB, not the "well under 200" the brief carried for a long time -
     * a normal-tier card is 488x680 uploaded RGBA with no mipmaps, which is 1.27 MiB exactly.
     * See {@link dev.gathering.core.card.TextureBudget}, which does the arithmetic and is
     * checked against this number by a game test.
     */
    public static final int MAX_RESIDENT_TEXTURES = 256;

    private static final String CACHE_DIRECTORY = "image-cache";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_REMEMBERED_FAILURES = 512;

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");
    private static final ClientCardImages INSTANCE = new ClientCardImages();

    private final ExecutorService fetchers = Executors.newFixedThreadPool(2, daemonThreads("gathering-card-art"));
    // Redirects followed on purpose: a CDN that moves an image should not look like a
    // missing card. The JDK client never follows them by default.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * One texture that is on the card, and how much of the graphics card it is holding.
     * <p>The size is kept because the budget is in bytes and the cache was counting textures:
     * two hundred and fifty-six of them at the crisp tier is seven hundred and fifty
     * megabytes, against a documented ceiling of three hundred and twenty-five. Reading a page
     * of cards with the key held filled it with the large ones.
     */
    private record Held(ResourceLocation id, long bytes, boolean crisp) {
    }

    /** Access-ordered, so iteration order is least-recently-used first. Client thread only. */
    private final LinkedHashMap<String, Held> resident = new LinkedHashMap<>(64, 0.75f, true);

    /** What the resident textures add up to, kept as they come and go rather than summed. */
    private long residentBytes;

    /** How many of them are the large tier, which has a much smaller allowance of its own. */
    private int residentCrisp;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Urls this session has given up on, oldest forgotten first.
     * <p>Bounded, so a session spent looking at cards nobody has art for cannot grow forever.
     * It used to be a plain set that stopped accepting anything once it was full, which meant
     * the five-hundred-and-thirteenth dead link was neither remembered as failed nor put on a
     * retry wait - so every frame that asked for it started another fetch, and a client left
     * open on a set with no art fetched the same missing picture as fast as two threads could
     * ask for it.
     * <p>Forgetting the oldest is the right way round: the url that has not come up for
     * longest is the one a later retry costs least, and something genuinely gone will simply
     * fail again once and go back in.
     */
    private final Set<String> failed = java.util.Collections.newSetFromMap(
            java.util.Collections.synchronizedMap(
                    new LinkedHashMap<>(64, 0.75f, false) {
                        @Override
                        protected boolean removeEldestEntry(
                                java.util.Map.Entry<String, Boolean> eldest) {
                            return size() > MAX_REMEMBERED_FAILURES;
                        }
                    }));

    /**
     * Urls whose fetch went wrong in a way that might not go wrong next time, and when it is
     * worth asking again - with how many goes each has had, so a dead one is eventually
     * given up on for real.
     */
    private final java.util.Map<String, Long> waiting = new ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> attempts = new ConcurrentHashMap<>();

    /** How long to wait before the first retry, doubling after that, and how many to allow. */
    private static final long FIRST_RETRY_MILLIS = 2_000L;
    private static final int MOST_ATTEMPTS = 5;
    private final AtomicInteger textureCounter = new AtomicInteger();

    private String userAgent = Gathering.MOD_NAME + " (Minecraft client)";

    private ClientCardImages() {
    }

    public static ClientCardImages get() {
        return INSTANCE;
    }

    /** Set once at client init so fetches identify themselves, as Scryfall's guidelines ask. */
    public void identifyAs(String agent) {
        this.userAgent = agent;
    }

    /**
     * The texture for a card image URL, if it is ready.
     * <p>Returns empty and starts fetching otherwise, so callers render a placeholder this
     * frame and the real art a few frames later. Never blocks.
     */
    public Optional<ResourceLocation> texture(String url) {
        if (url == null || url.isBlank() || failed.contains(url)) {
            return Optional.empty();
        }
        // Scryfall's, or not fetched. The URL is a string the server put in a card's summary,
        // and this used to fetch whatever it said - which made every client at a table a way
        // for the host to read addresses inside that client's own network. Refused once, and
        // then drawn as a card whose art is missing, the same as a picture that is not there.
        if (!ArtHosts.isAllowed(url)) {
            if (failed.add(url)) {
                LOGGER.warn("Card art is only fetched from Scryfall; not fetching {}", url);
            }
            return Optional.empty();
        }
        Long notBefore = waiting.get(url);
        if (notBefore != null && System.currentTimeMillis() < notBefore) {
            return Optional.empty();
        }
        Held ready = resident.get(url);
        if (ready != null) {
            return Optional.of(ready.id());
        }
        if (inFlight.add(url)) {
            fetchers.execute(() -> fetch(url));
        }
        return Optional.empty();
    }

    /**
     * Whether this URL has been tried and will not be tried again this session.
     * <p>Only for the failures that are really final - the picture is not there, or what came
     * back is not an image. A fetch that timed out or came back 429 is waiting rather than
     * failed, and the screen goes on saying it is still trying, because it is.
     */
    public boolean hasFailed(String url) {
        return url != null && failed.contains(url);
    }

    public int residentCount() {
        return resident.size();
    }

    /** What the resident textures come to, which is the budget the ceiling is written in. */
    public long residentMebibytes() {
        return residentBytes / (1024 * 1024);
    }

    public void shutdown() {
        fetchers.shutdownNow();
    }

    private void fetch(String url) {
        boolean handedOff = false;
        try {
            byte[] cached = readCached(url).orElse(null);
            if (cached == null) {
                Fetched came = download(url);
                if (came.bytes() == null || came.bytes().length == 0) {
                    if (came.worthRetrying()) {
                        waitBeforeRetrying(url);
                    } else {
                        markFailed(url);
                    }
                    return;
                }
                cached = came.bytes();
            }
            final byte[] bytes = cached;
            waiting.remove(url);
            attempts.remove(url);
            // Back to the client thread: NativeImage and the GL upload both belong there.
            // The url stays in flight until upload() has published the texture. Releasing it
            // here opened a gap - resident not yet filled, inFlight already empty - that the
            // render loop fetched into on the very next frame, and the second upload then
            // replaced the first texture in resident without releasing it: a leaked GL
            // texture per fetched card, plus the double decode.
            handedOff = true;
            prepare(url, bytes);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not load card art from {}: {}", url, e.toString());
            markFailed(url);
        } finally {
            if (!handedOff) {
                inFlight.remove(url);
            }
        }
    }

    /**
     * Turns bytes into pixels, off the render thread.
     * <p>Decoding a card scan, rounding its corners and copying it pixel by pixel is a real
     * piece of work - at the crisp tier, three quarters of a million pixels - and it used to
     * happen inside the job handed to the render thread. A screen full of cards arriving at
     * once was that job several times over, between two frames. Only the upload has to be on
     * the render thread, so only the upload is.
     */
    private void prepare(String url, byte[] bytes) {
        try {
            CardImageDecoder.DecodedImage decoded = CardImageDecoder.decode(bytes);
            // Card art arrives as a rectangle with the corners printed on it. A card is not a
            // rectangle.
            dev.gathering.core.image.RoundedCorners.apply(decoded);
            NativeImage image = toNativeImage(decoded);
            long size = (long) decoded.width() * decoded.height() * 4L;
            // Recognised by its size rather than by what was asked for: what comes back is
            // what counts against the budget.
            boolean crisp = decoded.height() >= dev.gathering.core.card.TextureBudget.Tier.CRISP.height();
            Minecraft.getInstance().execute(() -> upload(url, image, size, crisp));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not decode card art from {}: {}", url, e.toString());
            markFailed(url);
            discardCached(url);
            inFlight.remove(url);
        }
    }

    private void upload(String url, NativeImage image, long size, boolean crisp) {
        try {
            ResourceLocation id = Gathering.id("card_art/" + textureCounter.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            resident.put(url, new Held(id, size, crisp));
            residentBytes += size;
            if (crisp) {
                residentCrisp++;
            }
            evictDownToCap();
        } catch (RuntimeException e) {
            // Loud rather than debug: a card that will not draw is the single most visible
            // way this mod can look broken, and each url is only ever attempted once, so
            // this is one line per card rather than a flood.
            // Loud rather than debug: a card that will not draw is the single most visible
            // way this mod can look broken.
            LOGGER.warn("Could not upload card art from {}: {}", url, e.toString());
            markFailed(url);
            // What would not decode is thrown out of the disk cache too. The failed set only
            // lasts the session, so a truncated file kept here came back every session and
            // broke this card's art for good; gone, the next session downloads it fresh.
            discardCached(url);
            image.close();
        } finally {
            inFlight.remove(url);
        }
    }

    /** Client thread or fetcher thread; only touches the disk. */
    private void discardCached(String url) {
        Path file = cacheFile(url);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.debug("Could not discard cached card art for {}", url, e);
        }
    }

    /**
     * Copies decoded pixels into a texture.
     * <p>Decoding happens in the pure core with ImageIO, because Minecraft's own
     * {@code NativeImage.read} is stb_image and stb cannot read progressive JPEG - which is
     * what Scryfall serves for every tier but png. All that is left here is the copy.
     */
    private static NativeImage toNativeImage(CardImageDecoder.DecodedImage decoded) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, decoded.width(), decoded.height(), false);
        for (int y = 0; y < decoded.height(); y++) {
            for (int x = 0; x < decoded.width(); x++) {
                image.setPixelRGBA(x, y, decoded.pixelAt(x, y));
            }
        }
        return image;
    }

    /**
     * Client thread only, because releasing a texture touches GL.
     * <p>Three limits, all of them oldest-first, because the map is in access order: the byte
     * ceiling the budget is actually written in, a count so a wall of tiny textures cannot
     * grow without end, and a much smaller allowance for the large tier - which is what a
     * player reading card after card with the key held fills the cache with.
     */
    private void evictDownToCap() {
        Iterator<Map.Entry<String, Held>> oldestFirst = resident.entrySet().iterator();
        while (oldestFirst.hasNext() && tooMuchIsResident()) {
            Map.Entry<String, Held> eldest = oldestFirst.next();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
            residentBytes -= eldest.getValue().bytes();
            if (eldest.getValue().crisp()) {
                residentCrisp--;
            }
            oldestFirst.remove();
        }
    }

    private boolean tooMuchIsResident() {
        return resident.size() > MAX_RESIDENT_TEXTURES
                || residentBytes > dev.gathering.core.card.TextureBudget.CEILING_MEBIBYTES * 1024 * 1024
                || residentCrisp > dev.gathering.core.card.TextureBudget.CRISP_AT_ONCE;
    }

    private Optional<byte[]> readCached(String url) {
        Path file = cacheFile(url);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * What came back, and whether asking again could ever give a different answer.
     * <p>The difference is the whole point. A 404 means the picture is not there and never
     * will be; a timeout, a 429 or a 503 mean the network was busy, which is a thing that
     * stops being true.
     */
    private record Fetched(byte[] bytes, boolean worthRetrying) {

        static final Fetched GONE = new Fetched(null, false);
        static final Fetched LATER = new Fetched(null, true);
    }

    private Fetched download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", userAgent)
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // The body is closed on every way out of here, not only the one that reads it. A
            // missing or rate-limited picture used to return with its stream still open, and
            // a wall of packs whose art is all 404 is a wall of connections nobody closed.
            try (java.io.InputStream in = response.body()) {
                int status = response.statusCode();
                if (status != 200) {
                    LOGGER.warn("Card art fetch returned HTTP {} for {}", status, url);
                    // Only "it is not there" is final. Everything else - a rate limit, a bad
                    // gateway, a proxy having a moment - is worth asking again about.
                    return status == 404 || status == 410 ? Fetched.GONE : Fetched.LATER;
                }
                // Read up to a picture's worth and not a byte more. A body with no end to it
                // used to be read whole into one array; now it is a picture that is not there.
                byte[] body = in.readNBytes(ArtHosts.MOST_BYTES + 1);
                if (body.length > ArtHosts.MOST_BYTES) {
                    LOGGER.warn("Card art at {} is larger than any picture; not keeping it", url);
                    return Fetched.GONE;
                }
                writeCache(url, body);
                return new Fetched(body, false);
            }
        } catch (IOException e) {
            // A timeout or a dropped connection. This is the one that made whole cards look
            // permanently broken: dozens of images are asked for at once when a collection
            // opens, and whichever of them lost that race used to be given up on for the
            // rest of the session while everything else about the card loaded fine.
            LOGGER.warn("Card art fetch failed for {}: {}", url, e.toString());
            return Fetched.LATER;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Fetched.LATER;
        }
    }

    private void writeCache(String url, byte[] bytes) {
        Path file = cacheFile(url);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            // Written beside and moved into place, like the set symbols. A client killed
            // part way through a plain write left a truncated image that failed to decode
            // on every later session.
            Path beside = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(beside, bytes);
            Files.move(beside, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // A cache we cannot write is slower, not broken.
            LOGGER.debug("Could not cache card art for {}", url, e);
        }
    }

    private Path cacheFile(String url) {
        String hash = sha1(url);
        if (hash == null) {
            return null;
        }
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(Gathering.MOD_ID)
                .resolve(CACHE_DIRECTORY)
                .resolve(hash.substring(0, 2))
                .resolve(hash + ".png");
    }

    /**
     * Puts a url aside for a while rather than giving up on it.
     * <p>Backing off each time so a host that is genuinely down is not hammered, and giving
     * up for real after a few goes so a dead link cannot be retried forever.
     */
    private void waitBeforeRetrying(String url) {
        int gone = attempts.merge(url, 1, Integer::sum);
        if (gone >= MOST_ATTEMPTS) {
            LOGGER.warn("Giving up on card art at {} after {} tries", url, gone);
            markFailed(url);
            return;
        }
        waiting.put(url, System.currentTimeMillis() + FIRST_RETRY_MILLIS * (1L << (gone - 1)));
    }

    private void markFailed(String url) {
        failed.add(url);
        // The two maps that got it here are no use once it is in the failed set, and leaving
        // them full would mean a url the set later forgets came back already out of tries.
        waiting.remove(url);
        attempts.remove(url);
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static java.util.concurrent.ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
