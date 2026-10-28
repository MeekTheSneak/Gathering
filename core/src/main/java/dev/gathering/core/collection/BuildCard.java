package dev.gathering.core.collection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One card as a deck builder needs to think about it.
 * <p>Deliberately not {@link dev.gathering.core.card.CardMetadata} and deliberately not the
 * network summary either. The builder's logic is pure and lives here where it can be checked
 * in milliseconds, so it needs a shape with no Minecraft on its classpath and nothing on it
 * that a screen happens to want - and the screen needs to be able to build one out of what it
 * already has. This is the intersection: the six things you cannot lay a deck out without.
 *
 * @param printing which physical copy this is, because a collection holds printings and two
 *                 Sol Rings in a box are two things you can pick up
 * @param oracle   which <em>card</em> it is, because every copy limit in Magic is by card and
 *                 not by printing - four Lightning Bolts is four Bolts however they were
 *                 printed, and a commander deck is singleton by this and not by that
 * @param manaValue what the curve is drawn from
 * @param colorIdentity what a commander's color identity is checked against
 */
public record BuildCard(
        UUID printing,
        UUID oracle,
        String name,
        String typeLine,
        String oracleText,
        double manaValue,
        Set<String> colorIdentity,
        boolean foil) {

    public BuildCard {
        if (printing == null) {
            throw new IllegalArgumentException("A card being built with needs a printing");
        }
        oracle = oracle == null ? printing : oracle;
        name = name == null ? "" : name;
        typeLine = typeLine == null ? "" : typeLine;
        oracleText = oracleText == null ? "" : oracleText;
        colorIdentity = colorIdentity == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(colorIdentity));
    }

    public CardKind kind() {
        return CardKind.of(typeLine);
    }

    /**
     * Whether this card may be in a deck led by a commander of this identity.
     * <p>Stated here and used to <em>say so</em>, never to refuse. A deck builder that will
     * not let somebody put a card in their own deck has decided it knows the format better
     * than they do, and this mod's whole shape is the other way round - the pre-game check is
     * the one referee, and it runs at the door rather than while the deck is being built.
     */
    public boolean insideIdentity(Set<String> identity) {
        return identity.containsAll(colorIdentity);
    }
}
