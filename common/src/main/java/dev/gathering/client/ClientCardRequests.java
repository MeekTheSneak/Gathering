package dev.gathering.client;

import dev.gathering.core.card.MetadataRequests;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.RequestCardMetadataPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Asks the server what the cards in this player's inventory are.
 *
 * <p>Card metadata is thrown away on disconnect, so after a restart - or a crash, or a server
 * hop - a card in an inventory is a UUID and nothing else: no name, no art, nothing to look
 * at when the read key is held. Opening a deck asked about that deck's cards, and nothing
 * asked about anything else, so a loose card stayed blank until it happened to be in a deck
 * somebody opened.
 *
 * <p>Which printings are worth asking about is decided by {@link MetadataRequests}, which is
 * pure and tested, because this runs every tick and the failure mode of getting it wrong is
 * a request storm aimed at somebody else's server.
 *
 * <p>Only ever asks about cards the player can see themselves holding, so it grants no
 * access they did not have.
 *
 * <p>Client-only.
 */
public final class ClientCardRequests {

    /** Every half second. Cards do not appear in an inventory quickly enough to need more. */
    private static final int TICK_INTERVAL = 10;

    /** One request per sweep; a big inventory takes a few sweeps rather than one huge packet. */
    private static final int BATCH = 64;

    private static final MetadataRequests REQUESTS = new MetadataRequests();

    private static int countdown;

    private ClientCardRequests() {
    }

    public static void tick() {
        if (--countdown > 0) {
            return;
        }
        countdown = TICK_INTERVAL;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            return;
        }

        List<UUID> held = heldPrintings(player);
        List<UUID> wanted = REQUESTS.next(
                held, printing -> ClientCardCache.get().summary(printing).isPresent(),
                System.currentTimeMillis(), BATCH);
        if (!wanted.isEmpty()) {
            ClientNetworking.send(new RequestCardMetadataPayload(wanted));
        }
    }

    /** Called on disconnect, alongside the cache: the next server is a different one. */
    public static void clear() {
        REQUESTS.clear();
        countdown = 0;
    }

    /** How far off a card has to be before it is not worth knowing what it is. */
    private static final double IN_SIGHT = 32.0d;

    /**
     * Every printing this client can see.
     *
     * <p>Loose cards, plus each deck's commanders - a deck shows its commander's name on the
     * item itself, so that much is needed without opening it. The rest of a deck is asked for
     * when the deck is opened, because a shelf of deckboxes should not be a thousand-card
     * request.
     *
     * <p>And cards nobody is holding: framed on a wall, or lying on the floor where somebody
     * dropped them. Those used to be asked about by nothing at all, so a card in an item frame
     * drew as its own back - correct-looking to whoever framed it, because they had held it a
     * moment earlier and their cache still knew it, and a blank sleeve to everybody else and
     * to them after a rejoin. Framing a good pull is the first thing anybody does with a
     * collection, so it has to survive somebody else walking past it.
     *
     * <p>No access comes with this: an item frame's contents and a dropped stack are already
     * on this client - the entity was synced to it - so this asks about cards it can see and
     * nothing more, which is the same rule the inventory sweep follows.
     */
    private static List<UUID> heldPrintings(Player player) {
        List<UUID> printings = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            collect(inventory.getItem(slot), printings);
        }
        collect(player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried(), printings);

        // Cards on a table are not in anybody's inventory, and they are the ones being looked
        // at hardest. Only the ones this client was told the identity of - an anonymous card
        // has no printing to ask about, which is the point of it.
        ClientTableState.view().ifPresent(board -> board.allCardViews().stream()
                .filter(dev.gathering.core.game.visibility.CardView.Visible.class::isInstance)
                .map(dev.gathering.core.game.visibility.CardView.Visible.class::cast)
                .map(visible -> visible.identity().scryfallId())
                .filter(java.util.Objects::nonNull)
                .forEach(printings::add));

        // Cards in the world around the player: framed, or dropped. Bounded by sight rather
        // than by the whole level, and asked for once - a printing already in the cache is
        // dropped before any request is built.
        net.minecraft.world.phys.AABB nearby = player.getBoundingBox().inflate(IN_SIGHT);
        for (net.minecraft.world.entity.decoration.ItemFrame frame : player.level()
                .getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, nearby)) {
            collect(frame.getItem(), printings);
        }
        for (net.minecraft.world.entity.item.ItemEntity dropped : player.level()
                .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby)) {
            collect(dropped.getItem(), printings);
        }

        return printings;
    }

    private static void collect(ItemStack stack, List<UUID> into) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CardItem.cardOf(stack).flatMap(CardComponent::scryfallId).ifPresent(into::add);
        DeckItem.deckOf(stack)
                .map(DeckComponent::commanders)
                .ifPresent(commanders -> commanders.forEach(
                        card -> card.scryfallId().ifPresent(into::add)));
    }
}
