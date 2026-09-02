package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One set of GUI art the mod can draw itself with.
 * <p>A theme is a folder of PNGs and a small file naming it. That is the whole of it: no
 * colors, no sizes and no rules live here, because a theme that could change anything but a
 * picture would mean repainting the pictures was not enough. Adding one is a folder and a
 * file in a resource pack - see {@link GuiThemes} for where they are read from and
 * {@code docs/themes.md} for how to write one.
 * <p>Every element's file name is worked out once, here, rather than on each draw. Screens ask
 * for elements while drawing, tens of times a frame, and building a {@link ResourceLocation}
 * per rectangle would be a string concatenation and an allocation for something that never
 * changes.
 * <p>Client-only.
 */
public final class GuiTheme {

    /** Where a theme that says nothing about its art keeps it. */
    public static final String DEFAULT_FOLDER = "basic";

    private final ResourceLocation id;
    private final Component name;
    private final ResourceLocation sprites;
    private final int order;

    /** This theme's file for each element, made once. Indexed by {@link Element#ordinal()}. */
    private final ResourceLocation[] byElement;

    public GuiTheme(ResourceLocation id, Component name, ResourceLocation sprites, int order) {
        this.id = id;
        this.name = name;
        this.sprites = sprites;
        this.order = order;
        Element[] elements = Element.all();
        this.byElement = new ResourceLocation[elements.length];
        for (Element element : elements) {
            this.byElement[element.ordinal()] =
                    sprites.withSuffix("/" + element.fileName());
        }
    }

    /** What names this theme: {@code gathering:basic}, or a pack's own. */
    public ResourceLocation id() {
        return id;
    }

    /** What a player sees it called. */
    public Component name() {
        return name;
    }

    /** The folder its art lives in, as {@code namespace:folder} under {@code gui/sprites}. */
    public ResourceLocation sprites() {
        return sprites;
    }

    /** Where it sits in the list. Lower first; the default is always first whatever it says. */
    public int order() {
        return order;
    }

    /** Where this theme's art for one element lives, whether or not anybody drew it. */
    public ResourceLocation spriteOf(Element element) {
        return byElement[element.ordinal()];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GuiTheme theme && id.equals(theme.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
