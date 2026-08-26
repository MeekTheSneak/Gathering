package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.RevealUntil;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.network.RevealUntilPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turning a library over until something turns up: cascade, and reveal until type.
 *
 * <p>The half of these that has to be here rather than on the client is the counting. Nobody
 * may know their own library's order, so nobody can say how many cards a cascade turns over -
 * only the server can walk down and look. What it does with the answer is an ordinary reveal,
 * through the same event, the same fold and the same visibility decision every other reveal
 * goes through; there is no second way of showing cards.
 *
 * <p>No rules enforcement, which is section 16 and not a detail. Nothing here casts the card
 * it stopped on, moves anything, or knows what cascade means - the mod turns cards face up and
 * the players decide what that meant, exactly as they do across a table. What it saves is
 * counting cards down a face-down pile, which is the part a computer is better at and the part
 * nobody enjoys.
 *
 * <p>Server thread only.
 */
public final class LibraryReveals {

    /** How far a player may be from a table and still be playing at it. Same as every verb. */
    private static final double REACH = 12.0d;

    private LibraryReveals() {
    }

    public static void handle(ServerPlayer player, RevealUntilPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos clicked = payload.table();
        if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > REACH * REACH) {
            return;
        }
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return;
        }
        BlockPos origin = TableBlock.originOf(state, clicked);
        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        if (session == null || seat == null) {
            return;
        }

        int found = howFar(session, seat, payload);
        if (found <= 0) {
            // Nothing matched, and saying so is the whole answer. Turning nothing over and
            // leaving the player to wonder whether the button worked is the failure this
            // message exists to avoid - and revealing the library while looking would be
            // worse than either.
            player.sendSystemMessage(Component.translatable("message.gathering.reveal_until_none"));
            return;
        }

        GameSession.Result result = session.submit(new GameEvent.LibraryRevealed(seat, seat, found));
        if (result instanceof GameSession.Result.Rejected rejected) {
            player.sendSystemMessage(Component.literal(rejected.reason()));
            return;
        }
        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::setChanged);
        TableBroadcast.sendToTable(level, origin);
    }

    /**
     * How many cards down the answer is, or none.
     *
     * <p>The cache only, never a fetch: this runs on the server thread with a player waiting,
     * and a hundred and forty lookups against somebody else's host is not something to do
     * between two frames. A card nobody has looked up is passed over rather than stopped on -
     * see {@link RevealUntil}.
     */
    private static int howFar(GameSession session, SeatId seat, RevealUntilPayload payload) {
        CardDataService service = CardDataService.active().orElse(null);
        List<CardInstanceId> library = session.state().contents(seat, Zone.LIBRARY);
        List<CardMetadata> known = new ArrayList<>(Math.min(library.size(), RevealUntil.MOST_TO_TURN_OVER));
        for (CardInstanceId card : library) {
            if (known.size() >= RevealUntil.MOST_TO_TURN_OVER) {
                break;
            }
            CardIdentity identity = session.state().requireCard(card).identity();
            known.add(service == null ? null : CollectionView.known(service, identity));
        }
        return RevealUntil.howFarDown(known, questionFrom(payload));
    }

    /** Which question the player asked, as something a card can be held up against. */
    private static Predicate<CardMetadata> questionFrom(RevealUntilPayload payload) {
        return switch (payload.until()) {
            case CHEAPER_THAN -> RevealUntil.cheaperThan(payload.manaValue());
            case OF_TYPE -> RevealUntil.ofType(payload.wanted());
        };
    }
}
