package dev.gathering.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gathering.Gathering;
import dev.gathering.client.CardItemRenderer;
import dev.gathering.client.PackItemRenderer;
import dev.gathering.client.CardZoomOverlay;
import dev.gathering.client.ClientCardCache;
import dev.gathering.client.ClientFetching;
import dev.gathering.client.ClientHoverState;
import dev.gathering.client.ClientNetworking;
import dev.gathering.client.TableColors;
import dev.gathering.client.DeckContentsScreen;
import dev.gathering.client.ZoomKeyState;
import dev.gathering.item.GatheringContent;
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
 * <p>The side is declared on the annotation rather than guarded inside each method, so a
 * dedicated server never loads this class or anything it names.
 */
// The mod bus said out loud. NeoForge from about 21.1.100 works the bus out from the event, and this mod was
// built on one of those; on the earlier 21.1 releases its range admits, a mod-bus event on the game bus stopped
// the mod loading at all ("IModBusEvent events are not allowed on the common NeoForge bus").
@SuppressWarnings("removal")
@EventBusSubscriber(value = Dist.CLIENT, modid = Gathering.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
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
        // The table's verbs, from the shared list rather than one written here. A verb added
        // to dev.gathering.client.TableShortcuts turns up in this loader's Controls screen and
        // in the other one's without anybody remembering to add it in two places.
        for (KeyMapping mapping : dev.gathering.client.TableShortcuts.all()) {
            // Read only inside the table's screen, so they share keys with the game's own without either being a
            // conflict: the Controls screen does not mark Q red for Drop and the table's untap.
            mapping.setKeyConflictContext(net.neoforged.neoforge.client.settings.KeyConflictContext.GUI);
            event.register(mapping);
        }
    }

    /** The miniature on the table top, which is what makes a table worth more than a menu. */
    @SubscribeEvent
    public static void onRegisterRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                GatheringContent.TABLE_ENTITY.get(),
                dev.gathering.client.TableMiniatureRenderer::new);
        event.registerBlockEntityRenderer(
                GatheringContent.SCOREKEEPERS_DESK_ENTITY.get(),
                dev.gathering.client.ScorekeepersDeskRenderer::new);
        event.registerBlockEntityRenderer(
                GatheringContent.DISPLAY_CASE_ENTITY.get(),
                dev.gathering.client.DisplayCaseRenderer::new);
        // A chair's seat is never drawn: the chair is the block, and the seat is only what is sat on.
        event.registerEntityRenderer(GatheringContent.CHAIR_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    @SubscribeEvent
    /**
     * Attaches the card's own renderer, so a card in hand shows its printed face.
     * <p>Through the event rather than {@code Item#initializeClient}, which is deprecated for
     * removal - and which would have forced a NeoForge-only subclass of an item that otherwise
     * has no loader-specific behavior at all.
     */
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
    public static void onRegisterBlockColors(
            net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        // Every table, not only the wooden one: the felt is the same dyeable surface on all
        // of them, and a table left off this list keeps its undyed texture forever with no
        // error to say why.
        for (var dyed : GatheringContent.everyDyedBlock()) {
            event.register((state, level, pos, tint) -> dev.gathering.block.FurnitureDye.tint(state, tint),
                    dyed.get());
        }
        for (var table : GatheringContent.tables()) {
            event.register(TableColors::tintOf, table.get());
        }
    }

    @SubscribeEvent
    public static void onRegisterItemColors(
            net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        for (var item : GatheringContent.tableItems()) {
            event.register((stack, tintIndex) -> TableColors.itemTintOf(tintIndex), item.get());
        }
        event.register(dev.gathering.item.DeckItem::tintOf, GatheringContent.DECK.get());
        event.register(dev.gathering.item.TrophyItem::tintOf, GatheringContent.TROPHY.get());
    }


    /** The mods list's Config button, opening the mod's own settings over the list it came from. */
    public static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mod, parent) -> new dev.gathering.client.SettingsScreen(parent));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Not parallel-safe work: these bind shared state, so they go on the main thread.
        event.enqueueWork(() -> {
            CardNameLookup.Binding.bind(ClientCardCache.get());
            DeckScreenHook.Binding.bind(hand -> Minecraft.getInstance().setScreen(new DeckContentsScreen(hand)));
            dev.gathering.service.DeckMadeHook.Binding.bind((handle, deck) ->
                    dev.gathering.client.ClientNetworking.send(
                            new dev.gathering.network.DeckMadePayload(handle, deck)));
            CardZoomOverlay.bindKeyState(ZoomKeyState.of(ZOOM_KEY, ZOOM_KEY::getKey));
            CardZoomOverlay.bindKeyName(ZOOM_KEY::getTranslatedKeyMessage);
            dev.gathering.client.TableShortcuts.bindKeyLookup(KeyMapping::getKey);
            // Where the row of remembered token names is kept: a name learned on one server
            // is not an offer worth making on another.
            dev.gathering.client.RecentThings.bindServerLookup(
                    dev.gathering.client.WhichServer::name);
            ClientNetworking.bindSender(payload -> {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
                }
            });
            ClientFetching.identifyAs(
                    Gathering.MOD_NAME + " client (+https://github.com/MeekTheSneak/Gathering)");
            // What happens when each clientbound payload arrives is ClientPayloads', shared with
            // Fabric. Checked against the protocol first, so a payload the server can send and
            // this client cannot apply stops the client starting rather than going missing.
            dev.gathering.client.ClientPayloads.checkCovers(
                    dev.gathering.network.GatheringProtocol.TO_CLIENT);
            GatheringClientPayloadHandlers.bind(GatheringNeoForgeClient::handlePayload);

            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onRenderGui);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onScreenRenderPre);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onScreenInit);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onRenderScreen);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onClientTick);
            // First of every listener, so a sweep has the drag before Mouse Tweaks' own right-drag
            // sees it and drops the deck into an empty slot the sweep passes.
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, GatheringNeoForgeClient::onMousePressed);
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, GatheringNeoForgeClient::onMouseDragged);
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, GatheringNeoForgeClient::onMouseReleased);
            NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.GameShuttingDownEvent closing) -> dev.gathering.client.ClientTicks.stopping());
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onRenderTooltip);
            NeoForge.EVENT_BUS.addListener(GatheringNeoForgeClient::onLoggingOut);
        });
    }

    /**
     * Every clientbound payload, onto the client thread and into the shared routes.
     * <p>Enqueued, as every route but the card metadata one was before. The registrar already
     * runs handlers on the client thread, where enqueueing runs the work straight through, so
     * this changes no ordering; it keeps the one line that decides the thread saying so.
     */
    private static void handlePayload(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> dev.gathering.client.ClientPayloads.apply(payload));
    }

    /** The overlay over the HUD, for a card held in hand. */
    private static void onRenderGui(RenderGuiEvent.Post event) {
        // With a screen open the screen hook draws it; drawing from both would double the
        // backdrop and dim the card.
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        dev.gathering.client.EventHud.render(event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
        CardZoomOverlay.render(
                event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
        // Last, so a notice said while a screen was open carries on being readable over the
        // world once the screen that prompted it has gone.
        dev.gathering.client.ScreenNotice.render(event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    /**
     * Clears the hovered stack before the screen draws.
     * <p>The tooltip event only fires when there is a tooltip, so without this the last card
     * the cursor touched would keep answering for every empty slot after it.
     */
    private static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        ClientHoverState.clear();
    }

    /**
     * Puts the look picker into the game's own video settings.
     * <p>The condition and the widget are in :common, so this loader and the other one offer
     * the same row rather than two rows that drifted apart.
     */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        dev.gathering.client.GuiThemeOption.addTo(event.getScreen());
    }

    /** A deck swept over cards with the right button held. See {@link dev.gathering.client.DeckSweep}. */
    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            dev.gathering.client.DeckSweep.pressed(screen, event.getButton());
        }
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                && dev.gathering.client.DeckSweep.dragged(screen, event.getMouseButton())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        dev.gathering.client.DeckSweep.released(event.getButton());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        dev.gathering.client.ClientTicks.tick(Minecraft.getInstance());
    }

    /**
     * The inspect panel over an open screen, beside the cursor.
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
        // And over that: whatever the mod has just been asked and answered.
        dev.gathering.client.ScreenNotice.render(event.getGuiGraphics(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
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
        dev.gathering.client.ClientState.forgetTheServer();
    }
}
