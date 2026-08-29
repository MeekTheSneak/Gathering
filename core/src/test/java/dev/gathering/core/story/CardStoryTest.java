package dev.gathering.core.story;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a card has been.
 *
 * <p>Most of this is about the bound, because the bound is where a design decision lives: a
 * card that changed hands a hundred times has to lose ninety-something of them, and which
 * ones it loses is the whole question.
 */
class CardStoryTest {

    private static CardStory.Chapter pulled(String who) {
        return new CardStory.Chapter(HowItCame.PULLED, who, "", "dsk", "2026-01-01");
    }

    private static CardStory.Chapter won(String who, String from) {
        return new CardStory.Chapter(HowItCame.WON, who, from, "", "2026-02-02");
    }

    @Test
    @DisplayName("a card nothing has happened to has nothing to say")
    void nothingYet() {
        assertThat(CardStory.NONE.isEmpty()).isTrue();
        assertThat(CardStory.NONE.beginning()).isNull();
        assertThat(CardStory.NONE.latest()).isNull();
        assertThat(CardStory.NONE.hasGaps()).isFalse();
    }

    @Test
    @DisplayName("where it came from, and how it got here")
    void bothEnds() {
        CardStory story = CardStory.begunWith(pulled("Alice")).and(won("Bob", "Alice"));

        assertThat(story.chapters()).hasSize(2);
        assertThat(story.beginning().who()).isEqualTo("Alice");
        assertThat(story.beginning().how()).isEqualTo(HowItCame.PULLED);
        assertThat(story.latest().who()).isEqualTo("Bob");
        assertThat(story.latest().from()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("a long history keeps where it started and how it got here")
    void theMiddleGoesFirst() {
        CardStory story = CardStory.begunWith(pulled("Alice"));
        for (int index = 0; index < CardStory.MOST + 5; index++) {
            story = story.and(won("Owner " + index, "Owner " + (index - 1)));
        }

        assertThat(story.chapters()).hasSize(CardStory.MOST);
        // Where it came from survives however far it travels afterwards.
        assertThat(story.beginning().who()).isEqualTo("Alice");
        assertThat(story.beginning().how()).isEqualTo(HowItCame.PULLED);
        // And so does the most recent hand it came into.
        assertThat(story.latest().who()).isEqualTo("Owner " + (CardStory.MOST + 4));
        assertThat(story.hasGaps()).isTrue();
        assertThat(story.forgotten()).isEqualTo(6);
    }

    @Test
    @DisplayName("a story that lost nothing says so")
    void noGaps() {
        CardStory story = CardStory.begunWith(pulled("Alice")).and(won("Bob", "Alice"));

        assertThat(story.hasGaps()).isFalse();
        assertThat(story.forgotten()).isZero();
    }

    @Test
    @DisplayName("adding nothing changes nothing")
    void addingNothing() {
        CardStory story = CardStory.begunWith(pulled("Alice"));

        assertThat(story.and(null)).isEqualTo(story);
        assertThat(new CardStory(null, 0)).isEqualTo(CardStory.NONE);
    }

    @Test
    @DisplayName("a name longer than a name is cut to one")
    void longNames() {
        CardStory.Chapter chapter = pulled("a-name-far-longer-than-minecraft-allows");

        assertThat(chapter.who()).hasSize(CardStory.LONGEST_NAME);
    }

    @Test
    @DisplayName("a chapter with nothing in it is still readable rather than null")
    void emptyChapter() {
        CardStory.Chapter chapter = new CardStory.Chapter(null, null, null, null, null);

        assertThat(chapter.how()).isEqualTo(HowItCame.PULLED);
        assertThat(chapter.who()).isEmpty();
        assertThat(chapter.from()).isEmpty();
    }

    @Test
    @DisplayName("only what came out of somebody's hands names whose")
    void whoItCameFrom() {
        assertThat(HowItCame.WON.hasSomebodyBefore()).isTrue();
        assertThat(HowItCame.TRADED.hasSomebodyBefore()).isTrue();
        assertThat(HowItCame.PULLED.hasSomebodyBefore()).isFalse();
    }

    @Test
    @DisplayName("a way of coming survives the name it is written down as")
    void throughItsName() {
        for (HowItCame how : HowItCame.all()) {
            assertThat(HowItCame.named(how.id())).isEqualTo(how);
        }
        assertThat(HowItCame.named("something a later version wrote")).isNull();
        assertThat(HowItCame.named(null)).isNull();
    }
}
