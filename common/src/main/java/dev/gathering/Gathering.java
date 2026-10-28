package dev.gathering;

import net.minecraft.resources.ResourceLocation;

/** Mod-wide constants. The one place the namespace is spelled out. */
public final class Gathering {

    public static final String MOD_ID = "gathering";
    public static final String MOD_NAME = "Gathering";

    /**
     * Required by the WotC Fan Content Policy and shown on the title screen.
     * Not a string to be quietly dropped in a refactor.
     */
    public static final String FAN_CONTENT_DISCLAIMER =
            "Unofficial Fan Content permitted under the Fan Content Policy. "
                    + "Not approved/endorsed by Wizards. Portions of the materials used are property of "
                    + "Wizards of the Coast. ©Wizards of the Coast LLC.";

    /** Required by the Scryfall API guidelines wherever card data is displayed. */
    public static final String SCRYFALL_ATTRIBUTION = "Card data and images courtesy of Scryfall.";

    private Gathering() {
    }

    public static ResourceLocation id(String path) {
        // 1.21.1: the class is ResourceLocation and the constructor is not public.
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
