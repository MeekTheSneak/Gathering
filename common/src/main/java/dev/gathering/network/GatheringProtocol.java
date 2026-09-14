package dev.gathering.network;

import dev.gathering.server.CardMetadataRequests;
import dev.gathering.server.DeckEdits;
import dev.gathering.server.DecklistImport;
import dev.gathering.server.TableActions;
import dev.gathering.service.CardDataService;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every payload this mod sends, which way it goes, and - for the ones a client sends - what
 * the server does with it.
 * <p>Both loaders used to spell all of this out for themselves: sixty-odd registrations each,
 * and a handler per serverbound payload written twice, once against each loader's context. The
 * two lists agreed because somebody had kept them agreeing, and the only thing that would have
 * said otherwise was a player on one loader finding a verb that did nothing.
 * <p>Now each loader walks these lists and adapts what is genuinely its own: how it registers a
 * type, which thread a handler runs on, and how it hands over the player. Neither can register
 * something the other does not, and a handler exists once.
 * <p>Typed all the way through. Each route carries its own payload type, codec and handler with
 * the same type parameter, and the loaders register them through a generic method, so there is
 * no cast and no reflection anywhere between the wire and the handler.
 * <p><b>Nothing here names a client class.</b> This is loaded by dedicated servers. The
 * clientbound list is types and codecs only: what a client does with them is wired by each
 * loader's client bootstrap.
 */
public final class GatheringProtocol {

    private GatheringProtocol() {
    }

