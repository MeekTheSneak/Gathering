package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CloseTablePayload;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.network.ImportDecklistPayload;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.network.OpenSideboardPayload;
import dev.gathering.network.OpenTableSetupPayload;
import dev.gathering.network.RequestCardMetadataPayload;
import dev.gathering.network.SideboardEditPayload;
import dev.gathering.network.StartTablePayload;
import dev.gathering.network.TableActionPayload;
import dev.gathering.network.TableViewPayload;
import dev.gathering.server.CardMetadataRequests;
import dev.gathering.server.DeckEdits;
import dev.gathering.server.DecklistImport;
import dev.gathering.server.TableActions;
import dev.gathering.service.CardDataService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload registration and the server-side handlers.
 *
 * <p>The client handlers live in the client package, wired in from there, so nothing on a
 * dedicated server ever names a client class.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID)
public final class GatheringNetwork {

    /** Bumped when a payload's shape changes in a way an older client cannot read. */
    private static final String PROTOCOL_VERSION = "1";

    private GatheringNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                ImportDecklistPayload.TYPE,
                ImportDecklistPayload.STREAM_CODEC,
                GatheringNetwork::onImportRequest);

        registrar.playToServer(
                RequestCardMetadataPayload.TYPE,
                RequestCardMetadataPayload.STREAM_CODEC,
                GatheringNetwork::onMetadataRequest);

        registrar.playToServer(
                DeckEditPayload.TYPE,
                DeckEditPayload.STREAM_CODEC,
                GatheringNetwork::onDeckEdit);

        registrar.playToServer(
                TableActionPayload.TYPE,
                TableActionPayload.STREAM_CODEC,
                GatheringNetwork::onTableAction);

        registrar.playToServer(
                StartTablePayload.TYPE,
                StartTablePayload.STREAM_CODEC,
                GatheringNetwork::onStartTable);

        registrar.playToServer(
                SideboardEditPayload.TYPE,
                SideboardEditPayload.STREAM_CODEC,
                GatheringNetwork::onSideboardEdit);

        // Registered here so both sides agree on the protocol; the handlers are supplied by
        // the client bootstrap, which is the only place allowed to name a client class.
        registrar.playToClient(
                CardMetadataPayload.TYPE,
                CardMetadataPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                ImportResultPayload.TYPE,
                ImportResultPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                OpenImportScreenPayload.TYPE,
                OpenImportScreenPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                TableViewPayload.TYPE,
                TableViewPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                CloseTablePayload.TYPE,
                CloseTablePayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                OpenTableSetupPayload.TYPE,
                OpenTableSetupPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                OpenSideboardPayload.TYPE,
                OpenSideboardPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
    }

    private static void onImportRequest(ImportDecklistPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.pipeline_unavailable"));
            return;
        }
        // Import itself is asynchronous by construction, so this hands straight off to the
        // card pipeline's executor rather than doing anything on the network thread.
        DecklistImport.importFor(
                player, service, payload.decklist(), payload.deckName(), payload.description());
    }

    private static void onStartTable(StartTablePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.gathering.server.TableSetup.handle(player, payload);
        }
    }

    private static void onSideboardEdit(SideboardEditPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.gathering.server.Sideboarding.handle(player, payload);
        }
    }

    private static void onTableAction(TableActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TableActions.handle(player, payload);
        }
    }

    private static void onDeckEdit(DeckEditPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DeckEdits.handle(player, payload);
        }
    }

    private static void onMetadataRequest(RequestCardMetadataPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        CardDataService.active().ifPresent(service ->
                CardMetadataRequests.handle(player, service, payload));
    }
}
