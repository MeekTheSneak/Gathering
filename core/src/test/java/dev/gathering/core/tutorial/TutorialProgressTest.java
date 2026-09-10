package dev.gathering.core.tutorial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The guided first game, driven without a game. */
class TutorialProgressTest {

    private static TutorialProgress through(TutorialProgress from, TutorialStep... steps) {
        TutorialProgress at = from;
        for (TutorialStep step : steps) {
            at = at.saw(step);
        }
        return at;
    }

    @Nested
    @DisplayName("getting through it")
    class GettingThrough {

        @Test
        @DisplayName("starts on the first step with nothing done")
        void theBeginning() {
            TutorialProgress at = TutorialProgress.start();
            assertThat(at.showing()).isEqualTo(TutorialStep.DRAW);
            assertThat(at.count()).isZero();
            assertThat(at.isFinished()).isFalse();
            assertThat(at.isOver()).isFalse();
        }

        @Test
        @DisplayName("moves on when the step on screen is actually done")
        void oneStep() {
            TutorialProgress at = TutorialProgress.start().saw(TutorialStep.DRAW);
            assertThat(at.isDone(TutorialStep.DRAW)).isTrue();
            assertThat(at.showing()).isEqualTo(TutorialStep.PLAY);
        }

        @Test
        @DisplayName("is finished only when all six are done")
        void allSix() {
            TutorialProgress at = through(TutorialProgress.start(), TutorialStep.values());
            assertThat(at.count()).isEqualTo(TutorialStep.count());
            assertThat(at.isFinished()).isTrue();
            assertThat(at.isOver()).isTrue();
            // And the last instruction stays on screen rather than falling off the end.
            assertThat(at.showing()).isEqualTo(TutorialStep.PASS);
        }
    }

    @Nested
    @DisplayName("what does not count")
    class WhatDoesNotCount {

        @Test
        @DisplayName("a step that is not the one being asked for")
        void exploringIsNotCompleting() {
            // A player tapping a card during the draw step is exploring, which is fine. It is
            // not drawing a card, and recording it as one would leave the tutorial claiming
            // they had done something they had not.
            TutorialProgress at = TutorialProgress.start().saw(TutorialStep.TAP);
            assertThat(at.showing()).isEqualTo(TutorialStep.DRAW);
            assertThat(at.count()).isZero();
        }

        @Test
        @DisplayName("the same step twice")
        void doneOnce() {
            TutorialProgress at = TutorialProgress.start()
                    .saw(TutorialStep.DRAW)
                    .saw(TutorialStep.DRAW);
            assertThat(at.count()).isEqualTo(1);
            assertThat(at.showing()).isEqualTo(TutorialStep.PLAY);
        }

        @Test
        @DisplayName("anything at all, once it has been skipped")
        void skippedIsFinishedWith() {
            TutorialProgress at = TutorialProgress.start().skip().saw(TutorialStep.DRAW);
            assertThat(at.count()).isZero();
            assertThat(at.skipped()).isTrue();
        }

        @Test
        @DisplayName("null, which is what an action nobody is asking about looks like")
        void nothing() {
            TutorialProgress at = TutorialProgress.start().saw(null);
            assertThat(at).isEqualTo(TutorialProgress.start());
        }
    }

    @Nested
    @DisplayName("skipping")
    class Skipping {

