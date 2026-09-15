package dev.gathering.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gathering.Gathering;
import dev.gathering.client.CardFaceRenderer;
import dev.gathering.client.CardZoomOverlay;
import dev.gathering.client.ClientCardCache;
import dev.gathering.client.ClientFetching;
import dev.gathering.client.ClientHoverState;
import dev.gathering.client.ClientNetworking;
import dev.gathering.client.ClientPayloads;
import dev.gathering.client.GuiThemeOption;
import dev.gathering.client.TableColors;
import dev.gathering.client.DeckContentsScreen;
import dev.gathering.client.ZoomKeyState;
import dev.gathering.item.GatheringContent;
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

    /** One clientbound route, applied on the client thread. Generic so the type needs no cast. */
    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload>
            void receive(ClientPayloads.Route<T> route) {
        ClientPlayNetworking.registerGlobalReceiver(route.type(), (payload, context) ->
                context.client().execute(() -> route.apply().accept(payload)));
    }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ZOOM_KEY);
        // The table's verbs, from the shared list. See the note in the NeoForge client.
        for (KeyMapping mapping : dev.gathering.client.TableShortcuts.all()) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }

        CardNameLookup.Binding.bind(ClientCardCache.get());
        DeckScreenHook.Binding.bind(hand -> Minecraft.getInstance().setScreen(new DeckContentsScreen(hand)));
        dev.gathering.service.CreativeDeckHook.Binding.bind((player, deck, cards) -> {
            if (player.level().isClientSide() && player.isCreative()
                && Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
                dev.gathering.client.ClientNetworking.send(new dev.gathering.network.CreativeDeckEditPayload(deck, cards));
            }
        });
        CardZoomOverlay.bindKeyState(ZoomKeyState.of(ZOOM_KEY, () -> KeyBindingHelper.getBoundKeyOf(ZOOM_KEY)));
        CardZoomOverlay.bindKeyName(ZOOM_KEY::getTranslatedKeyMessage);
        dev.gathering.client.TableShortcuts.bindKeyLookup(KeyBindingHelper::getBoundKeyOf);
        dev.gathering.client.RecentThings.bindServerLookup(
                dev.gathering.client.WhichServer::name);
        ClientNetworking.bindSender(ClientPlayNetworking::send);
        // The client's half of the protocol check: answer with our number. A development run may
        // pretend to another, which is how a mismatched pair is tried without building two jars.
        int ours = net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                ? Integer.getInteger("gathering.pretendProtocol", dev.gathering.network.GatheringProtocol.VERSION)
                : dev.gathering.network.GatheringProtocol.VERSION;
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.registerGlobalReceiver(
                dev.gathering.fabric.ProtocolCheck.TYPE,
                (question, context) -> context.responseSender().sendPacket(new dev.gathering.fabric.ProtocolCheck(ours)));
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
        // And the tournament floating over a Scorekeeper's Desk.
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                GatheringContent.SCOREKEEPERS_DESK_ENTITY.get(),
                dev.gathering.client.ScorekeepersDeskRenderer::new);
        // A chair's seat is never drawn: the chair is the block, and the seat is only what is sat on.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                GatheringContent.CHAIR_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);

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

        // What happens when each clientbound payload arrives is ClientPayloads', shared with
        // NeoForge; this loader only registers a receiver for each and gets the work onto the
        // client thread. Checked against the protocol first, so a payload the server can send
        // and this client cannot apply stops the client starting rather than going missing.
        ClientPayloads.checkCovers(dev.gathering.network.GatheringProtocol.TO_CLIENT);
        for (ClientPayloads.Route<?> route : ClientPayloads.ROUTES) {
            receive(route);
        }

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

        ClientTickEvents.END_CLIENT_TICK.register(dev.gathering.client.ClientTicks::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> dev.gathering.client.ClientTicks.stopping());

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            // With a screen open the screen hook draws it. The HUD still renders under an
            // open screen, so without this the zoom drew from both hooks at once - a doubled
            // backdrop and a full-screen card fighting the screen's own panel. NeoForge's
            // HUD hook has the same guard.
            if (Minecraft.getInstance().screen != null) {
                return;
            }
            dev.gathering.client.EventHud.render(graphics,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
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
            dev.gathering.client.ClientState.forgetTheServer();
        });
    }
}
