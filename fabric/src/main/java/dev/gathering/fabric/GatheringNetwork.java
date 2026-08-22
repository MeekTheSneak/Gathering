package dev.gathering.fabric;

import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.network.ImportDecklistPayload;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.network.RequestCardMetadataPayload;
import dev.gathering.server.CardMetadataRequests;
import dev.gathering.server.DeckEdits;
import dev.gathering.server.DecklistImport;
import dev.gathering.service.CardDataService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * Payload registration and the server-side handlers, mirroring the NeoForge bootstrap.
 *
 * <p>Clientbound types are registered here too so both sides agree on the protocol; their
 * handlers are installed by the client entry point, which is the only place allowed to name
 * a client class.
 */
final class GatheringNetwork {

    private GatheringNetwork() {
    }

    static void bootstrap() {
        PayloadTypeRegistry.playC2S().register(ImportDecklistPayload.TYPE, ImportDecklistPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                RequestCardMetadataPayload.TYPE, RequestCardMetadataPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DeckEditPayload.TYPE, DeckEditPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CardMetadataPayload.TYPE, CardMetadataPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ImportResultPayload.TYPE, ImportResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenImportScreenPayload.TYPE, OpenImportScreenPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ImportDecklistPayload.TYPE, (payload, context) -> {
            CardDataService service = CardDataService.active().orElse(null);
            if (service == null) {
                context.player().sendSystemMessage(
                        Component.translatable("message.gathering.pipeline_unavailable"));
                return;
            }
            // Import is asynchronous by construction; nothing here touches the network thread
            // beyond handing the text over.
            DecklistImport.importFor(
                    context.player(), service, payload.decklist(), payload.deckName(), payload.description());
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestCardMetadataPayload.TYPE, (payload, context) ->
                CardDataService.active().ifPresent(service ->
                        CardMetadataRequests.handle(context.player(), service, payload)));

        ServerPlayNetworking.registerGlobalReceiver(DeckEditPayload.TYPE, (payload, context) ->
                DeckEdits.handle(context.player(), payload));
    }
}
