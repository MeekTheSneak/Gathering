package dev.gathering.core.trade;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Whether a struck trade can be honoured")
class TradeSettlementTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BEN = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final CardIdentity BOLT = CardIdentity.ofPrinting(
            UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111"), false);
    private static final CardIdentity RING = CardIdentity.ofPrinting(
            UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222"), false);

    private static TradeTable struck() {
        return TradeTable.between(ANA, BEN)
                .putUp(ANA, BOLT, 2)
                .putUp(BEN, RING, 1)
                .agree(ANA)
                .agree(BEN);
    }

    @Test
    @DisplayName("each side's cards go to the other")
    void theCardsCross() {
        var settled = TradeSettlement.of(struck(),
                CardTally.builder().add(BOLT, 4).build(),
                CardTally.builder().add(RING, 1).build()).orElseThrow();

        assertThat(settled.toLeft().of(RING)).isEqualTo(1);
        assertThat(settled.toRight().of(BOLT)).isEqualTo(2);
        assertThat(settled.size()).isEqualTo(3);
        assertThat(settled.left()).isEqualTo(ANA);
    }

    @Test
    @DisplayName("a card that went missing stops the whole trade, not half of it")
    void allOfItOrNoneOfIt() {
        // Losing a card mid-deal would otherwise be a way to take one.
        var settled = TradeSettlement.of(struck(),
                CardTally.builder().add(BOLT, 1).build(),
                CardTally.builder().add(RING, 1).build());

        assertThat(settled).isEmpty();
        assertThat(TradeSettlement.shortOf(struck(),
                CardTally.builder().add(BOLT, 1).build(),
                CardTally.builder().add(RING, 1).build()))
                .containsExactly(new TradeSettlement.Missing(ANA, BOLT, 1));
    }

    @Test
    @DisplayName("what is short is named, because somebody has to be told")
    void whatIsShortIsNamed() {
        var missing = TradeSettlement.shortOf(struck(), CardTally.EMPTY, CardTally.EMPTY);

        assertThat(missing).containsExactly(
                new TradeSettlement.Missing(ANA, BOLT, 2),
                new TradeSettlement.Missing(BEN, RING, 1));
    }

    @Test
    @DisplayName("a trade nobody struck does not settle, however well covered")
    void onlyAStruckTableSettles() {
        TradeTable open = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1).agree(ANA);

        assertThat(TradeSettlement.of(open,
                CardTally.builder().add(BOLT, 4).build(), CardTally.EMPTY)).isEmpty();
        assertThat(TradeSettlement.of(null, CardTally.EMPTY, CardTally.EMPTY)).isEmpty();
        assertThat(TradeSettlement.shortOf(null, CardTally.EMPTY, CardTally.EMPTY)).isEmpty();
    }

    @Test
    @DisplayName("a trade where one side gives nothing is still a trade")
    void aGiftIsATrade() {
        TradeTable gift = TradeTable.between(ANA, BEN)
                .putUp(ANA, BOLT, 1).agree(ANA).agree(BEN);

        var settled = TradeSettlement.of(gift,
                CardTally.builder().add(BOLT, 1).build(), CardTally.EMPTY).orElseThrow();

        assertThat(settled.toRight().of(BOLT)).isEqualTo(1);
        assertThat(settled.toLeft().isEmpty()).isTrue();
    }
}
