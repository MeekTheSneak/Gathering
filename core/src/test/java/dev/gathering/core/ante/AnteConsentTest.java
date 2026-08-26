package dev.gathering.core.ante;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.ante.AnteConsent.Answer;
import dev.gathering.core.game.SeatId;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class AnteConsentTest {

    private static final SeatId ANA = new SeatId(0);
    private static final SeatId BEN = new SeatId(1);
    private static final Set<SeatId> BOTH = Set.of(ANA, BEN);

    @Test
    void nobodyHasAnsweredWhenTheQuestionIsFirstAsked() {
        AnteConsent asking = AnteConsent.asking(BOTH);
        assertThat(asking.answerFrom(ANA)).isEqualTo(Answer.WAITING);
        assertThat(asking.settled()).isFalse();
        assertThat(asking.refused()).isFalse();
        assertThat(asking.waitingOn()).containsExactlyInAnyOrder(ANA, BEN);
    }

    @Test
    void silenceIsNotAgreement() {
        // The failure this whole rule exists to prevent: somebody's card going into a pot
        // because they were making tea.
        AnteConsent one = AnteConsent.asking(BOTH).from(ANA, Answer.IN);
        assertThat(one.settled()).isFalse();
        assertThat(one.waitingOn()).containsExactly(BEN);
    }

    @Test
    void everybodyInIsAGameForKeeps() {
        AnteConsent all = AnteConsent.asking(BOTH).from(ANA, Answer.IN).from(BEN, Answer.IN);
        assertThat(all.settled()).isTrue();
        assertThat(all.waitingOn()).isEmpty();
    }

    @Test
    void oneRefusalIsEnoughToStopIt() {
        AnteConsent no = AnteConsent.asking(BOTH).from(ANA, Answer.IN).from(BEN, Answer.OUT);
        assertThat(no.refused()).isTrue();
        assertThat(no.settled()).isFalse();
    }

    @Test
    void changingYourMindIsAllowedRightUpUntilItIsSettled() {
        AnteConsent said = AnteConsent.asking(BOTH)
                .from(ANA, Answer.IN)
                .from(ANA, Answer.OUT);
        assertThat(said.answerFrom(ANA)).isEqualTo(Answer.OUT);
        assertThat(said.refused()).isTrue();

        AnteConsent again = said.from(ANA, Answer.IN).from(BEN, Answer.IN);
        assertThat(again.settled()).isTrue();
    }

    @Test
    void anEmptyTableAgreesToNothing() {
        // Unanimity over nobody is how a rule like this gets accidentally satisfied.
        assertThat(AnteConsent.asking(Set.of()).settled()).isFalse();
    }

    @Test
    void anAnswerFromSomebodyWhoIsNotAtTheTableIsNotAVote() {
        AnteConsent asking = AnteConsent.asking(Set.of(ANA));
        AnteConsent tried = asking.from(BEN, Answer.IN);
        assertThat(tried.answerFrom(BEN)).isEqualTo(Answer.WAITING);
        assertThat(tried.settled()).isFalse();
    }

    @Test
    void aYesLeftBehindBySomebodyWhoStoodUpDoesNotCount() {
        AnteConsent both = AnteConsent.asking(BOTH).from(ANA, Answer.IN).from(BEN, Answer.IN);
        // Ben gives up his chair; the table is now Ana's alone, and Ben's yes goes with him.
        AnteConsent alone = new AnteConsent(Set.of(ANA), both.answers());
        assertThat(alone.answers()).containsOnlyKeys(ANA);
        assertThat(alone.settled()).isTrue();

        // And the other way round: Ana leaves, so the only remaining seat has not answered.
        AnteConsent fresh = new AnteConsent(Set.of(BEN), Set.of(ANA).isEmpty() ? both.answers()
                : java.util.Map.of(ANA, Answer.IN));
        assertThat(fresh.settled()).isFalse();
    }

    /**
     * Somebody who sits down after the question was asked has to be asked too.
     *
     * <p>The seats are fixed when the question goes out, so without rebuilding against who is
     * actually there, a table could reach unanimity and start with a player who was never
     * asked - a card taken off somebody who did not agree, which is the single failure this
     * whole feature exists to prevent.
     */
    @Test
    void arrivingAfterTheQuestionMeansTheTableIsWaitingAgain() {
        AnteConsent alone = AnteConsent.asking(Set.of(ANA)).from(ANA, Answer.IN);
        assertThat(alone.settled()).isTrue();

        Set<SeatId> both = new LinkedHashSet<>(Set.of(ANA, BEN));
        AnteConsent joined = new AnteConsent(both, alone.answers());
        assertThat(joined.settled()).isFalse();
        assertThat(joined.waitingOn()).containsExactly(BEN);
        // And Ana's yes still stands - the newcomer is the only one being waited on.
        assertThat(joined.answerFrom(ANA)).isEqualTo(Answer.IN);
    }

    /**
     * Whatever anybody says in whatever order, a table is only settled when every seat is in.
     *
     * <p>The property that matters, because the cost of getting it wrong is somebody losing a
     * card they never agreed to play for.
     */
    @Property
    void aTableIsSettledOnlyWhenEverySeatSaidYes(@ForAll("conversations") AnteConsent talk) {
        boolean everyoneIn = !talk.seats().isEmpty()
                && talk.seats().stream().allMatch(seat -> talk.answerFrom(seat) == Answer.IN);
        assertThat(talk.settled()).isEqualTo(everyoneIn);
        if (talk.settled()) {
            assertThat(talk.refused()).isFalse();
            assertThat(talk.waitingOn()).isEmpty();
        }
    }

    @Provide
    Arbitrary<AnteConsent> conversations() {
        return Arbitraries.integers().between(0, 3).list().ofMaxSize(8).map(said -> {
            Set<SeatId> seats = new LinkedHashSet<>();
            for (int index = 0; index < 3; index++) {
                seats.add(new SeatId(index));
            }
            AnteConsent talk = AnteConsent.asking(seats);
            Answer[] answers = Answer.values();
            for (int index = 0; index < said.size(); index++) {
                talk = talk.from(new SeatId(said.get(index) % 3),
                        answers[index % answers.length]);
            }
            return talk;
        });
    }
}
