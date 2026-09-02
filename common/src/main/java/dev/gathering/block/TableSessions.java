package dev.gathering.block;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import dev.gathering.item.DeckItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Starting, finding and ending the game on a cluster of tables.
 * <p>A cluster runs one game, so one of its tables has to hold it. That is the cluster's
 * first cell in its own stable order - the same order that numbers the seats - so every table
 * in a cluster agrees on where the game is without anything having to be written down about
 * it.
 * <p>Seats are numbered by that order too: seat <i>n</i> of the session is the <i>n</i>th of
 * the cluster's seat positions. The shape is frozen while anybody is seated, so those
 * numbers cannot move under a live game.
 */
public final class TableSessions {

    /** What a table offers before anybody has chosen: a single game of Commander. */
    public static MatchRules defaultRules() {
        return MatchRules.single(dev.gathering.core.format.FormatPresets.COMMANDER);
    }

    private TableSessions() {
    }

    /** The table holding this cluster's game, whether or not there is one. */
    public static Optional<BlockPos> anchorOf(BlockGetter level, BlockPos tableOrigin) {
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        return cluster.isEmpty()
                ? Optional.empty()
                : Optional.of(TableClusters.blockPos(tableOrigin, cluster.cells().get(0)));
    }

    public static Optional<GameSession> sessionAt(BlockGetter level, BlockPos tableOrigin) {
        return anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .flatMap(TableBlockEntity::session);
    }

    /** The set of games running here, if there is one. */
    public static Optional<MatchState> matchAt(BlockGetter level, BlockPos tableOrigin) {
        return anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .flatMap(TableBlockEntity::match);
    }

