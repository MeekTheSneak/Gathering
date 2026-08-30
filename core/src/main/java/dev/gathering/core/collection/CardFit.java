package dev.gathering.core.collection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which of the cards you own go with the commander you have picked.
 *
 * <p><strong>Why this is not EDHREC.</strong> The obvious way to answer "what should go in
 * this deck" is to ask the site that has counted a million of them. EDHREC publishes no API
 * for other tools to use: what exists is the endpoint their own website calls, undocumented
 * and offered to nobody. This mod already refuses that trade once, in
 * {@link dev.gathering.core.deck.DeckLink}, where a Moxfield link is recognised and never
 * fetched because their API turns third parties away and working around that is not something
 * the mod will do. Pointing every copy of a Minecraft mod at an endpoint its owners never
 * offered is the same act with a different hostname, and it would be the mod's own stated
 * policy broken the first time it was inconvenient.
 *
 * <p>So this answers a narrower question honestly rather than a broader one on somebody
 * else's infrastructure: <em>of the cards actually in your box, which ones does this commander
 * have something to do with?</em> That is the question a player at a collection block is
 * really asking - a recommendation you do not own is a shopping list - and every word it needs
 * is in the card data the mod has already fetched and cached.
 *
 * <p>It is a reading of the text, not a count of what people play, and it does not pretend
 * otherwise. What it is good at is the thing crowd data is worst at: it has read
 * <em>your</em> cards.
 *
 * <p>Pure.
 */
public final class CardFit {

    private CardFit() {
    }

    /** A candidate and why it came up, so a screen can say more than "trust me". */
    public record Fit(BuildCard card, int score, List<String> because) {

        public Fit {
            because = because == null ? List.of() : List.copyOf(because);
        }
    }

    /**
     * The cards in this collection that suit this commander, best first.
     *
     * <p>Colour identity is a filter and not a score: a card outside the commander's colours
     * cannot be in the deck at all, so offering it as a suggestion would be offering something
     * the player would have to take straight back out. Everything that survives that is then
     * ranked on how much it has to do with what the commander says.
     *
     * @param already cards the deck already holds, by oracle id, so a suggestion is never
     *                something already sitting in the list
     */
    public static List<Fit> forCommander(
            BuildCard commander, List<BuildCard> collection, DeckBuild already, int most) {
        if (commander == null || collection == null) {
            return List.of();
        }
        Set<String> identity = commander.colorIdentity();
        Set<String> wanted = themesOf(commander);

        List<Fit> found = new ArrayList<>();
        Set<java.util.UUID> seen = new LinkedHashSet<>();
        for (BuildCard card : collection) {
            if (card.oracle().equals(commander.oracle())
                    || !card.insideIdentity(identity)
                    || (already != null && already.copiesOf(card.oracle()) > 0)
                    || !seen.add(card.oracle())) {
                continue;
            }
            List<String> because = reasons(card, wanted);
            int score = scoreOf(card, wanted, because.size());
            if (score > 0) {
                found.add(new Fit(card, score, because));
            }
        }
        found.sort(Comparator
                .comparingInt((Fit fit) -> -fit.score())
                .thenComparing(fit -> fit.card().name()));
        return List.copyOf(found.subList(0, Math.min(found.size(), Math.max(0, most))));
    }

    /**
     * What a commander is about, as the handful of words worth matching on.
     *
     * <p>Its creature types - a tribal commander is the easiest deck in Magic to build and the
     * one where "what do I own" is the whole question - plus whichever of the recurring themes
     * below its rules text names. Deliberately a small list of things that name themselves in
     * card text: this is a text match and it is only as good as the words being unambiguous,
     * so a theme that has to be inferred is not on it.
     */
    static Set<String> themesOf(BuildCard commander) {
        Set<String> themes = new LinkedHashSet<>();
        String text = commander.oracleText().toLowerCase(Locale.ROOT);
        for (String theme : THEMES) {
            if (text.contains(theme)) {
                themes.add(theme);
            }
        }
        // The creature types off the far side of the em dash: "Legendary Creature - Elf Druid"
        // is an elf deck and a druid deck, and both of those are words its payoffs print.
        String line = commander.typeLine();
        int dash = line.indexOf('—');
        if (dash >= 0) {
            for (String word : line.substring(dash + 1).trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (word.length() > 2) {
                    themes.add(word);
                }
            }
        }
        return themes;
    }

    /**
     * Why this card came up, in the commander's own words.
     *
     * <p>Given back to the screen so a suggestion can say what it is for. A list of cards with
     * no reason beside them is a list somebody has to take on faith, and this one has not
     * earned that - it is a text match, and showing the word it matched on is what lets a
     * player see at a glance whether it found something real or a coincidence.
     */
    static List<String> reasons(BuildCard card, Set<String> themes) {
        String text = (card.oracleText() + " " + card.typeLine()).toLowerCase(Locale.ROOT);
        List<String> because = new ArrayList<>();
        for (String theme : themes) {
            if (text.contains(theme) && because.size() < MOST_REASONS) {
                because.add(theme);
            }
        }
        return because;
    }

    /**
     * How well this card fits, as a number with no units.
     *
     * <p>Only ever compared with other scores from the same call, so what matters is the order
     * it puts things in. A shared theme is worth most; being in the commander's exact colours
     * rather than merely inside them is worth a little, because a card that uses all of them
     * is a card the deck was built to cast; and ramp and draw are worth a little on their own,
     * because every commander deck wants them and a collection is usually short of both.
     */
    static int scoreOf(BuildCard card, Set<String> themes, int shared) {
        int score = shared * PER_THEME;
        String text = card.oracleText().toLowerCase(Locale.ROOT);
        for (String staple : ALWAYS_WANTED) {
            if (text.contains(staple)) {
                score += PER_STAPLE;
                break;
            }
        }
        if (!card.colorIdentity().isEmpty() && themes.isEmpty()) {
            // Nothing to match on at all - a vanilla commander, or one whose text says nothing
            // this can read. Everything legal is then equally suggestible, which is a list in
            // name order and no use, so only the staples come back.
            score = score > 0 ? score : 0;
        }
        return score;
    }

    /** Kept short on purpose: a reason list longer than this is a paragraph, not a reason. */
    private static final int MOST_REASONS = 3;

    private static final int PER_THEME = 10;
    private static final int PER_STAPLE = 3;

    /**
     * The two things every commander deck is short of, in the words cards use to offer them.
     *
     * <p>Not a claim about what is good - it is the one piece of deckbuilding advice that is
     * true of every deck in the format regardless of what it is trying to do, which is why it
     * is safe to bake in and why nothing else is.
     */
    private static final List<String> ALWAYS_WANTED = List.of(
            "search your library for a", "add {", "draw a card", "draw two cards");

    /**
     * Themes a card names in so many words.
     *
     * <p>Every one of these is a phrase that appears in the text of both the commander that
     * wants it and the cards that provide it, which is the only kind of theme a text match can
     * honestly find. Mechanics that have to be inferred from what a card does are not here,
     * because guessing at them would produce confident nonsense.
     */
    private static final List<String> THEMES = List.of(
            "+1/+1 counter", "-1/-1 counter", "landfall", "lifelink", "deathtouch", "flying",
            "trample", "sacrifice", "graveyard", "token", "treasure", "energy", "proliferate",
            "mill", "explore", "convoke", "equip", "aura", "enchantment", "artifact",
            "cast your commander", "commander", "dies", "enters the battlefield", "attacks",
            "discard", "exile", "untap", "counter target spell", "each opponent", "double");
}
