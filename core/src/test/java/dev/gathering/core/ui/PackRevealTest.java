package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.Rarity;
import dev.gathering.core.ui.PackReveal.Tier;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Going through a pack one card at a time.
 * <p>Everything about the ceremony that can be wrong without a window: the order, what the card in front
 * is lit by, and where the turn ends.
 */
class PackRevealTest {

    @Test
    @DisplayName("a pack is gone through worst first, so it ends on the card it was opened for")
    void theBestCardIsLast() {
        PackReveal pack = PackReveal.of(List.of(Tier.RARE, Tier.PLAIN, Tier.MYTHIC, Tier.PLAIN));

        assertThat(pack.order())
                .containsExactly(Tier.PLAIN, Tier.PLAIN, Tier.RARE, Tier.MYTHIC);
        assertThat(pack.inFront()).isEqualTo(Tier.PLAIN);
    }

    @Test
    @DisplayName("the card in front is lit by the one behind it, and the last card by nothing")
    void theTellIsTheNextCard() {
        PackReveal pack = PackReveal.of(List.of(Tier.PLAIN, Tier.PLAIN, Tier.MYTHIC));

        // First card: nothing behind it worth announcing.
        assertThat(pack.tells()).isFalse();
        assertThat(pack.nextUp()).isEqualTo(Tier.PLAIN);

        // Second: the mythic is next, and it says so.
        PackReveal second = pack.turned();
        assertThat(second.tells()).isTrue();
        assertThat(second.nextUp()).isEqualTo(Tier.MYTHIC);

        // Third: the mythic is the card you are looking at, so there is nothing left to promise.
        PackReveal third = second.turned();
        assertThat(third.inFront()).isEqualTo(Tier.MYTHIC);
        assertThat(third.tells()).isFalse();
    }

    @Test
    @DisplayName("turning the last card finishes the pack, and turning a finished one changes nothing")
    void theEndIsTheEnd() {
        PackReveal pack = PackReveal.of(List.of(Tier.PLAIN, Tier.RARE));

        assertThat(pack.finished()).isFalse();
        PackReveal done = pack.turned().turned();
        assertThat(done.finished()).isTrue();
        assertThat(done.left()).isZero();
        assertThat(done.turned()).isEqualTo(done);
    }

    /**
     * The one that bit: a showcase used to be a tier of its own and shadowed the rarity, so a showcase
     * mythic announced itself as a showcase - the smaller noise for the bigger card.
     */
    @Test
    @DisplayName("rarity decides, so a showcase mythic is a mythic")
    void rarityDecidesAndNothingElse() {
        assertThat(Tier.of(Rarity.MYTHIC)).isEqualTo(Tier.MYTHIC);
        assertThat(Tier.of(Rarity.RARE)).isEqualTo(Tier.RARE);
        assertThat(Tier.of(Rarity.COMMON)).isEqualTo(Tier.PLAIN);
        // The slots that stand in for a rare are rares: a Special Guest is what the pack was opened for.
        assertThat(Tier.of(Rarity.BONUS)).isEqualTo(Tier.RARE);
        assertThat(Tier.of(null)).isEqualTo(Tier.PLAIN);
        // And a mythic is the top of the order however a pack is cut, so it is always turned last.
        assertThat(Tier.MYTHIC.ordinal()).isGreaterThan(Tier.RARE.ordinal());
    }

    @Property
    @Label("however a pack is cut, turning it through always ends on its best card and no sooner")
    void everyPackEndsOnItsBest(@ForAll @Size(min = 1, max = 15) List<Tier> cards) {
        PackReveal pack = PackReveal.of(cards);
        Tier best = cards.stream().max(java.util.Comparator.comparingInt(Enum::ordinal)).orElseThrow();

        int turns = 0;
        while (!pack.finished()) {
            // The light never promises something the pack does not still have to give.
            if (pack.tells()) {
                assertThat(pack.nextUp().worthAnnouncing()).isTrue();
            }
            Tier front = pack.inFront();
            assertThat(front.ordinal()).isLessThanOrEqualTo(best.ordinal());
            pack = pack.turned();
            turns++;
        }
        assertThat(turns).isEqualTo(cards.size());
    }

    @Property
    @Label("the order never leaves a better card in front of a worse one")
    void theOrderOnlyClimbs(@ForAll @Size(min = 2, max = 20) List<Tier> cards) {
        List<Tier> order = PackReveal.inOrder(cards);
        for (int at = 1; at < order.size(); at++) {
            assertThat(order.get(at - 1).ordinal()).isLessThanOrEqualTo(order.get(at).ordinal());
        }
        assertThat(order).hasSameSizeAs(cards);
    }

    @Property
    @Label("however far through a pack you are, what is left plus what is shown is the pack")
    void nothingIsLostOnTheWay(
            @ForAll @Size(min = 1, max = 12) List<Tier> cards,
            @ForAll @IntRange(min = 0, max = 12) int turns) {
        PackReveal pack = PackReveal.of(cards);
        for (int turn = 0; turn < turns; turn++) {
            pack = pack.turned();
        }
        assertThat(pack.shown() + pack.left()).isEqualTo(cards.size());
    }
}
