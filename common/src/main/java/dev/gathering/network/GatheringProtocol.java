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

    /**
     * Bumped when a payload's shape changes in a way an older client cannot read.
     * <p>Two, because several did at once and version one stayed put through all of them: the
     * deck component was split into a public copy and an owner's copy, the owner's push grew
     * a number, a trade action and a trade view grew the trade's own identity, a build request
     * and its result grew the press they belong to, and closing a table grew the table it is
     * about. A mixed old-and-new pair does not fail gracefully on any of those - it fails
     * while decoding a payload, which disconnects whoever is on the wrong side of it with a
     * message about a byte count. Refusing to connect at all is the honest answer, and it is
     * what a different number here buys.
     * <p>Three, for the batched table action: a client that sends a selection's verbs as one
     * payload cannot play at a server that does not know the payload.
     * <p>Four, for tokens by printing: a card summary now carries the printing of each token a
     * card makes, and a name that matches several tokens is answered with a choice.
     * <p>Five, for card lookups that end without a name: a server now says whether a printing
     * does not exist or could not be looked up, in a payload an older client cannot read.
     * <p>Six, for draft and sealed signups: creating one, acting at one, and being shown one.
     * <p>Seven, for playing a long table apart.
     * <p>Eight, for tournaments: creating, acting in and being shown one, and being pointed to a seat.
     * <p>Nine, for the pick clock and registration points: pack settings carry a clock, a draft
     * view carries the seconds left, and a host can mark where players register.
     * <p>Ten, for loaner decks: a deck on the wire carries whether it was lent.
     * <p>Eleven, for the London mulligan: a seat on the board carries its mulligans.
     * <p>Twelve, for choosing to draw: a move an older server cannot read, and a turn marker on
     * the board that says the choice has been made.
     * <p>Thirteen, for a host's controls: an event's view carries why each of them does not apply.
     * <p>Fourteen, for a table's terms: the format, match length, game and stakes sent beside its
     * board.
     * <p>Fifteen, for a pack's cards waiting under its wrapper until it is torn.
     * <p>Sixteen, for the cards a creative click put into a deck.
     * <p>Seventeen, for hosting a tournament at a Scorekeeper's Desk rather than a table: the list
     * says which desk it was opened at, and creating one names the desk.
     * <p>Eighteen, for joining a game that is on: asked to join or watch, choosing a deck from a list, and
     * asked whether to play one that is not legal anyway.
     * <p>Nineteen, for seats kept for players away from the board: who is away and for how long beside the
     * board, and a vote to free a seat.
     * <p>Twenty, for telling the server the guided first game began and was finished, which the starter boosters
     * now require; and a top cut left to the player count.
     * <p>Twenty-one, for taking a payload away: a creative client no longer tells the server which cards it
     * put into a deck, because the server now reads them off the copy the creative menu sends it anyway.
     * <p>Twenty-two, for who is let into a collection: the owner asks for the list, opens or shuts it to
     * everybody, and lets a player in or shuts them out by name.
     * <p>Kept here, beside the payloads it numbers, since both loaders check it: NeoForge by
     * registering its payloads under it, Fabric by asking a joining client for its number while
     * the connection is configured.
     */
    public static final int VERSION = 22;

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

    /**
     * The same handler, answered only while the player has budget left for it.
     * <p>For the requests that cost the server more than the packet: a card lookup queued behind
     * everybody else's on the one card worker, or a board sent to everybody at a table. A person
     * never runs one of these dry; a client sending them in a loop does, and is dropped.
     */
    private static <T> BiConsumer<ServerPlayer, T> budgeted(
            dev.gathering.server.ActionBudget budget, BiConsumer<ServerPlayer, T> handler) {
        return (player, payload) -> {
            if (budget.spend(player.getUUID(), 1)) {
                handler.accept(player, payload);
            }
        };
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
            toServer(CreateEventPayload.TYPE, CreateEventPayload.STREAM_CODEC,
                    dev.gathering.server.events.EventViews::create),
            toServer(EventActionPayload.TYPE, EventActionPayload.STREAM_CODEC,
                    dev.gathering.server.events.EventViews::act),
            toServer(TablesApartPayload.TYPE, TablesApartPayload.STREAM_CODEC,
                    dev.gathering.server.TablesApart::handle),
            toServer(CreatePodPayload.TYPE, CreatePodPayload.STREAM_CODEC,
                    dev.gathering.server.PodLobbies::create),
            toServer(PodActionPayload.TYPE, PodActionPayload.STREAM_CODEC,
                    dev.gathering.server.PodLobbies::act),
            toServer(JoinTableAnswerPayload.TYPE, JoinTableAnswerPayload.STREAM_CODEC,
                    dev.gathering.server.TableJoining::answer),
            toServer(ChooseDeckPayload.TYPE, ChooseDeckPayload.STREAM_CODEC,
                    dev.gathering.server.TableJoining::choose),
            toServer(AwayVotePayload.TYPE, AwayVotePayload.STREAM_CODEC,
                    dev.gathering.server.AwayFromBoard::vote),
            toServer(StartTablePayload.TYPE, StartTablePayload.STREAM_CODEC,
                    dev.gathering.server.TableSetup::handle),
            toServer(SideboardEditPayload.TYPE, SideboardEditPayload.STREAM_CODEC,
                    dev.gathering.server.Sideboarding::handle),
            toServer(CreateTokenPayload.TYPE, CreateTokenPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.CARD_LOOKUPS, (player, payload) -> CardDataService.active().ifPresent(service ->
                            dev.gathering.server.TokenCreation.handle(player, service, payload)))),
            toServer(MakeTokenPayload.TYPE, MakeTokenPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.CARD_LOOKUPS, (player, payload) -> CardDataService.active().ifPresent(service ->
                            dev.gathering.server.TokenCreation.handleChosen(player, service, payload)))),
            toServer(PackTornPayload.TYPE, PackTornPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.PackWrappers.torn(player, payload.wrapper())),
            toServer(StarterPayload.TYPE, StarterPayload.STREAM_CODEC,
                    dev.gathering.server.StarterBoosters::handle),
            toServer(LessonPayload.TYPE, LessonPayload.STREAM_CODEC,
                    dev.gathering.server.LessonRecords::handle),
            toServer(PracticePayload.TYPE, PracticePayload.STREAM_CODEC,
                    dev.gathering.server.PracticeTable::handle),
            toServer(BringInDungeonPayload.TYPE, BringInDungeonPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.CARD_LOOKUPS, (player, payload) -> CardDataService.active().ifPresent(service ->
                            dev.gathering.server.Dungeons.handle(player, service, payload)))),
            toServer(AskSetProgressPayload.TYPE, AskSetProgressPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.CARD_LOOKUPS, (player, payload) -> dev.gathering.server.CollectionSets.progress(
                            player, payload.collection()))),
            toServer(MarkWantedPayload.TYPE, MarkWantedPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.Wants.mark(
                            player, payload.printing(), payload.wanted())),
            toServer(AskSetMissingPayload.TYPE, AskSetMissingPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.WHOLE_SETS, (player, payload) -> dev.gathering.server.CollectionSets.missing(
                            player, payload.collection(), payload.setCode()))),
            toServer(TableChatPayload.TYPE, TableChatPayload.STREAM_CODEC,
                    dev.gathering.server.TableTalk::handle),
            toServer(RollDicePayload.TYPE, RollDicePayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.DiceRolls::roll)),
            toServer(RollPlanarPayload.TYPE, RollPlanarPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.DiceRolls::planar)),
            toServer(FlipCoinPayload.TYPE, FlipCoinPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.DiceRolls::flip)),
            toServer(FetchBasicPayload.TYPE, FetchBasicPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.BasicLandFetch::handle)),
            toServer(RevealUntilPayload.TYPE, RevealUntilPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.LibraryReveals::handle)),
            toServer(DiscardAtRandomPayload.TYPE, DiscardAtRandomPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.RandomDiscards::handle)),
            toServer(ToBottomAtRandomPayload.TYPE, ToBottomAtRandomPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.TABLE_REQUESTS, dev.gathering.server.RandomReturns::handle)),
            toServer(DraftPickPayload.TYPE, DraftPickPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.DraftActions.handle(
                            player, payload.pod(), payload.positions())),
            toServer(AddBasicsPayload.TYPE, AddBasicsPayload.STREAM_CODEC,
                    budgeted(dev.gathering.server.ActionBudget.CARD_LOOKUPS, dev.gathering.server.BasicLands::handle)),
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
            toServer(CollectionKeysAskPayload.TYPE, CollectionKeysAskPayload.STREAM_CODEC,
                    (player, payload) -> dev.gathering.server.CollectionKeys.show(player, payload.where())),
            toServer(CollectionLockPayload.TYPE, CollectionLockPayload.STREAM_CODEC,
                    dev.gathering.server.CollectionKeys::lock),
            toServer(CollectionKeyPayload.TYPE, CollectionKeyPayload.STREAM_CODEC,
                    dev.gathering.server.CollectionKeys::set),
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
            toClient(JoinTablePromptPayload.TYPE, JoinTablePromptPayload.STREAM_CODEC),
            toClient(OpenDeckPickerPayload.TYPE, OpenDeckPickerPayload.STREAM_CODEC),
            toClient(DeckNotLegalPayload.TYPE, DeckNotLegalPayload.STREAM_CODEC),
            toClient(TableAwayPayload.TYPE, TableAwayPayload.STREAM_CODEC),
            toClient(PodLobbyPayload.TYPE, PodLobbyPayload.STREAM_CODEC),
            toClient(EventListPayload.TYPE, EventListPayload.STREAM_CODEC),
            toClient(EventViewPayload.TYPE, EventViewPayload.STREAM_CODEC),
            toClient(EventPointerPayload.TYPE, EventPointerPayload.STREAM_CODEC),
            toClient(OpenCollectionPayload.TYPE, OpenCollectionPayload.STREAM_CODEC),
            toClient(CollectionPagePayload.TYPE, CollectionPagePayload.STREAM_CODEC),
            toClient(CollectionKeysPayload.TYPE, CollectionKeysPayload.STREAM_CODEC),
            toClient(OpenLoanersPayload.TYPE, OpenLoanersPayload.STREAM_CODEC),
            toClient(AntePotPayload.TYPE, AntePotPayload.STREAM_CODEC),
            toClient(TableTermsPayload.TYPE, TableTermsPayload.STREAM_CODEC),
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
