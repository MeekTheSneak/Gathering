package dev.gathering.client;

import dev.gathering.core.ui.Facing;
import dev.gathering.core.ui.KeptView;
import dev.gathering.core.ui.ViewHold;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

/**
 * Where the player was looking, given back to them.
 * <p>Two halves of one report - "where you were looking before you open a pack, open a menu,
 * use a block, etc, needs to be kept the same after you exit that menu" and "holding alt while
 * holding a card in your hand needs to lock your camera movement".
 * <p>The first half is a screen: the view is written down when one of the mod's screens takes
 * the window and put back when the window comes back. Watched here rather than added to each
 * screen's own {@code onClose} because a screen has more ways out than that one - escape, a
 * packet that closes it, a table that went away, the loader closing everything - and every one
 * of them ends with the same client holding no screen. One watcher over all of them cannot be
 * the one route somebody forgot.
 * <p>The second is a card: while the read key is down over a card, the view does not move at
 * all and what the mouse asked for turns the card instead. See {@link ViewHold} and
 * {@link CardTilt}.
 * <p>Nothing here survives the key going up, a screen opening, the player dying, or the server
 * changing - the first three because the hold is re-decided from scratch every frame and the
 * last because {@link ClientState} says so.
 * <p>Client-only, and read on the render thread as well as the client tick.
 */
public final class ViewKeeper {

    private static final KeptView kept = new KeptView();

    private static final ViewHold hold = new ViewHold(CardTilt.LOOK_FOR_FULL);

    /** What the window held last time this was asked: nothing, this mod's, or the game's own. */
    private enum Held {
        NOTHING, OURS, SOMETHING_ELSE
    }

    private static Held was = Held.NOTHING;

    private ViewKeeper() {
    }

    /**
     * One client tick: notices the window changing hands, and keeps the hold honest.
     * <p>The hold is also asked for on every frame, from the camera, which is where it can be
     * answered without the view moving between ticks. Asking here too costs nothing - after a
     * frame has put the view back, there is no movement left to count - and it means the lock
     * still works if that hook is ever not there to run.
     */
    public static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        Player player = client.player;
        if (player == null || !player.isAlive()) {
            // No view to keep and none to put back. A player who died while a screen was open
            // is put back into the world by the game, facing wherever it decided.
            kept.forget();
            hold.release();
            was = client.screen == null ? Held.NOTHING : Held.SOMETHING_ELSE;
            return;
        }
        Screen screen = client.screen;
        Held now = screen == null ? Held.NOTHING : isOurs(screen) ? Held.OURS : Held.SOMETHING_ELSE;
        if (now == Held.OURS && was == Held.NOTHING) {
            kept.opened(facing(player), player.getX(), player.getY(), player.getZ());
        } else if (now == Held.NOTHING && was == Held.OURS) {
            // From where they now are: somebody teleported while a screen was open is somewhere else,
            // and putting the old view back over the rotation the server chose would send it up as
            // though they had turned.
            kept.closed(player.getX(), player.getY(), player.getZ())
                    .ifPresent(view -> lookAgain(player, view));
        } else if (now == Held.SOMETHING_ELSE && was == Held.OURS) {
            // The game took the window - a pause screen, a disconnect notice. Putting a view
            // back over whatever that leaves behind is not this mod's to do.
            kept.forget();
        }
        was = now;
        heldStill();
    }

    /**
     * Where the view is to be this frame while a card is being read, or empty.
     * <p>Asked from each loader's camera hook, which runs after the mouse has been applied and
     * before the world is drawn with it - the one moment where putting the view back leaves
     * nothing on the screen having moved. It puts the player's own rotation back as well as
     * answering, because the camera is only what is drawn: the rotation is what is sent to the
     * server, what the game points at blocks with, and what the player is left facing.
     */
    public static Optional<Facing> heldStill() {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        boolean holding = player != null
                && player.isAlive()
                && client.screen == null
                // The table's own camera is somewhere else entirely and decides its own angles.
                && !TableCameraView.isLooking()
                && CardZoomOverlay.isActive()
                && CardZoomOverlay.cardInHand().isPresent();
        Optional<Facing> put = hold.frame(holding, player == null ? null : facing(player));
        put.ifPresent(view -> lookAgain(player, view));
        return put;
    }

    /** How far the mouse has asked to turn since this read started, for the card to turn by. */
    public static float turnedYaw() {
        return hold.turnedYaw();
    }

    /** And up and down. */
    public static float turnedPitch() {
        return hold.turnedPitch();
    }

    /**
     * Drops both, for a server changing underneath them.
     * <p>Named {@code clear} because that is the name {@code tools/statecheck.py} looks for. A
     * view kept from one server would be put back over the next one, and a lock that outlived
     * its table would be a player who cannot turn round on the main menu.
     */
    public static void clear() {
        kept.forget();
        hold.release();
        was = Held.NOTHING;
    }

    private static Facing facing(Player player) {
        return Facing.of(player.getYRot(), player.getXRot());
    }

    /**
     * Puts a view back, previous frame and all.
     * <p>Without the previous rotation the camera spends the next tick interpolating from
     * where the mouse got to toward where it was put back, which reads as the view shuddering
     * rather than as it never having moved.
     */
    private static void lookAgain(Player player, Facing view) {
        if (player == null) {
            return;
        }
        player.setYRot(view.yaw());
        player.setXRot(view.pitch());
        player.yRotO = view.yaw();
        player.xRotO = view.pitch();
        player.setYHeadRot(view.yaw());
        player.yHeadRotO = view.yaw();
    }

    /**
     * Whether this screen is one of the mod's.
     * <p>By package, because there is no one base class to ask: the mod's screens are a mix of
     * {@code ChildScreen}, plain screens and screens the game itself opened for us, and the
     * one thing every one of them has in common is where it was written.
     */
    private static boolean isOurs(Screen screen) {
        return screen.getClass().getName().startsWith("dev.gathering.");
    }
}
