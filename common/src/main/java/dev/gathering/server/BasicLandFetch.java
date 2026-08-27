package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.game.LibraryBasics;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.network.FetchBasicPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Going and getting a basic land out of your own deck.
 *
 * <p>Out of the deck, and never out of nowhere. A Forest that was never in your library is a
 * Forest you did not build for, and a table where anybody can call one up whenever they like
 * is not playing the same game as the person who chose to run twelve. This used to make a
 * token off a Scryfall lookup, which was quick to write and wrong: it turned a search into a
 * conjuring trick, and a deck's land count into a suggestion.
 *
 * <p>So a deck with no Forests in it fetches no Forest, and is told so. That is the answer,
 * not a failure.
 *
 * <p>Read out of the card cache only, never a fetch: this runs on the server thread with a
 * player waiting. A card nobody has looked up is passed over rather than guessed at - see
 * {@link LibraryBasics} - which in practice means a card from an imported deck, since importing
 * is what put it in the cache.
 *
 * <p>Nothing is shuffled afterwards. Searching a library and shuffling it are two things a
 * player does, and this mod does not do the second one for them - section 16, no rules
 * enforcement. Nobody has learned anything about the order: the server picked the cards, and
 * the player never saw the library.
 *
 * <p>Server thread only.
 */
public final class BasicLandFetch {

    private BasicLandFetch() {
    }

    public static void handle(ServerPlayer player, FetchBasicPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = at.origin();
        GameSession session = at.session();
        SeatId seat = at.seat();
        String name = payload.land().printedName();

        List<CardInstanceId> library = session.state().contents(seat, Zone.LIBRARY);
        List<Integer> found = LibraryBasics.findIn(
                whatTheyAre(session, seat, library), name, payload.count());
        if (found.isEmpty()) {
            // The honest answer, and the one that makes this a search rather than a wish.
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.no_basic_in_deck", name));
            return;
        }

        int moved = 0;
        for (int position : found) {
            GameSession.Result result = session.submit(new GameEvent.CardMoved(
                    seat, library.get(position),
                    ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.TOP));
            if (result instanceof GameSession.Result.Rejected) {
                break;
            }
            moved++;
        }
        if (moved == 0) {
            return;
        }
        TableSessions.markDirty(level, origin);
        TableBroadcast.sendToTable(level, origin);
        // How many really came out, because asking for three when the deck holds two is a
        // normal thing to do and being told "done" would be a lie about your own library.
        player.sendSystemMessage(Component.translatable(
                "message.gathering.basics_fetched", moved, name));
    }

    /**
     * What each card in the library is, as far as this server knows.
     *
     * <p>Parallel to the library itself: the same length, in the same order, with a null
     * wherever the cache cannot say. {@link LibraryBasics} joins the two by position.
     */
    private static List<CardMetadata> whatTheyAre(
            GameSession session, SeatId seat, List<CardInstanceId> library) {
        CardDataService service = CardDataService.active().orElse(null);
        List<CardMetadata> known = new ArrayList<>(library.size());
        for (CardInstanceId card : library) {
            CardIdentity identity = session.state().requireCard(card).identity();
            known.add(service == null ? null : CollectionView.known(service, identity));
        }
        return known;
    }
}
