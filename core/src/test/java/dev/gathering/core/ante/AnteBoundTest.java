package dev.gathering.core.ante;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.config.GatheringConfig;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A pot the settings allow is a pot the table can carry.
 * <p>The bound on the packet that carries a pot to the room was a round number somebody chose,
 * and the settings could describe a larger pot than it: eight seats at a full cluster, ten
 * cards each, is eighty cards through a list bounded at sixty-four. Nothing caught it because
 * the two numbers lived in different modules and neither knew about the other.
 */
class AnteBoundTest {

    @Test
    @DisplayName("the biggest pot the settings allow is the bound on a pot")
    void theBoundIsTheBiggestAllowedPot() {
        int seats = TableCluster.MAX_TABLES * TableCluster.SEATS_PER_TABLE;
        assertThat(AntePot.MOST_IN_A_POT)
                .isEqualTo(seats * GatheringConfig.Ante.MOST_PER_PLAYER)
                .isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("a full cluster staking the maximum builds a pot that still fits")
    void afullTableFits() {
        Map<SeatId, List<CardIdentity>> stakes = new LinkedHashMap<>();
        for (int seat = 0; seat < TableCluster.MAX_TABLES * TableCluster.SEATS_PER_TABLE; seat++) {
            List<CardIdentity> staked = new ArrayList<>();
            for (int card = 0; card < GatheringConfig.Ante.MOST_PER_PLAYER; card++) {
                staked.add(CardIdentity.ofPrinting(UUID.randomUUID()));
            }
            stakes.put(SeatId.of(seat), staked);
        }

        AntePot pot = new AntePot(stakes);

        assertThat(pot.size()).isEqualTo(80).isLessThanOrEqualTo(AntePot.MOST_IN_A_POT);
    }

    /** And the settings cannot describe a bigger stake than the pot was measured for. */
    @Test
    @DisplayName("the settings clamp a player's stake to the number the pot was sized for")
    void theSettingsCannotAskForMore() throws Exception {
        GatheringConfig config = GatheringConfig.read(dev.gathering.core.config.Toml.read(
                "modes.collection_enabled = true\nante.enabled = true\nante.cards_per_player = 99\n"));

        assertThat(config.ante().cardsPerPlayer()).isEqualTo(GatheringConfig.Ante.MOST_PER_PLAYER);
    }
}
