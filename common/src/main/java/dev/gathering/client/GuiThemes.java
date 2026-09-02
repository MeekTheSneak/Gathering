package dev.gathering.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.Gathering;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which looks are installed, read from the resource packs rather than written in Java.
 *
 * <p>A theme is a file at {@code assets/<namespace>/gui_themes/<name>.json} saying what it is
 * called and which folder of {@code textures/gui/sprites} its art is in. Nothing else. That is
 * the whole reason this is data: a Future Sight look, a retro look, a soft round one - none of
 * those are code, and a mod that made them code would be a mod where only its author could
 * have them.
 *
 * <pre>{@code
 * {
 *   "name": "Retro",
 *   "sprites": "mypack:retro",
 *   "order": 20
 * }
 * }</pre>
 *
 * <p>The art itself goes in {@code assets/mypack/textures/gui/sprites/retro/}, with a
 * {@code .mcmeta} beside each PNG. Anything a theme leaves out falls back to the default one,
 * so a pack may repaint six elements and inherit the rest - and so a half-painted theme cannot
 * reach the screen looking broken.
 *
 * <p>Re-read when the resource packs are, which is noticed rather than subscribed to: the
 * sprite for the default panel is a different object after every reload, so holding on to it
 * and comparing is a reload listener that needs no loader to register it. That matters because
 * registering one differs between NeoForge and Fabric and this has no other reason to.
 *
 * <p>Client thread only.
 */
public final class GuiThemes {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** The one every other theme falls back to, and the only one that has to be complete. */
    public static final ResourceLocation DEFAULT =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, GuiTheme.DEFAULT_FOLDER);

    /**
     * One sprite of the default theme, watched to notice a resource reload.
     *
     * <p>Built once. This is asked for on the way to every sprite the mod draws, and it used
     * to build the path and the location again each time - a string and an object per sprite
     * per frame, for a value that cannot change.
     */
    private static final ResourceLocation WATCHED =
            DEFAULT.withSuffix("/" + GatheringSprites.Element.PANEL.fileName());

    /** Where a pack declares one. */
    private static final String FOLDER = "gui_themes";

    private static final String SUFFIX = ".json";

    /** Where a theme with nothing to say about its place in the list goes. */
    private static final int LAST = 1000;

    private static List<GuiTheme> known = List.of();

    /** The atlas the list above was read for. A different object means the packs changed. */
    private static TextureAtlasSprite readFor;

    /** The theme in force, and the id it was resolved from, so the lookup is not done per draw. */
    private static GuiTheme active;
    private static String activeFrom;

    private GuiThemes() {
    }

    /** Every installed theme, the default first and the rest in the order they asked for. */
    public static List<GuiTheme> all() {
        rereadIfPacksChanged();
        return known;
    }

    /** The one being drawn with. Never null: an id nobody recognizes draws the default. */
    public static GuiTheme active() {
        rereadIfPacksChanged();
        String wanted = ClientSettings.themeId();
        GuiTheme known = active;
        if (known != null && wanted.equals(activeFrom)) {
            return known;
        }
        GuiTheme found = byId(wanted);
        active = found;
        activeFrom = wanted;
        return found;
    }

    /** Puts one on, and remembers it. */
    public static void wear(GuiTheme theme) {
        if (theme != null) {
            ClientSettings.themeId(theme.id().toString());
        }
    }

    /** The theme with this id, or the default one. Never throws on a name out of a file. */
    public static GuiTheme byId(String id) {
        for (GuiTheme theme : all()) {
            if (theme.id().toString().equals(id)) {
                return theme;
            }
        }
        return fallback();
    }

    /** The default theme, built from nothing if no pack declares it. */
    private static GuiTheme fallback() {
        for (GuiTheme theme : known) {
            if (theme.id().equals(DEFAULT)) {
                return theme;
            }
        }
        // Only reachable if the mod's own assets are missing, which is a broken install rather
        // than a state to design for - but a screen still has to draw, so it draws something.
        return new GuiTheme(DEFAULT, Component.literal(GuiTheme.DEFAULT_FOLDER), DEFAULT, 0);
    }

    /**
     * Reads the theme files again if the resource packs have been reloaded since the last time.
     *
     * <p>Cheap enough to ask on every call: one hash lookup and a reference comparison, against
     * a list that only changes when somebody adds a pack.
     */
    private static void rereadIfPacksChanged() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        TextureAtlasSprite now = client.getGuiSprites().getSprite(WATCHED);
        if (now == readFor && !known.isEmpty()) {
            return;
        }
        readFor = now;
        known = read(client);
        active = null;
        activeFrom = null;
    }

    private static List<GuiTheme> read(Minecraft client) {
        Map<ResourceLocation, Resource> files = client.getResourceManager()
                .listResources(FOLDER, where -> where.getPath().endsWith(SUFFIX));
        List<GuiTheme> found = new ArrayList<>(files.size());
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            GuiTheme theme = readOne(file.getKey(), file.getValue());
            if (theme != null) {
                found.add(theme);
            }
        }
        // The default first whatever it asked for, because it is the one every other theme
        // falls back to and the one somebody is going back to when they change their mind.
        found.sort(Comparator
                .comparing((GuiTheme theme) -> theme.id().equals(DEFAULT) ? 0 : 1)
                .thenComparingInt(GuiTheme::order)
                .thenComparing(theme -> theme.id().toString()));
        return List.copyOf(found);
    }

    /**
     * One theme file, or null if it cannot be read.
     *
     * <p>A theme that will not parse is a line in the log and a theme that is not offered.
     * Never an exception: this is somebody else's file, and a resource pack with a typo in it
     * must not be a client that will not draw.
     */
    private static GuiTheme readOne(ResourceLocation where, Resource file) {
        ResourceLocation id = idOf(where);
        if (id == null) {
            return null;
        }
        try (BufferedReader reader = file.openAsReader()) {
            JsonObject said = JsonParser.parseReader(reader).getAsJsonObject();
            String sprites = said.has("sprites")
                    ? said.get("sprites").getAsString()
                    : id.toString();
            ResourceLocation art = ResourceLocation.tryParse(sprites);
            if (art == null) {
                LOGGER.warn("The {} theme names its art as '{}', which is not a folder name.",
                        id, sprites);
                return null;
            }
            // A translation key, which renders as itself when nothing translates it - so a
            // pack may write either "theme.mypack.retro" or "Retro" and get what it meant.
            Component name = Component.translatable(said.has("name")
                    ? said.get("name").getAsString()
                    : id.getPath());
            int order = said.has("order") ? said.get("order").getAsInt() : LAST;
            return new GuiTheme(id, name, art, order);
        } catch (IOException | RuntimeException couldNotRead) {
            LOGGER.warn("Could not read the theme at {}: {}", where, couldNotRead.toString());
            return null;
        }
    }

    /** {@code assets/x/gui_themes/retro.json} names the theme {@code x:retro}. */
    private static ResourceLocation idOf(ResourceLocation file) {
        String path = file.getPath();
        if (!path.startsWith(FOLDER + "/") || !path.endsWith(SUFFIX)) {
            return null;
        }
        String name = path.substring(FOLDER.length() + 1, path.length() - SUFFIX.length());
        return ResourceLocation.tryBuild(file.getNamespace(), name);
    }
}
