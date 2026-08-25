package dev.gathering.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gathering.Gathering;
import dev.gathering.client.CardItemRenderer;
import dev.gathering.client.PackItemRenderer;
import dev.gathering.client.CardZoomOverlay;
import dev.gathering.client.ClientCardCache;
import dev.gathering.client.ClientFetching;
import dev.gathering.client.ClientHoverState;
import dev.gathering.client.ClientCardRequests;
import dev.gathering.client.DevScene;
import dev.gathering.client.ClientNetworking;
import dev.gathering.client.TableColours;
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
    /** The miniature on the table top, which is what makes a table worth more than a menu. */
    @SubscribeEvent
    public static void onRegisterRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                GatheringContent.TABLE_ENTITY.get(),
                dev.gathering.client.TableMiniatureRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CardItemRenderer.instance();
            }
        }, GatheringContent.CARD.get());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return PackItemRenderer.instance();
            }
        }, GatheringContent.PACK.get());
    }

    /** The felt is one texture tinted per table, so the tint needs a handler on each loader. */
    @SubscribeEvent
    public static void onRegisterBlockColours(
            net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register(TableColours::tintOf, GatheringContent.TABLE.get());
    }

    @SubscribeEvent
    public static void onRegisterItemColours(
            net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> TableColours.itemTintOf(tintIndex),
                GatheringContent.TABLE_ITEM.get());
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
            ClientFetching.identifyAs(
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
        if (payload instanceof dev.gathering.network.OpenTableSetupPayload setup) {
            context.enqueueWork(() -> Minecraft.getInstance()
                    .setScreen(new dev.gathering.client.TableSetupScreen(setup.table())));
            return;
        }
        if (payload instanceof dev.gathering.network.OpenSideboardPayload sideboard) {
            context.enqueueWork(() -> acceptSideboard(sideboard));
            return;
        }
        if (payload instanceof dev.gathering.network.TableViewPayload table) {
            context.enqueueWork(() -> acceptTableView(table));
            return;
        }
        if (payload instanceof dev.gathering.network.PackOpenedPayload opened) {
            context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                    new dev.gathering.client.PackOpeningScreen(
                            opened.setCode(), opened.kind(), opened.cards())));
            return;
        }
        if (payload instanceof dev.gathering.network.OpenCollectionPayload collection) {
            context.enqueueWork(() ->
                    dev.gathering.client.CollectionScreen.show(collection));
            return;
        }
        if (payload instanceof dev.gathering.network.CollectionPagePayload page) {
            context.enqueueWork(() -> dev.gathering.client.CollectionScreen.accept(page));
            return;
        }
        if (payload instanceof dev.gathering.network.DraftViewPayload pod) {
            context.enqueueWork(() -> acceptDraftView(pod));
            return;
        }
        if (payload instanceof dev.gathering.network.TradeViewPayload trade) {
            context.enqueueWork(() -> dev.gathering.client.TradeScreen.accept(trade));
            return;
        }
        if (payload instanceof dev.gathering.network.CloseTablePayload) {
            context.enqueueWork(() -> {
                dev.gathering.client.ClientTableState.clear();
                if (Minecraft.getInstance().screen instanceof dev.gathering.client.TableScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
            });
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

    /** Takes a pack off the wire and puts it in front of the drafter it belongs to. */
    private static void acceptDraftView(dev.gathering.network.DraftViewPayload payload) {
        dev.gathering.client.DraftScreen.show(payload.pod(), payload.view(), payload.open());
    }

    /** Hands the payload to the screen, which decides whether to open or refresh. */
    private static void acceptSideboard(dev.gathering.network.OpenSideboardPayload payload) {
        dev.gathering.client.SideboardScreen.open(
                payload.table(), payload.deck(), payload.gameNumber(), payload.bestOf());
    }

    /** Takes a board off the wire and, if asked, sits the player down at it. */
    private static void acceptTableView(dev.gathering.network.TableViewPayload payload) {
        try {
            dev.gathering.core.game.visibility.GameView board =
                    dev.gathering.core.game.persistence.ViewCodec.read(payload.view());
            // A seated view is this player's own board; a spectator view is the public one
            // that feeds the miniature on the table. Only the first belongs to a seat.
            boolean seated = board.viewer()
                    instanceof dev.gathering.core.game.visibility.Viewer.Seated;
            dev.gathering.client.ClientTableState.accept(payload.table(), board, seated);
        } catch (java.io.IOException e) {
            // A board this client cannot read is one it must not draw a guess at.
            dev.gathering.client.ClientTableState.forget(payload.table());
            return;
        }
        if (payload.open() && !(Minecraft.getInstance().screen
                instanceof dev.gathering.client.TableScreen)) {
            Minecraft.getInstance().setScreen(new dev.gathering.client.TableScreen(payload.table()));
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

    private static void onClientTick(ClientTickEvent.Post event) {
        // No screen means no slots, so nothing is hovered.
        if (Minecraft.getInstance().screen == null) {
            ClientHoverState.clear();
        }
        ClientCardRequests.tick();
        DevScene.tick(Minecraft.getInstance());
    }

    /**
     * The inspect panel over an open screen, beside the cursor.
     *
     * <p>Drawn last, so it sits over the vanilla tooltip it replaces rather than fighting it
     * for the same patch of screen.
     */
    private static void onRenderScreen(ScreenEvent.Render.Post event) {
        CardZoomOverlay.renderAtCursor(
                event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight(),
                event.getMouseX(),
                event.getMouseY());
    }

    /**
     * Vanilla keeps the hovered slot private to the container screen, so the tooltip event -
     * which already knows which stack it is describing - is where the overlay learns it.
     */
    private static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        ClientHoverState.setHovered(event.getItemStack());
        if (CardZoomOverlay.replacesTooltipFor(event.getItemStack())) {
            // The inspect panel is about to draw in this exact spot and says more.
            event.setCanceled(true);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // What one server told us is not true of the next one.
        ClientCardCache.get().clear();
        ClientCardRequests.clear();
        ClientHoverState.clear();
        dev.gathering.client.ClientTableState.clear();
    }
}
