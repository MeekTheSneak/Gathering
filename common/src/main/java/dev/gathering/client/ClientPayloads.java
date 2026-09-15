package dev.gathering.client;

import dev.gathering.network.GatheringProtocol;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What this client does with each payload the server sends, written once.
 * <p>{@link GatheringProtocol} already says which payloads exist, which way they go and what the
 * server does with the ones it receives. The client half was still written twice: Fabric
 * registered a receiver per payload and NeoForge repeated the same behaviors as a chain
 * of type tests - which import screen gets a result, what a closed table does, how a sideboard
 * opens. They agreed, because somebody kept them agreeing, and the next screen-routing fix would
 * have been made in one of them.
 * <p>Now each loader hands every clientbound payload here, on the client thread, and keeps only
 * what is its own: how a receiver is registered and how work reaches the client thread.
 * <p><b>Client only.</b> This names screens, so nothing a dedicated server loads may name it -
 * the shared protocol does not, and each loader reaches it from its client entry point.
 */
public final class ClientPayloads {

    private ClientPayloads() {
    }

    /**
     * One payload type and what applying it does.
     * <p>The payload's class rides along so the one place a payload of unknown type becomes a
     * typed one is a checked cast, not an unchecked one.
     */
    public record Route<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type, Class<T> payloadClass, Consumer<T> apply) {

        /** Applies a payload already known to be of this route's type. */
        public void applyTo(CustomPacketPayload payload) {
            apply.accept(payloadClass.cast(payload));
        }
    }

    private static <T extends CustomPacketPayload> Route<T> route(
            CustomPacketPayload.Type<T> type, Class<T> payloadClass, Consumer<T> apply) {
        return new Route<>(type, payloadClass, apply);
    }

    /** Every payload a client applies, and how. Must match the protocol's clientbound list. */
    public static final List<Route<?>> ROUTES = List.of(
            route(dev.gathering.network.CardMetadataPayload.TYPE,
                    dev.gathering.network.CardMetadataPayload.class,
                    metadata -> ClientCardCache.get().accept(metadata.cards())),
            route(dev.gathering.network.CardsUnresolvedPayload.TYPE,
                    dev.gathering.network.CardsUnresolvedPayload.class,
                    ClientCardCache.get()::acceptUnresolved),
            route(dev.gathering.network.ImportResultPayload.TYPE,
                    dev.gathering.network.ImportResultPayload.class,
                    ClientPayloads::importFinished),
            route(dev.gathering.network.OpenImportScreenPayload.TYPE,
                    dev.gathering.network.OpenImportScreenPayload.class,
                    open -> Minecraft.getInstance().setScreen(new DecklistImportScreen())),
            route(dev.gathering.network.SetProgressPayload.TYPE,
                    dev.gathering.network.SetProgressPayload.class,
                    SetProgressScreen::accept),
            route(dev.gathering.network.SetMissingPayload.TYPE,
                    dev.gathering.network.SetMissingPayload.class,
                    MissingCardsScreen::accept),
            route(dev.gathering.network.WantsPayload.TYPE,
                    dev.gathering.network.WantsPayload.class,
                    ClientWants::accept),
            route(dev.gathering.network.TableSaidPayload.TYPE,
                    dev.gathering.network.TableSaidPayload.class,
                    ClientTableChat::accept),
            route(dev.gathering.network.TableViewPayload.TYPE,
                    dev.gathering.network.TableViewPayload.class,
                    ClientTableState::acceptPayload),
            route(dev.gathering.network.DraftViewPayload.TYPE,
                    dev.gathering.network.DraftViewPayload.class,
                    pod -> DraftScreen.show(pod.pod(), pod.view(), pod.open(), pod.secondsLeft())),
            route(dev.gathering.network.TradeViewPayload.TYPE,
                    dev.gathering.network.TradeViewPayload.class,
                    TradeScreen::accept),
            route(dev.gathering.network.PackOpenedPayload.TYPE,
                    dev.gathering.network.PackOpenedPayload.class,
                    opened -> Minecraft.getInstance().setScreen(new PackOpeningScreen(
                            opened.setCode(), opened.kind(), opened.cards()))),
            route(dev.gathering.network.MyDeckPayload.TYPE,
                    dev.gathering.network.MyDeckPayload.class,
                    ClientHeldDeck::accept),
            route(dev.gathering.network.CloseTablePayload.TYPE,
                    dev.gathering.network.CloseTablePayload.class,
                    closing -> ClientTableState.closed(closing.table())),
            route(dev.gathering.network.EventListPayload.TYPE,
                    dev.gathering.network.EventListPayload.class,
                    EventListScreen::accept),
            route(dev.gathering.network.EventViewPayload.TYPE,
                    dev.gathering.network.EventViewPayload.class,
                    EventScreen::accept),
            route(dev.gathering.network.EventPointerPayload.TYPE,
                    dev.gathering.network.EventPointerPayload.class,
                    EventHud::point),
            route(dev.gathering.network.PodLobbyPayload.TYPE,
                    dev.gathering.network.PodLobbyPayload.class,
                    PodLobbyScreen::accept),
            route(dev.gathering.network.OpenTableSetupPayload.TYPE,
                    dev.gathering.network.OpenTableSetupPayload.class,
                    setup -> Minecraft.getInstance().setScreen(new TableSetupScreen(setup.table()))),
            route(dev.gathering.network.OpenCollectionPayload.TYPE,
                    dev.gathering.network.OpenCollectionPayload.class,
                    CollectionScreen::show),
            route(dev.gathering.network.CollectionPagePayload.TYPE,
                    dev.gathering.network.CollectionPagePayload.class,
                    CollectionScreen::accept),
            route(dev.gathering.network.OpenLoanersPayload.TYPE,
                    dev.gathering.network.OpenLoanersPayload.class,
                    LoanerScreen::accept),
            route(dev.gathering.network.TableTermsPayload.TYPE,
                    dev.gathering.network.TableTermsPayload.class,
                    terms -> ClientTableState.acceptTerms(terms.table(), terms.terms())),
            route(dev.gathering.network.AntePotPayload.TYPE,
                    dev.gathering.network.AntePotPayload.class,
                    pot -> ClientTableState.acceptPot(pot.table(), pot.cards())),
            route(dev.gathering.network.AnteConsentPayload.TYPE,
                    dev.gathering.network.AnteConsentPayload.class,
                    AnteConsentScreen::accept),
            route(dev.gathering.network.ReplayListPayload.TYPE,
                    dev.gathering.network.ReplayListPayload.class,
                    ReplayListScreen::accept),
            route(dev.gathering.network.ReplayFramePayload.TYPE,
                    dev.gathering.network.ReplayFramePayload.class,
                    ClientReplay::accept),
            route(dev.gathering.network.TokenChoicesPayload.TYPE,
                    dev.gathering.network.TokenChoicesPayload.class,
                    TokenChoices::show),
            route(dev.gathering.network.OpenSideboardPayload.TYPE,
                    dev.gathering.network.OpenSideboardPayload.class,
                    sideboard -> SideboardScreen.open(sideboard.table(), sideboard.deck(),
                            sideboard.gameNumber(), sideboard.bestOf())));

    private static final Map<ResourceLocation, Route<?>> BY_ID = byId();

    private static Map<ResourceLocation, Route<?>> byId() {
        Map<ResourceLocation, Route<?>> found = new HashMap<>();
        for (Route<?> route : ROUTES) {
            if (found.put(route.type().id(), route) != null) {
                throw new IllegalStateException("Two client routes for " + route.type().id());
            }
        }
        return Map.copyOf(found);
    }

    /**
     * Applies a payload the server sent. Client thread only - each loader gets it there.
     * <p>A payload with no route is said rather than dropped: the loaders register exactly the
     * protocol's clientbound list and {@link #checkCovers} is asked at start-up, so reaching this
     * means the two lists came apart, which is a bug to see rather than a packet to ignore.
     */
    public static void apply(CustomPacketPayload payload) {
        Route<?> route = BY_ID.get(payload.type().id());
        if (route == null) {
            throw new IllegalStateException("No client route for " + payload.type().id());
        }
        route.applyTo(payload);
    }

    /**
     * Fails start-up if a clientbound payload has no route here, or a route has no payload.
     * <p>Asked by both loaders' client entry points, so the list that says what is sent and the
     * list that says what is done with it cannot drift apart without the client refusing to start.
     */
    public static void checkCovers(List<GatheringProtocol.ToClient<?>> sent) {
        Set<ResourceLocation> sentIds = new HashSet<>();
        for (GatheringProtocol.ToClient<?> each : sent) {
            sentIds.add(each.type().id());
        }
        if (!sentIds.equals(BY_ID.keySet())) {
            Set<ResourceLocation> unrouted = new HashSet<>(sentIds);
            unrouted.removeAll(BY_ID.keySet());
            Set<ResourceLocation> unsent = new HashSet<>(BY_ID.keySet());
            unsent.removeAll(sentIds);
            throw new IllegalStateException("Client payload routes do not match the protocol:"
                    + " sent but not routed " + unrouted + ", routed but not sent " + unsent);
        }
    }

    /**
     * A deck import or build finished: told to whichever screen asked.
     * <p>The builder and the import screen both send requests that come back as this, and only
     * the one open can use the answer. Neither open means the player has moved on, and nothing
     * is opened for an answer nobody is waiting on.
     */
    private static void importFinished(dev.gathering.network.ImportResultPayload result) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof DeckBuilderScreen builder) {
            builder.onResult(result);
        } else if (screen instanceof DecklistImportScreen importer) {
            importer.onResult(result);
        }
    }
}
