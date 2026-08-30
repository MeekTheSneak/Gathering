package dev.gathering.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gathering.Gathering;
import dev.gathering.client.CardFaceRenderer;
import dev.gathering.client.CardZoomOverlay;
import dev.gathering.client.ClientCardCache;
import dev.gathering.client.ClientFetching;
import dev.gathering.client.ClientHoverState;
import dev.gathering.client.ClientCardRequests;
import dev.gathering.client.ClientNetworking;
import dev.gathering.client.GuiThemeOption;
import dev.gathering.client.TableColors;
import dev.gathering.client.DeckContentsScreen;
import dev.gathering.client.DecklistImportScreen;
import dev.gathering.client.ZoomKeyState;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.service.CardNameLookup;
import dev.gathering.service.DeckScreenHook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Client setup for Fabric, mirroring the NeoForge one.
 *
 * <p>Entry point is declared as {@code client} in fabric.mod.json, so a dedicated server
 * never loads this class or anything it names.
 */
public final class GatheringFabricClient implements ClientModInitializer {

    /** Hold to read a card. Deliberately a hold, not a toggle: reading is momentary. */
    private static final KeyMapping ZOOM_KEY = new KeyMapping(
            "key." + Gathering.MOD_ID + ".zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories." + Gathering.MOD_ID);