    /**
     * Marks the table's saved state dirty, so the log just appended to reaches the disk.
     * <p>One name for a three-line idiom seven handlers each spelled out - and the eighth
     * would have been the one that forgot, which is a game that plays fine until the chunk
     * unloads and comes back without its last few moves.
     */
    public static void markDirty(BlockGetter level, BlockPos tableOrigin) {
        anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::setChanged);
    }

    public static boolean hasSession(BlockGetter level, BlockPos tableOrigin) {
        return anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .map(TableBlockEntity::hasSession)
                .orElse(false);
    }

    /**
     * Starts a game on this cluster.
     * <p>Every seat the cluster has becomes a seat in the session, whether or not anybody is
     * in it: the shape is frozen for the duration, so the seats cannot move, and somebody
     * arriving later should find a seat waiting rather than a game that has no room.
     */
    public static Outcome start(Level level, BlockPos tableOrigin, MatchRules rules) {
        return start(level, tableOrigin, rules, null);
    }

    /**
     * Starts a game, either the first of a set or the next one.
     * <p>{@code continuing} is the score so far, or null to begin a new set at nil-nil. The
     * next game of a set also gets every deck the table is holding put straight back down and
     * shuffled: in paper you keep your deck between games of a match, and making people hand
     * it over again each time would be ceremony with a chance of getting it wrong.
     */
    public static Outcome start(
            Level level, BlockPos tableOrigin, MatchRules rules, MatchState continuing) {
        BlockPos anchor = anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null) {
            return Outcome.NO_TABLE;
        }
        if (table.hasSession()) {
            return Outcome.ALREADY_RUNNING;
        }

        TableCluster cluster = TableClusters.at(level, tableOrigin);
        List<SeatAnchor> anchors = cluster.seats();
        if (TableSeats.occupiedSeats(level, tableOrigin) == 0) {
            return Outcome.NOBODY_SEATED;
        }

        List<SeatId> seats = new ArrayList<>(anchors.size());
        for (int index = 0; index < anchors.size(); index++) {
            seats.add(new SeatId(index));
        }
        GameSession session = GameSession.create(
                seats, rules.format().startingLife(), SessionSeed.random(), UndoMode.shippedDefault());

        // Everybody already registered joins the game they were waiting for.
        for (int index = 0; index < anchors.size(); index++) {
            SeatAnchor seat = anchors.get(index);
            Optional<java.util.UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, seat.cell()))
                    .flatMap(other -> other.occupantOf(seat.side()));
            if (occupant.isEmpty()) {
                continue;
            }
            Player player = level.getPlayerByUUID(occupant.get());
            String name = player == null ? "Player" : player.getGameProfile().getName();
            session.submit(new GameEvent.SeatTaken(new SeatId(index), new PlayerRef(occupant.get(), name)));
        }

        table.beginSession(session, rules.format().startingLife(),
                continuing == null ? MatchState.beginning(rules) : continuing);

        // Decks the table is already holding go back down by themselves. Only ever true for
        // the second game of a set onwards, because nothing is held before the first.
        table.heldDecks().forEach((seat, deck) -> {
            if (!session.state().hasSeat(seat)) {
                return;
            }
            session.submit(new GameEvent.DeckLoaded(seat,
                    deck.entries().stream().map(dev.gathering.item.CardComponent::toIdentity).toList(),
                    deck.commanders().stream().map(dev.gathering.item.CardComponent::toIdentity).toList(),
                    deck.sleeve()));
            session.submit(new GameEvent.LibraryShuffled(seat, seat));
        });
        return Outcome.STARTED;
    }

    public static Outcome end(Level level, BlockPos tableOrigin, SeatId actor, String reason) {
        BlockPos anchor = anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null || !table.hasSession()) {
            return Outcome.NOT_RUNNING;
        }
        table.session().ifPresent(session -> {
            session.submit(new GameEvent.SessionEnded(actor, reason));
            // Written down before the table forgets it. A session is an event log and a seed,
            // so a finished game reproduces exactly - and every one of them used to be thrown
            // away at exactly this line.
            rememberTheGame(level, tableOrigin, session);
        });
        creditEverybodyWhoPlayed(level, tableOrigin);
        returnDecks(level, tableOrigin, table);
        // Nobody won this one, so every card goes back to whoever staked it.
        settlePot(level, tableOrigin, table, null);
        table.endSession();
        // Told before it is forgotten, or everyone at the table keeps looking at the last
        // board they were sent - which is worse than an empty screen, because it looks live.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            dev.gathering.server.TableBroadcast.closeAtTable(server, tableOrigin);
        }
        return Outcome.ENDED;
    }

    /**
     * Marks a game finished for everybody still sitting at it.
     * <p>Everybody rather than a winner, because this mod has no idea who won one and is
     * never going to: it does not enforce a rule and does not read a board. Playing a game
     * through to the end is the thing worth remembering, and the table can honestly say that
     * happened.
     */
    private static void creditEverybodyWhoPlayed(Level level, BlockPos tableOrigin) {
        for (SeatAnchor seat : TableClusters.at(level, tableOrigin).seats()) {
            occupantOf(level, tableOrigin, seat)
                    .filter(net.minecraft.server.level.ServerPlayer.class::isInstance)
                    .map(net.minecraft.server.level.ServerPlayer.class::cast)
                    .ifPresent(player -> dev.gathering.server.Achievements.award(
                            player, dev.gathering.server.Achievements.FIRST_GAME));
        }
    }

    /**
     * Keeps the finished game, and tells the people who played it that it was kept.
     * <p>Named here rather than folded into the line above because it is the one thing in
     * {@code end} that is not about tidying the table away. Failure is silent past a log line:
     * a replay that could not be written is a replay nobody watches, and it must never be the
     * reason a game cannot end.
     */
    private static void rememberTheGame(Level level, BlockPos tableOrigin, GameSession session) {
        List<SeatAnchor> anchors = TableClusters.at(level, tableOrigin).seats();
        List<dev.gathering.server.Replays.Played> played = new ArrayList<>();
        for (int index = 0; index < anchors.size(); index++) {
            occupantOf(level, tableOrigin, anchors.get(index))
                    .map(player -> new dev.gathering.server.Replays.Played(
                            player.getGameProfile().getName(), player.getUUID()))
                    .ifPresent(played::add);
        }
        if (!dev.gathering.server.Replays.keep(session, session.startingLife(), played)) {
            return;
        }
        for (SeatAnchor seat : anchors) {
            occupantOf(level, tableOrigin, seat).ifPresent(player ->
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "message.gathering.game_recorded")));
        }
    }

    /**
     * Gives every deck back to whoever put it down.
     * <p>To the player if they are still here, and onto the table if they are not - never
     * nowhere. A deck is somebody's collection and hours of building; losing one because its
     * owner logged out before the game ended is not a trade-off, it is a bug with an
     * explanation attached.
     */
    public static void returnDecks(Level level, BlockPos tableOrigin, TableBlockEntity table) {
        table.releaseDecks().forEach((seat, held) -> giveBack(level, tableOrigin, seat, held));
    }

    /**
     * Hands one seat's deck back, if the table is holding one.
     * <p>Called when a player leaves the table, which is the moment they mean "give me my
     * cards" and which used to hand them nothing at all: a deck came back only when the whole
     * match ended, and ending a match is something the rest of the table is in the middle of.
     * Silent when there is no deck to give, because leaving a table you never put a deck on
     * is the ordinary case.
     */
    public static void returnDeckTo(Level level, BlockPos tableOrigin, SeatId seat) {
        anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .flatMap(table -> table.releaseDeck(seat))
                .ifPresent(held -> giveBack(level, tableOrigin, seat, held));
    }

    /**
     * Puts a released deck into somebody's hands, or onto the table if nobody's are here.
     * <p>To whoever put it down, wherever they are on the server - not to whoever is sitting
     * in the chair now. Handing it to the chair meant a player who stood up mid-match had
     * their deck dropped on the floor, and a player who took the vacated chair had somebody
     * else's collection put into their inventory, which is cards changing hands because of
     * where a person happened to be standing.
     * <p>The seat is still the fallback, for a world saved before decks remembered whose they
     * were, and the floor beside the table is the fallback after that: a deck is somebody's
     * collection and hours of building, and losing one because its owner logged out is not a
     * trade-off, it is a bug with an explanation attached.
     */
    private static void giveBack(
            Level level, BlockPos tableOrigin, SeatId seat, TableBlockEntity.HeldDeck held) {
        ItemStack stack = DeckItem.of(held.deck());
        // The pool goes back with the deck. A drafted deck that came back without one would
        // still look like a drafted deck and no longer be held to what was opened.
        if (held.pool() != null && !held.pool().isEmpty()) {
            stack.set(dev.gathering.registry.GatheringComponents.POOL.get(), held.pool());
        }

        Player owner = held.owner() == null ? null : level.getPlayerByUUID(held.owner());
        if (owner == null) {
            List<SeatAnchor> anchors = TableClusters.at(level, tableOrigin).seats();
            owner = seat.index() < anchors.size()
                    ? occupantOf(level, tableOrigin, anchors.get(seat.index())).orElse(null)
                    : null;
        }
        if (owner == null || !owner.getInventory().add(stack)) {
            Containers.dropItemStack(level,
                    tableOrigin.getX() + 0.5, tableOrigin.getY() + 1.0, tableOrigin.getZ() + 0.5, stack);
        }
    }

    /**
     * Settles the pot: to the winner, or back to everybody who put a card in.
     * <p>Called wherever a game stops being a game, which is two places - a match that ended
     * with somebody ahead, and a session that was voided or ended by hand. Those are the two
     * resolutions the pot was built to tell apart, and passing null for the winner is how the
     * second one says so.
     * <p>The pot is emptied by the release, before any card is handed anywhere, so a settle
     * that runs twice pays out once. Cards go to the player if they are still here and onto
     * the table if they are not - never nowhere, exactly as a deck does.
     */
    public static void settlePot(
            Level level, BlockPos tableOrigin, TableBlockEntity table, SeatId winner) {
        dev.gathering.core.ante.AntePot pot = table.releasePot();
        if (pot.isEmpty()) {
            return;
        }
        List<SeatAnchor> anchors = TableClusters.at(level, tableOrigin).seats();
        dev.gathering.core.ante.AntePot.Payout payout =
                winner == null ? pot.backToOwners() : pot.toWinner(winner);

        payout.to().forEach((seat, cards) -> {
            // A pot going back to the people who filled it goes back to the people, not to
            // the chairs they were sitting in: a staker who stood up and was replaced had
            // their card handed to whoever sat down after them, which is a card changing
            // owner because of where somebody happened to be standing. Wherever they are on
            // the server, then the seat, then the table top - the same ladder a held deck
            // comes back down, and for the same reason.
            //
            // A pot going to a winner is different and stays as it was: the winner is whoever
            // just won, which is whoever is in that chair now.
            Player owner = winner == null
                    ? table.stakerOf(seat).map(level::getPlayerByUUID).orElse(null)
                    : null;
            if (owner == null) {
                owner = seat.index() < anchors.size()
                        ? occupantOf(level, tableOrigin, anchors.get(seat.index())).orElse(null)
                        : null;
            }
            for (dev.gathering.core.card.CardIdentity card : cards) {
                ItemStack stack = dev.gathering.item.CardItem.of(
                        dev.gathering.item.CardComponent.of(card));
                // A card that came back to whoever staked it is a card nothing happened to.
                // A card that did not is the one the whole feature is for: this is the moment
                // it stops being a copy of a printing and becomes a thing with a history.
                Player receiving = owner;
                if (winner != null && receiving instanceof net.minecraft.server.level.ServerPlayer won) {
                    dev.gathering.server.CardStories.remember(stack,
                            dev.gathering.server.CardStories.wonBy(won,
                                    whoStaked(level, tableOrigin, table, anchors, pot, card, seat)));
                }
                if (receiving == null || !receiving.getInventory().add(stack)) {
                    Containers.dropItemStack(level, tableOrigin.getX() + 0.5,
                            tableOrigin.getY() + 1.0, tableOrigin.getZ() + 0.5, stack);
                }
            }
        });
        // Last, and only once the pot has actually been paid out: the names outlive the
        // release by exactly one call, because the release is what stops a settle running
        // twice, and the names are what the payout needs after it.
        table.forgetStakers();
    }

    /**
     * Who put this card in the pot, if it was not the person taking it out.
     * <p>A name rather than a seat, because a story is read by a person years later and "seat
     * two" means nothing to them. Blank where the staker has logged out: their name is not
     * worth holding a profile lookup for, and "won in an ante game" is still the fact.
     * <p>Where two people staked the same printing this names the first of them, which is a
     * guess. It is the right kind of guess: the alternative is naming nobody, and the card
     * genuinely did come out of one of their hands.
     */
    private static String whoStaked(
            Level level, BlockPos tableOrigin, TableBlockEntity table, List<SeatAnchor> anchors,
            dev.gathering.core.ante.AntePot pot, dev.gathering.core.card.CardIdentity card,
            SeatId taking) {
        for (var stake : pot.stakes().entrySet()) {
            if (stake.getKey().equals(taking) || !stake.getValue().contains(card)) {
                continue;
            }
            // The person the table recorded, before the chair they were in: a staker who
            // stood up mid-match is exactly the case where the chair names somebody else,
            // and putting the wrong name on a card's history is worse than putting none.
            Player staker = table.stakerOf(stake.getKey())
                    .map(level::getPlayerByUUID)
                    .orElse(null);
            if (staker == null) {
                int index = stake.getKey().index();
                if (index >= anchors.size()) {
                    continue;
                }
                staker = occupantOf(level, tableOrigin, anchors.get(index)).orElse(null);
            }
            return staker == null ? "" : staker.getGameProfile().getName();
        }
        return "";
    }

    /**
     * Puts the running game's seats back in step with who is actually sitting at the table.
     * <p>A seat is claimed on the table block and a seat is taken in the session, and until
     * now only the first happened after a game had started: everybody present when the game
     * began was seated into it, and anybody who walked up afterwards claimed a chair the
     * session never heard about. Their column said "(away)" for the rest of the evening with
     * them sitting in it, which is the report this exists to answer - and they were a
     * spectator to their own board, because a seat nobody holds is a seat nobody can act as.
     * <p>Reconciling rather than reacting to the one click: the same walk fixes a player who
     * reconnected under a new display name, a chair that changed hands while the server was
     * down, and a session loaded from disk beside a table whose claims outlived it. Every
     * call is a no-op unless something actually differs, so it is safe on any seat change.
     */
    public static void seatingChanged(Level level, BlockPos tableOrigin) {
        GameSession session = sessionAt(level, tableOrigin).orElse(null);
        if (session == null || session.state().ended()) {
            return;
        }
        List<SeatAnchor> anchors = TableClusters.at(level, tableOrigin).seats();
        for (int index = 0; index < anchors.size(); index++) {
            SeatId seat = new SeatId(index);
            if (!session.state().hasSeat(seat)) {
                continue;
            }
            PlayerRef sittingThere = occupantOf(level, tableOrigin, anchors.get(index))
                    .map(player -> new PlayerRef(player.getUUID(), player.getGameProfile().getName()))
                    .orElse(null);
            PlayerRef inTheSession = session.state().seatState(seat).occupant();
            if (java.util.Objects.equals(sittingThere, inTheSession)) {
                continue;
            }
            // Released first even when somebody is arriving, so the log reads as two things
            // happening rather than one player silently becoming another.
            if (inTheSession != null) {
                session.submit(new GameEvent.SeatReleased(seat));
            }
            if (sittingThere != null) {
                session.submit(new GameEvent.SeatTaken(seat, sittingThere));
            }
        }
    }

    private static Optional<Player> occupantOf(Level level, BlockPos tableOrigin, SeatAnchor seat) {
        return TableBlock
                .entityAt(level, TableClusters.blockPos(tableOrigin, seat.cell()))
                .flatMap(table -> table.occupantOf(seat.side()))
                .map(level::getPlayerByUUID);
    }

    /** Which session seat a player holds at this cluster, if any. */
    public static Optional<SeatId> seatIdOf(BlockGetter level, BlockPos tableOrigin, java.util.UUID player) {
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        return TableSeats.seatOf(level, tableOrigin, player)
                .map(cluster.seats()::indexOf)
                .filter(index -> index >= 0)
                .map(SeatId::new);
    }

    /** What came of asking. Each refusal is its own answer so it can be explained. */
    public enum Outcome {
        STARTED,
        ENDED,
        ALREADY_RUNNING,
        NOT_RUNNING,
        NOBODY_SEATED,
        NO_TABLE;

        public String messageKey() {
            return "message.gathering.session_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
