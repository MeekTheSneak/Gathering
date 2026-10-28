package dev.gathering.core.card;

/**
 * One printed face of a card.
 * <p>Single-faced cards have exactly one; transform, modal double-faced, and split-with-
 * separate-images cards have two. The zoom overlay reads oracle text from the face it is
 * showing, which is the only way errata-accurate text works for a card whose two halves
 * disagree.
 */
public record CardFace(
        String name,
        String manaCost,
        String typeLine,
        String oracleText,
        String power,
        String toughness,
        String loyalty,
        String flavorText,
        String artist,
        ImageUris imageUris) {

    public boolean hasImages() {
        return imageUris != null && !imageUris.isEmpty();
    }
}
