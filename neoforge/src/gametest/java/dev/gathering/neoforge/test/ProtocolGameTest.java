package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.network.GatheringProtocol;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The one list both loaders register payloads from says each payload once, one way.
 * <p>Registering a type twice fails the whole registration at startup on NeoForge and not
 * necessarily on Fabric, and a type registered in both directions is a client able to send
 * something only the server should. Both would have been a difference between two
 * hand-written lists before; with one list they would be a difference between the list and
 * itself, which this checks.
 * <p>The registrations themselves are exercised by the game-test servers booting on both
 * loaders, which they cannot do with a route the loader refuses.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtocolGameTest {

    @GameTest(template = "empty")
    public static void everypayloadisregisteredonceonewayandasthismod(GameTestHelper helper) {
        Set<ResourceLocation> toServer = new HashSet<>();
        for (GatheringProtocol.ToServer<?> route : GatheringProtocol.TO_SERVER) {
            if (!toServer.add(route.type().id())) {
                helper.fail("sent to the server twice: " + route.type().id());
                return;
            }
            if (route.handler() == null || route.codec() == null) {
                helper.fail("a route with no handler or codec: " + route.type().id());
                return;
            }
        }
        Set<ResourceLocation> toClient = new HashSet<>();
        for (GatheringProtocol.ToClient<?> route : GatheringProtocol.TO_CLIENT) {
            if (!toClient.add(route.type().id())) {
                helper.fail("sent to clients twice: " + route.type().id());
                return;
            }
            if (toServer.contains(route.type().id())) {
                helper.fail("registered in both directions: " + route.type().id());
                return;
            }
        }
        Set<ResourceLocation> all = new HashSet<>(toServer);
        all.addAll(toClient);
        for (ResourceLocation id : all) {
            if (!Gathering.MOD_ID.equals(id.getNamespace())) {
                helper.fail("a payload outside this mod's namespace: " + id);
                return;
            }
        }
        helper.succeed();
    }
}