        @Test
        @DisplayName("is never recorded as finishing, however far through")
        void skippingIsNotFinishing() {
            TutorialProgress nearlyThere = through(TutorialProgress.start(),
                    TutorialStep.DRAW, TutorialStep.PLAY, TutorialStep.TAP,
                    TutorialStep.COUNT, TutorialStep.READ);
            assertThat(nearlyThere.count()).isEqualTo(5);

            TutorialProgress gaveUp = nearlyThere.skip();
            assertThat(gaveUp.isFinished()).isFalse();
            assertThat(gaveUp.skipped()).isTrue();
            assertThat(gaveUp.isOver()).isTrue();
            // What they did do is still true, and still theirs.
            assertThat(gaveUp.count()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("going back")
    class GoingBack {

        @Test
        @DisplayName("shows the previous instruction without undoing anything")
        void reviewNotUndo() {
            TutorialProgress at = TutorialProgress.start().saw(TutorialStep.DRAW).back();
            assertThat(at.showing()).isEqualTo(TutorialStep.DRAW);
            // The card really was drawn. A Back button that put it back would be a rules
            // engine, and this mod does not have one.
            assertThat(at.isDone(TutorialStep.DRAW)).isTrue();
        }

        @Test
        @DisplayName("stops at the first step")
        void noFurtherBack() {
            TutorialProgress at = TutorialProgress.start().back().back();
            assertThat(at.showing()).isEqualTo(TutorialStep.DRAW);
        }

        @Test
        @DisplayName("and then forward again does not mark the skipped-over step done")
        void forwardIsNotDoing() {
            TutorialProgress at = TutorialProgress.start().forward();
            assertThat(at.showing()).isEqualTo(TutorialStep.PLAY);
            assertThat(at.isDone(TutorialStep.DRAW)).isFalse();
            assertThat(at.isFinished()).isFalse();
        }

        @Test
        @DisplayName("stops at the last step")
        void noFurtherForward() {
            TutorialProgress at = TutorialProgress.start();
            for (int push = 0; push < 20; push++) {
                at = at.forward();
            }
            assertThat(at.showing()).isEqualTo(TutorialStep.PASS);
            assertThat(at.isFinished()).isFalse();
        }
    }

    @Nested
    @DisplayName("matching an action to a step")
    class MatchingActions {

        @Test
        @DisplayName("answers with the step on screen when the verb is its verb")
        void theRightVerb() {
            assertThat(TutorialProgress.start().stepFor("draw")).isEqualTo(TutorialStep.DRAW);
        }

        @Test
        @DisplayName("answers with nothing for a verb belonging to another step")
        void anotherStepsVerb() {
            assertThat(TutorialProgress.start().stepFor("tap")).isNull();
        }

        @Test
        @DisplayName("answers with nothing for the read step, which no action can complete")
        void readingIsNotAnAction() {
            // Reading a card sends nothing and changes nothing, so no confirmed action can
            // ever be evidence for it - which is exactly why its evidence is the other kind.
            TutorialProgress atRead = through(TutorialProgress.start(),
                    TutorialStep.DRAW, TutorialStep.PLAY, TutorialStep.TAP, TutorialStep.COUNT);
            assertThat(atRead.showing()).isEqualTo(TutorialStep.READ);
            assertThat(atRead.stepFor("draw")).isNull();
            assertThat(atRead.stepFor(null)).isNull();
        }

        @Test
        @DisplayName("answers with nothing once it is over")
        void overIsOver() {
            assertThat(TutorialProgress.start().skip().stepFor("draw")).isNull();
        }
    }

    @Nested
    @DisplayName("the steps themselves")
    class TheSteps {

        @Test
        @DisplayName("name a verb the catalogue carries, or none at all")
        void verbsExist() {
            for (TutorialStep step : TutorialStep.values()) {
                if (step.action() != null) {
                    assertThat(dev.gathering.core.ui.TableActions.has(step.action()))
                            .as("step %s points at a verb the catalogue does not carry: %s",
                                    step, step.action())
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("are satisfied by a confirmed action, except the one that moves nothing")
        void evidenceMatchesTheVerb() {
            for (TutorialStep step : TutorialStep.values()) {
                boolean watchable = step.evidence() == TutorialStep.Evidence.THE_SERVER_SAID_SO;
                assertThat(step.action() != null)
                        .as("%s says its evidence is %s", step, step.evidence())
                        .isEqualTo(watchable);
            }
        }
    }
}
