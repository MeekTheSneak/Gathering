package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A payload about one table in the world, which is therefore a payload that names where.
 * <p>It exists so that "where" can be asked of a payload without knowing which payload it is.
 * Every one of these already carried a table position; what was missing was a way for the one
 * place that puts them on the wire to read it.
 * <p>The reason that matters is the guided first game. It is played on a board this client
 * builds for itself, filed at {@link dev.gathering.client.TutorialDemo#table()} - a position no
 * table can occupy - and nothing done to it may leave the machine. Game moves are routed by
 * {@code ClientTableActions}, which every verb passes through; but a screen also sends whole
 * payloads of its own for the handful of things the server decides, from seventeen different
 * files. Guarding those one call at a time would be guarding them until somebody added the
 * eighteenth.
 * <p>So they are guarded by type instead, once, in {@code ClientNetworking.send}. A payload
 * that names a table is a payload whose table can be checked, and {@code tools/tablecheck.py}
 * fails the build if a payload grows a table position without saying so here.
 */
public interface AtATable extends CustomPacketPayload {

    /** The table this is about. */
    BlockPos table();
}
