package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.SetRelease;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Archive Pack's contents: everything in Magic's history that no way into a collection reaches. */
class ArchiveAuditTest {

    private static final UUID IN_A_BOOSTER = new UUID(1, 1);
    private static final UUID IN_A_PRECON = new UUID(1, 2);
    private static final UUID NOWHERE = new UUID(1, 3);
    private static final UUID OLD_CARD = new UUID(2, 1);

    private static final ArchiveAudit.SetFacts DRAWN = new ArchiveAudit.SetFacts("new",
            List.of(IN_A_BOOSTER, IN_A_PRECON, NOWHERE), Set.of(IN_A_BOOSTER), Set.of(IN_A_PRECON), true);
    private static final ArchiveAudit.SetFacts OLD = new ArchiveAudit.SetFacts("old",
            List.of(OLD_CARD), Set.of(OLD_CARD), Set.of(), true);

    @Test
    @DisplayName("a set the server does not draw from is all archive, whatever its boosters held")
    void aSetNotInPlayIsAllArchive() {
        assertThat(ArchiveAudit.unobtainable(List.of(DRAWN, OLD), Set.of("new"), true))
                .containsExactlyInAnyOrder(NOWHERE, OLD_CARD);
    }

    @Test
    @DisplayName("a precon's cards are reachable only while a shop sells it")
    void productsCountOnlyWithTheShopOpen() {
        assertThat(ArchiveAudit.unobtainable(List.of(DRAWN), Set.of("new"), false))
                .containsExactlyInAnyOrder(IN_A_PRECON, NOWHERE);
    }

    @Test
    @DisplayName("a set drawn from whose reach is unknown yet is taken as reaching nothing")
    void unknownReachIsNoReach() {
        ArchiveAudit.SetFacts unread = new ArchiveAudit.SetFacts("new",
                List.of(IN_A_BOOSTER), Set.of(IN_A_BOOSTER), Set.of(), false);
        assertThat(ArchiveAudit.unobtainable(List.of(unread), Set.of("new"), true)).containsExactly(IN_A_BOOSTER);
    }

    @Test
    @DisplayName("promo and precon sets are audited; tokens, memorabilia and digital sets are not")
    void whichSetsAreAudited() {
        assertThat(ArchiveAudit.isAudited(set("plst", "promo", false))).isTrue();
        assertThat(ArchiveAudit.isAudited(set("c21", "commander", false))).isTrue();
        assertThat(ArchiveAudit.isAudited(set("tmh3", "token", false))).isFalse();
        assertThat(ArchiveAudit.isAudited(set("wc97", "memorabilia", false))).isFalse();
        assertThat(ArchiveAudit.isAudited(set("ymid", "alchemy", true))).isFalse();
        assertThat(ArchiveAudit.isAudited(set("prm", "promo", true))).isFalse();
    }

    private static SetRelease set(String code, String type, boolean digital) {
        return new SetRelease(code, code, type, "2020-01-01", digital, 10, 10);
    }
}
