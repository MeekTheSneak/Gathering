package dev.gathering.neoforge;

import dev.gathering.Gathering;
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
        // Said out loud rather than inherited. NeoForge 21.1.248's PayloadRegistrar starts at
        // HandlerThread.MAIN and wraps every handler in MainThreadPayloadHandler, so a handler
        // is already on the server thread - or the render thread, going the other way - before
        // its first line runs. Half of these used to hop again with enqueueWork and half did
        // not, which read as the unwrapped half being unsafe. Neither is: enqueueWork runs the
        // task straight through when it is already on the main thread, so the hops were inert.
        // One statement here, and no per-handler ceremony - and nothing depending on a default
        // that a later NeoForge is free to change.
        var registrar = event.registrar(PROTOCOL_VERSION)
                .executesOn(net.neoforged.neoforge.network.registration.HandlerThread.MAIN);

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
                dev.gathering.network.RenameDeckPayload.TYPE,
                dev.gathering.network.RenameDeckPayload.STREAM_CODEC,
                GatheringNetwork::onDeckRename);

        registrar.playToServer(
                dev.gathering.network.TradeActionPayload.TYPE,
                dev.gathering.network.TradeActionPayload.STREAM_CODEC,
                GatheringNetwork::onTradeAction);

        registrar.playToServer(
                dev.gathering.network.TakeLoanerPayload.TYPE,
                dev.gathering.network.TakeLoanerPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.Lending.handle(player, payload);
                    }
                });

        registrar.playToServer(
                dev.gathering.network.AnteAnswerPayload.TYPE,
                dev.gathering.network.AnteAnswerPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.Antes.answer(player, payload.table(),
                                payload.in()
                                        ? dev.gathering.core.ante.AnteConsent.Answer.IN
                                        : dev.gathering.core.ante.AnteConsent.Answer.OUT);
                    }
                });

        registrar.playToServer(
                TableActionPayload.TYPE,
                TableActionPayload.STREAM_CODEC,
                GatheringNetwork::onTableAction);

        registrar.playToServer(
                UndoPayload.TYPE,
                UndoPayload.STREAM_CODEC,
                GatheringNetwork::onUndo);

        registrar.playToServer(
                StartTablePayload.TYPE,
                StartTablePayload.STREAM_CODEC,
                GatheringNetwork::onStartTable);

        registrar.playToServer(
                SideboardEditPayload.TYPE,
                SideboardEditPayload.STREAM_CODEC,
                GatheringNetwork::onSideboardEdit);

        registrar.playToServer(
                CreateTokenPayload.TYPE,
                CreateTokenPayload.STREAM_CODEC,
                GatheringNetwork::onCreateToken);

        registrar.playToServer(
                dev.gathering.network.RollDicePayload.TYPE,
                dev.gathering.network.RollDicePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.DiceRolls.roll(player, payload);
                    }
                });
        registrar.playToServer(
                dev.gathering.network.FlipCoinPayload.TYPE,
                dev.gathering.network.FlipCoinPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.DiceRolls.flip(player, payload);
                    }
                });
        registrar.playToServer(
                dev.gathering.network.FetchBasicPayload.TYPE,
                dev.gathering.network.FetchBasicPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.BasicLandFetch.handle(player, payload);
                    }
                });

        registrar.playToServer(
                dev.gathering.network.RevealUntilPayload.TYPE,
                dev.gathering.network.RevealUntilPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.LibraryReveals.handle(player, payload);
                    }
                });

        registrar.playToServer(
                dev.gathering.network.DiscardAtRandomPayload.TYPE,
                dev.gathering.network.DiscardAtRandomPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.RandomDiscards.handle(player, payload);
                    }
                });

        registrar.playToServer(
                dev.gathering.network.ToBottomAtRandomPayload.TYPE,
                dev.gathering.network.ToBottomAtRandomPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.gathering.server.RandomReturns.handle(player, payload);
                    }
                });

        registrar.playToServer(
                dev.gathering.network.DraftPickPayload.TYPE,
                dev.gathering.network.DraftPickPayload.STREAM_CODEC,
                GatheringNetwork::onDraftPick);

        registrar.playToServer(
                dev.gathering.network.AddBasicsPayload.TYPE,
                dev.gathering.network.AddBasicsPayload.STREAM_CODEC,
                GatheringNetwork::onAddBasics);

        registrar.playToServer(
                dev.gathering.network.CollectionSearchPayload.TYPE,
                dev.gathering.network.CollectionSearchPayload.STREAM_CODEC,
                (payload, context) -> dev.gathering.server.CollectionView.search(
                        (net.minecraft.server.level.ServerPlayer) context.player(),
                        payload.where(), payload.query(), payload.descending(), payload.page(),
                        payload.perPage()));
        registrar.playToServer(
                dev.gathering.network.CollectionTakePayload.TYPE,
                dev.gathering.network.CollectionTakePayload.STREAM_CODEC,
                (payload, context) -> dev.gathering.server.CollectionView.take(
                        (net.minecraft.server.level.ServerPlayer) context.player(),
                        payload.where(), payload.card(), payload.howMany()));

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
                dev.gathering.network.DraftViewPayload.TYPE,
                dev.gathering.network.DraftViewPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                dev.gathering.network.TradeViewPayload.TYPE,
                dev.gathering.network.TradeViewPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                dev.gathering.network.PackOpenedPayload.TYPE,
                dev.gathering.network.PackOpenedPayload.STREAM_CODEC,
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
                dev.gathering.network.OpenCollectionPayload.TYPE,
                dev.gathering.network.OpenCollectionPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                dev.gathering.network.CollectionPagePayload.TYPE,
                dev.gathering.network.CollectionPagePayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                dev.gathering.network.OpenLoanersPayload.TYPE,
                dev.gathering.network.OpenLoanersPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                dev.gathering.network.AntePotPayload.TYPE,
                dev.gathering.network.AntePotPayload.STREAM_CODEC,
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
        registrar.playToClient(
                dev.gathering.network.AnteConsentPayload.TYPE,
                dev.gathering.network.AnteConsentPayload.STREAM_CODEC,
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
        DecklistImport.importFor(player, service, payload.decklist(), payload.deckName(),
                payload.description(), payload.from().orElse(null));
    }

    private static void onStartTable(StartTablePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.gathering.server.TableSetup.handle(player, payload);
        }
    }

    private static void onCreateToken(CreateTokenPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CardDataService.active().ifPresent(service ->
                    dev.gathering.server.TokenCreation.handle(player, service, payload));
        }
    }

    private static void onSideboardEdit(SideboardEditPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.gathering.server.Sideboarding.handle(player, payload);
        }
    }

    private static void onUndo(UndoPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            dev.gathering.server.TableActions.handleUndo(player, payload);
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

    private static void onTradeAction(
            dev.gathering.network.TradeActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.gathering.server.TradeSessions.handle(player, payload);
        }
    }

    private static void onDeckRename(
            dev.gathering.network.RenameDeckPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DeckEdits.rename(player, payload);
        }
    }

    private static void onAddBasics(
            dev.gathering.network.AddBasicsPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            dev.gathering.server.BasicLands.handle(player, payload);
        }
    }

    private static void onDraftPick(
            dev.gathering.network.DraftPickPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            dev.gathering.server.DraftActions.handle(player, payload.pod(), payload.positions());
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
