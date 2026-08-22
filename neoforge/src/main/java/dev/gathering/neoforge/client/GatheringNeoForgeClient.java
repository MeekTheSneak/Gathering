package dev.gathering.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gathering.Gathering;
import dev.gathering.client.CardItemRenderer;
import dev.gathering.client.CardZoomOverlay;
import dev.gathering.client.ClientCardCache;
import dev.gathering.client.ClientCardImages;
import dev.gathering.client.ClientHoverState;
import dev.gathering.client.ClientNetworking;
import dev.gathering.client.DeckContentsScreen;
import dev.gathering.client.DecklistImportScreen;
import dev.gathering.client.ZoomKeyState;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.neoforge.GatheringClientPayloadHandlers;
import dev.gathering.service.CardNameLookup;
import dev.gathering.service.DeckScreenHook;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Client setup for NeoForge.
 *
 * <p>The side is declared on the annotation rather than guarded inside each method, so a
 * dedicated server never loads this class or anything it names.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = Gathering.MOD_ID)
public final class GatheringNeoForgeClient {

    /** Hold to read a card. Deliberately a hold, not a toggle: reading is momentary. */
    private static final KeyMapping ZOOM_KEY = new KeyMapping(
            "key." + Gathering.MOD_ID + ".zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories." + Gathering.MOD_ID);

    private GatheringNeoForgeClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ZOOM_KEY);
    }

    /**
     * Attaches the card's own renderer, so a card in hand shows its printed face.
     *
     * <p>Through the event rather than {@code Item#initializeClient}, which is deprecated for
     * removal - and which would have forced a NeoForge-only subclass of an item that otherwise
     * has no loader-specific behaviour at all.
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CardItemRenderer.instance();
            }
        }, GatheringContent.CARD.get());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Not parallel-safe work: these bind shared state, so they go on the main thread.
        event.enqueueWork(() -> {
            CardNameLookup.Binding.bind(ClientCardCache.get());
            DeckScreenHook.Binding.bind(hand -> Minecraft.getInstance().setScreen(new DeckContentsScreen(hand)));
            CardZoomOverlay.bindKeyState(ZoomKeyState.of(ZOOM_KEY, ZOOM_KEY::getKey));
            ClientNetworking.bindSender(payload -> {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
                }
            });
            ClientCardImages.get().identifyAs(
                    Gathering.MOD_NAME + " client (+https://github.com/MeekTheSneak/Gathering)");
            GatheringClientPayloadHandlers.bind(GatheringNeoForgeClient::handlePayload);

            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onRenderGui);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onScreenRenderPre);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onRenderScreen);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onClientTick);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onRenderTooltip);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onLoggingOut);
        });
    }

    private static void handlePayload(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (payload instanceof CardMetadataPayload metadata) {
            ClientCardCache.get().accept(metadata.cards());
            return;
        }
        if (payload instanceof OpenImportScreenPayload) {
            context.enqueueWork(() -> Minecraft.getInstance().setScreen(new DecklistImportScreen()));
            return;
        }
        if (payload instanceof ImportResultPayload result) {
            context.enqueueWork(() -> {
                if (Minecraft.getInstance().screen instanceof DecklistImportScreen screen) {
                    screen.onResult(result);
                }
            });
        }
    }

    /** The overlay over the HUD, for a card held in hand. */
    private static void onRenderGui(RenderGuiEvent.Post event) {
        // With a screen open the screen hook draws it; drawing from both would double the
        // backdrop and dim the card.
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        CardZoomOverlay.render(
                event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    /**
     * Clears the hovered stack before the screen draws.
     *
     * <p>The tooltip event only fires when there is a tooltip, so without this the last card
     * the cursor touched would keep answering for every empty slot after it.
     */
    private static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        ClientHoverState.clear();
    }

    /** No screen means no slots, so nothing is hovered. */
    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            ClientHoverState.clear();
        }
    }

    /** The overlay over an open screen, for a card in a slot. Drawn last, over the tooltip. */
    private static void onRenderScreen(ScreenEvent.Render.Post event) {
        CardZoomOverlay.render(
                event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    /**
     * Vanilla keeps the hovered slot private to the container screen, so the tooltip event -
     * which already knows which stack it is describing - is where the overlay learns it.
     */
    private static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        ClientHoverState.setHovered(event.getItemStack());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // What one server told us is not true of the next one.
        ClientCardCache.get().clear();
        ClientHoverState.clear();
    }
}
