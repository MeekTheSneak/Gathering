package dev.gathering.neoforge.data;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Item models, generated rather than hand-written so {@code runData} catches a missing
 * texture at build time instead of as a purple cube in game.
 *
 * <p>The mod's own UI art is all that ships in the jar. Card faces are never textures here;
 * they are fetched by each client from Scryfall at runtime.
 */
public final class GatheringItemModels extends ItemModelProvider {

    public GatheringItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Gathering.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // The card and the pack are deliberately absent: both models are hand-authored as
        // builtin/entity so their own renderers draw the real card art and the real set
        // symbol, and their display transforms are meant to be edited by hand rather than
        // regenerated.
        basicItem(GatheringContent.DECK.get());
    }
}
