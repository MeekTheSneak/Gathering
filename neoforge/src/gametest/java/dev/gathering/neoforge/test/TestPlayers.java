package dev.gathering.neoforge.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/**
 * Players put on the test server the way a login puts them there.
 * <p>{@code GameTestHelper#makeMockServerPlayerInLevel} makes a stranger every time - a fresh id, and the
 * same name for all of them. Two things it cannot do are what some checks are about: the same player
 * coming back, which is what anything kept for somebody until they join is waiting for, and two players
 * a story can tell apart by name.
 */
final class TestPlayers {

    private TestPlayers() {
    }

    /**
     * A player with this id and name, joining: the server's own {@code placeNewPlayer}, so the loader's
     * login hooks run exactly as they do for somebody connecting.
     */
    static ServerPlayer joining(GameTestHelper helper, UUID id, String name) {
        var server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(),
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /** Logs a player out: off the server, saved, and the loader's logout hooks run. */
    static void leaving(GameTestHelper helper, ServerPlayer player) {
        if (player != null) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
