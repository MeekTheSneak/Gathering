package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A sealed pack as an item: what it carries, and what it refuses to be. */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackItemGameTest {

    @GameTest(template = "empty")
    public static void aPackCarriesItsSetAndItsProduct(GameTestHelper helper) {
        ItemStack stack = PackItem.of(new PackComponent("BLB", "Play"));

        PackComponent read = PackItem.packOf(stack).orElse(null);
        if (read == null) {
            helper.fail("A pack made with a component came back without one");
            return;
        }
        // Both are names rather than strings: a set code and a product kind are written down
        // one way whatever case somebody typed them in.
        if (!"blb".equals(read.setCode()) || !"play".equals(read.kind())) {
            helper.fail("A pack of BLB Play came back as " + read.id());
            return;
        }
        if (!read.isReal()) {
            helper.fail("A pack of a real set says it is not one");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPackOfNothingIsNotAPackOfSomething(GameTestHelper helper) {
        // A data component is a thing an operator can write by hand, and this one is on its
        // way into a URL and a file name.
        for (String notASet : new String[] {"", "  ", "../../etc/passwd", "blb/../x",
                "https://elsewhere.example/x", "toolongaset"}) {
            PackComponent pack = new PackComponent(notASet, "play");
            if (pack.isReal()) {
                helper.fail("'" + notASet + "' was accepted as a set to open a pack of");
                return;
            }
            if (!pack.setCode().isEmpty()) {
                helper.fail("'" + notASet + "' left " + pack.setCode() + " behind");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anItemThatIsNotAPackCarriesNothing(GameTestHelper helper) {
        if (PackItem.packOf(new ItemStack(Items.STONE)).isPresent()) {
            helper.fail("A stone reported itself a booster");
            return;
        }
        if (PackItem.packOf(ItemStack.EMPTY).isPresent()) {
            helper.fail("Nothing at all reported itself a booster");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPackWithNoKindIsStillAPack(GameTestHelper helper) {
        // Which product was not said, so the opening picks: a set with one kind of booster
        // should not need somebody to name it.
        PackComponent pack = new PackComponent("dmu", "");

        if (!pack.isReal() || !pack.kind().isEmpty()) {
            helper.fail("A pack of dmu with no product named came out as " + pack.id());
            return;
        }
        if (!"dmu".equals(pack.id())) {
            helper.fail("A pack with no product named is called " + pack.id());
            return;
        }
        helper.succeed();
    }
}
