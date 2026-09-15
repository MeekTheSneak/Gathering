package dev.gathering.neoforge.compat.create.client;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;

/**
 * With Create, the Scorekeeper's Desk is taught the way Create teaches its own blocks: hold W over
 * it for a scene. And it is listed among the sources for Display Links, where somebody looking for
 * what a board can show will look.
 * <p>The scene is built on Create's own Display Link schematic with the depot swapped for the desk,
 * so it needs no schematic of this mod's and matches the scene it sits beside.
 */
public final class GatheringPonderPlugin implements PonderPlugin {

    /** Create's schematic for its Display Link scene: a board, its power, and a depot with a link on it. */
    static final ResourceLocation DISPLAY_LINK_SCHEMATIC = ResourceLocation.fromNamespaceAndPath("create", "display_link");

    /** Called while this mod is constructed on a client, and only with Create installed. */
    public static void register() {
        PonderIndex.addPlugin(new GatheringPonderPlugin());
    }

    @Override
    public String getModId() {
        return Gathering.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(desk())
                .addStoryBoard(DISPLAY_LINK_SCHEMATIC, DeskPonderScene::deskAndBoard);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES).add(desk());
    }

    private static ResourceLocation desk() {
        return ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, GatheringContent.SCOREKEEPERS_DESK_ID);
    }
}