    /** Hands the payload to the screen, which decides whether to open or refresh. */
    private static void acceptSideboard(
            net.minecraft.client.Minecraft client, dev.gathering.network.OpenSideboardPayload payload) {
        dev.gathering.client.SideboardScreen.open(
                payload.table(), payload.deck(), payload.gameNumber(), payload.bestOf());
    }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ZOOM_KEY);

        CardNameLookup.Binding.bind(ClientCardCache.get());
        DeckScreenHook.Binding.bind(hand -> Minecraft.getInstance().setScreen(new DeckContentsScreen(hand)));
        CardZoomOverlay.bindKeyState(ZoomKeyState.of(ZOOM_KEY, () -> KeyBindingHelper.getBoundKeyOf(ZOOM_KEY)));
        ClientNetworking.bindSender(ClientPlayNetworking::send);
        ClientFetching.identifyAs(
                Gathering.MOD_NAME + " client (+https://github.com/MeekTheSneak/Gathering)");

        // Cards draw their own printed face rather than a generic icon. Same drawing code as
        // NeoForge; only the way it is reached differs.
        BuiltinItemRendererRegistry.INSTANCE.register(
                GatheringContent.CARD.get(),
                (stack, matrices, buffers, light, overlay) ->
                        CardFaceRenderer.render(stack, matrices, buffers, light));
        BuiltinItemRendererRegistry.INSTANCE.register(
                GatheringContent.PACK.get(),
                (stack, matrices, buffers, light, overlay) ->
                        dev.gathering.client.PackFaceRenderer.render(stack, matrices, buffers, light));

        // The miniature on the table top, which is what makes a table worth more than a menu.
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                GatheringContent.TABLE_ENTITY.get(),
                dev.gathering.client.TableMiniatureRenderer::new);

        // The felt is one texture tinted per table, so the tint needs a handler on each loader.
        // Every table, not only the wooden one: the felt is the same dyeable surface on all
        // of them, and a table left off this list keeps its undyed texture forever with no
        // error to say why.
        for (var table : GatheringContent.tables()) {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                    TableColors::tintOf, table.get());
        }
        for (var item : GatheringContent.tableItems()) {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                    (stack, tintIndex) -> TableColors.itemTintOf(tintIndex), item.get());
        }
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                dev.gathering.item.DeckItem::tintOf, GatheringContent.DECK.get());

        ClientPlayNetworking.registerGlobalReceiver(CardMetadataPayload.TYPE, (payload, context) ->
                ClientCardCache.get().accept(payload.cards()));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.TableViewPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> dev.gathering.client.ClientTableState.acceptPayload(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.SetProgressPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.SetProgressScreen.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.WantsPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.ClientWants.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.SetMissingPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.MissingCardsScreen.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.TableSaidPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.ClientTableChat.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.PackOpenedPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> context.client().setScreen(
                                new dev.gathering.client.PackOpeningScreen(
                                        payload.setCode(), payload.kind(), payload.cards()))));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.AntePotPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.ClientTableState.acceptPot(
                                        payload.table(), payload.cards())));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.AnteConsentPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.AnteConsentScreen.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.OpenLoanersPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.LoanerScreen.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.OpenCollectionPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.CollectionScreen.show(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.CollectionPagePayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.CollectionScreen.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.ReplayListPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.ReplayListScreen.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.ReplayFramePayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.ClientReplay.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.DraftViewPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> dev.gathering.client.DraftScreen.show(
                                payload.pod(), payload.view(), payload.open())));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.TradeViewPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                dev.gathering.client.TradeScreen.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.CloseTablePayload.TYPE, (payload, context) ->
                        context.client().execute(() -> {
                            dev.gathering.client.ClientTableState.clear();
                            if (context.client().screen instanceof dev.gathering.client.TableScreen) {
                                context.client().setScreen(null);
                            }
                        }));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.OpenTableSetupPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> context.client()
                                .setScreen(new dev.gathering.client.TableSetupScreen(payload.table()))));

        ClientPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.OpenSideboardPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> acceptSideboard(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(OpenImportScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new DecklistImportScreen())));

        ClientPlayNetworking.registerGlobalReceiver(ImportResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof DecklistImportScreen screen) {
                        screen.onResult(payload);
                    }
                }));

        // Vanilla keeps the hovered slot private to the container screen, so the tooltip
        // callback - which already knows which stack it is describing - is where the overlay
        // learns it.
        // Fabric has no cancellable tooltip render event, so the tooltip is emptied instead
        // and vanilla skips drawing it. Safe here because this only ever fires for a card,
        // and a card has no tooltip image - an empty list plus an image is what would throw.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            ClientHoverState.setHovered(stack);
            if (CardZoomOverlay.replacesTooltipFor(stack)) {
                lines.clear();
            }
        });

        // The tooltip callback only fires when there is a tooltip, so without clearing first
        // the last card the cursor touched would keep answering for every empty slot after it.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.beforeRender(screen).register(
                        (rendered, graphics, mouseX, mouseY, tickDelta) -> ClientHoverState.clear()));

        // Which look the mod draws itself with, put in the game's own video settings. The
        // condition and the widget are in :common so both loaders offer exactly the same row.
        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) -> GuiThemeOption.addTo(screen));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // No screen means no slots, so nothing is hovered.
            if (client.screen == null) {
                ClientHoverState.clear();
            }
            ClientCardRequests.tick();
            // The scripted run, on this loader too. It does nothing at all unless the
            // property is set, and it is the only thing that tells us whether Fabric plays
            // the game rather than merely starting it.
            dev.gathering.client.DevScene.tick(client);
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            // With a screen open the screen hook draws it. The HUD still renders under an
            // open screen, so without this the zoom drew from both hooks at once - a doubled
            // backdrop and a full-screen card fighting the screen's own panel. NeoForge's
            // HUD hook has the same guard.
            if (Minecraft.getInstance().screen != null) {
                return;
            }
            CardZoomOverlay.render(
                    graphics,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
        });

        // Over an open screen the inspect panel needs its own hook; drawn after the screen
        // so it sits above slots and the vanilla tooltip it replaces.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register(
                        (rendered, graphics, mouseX, mouseY, tickDelta) -> CardZoomOverlay.renderAtCursor(
                                graphics,
                                client.getWindow().getGuiScaledWidth(),
                                client.getWindow().getGuiScaledHeight(),
                                mouseX,
                                mouseY)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // What one server told us is not true of the next one.
            ClientCardCache.get().clear();
            dev.gathering.client.ClientWants.clear();
            ClientCardRequests.clear();
            ClientHoverState.clear();
            dev.gathering.client.ClientTableState.clear();
            dev.gathering.client.ClientTableChat.clear();
        });
    }
}
