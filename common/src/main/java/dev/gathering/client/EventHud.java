package dev.gathering.client;

import dev.gathering.network.EventPointerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The way to your seat in a venue: which table, how far, and which way, across the top of the
 * screen until you are standing at it.
 * <p>Client-only.
 */
public final class EventHud {

    private static final int TEXT = 0xFFE0B15A;

    /** Close enough to count as there. */
    private static final double ARRIVED = 2.5;

    private static BlockPos seat;
    private static int table;

    private EventHud() {
    }

    public static void point(EventPointerPayload payload) {
        if (payload.table() <= 0) {
            clear();
            return;
        }
        seat = payload.seat();
        table = payload.table();
    }

    public static void clear() {
        seat = null;
        table = 0;
    }

    /** Whether it is pointing anywhere. For the scripted harness. */
    static boolean isPointing() {
        return seat != null;
    }

    public static void render(GuiGraphics graphics, int width, int height) {
        Minecraft client = Minecraft.getInstance();
        if (seat == null || client.player == null) {
            return;
        }
        // Where the seat is now, which moves with the structure it is on. See WorldSpace.
        net.minecraft.world.phys.Vec3 there = dev.gathering.platform.WorldSpace.get().centerInWorld(client.level, seat);
        double dx = there.x - client.player.getX();
        double dz = there.z - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < ARRIVED) {
            clear();
            return;
        }
        // Which way, against where the player is facing: an arrow from eight.
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double turn = Math.floorMod((long) Math.round(bearing - client.player.getYRot()), 360L);
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[(int) Math.floorMod(Math.round(turn / 45.0), 8L)];
        Component line = Component.translatable("hud.gathering.event.seat", table, (int) Math.round(distance), arrow);
        graphics.drawCenteredString(client.font, line, width / 2, 8, TEXT);
    }
}