    /**
     * A payload a client sends, and what the server does when it arrives.
     * <p>Handlers run on the server thread, with a player the loader has already confirmed is
     * a server player. Everything past that - reach, seats, rights - is the handler's to check,
     * as it always was.
     */
    public record ToServer<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<ServerPlayer, T> handler) {
    }

    /** A payload the server sends. What the client does with it is the client bootstrap's. */
    public record ToClient<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    }

    private static <T extends CustomPacketPayload> ToServer<T> toServer(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<ServerPlayer, T> handler) {
        return new ToServer<>(type, codec, handler);
    }

    private static <T extends CustomPacketPayload> ToClient<T> toClient(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return new ToClient<>(type, codec);
    }

    /** Everything a client may send. */
    public static final List<ToServer<?>> TO_SERVER = List.of(
            toServer(ImportDecklistPayload.TYPE, ImportDecklistPayload.STREAM_CODEC,
                    GatheringProtocol::importDecklist),
            toServer(RequestCardMetadataPayload.TYPE, RequestCardMetadataPayload.STREAM_CODEC,
                    (player, payload) -> CardDataService.active().ifPresent(service ->
                            CardMetadataRequests.handle(player, service, payload))),
            toServer(DeckEditPayload.TYPE, DeckEditPayload.STREAM_CODEC, DeckEdits::handle),
            toServer(RenameDeckPayload.TYPE, RenameDeckPayload.STREAM_CODEC, DeckEdits::rename),
            toServer(SleeveDeckPayload.TYPE, SleeveDeckPayload.STREAM_CODEC, DeckEdits::sleeve),
            toServer(TradeActionPayload.TYPE, TradeActionPayload.STREAM_CODEC,
                    dev.gathering.server.TradeSessions::handle),
            toServer(TakeLoanerPayload.TYPE, TakeLoanerPayload.STREAM_CODEC,
                    dev.gathering.server.Lending::handle),
            toServer(AnteAnswerPayload.TYPE, AnteAnswerPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.Antes.answer(player, payload.table(),
                            payload.in()
                                    ? dev.gathering.core.ante.AnteConsent.Answer.IN
                                    : dev.gathering.core.ante.AnteConsent.Answer.OUT)),
            toServer(TableActionPayload.TYPE, TableActionPayload.STREAM_CODEC, TableActions::handle),
            toServer(TableActionsPayload.TYPE, TableActionsPayload.STREAM_CODEC,
                    TableActions::handleAll),
            toServer(UndoPayload.TYPE, UndoPayload.STREAM_CODEC, TableActions::handleUndo),
            toServer(CreatePodPayload.TYPE, CreatePodPayload.STREAM_CODEC,
                    dev.gathering.server.PodLobbies::create),
            toServer(PodActionPayload.TYPE, PodActionPayload.STREAM_CODEC,
                    dev.gathering.server.PodLobbies::act),
            toServer(StartTablePayload.TYPE, StartTablePayload.STREAM_CODEC,
                    dev.gathering.server.TableSetup::handle),
            toServer(SideboardEditPayload.TYPE, SideboardEditPayload.STREAM_CODEC,
                    dev.gathering.server.Sideboarding::handle),
            toServer(CreateTokenPayload.TYPE, CreateTokenPayload.STREAM_CODEC,
                    (player, payload) -> CardDataService.active().ifPresent(service ->
                            dev.gathering.server.TokenCreation.handle(player, service, payload))),
            toServer(MakeTokenPayload.TYPE, MakeTokenPayload.STREAM_CODEC,
                    (player, payload) -> CardDataService.active().ifPresent(service ->
                            dev.gathering.server.TokenCreation.handleChosen(player, service, payload))),
            toServer(StarterPayload.TYPE, StarterPayload.STREAM_CODEC,
                    dev.gathering.server.StarterBoosters::handle),
            toServer(PracticePayload.TYPE, PracticePayload.STREAM_CODEC,
                    dev.gathering.server.PracticeTable::handle),
            toServer(BringInDungeonPayload.TYPE, BringInDungeonPayload.STREAM_CODEC,
                    (player, payload) -> CardDataService.active().ifPresent(service ->
                            dev.gathering.server.Dungeons.handle(player, service, payload))),
            toServer(AskSetProgressPayload.TYPE, AskSetProgressPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.CollectionSets.progress(
                            player, payload.collection())),
            toServer(MarkWantedPayload.TYPE, MarkWantedPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.Wants.mark(
                            player, payload.printing(), payload.wanted())),
            toServer(AskSetMissingPayload.TYPE, AskSetMissingPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.CollectionSets.missing(
                            player, payload.collection(), payload.setCode())),
            toServer(TableChatPayload.TYPE, TableChatPayload.STREAM_CODEC,
                    dev.gathering.server.TableTalk::handle),
            toServer(RollDicePayload.TYPE, RollDicePayload.STREAM_CODEC,
                    dev.gathering.server.DiceRolls::roll),
            toServer(RollPlanarPayload.TYPE, RollPlanarPayload.STREAM_CODEC,
                    dev.gathering.server.DiceRolls::planar),
            toServer(FlipCoinPayload.TYPE, FlipCoinPayload.STREAM_CODEC,
                    dev.gathering.server.DiceRolls::flip),
            toServer(FetchBasicPayload.TYPE, FetchBasicPayload.STREAM_CODEC,
                    dev.gathering.server.BasicLandFetch::handle),
            toServer(RevealUntilPayload.TYPE, RevealUntilPayload.STREAM_CODEC,
                    dev.gathering.server.LibraryReveals::handle),
            toServer(DiscardAtRandomPayload.TYPE, DiscardAtRandomPayload.STREAM_CODEC,
                    dev.gathering.server.RandomDiscards::handle),
            toServer(ToBottomAtRandomPayload.TYPE, ToBottomAtRandomPayload.STREAM_CODEC,
                    dev.gathering.server.RandomReturns::handle),
            toServer(DraftPickPayload.TYPE, DraftPickPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.DraftActions.handle(
                            player, payload.pod(), payload.positions())),
            toServer(AddBasicsPayload.TYPE, AddBasicsPayload.STREAM_CODEC,
                    dev.gathering.server.BasicLands::handle),
            toServer(PocketCardsPayload.TYPE, PocketCardsPayload.STREAM_CODEC,
                    dev.gathering.server.PocketCards::handle),
            toServer(CollectionSearchPayload.TYPE, CollectionSearchPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.CollectionView.search(
                            player, payload.where(), payload.query(), payload.descending(),
                            payload.page(), payload.perPage(), payload.pockets(),
                            payload.revision())),
            toServer(CollectionTakePayload.TYPE, CollectionTakePayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.CollectionView.take(
                            player, payload.where(), payload.card(), payload.howMany())),
            toServer(BuildDeckPayload.TYPE, BuildDeckPayload.STREAM_CODEC,
                    dev.gathering.server.CollectionView::build),
            toServer(WatchReplayPayload.TYPE, WatchReplayPayload.STREAM_CODEC,
                    dev.gathering.server.ReplayWatch::handle));

    /** Everything the server may send. */
    public static final List<ToClient<?>> TO_CLIENT = List.of(
            toClient(CardMetadataPayload.TYPE, CardMetadataPayload.STREAM_CODEC),
            toClient(CardsUnresolvedPayload.TYPE, CardsUnresolvedPayload.STREAM_CODEC),
            toClient(ImportResultPayload.TYPE, ImportResultPayload.STREAM_CODEC),
            toClient(OpenImportScreenPayload.TYPE, OpenImportScreenPayload.STREAM_CODEC),
            toClient(SetProgressPayload.TYPE, SetProgressPayload.STREAM_CODEC),
            toClient(SetMissingPayload.TYPE, SetMissingPayload.STREAM_CODEC),
            toClient(WantsPayload.TYPE, WantsPayload.STREAM_CODEC),
            toClient(TableSaidPayload.TYPE, TableSaidPayload.STREAM_CODEC),
            toClient(TableViewPayload.TYPE, TableViewPayload.STREAM_CODEC),
            toClient(DraftViewPayload.TYPE, DraftViewPayload.STREAM_CODEC),
            toClient(TradeViewPayload.TYPE, TradeViewPayload.STREAM_CODEC),
            toClient(PackOpenedPayload.TYPE, PackOpenedPayload.STREAM_CODEC),
            toClient(MyDeckPayload.TYPE, MyDeckPayload.STREAM_CODEC),
            toClient(CloseTablePayload.TYPE, CloseTablePayload.STREAM_CODEC),
            toClient(OpenTableSetupPayload.TYPE, OpenTableSetupPayload.STREAM_CODEC),
            toClient(PodLobbyPayload.TYPE, PodLobbyPayload.STREAM_CODEC),
            toClient(OpenCollectionPayload.TYPE, OpenCollectionPayload.STREAM_CODEC),
            toClient(CollectionPagePayload.TYPE, CollectionPagePayload.STREAM_CODEC),
            toClient(OpenLoanersPayload.TYPE, OpenLoanersPayload.STREAM_CODEC),
            toClient(AntePotPayload.TYPE, AntePotPayload.STREAM_CODEC),
            toClient(AnteConsentPayload.TYPE, AnteConsentPayload.STREAM_CODEC),
            toClient(ReplayListPayload.TYPE, ReplayListPayload.STREAM_CODEC),
            toClient(ReplayFramePayload.TYPE, ReplayFramePayload.STREAM_CODEC),
            toClient(OpenSideboardPayload.TYPE, OpenSideboardPayload.STREAM_CODEC),
            toClient(TokenChoicesPayload.TYPE, TokenChoicesPayload.STREAM_CODEC));

    /**
     * A decklist to import, handed to the card pipeline's own executor.
     * <p>Said rather than dropped when there is no pipeline, because the import screen is
     * waiting on an answer and a silent refusal leaves it waiting.
     */
    private static void importDecklist(ServerPlayer player, ImportDecklistPayload payload) {
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.pipeline_unavailable"));
            return;
        }
        // Asynchronous by construction: the text goes to the pipeline's executor and this
        // returns, so the server thread is not held while Scryfall is asked about a hundred
        // and forty cards.
        DecklistImport.importFor(player, service, payload.decklist(), payload.deckName(),
                payload.description(), payload.from().orElse(null),
                java.util.Optional.of(payload.forRequest()));
    }
}
