package dev.gathering.fabric;

import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CloseTablePayload;
import dev.gathering.network.CreateTokenPayload;
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
import dev.gathering.network.UndoPayload;
import dev.gathering.network.TableViewPayload;
import dev.gathering.server.CardMetadataRequests;
import dev.gathering.server.DeckEdits;
import dev.gathering.server.DecklistImport;
import dev.gathering.server.TableActions;
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
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.RenameDeckPayload.TYPE,
                dev.gathering.network.RenameDeckPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.TradeActionPayload.TYPE,
                dev.gathering.network.TradeActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TableActionPayload.TYPE, TableActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UndoPayload.TYPE, UndoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(StartTablePayload.TYPE, StartTablePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                SideboardEditPayload.TYPE, SideboardEditPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                CreateTokenPayload.TYPE, CreateTokenPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.DraftPickPayload.TYPE,
                dev.gathering.network.DraftPickPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.AddBasicsPayload.TYPE,
                dev.gathering.network.AddBasicsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.CollectionSearchPayload.TYPE,
                dev.gathering.network.CollectionSearchPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.CollectionTakePayload.TYPE,
                dev.gathering.network.CollectionTakePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.AnteAnswerPayload.TYPE,
                dev.gathering.network.AnteAnswerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.AntePotPayload.TYPE,
                dev.gathering.network.AntePotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.AnteConsentPayload.TYPE,
                dev.gathering.network.AnteConsentPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
                dev.gathering.network.TakeLoanerPayload.TYPE,
                dev.gathering.network.TakeLoanerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.OpenLoanersPayload.TYPE,
                dev.gathering.network.OpenLoanersPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.OpenCollectionPayload.TYPE,
                dev.gathering.network.OpenCollectionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.CollectionPagePayload.TYPE,
                dev.gathering.network.CollectionPagePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TableViewPayload.TYPE, TableViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.DraftViewPayload.TYPE,
                dev.gathering.network.DraftViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.TradeViewPayload.TYPE,
                dev.gathering.network.TradeViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                dev.gathering.network.PackOpenedPayload.TYPE,
                dev.gathering.network.PackOpenedPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CloseTablePayload.TYPE, CloseTablePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CardMetadataPayload.TYPE, CardMetadataPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ImportResultPayload.TYPE, ImportResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenImportScreenPayload.TYPE, OpenImportScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                OpenTableSetupPayload.TYPE, OpenTableSetupPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
                OpenSideboardPayload.TYPE, OpenSideboardPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ImportDecklistPayload.TYPE, (payload, context) -> {
            CardDataService service = CardDataService.active().orElse(null);
            if (service == null) {
                context.player().sendSystemMessage(
                        Component.translatable("message.gathering.pipeline_unavailable"));
                return;
            }
            // Import is asynchronous by construction; nothing here touches the network thread
            // beyond handing the text over.
            DecklistImport.importFor(context.player(), service, payload.decklist(),
                    payload.deckName(), payload.description(), payload.from().orElse(null));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestCardMetadataPayload.TYPE, (payload, context) ->
                CardDataService.active().ifPresent(service ->
                        CardMetadataRequests.handle(context.player(), service, payload)));

        ServerPlayNetworking.registerGlobalReceiver(DeckEditPayload.TYPE, (payload, context) ->
                DeckEdits.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.RenameDeckPayload.TYPE, (payload, context) ->
                        DeckEdits.rename(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.TradeActionPayload.TYPE, (payload, context) ->
                        dev.gathering.server.TradeSessions.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(TableActionPayload.TYPE, (payload, context) ->
                TableActions.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(UndoPayload.TYPE, (payload, context) ->
                TableActions.handleUndo(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(StartTablePayload.TYPE, (payload, context) ->
                dev.gathering.server.TableSetup.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.DraftPickPayload.TYPE, (payload, context) ->
                        dev.gathering.server.DraftActions.handle(
                                context.player(), payload.pod(), payload.positions()));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.AddBasicsPayload.TYPE, (payload, context) ->
                        dev.gathering.server.BasicLands.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.CollectionSearchPayload.TYPE, (payload, context) ->
                        dev.gathering.server.CollectionView.search(
                                context.player(), payload.where(), payload.query(),
                                payload.descending(), payload.page(), payload.perPage()));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.CollectionTakePayload.TYPE, (payload, context) ->
                        dev.gathering.server.CollectionView.take(
                                context.player(), payload.where(), payload.card(),
                                payload.howMany()));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.AnteAnswerPayload.TYPE, (payload, context) ->
                        dev.gathering.server.Antes.answer(context.player(), payload.table(),
                                payload.in()
                                        ? dev.gathering.core.ante.AnteConsent.Answer.IN
                                        : dev.gathering.core.ante.AnteConsent.Answer.OUT));

        ServerPlayNetworking.registerGlobalReceiver(
                dev.gathering.network.TakeLoanerPayload.TYPE, (payload, context) ->
                        dev.gathering.server.Lending.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(SideboardEditPayload.TYPE, (payload, context) ->
                dev.gathering.server.Sideboarding.handle(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(CreateTokenPayload.TYPE, (payload, context) ->
                CardDataService.active().ifPresent(service ->
                        dev.gathering.server.TokenCreation.handle(context.player(), service, payload)));
    }
}
