package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Why a real booster is never five commons of one color, and now neither is this one.
 * <p>MTGJSON publishes {@code balanceColors} per sheet and this mod read it, wrote a note
 * saying it was not reproduced, and drew the whole slot by weight. That gives mono-color packs
 * at a rate no real box does, which is the first thing a limited player notices.
 */
class ColorBalanceTest {

    /** Enough of a sheet to be balanced: three commons of each of the five colors. */
    private static BoosterSheet fiveColors() {
        Map<UUID, Long> weights = new LinkedHashMap<>();
        Map<UUID, String> colors = new LinkedHashMap<>();
        for (int index = 0; index < ColorBalance.COLORS.length(); index++) {
            String color = String.valueOf(ColorBalance.COLORS.charAt(index));
            for (int copy = 0; copy < 3; copy++) {
                UUID printing = UUID.randomUUID();
                weights.put(printing, 1L);
                colors.put(printing, color);
            }
        }
        // And a gold card and a colorless one, which are on the sheet and in no column.
        UUID gold = UUID.randomUUID();
        weights.put(gold, 1L);
        colors.put(gold, "WU");
        UUID artifact = UUID.randomUUID();
        weights.put(artifact, 1L);
        colors.put(artifact, "");
        return new BoosterSheet("common", false, false, false, weights, true, colors);
    }

    @Nested
    @DisplayName("what can be balanced")
    class WhatApplies {

        @Test
        @DisplayName("a balanced sheet with all five colors and a long enough slot")
        void theordinaryCase() {
            assertThat(ColorBalance.applies(fiveColors(), 10)).isTrue();
            assertThat(ColorBalance.applies(fiveColors(), ColorBalance.NEEDS_AT_LEAST)).isTrue();
        }

        @Test
        @DisplayName("a slot too short to hold one of each is drawn plainly")
        void ashortSlotIsNotBalanced() {
            assertThat(ColorBalance.applies(fiveColors(), 4)).isFalse();
            assertThat(ColorBalance.applies(fiveColors(), 0)).isFalse();
        }

        @Test
        @DisplayName("a sheet the data does not call balanced is left alone")
        void anunbalancedSheetIsLeftAlone() {
            BoosterSheet plain = new BoosterSheet(
                    "common", false, false, false, fiveColors().weights());

            assertThat(ColorBalance.applies(plain, 10)).isFalse();
        }

        @Test
        @DisplayName("a balanced sheet missing a color cannot be balanced")
        void amissingColumnStopsIt() {
            BoosterSheet sheet = fiveColors();
            Map<UUID, String> withoutGreen = new LinkedHashMap<>(sheet.colors());
            withoutGreen.replaceAll((printing, color) -> color.equals("G") ? "" : color);
            BoosterSheet fourColors = new BoosterSheet(
                    "common", false, false, false, sheet.weights(), true, withoutGreen);

            assertThat(ColorBalance.applies(fourColors, 10)).isFalse();
        }

        @Test
        @DisplayName("a balanced sheet with no colors read for it cannot be balanced")
        void nocolorsMeansNoBalancing() {
            BoosterSheet blind = new BoosterSheet(
                    "common", false, false, false, fiveColors().weights(), true, Map.of());

            assertThat(ColorBalance.applies(blind, 10)).isFalse();
        }

        @Test
        @DisplayName("a fixed sheet is copied out whole, so there is nothing to balance")
        void afixedSheetIsNotDrawnFrom() {
            BoosterSheet sheet = fiveColors();
            BoosterSheet fixed = new BoosterSheet("themed", false, false, true,
                    sheet.weights(), true, sheet.colors());

            assertThat(ColorBalance.applies(fixed, 10)).isFalse();
        }
    }

    @Nested
    @DisplayName("the columns a sheet is cut into")
    class Columns {

        @Test
        @DisplayName("a column holds that color's mono-colored cards and nothing else")
        void acolumnIsMonoColored() {
            BoosterSheet sheet = fiveColors();

            BoosterSheet white = ColorBalance.columnOf(sheet, 'W');

            assertThat(white.size()).isEqualTo(3);
            for (UUID printing : white.printings()) {
                assertThat(sheet.colorOf(printing)).isEqualTo("W");
            }
        }

        @Test
        @DisplayName("gold and colorless cards are in no column, and still on the sheet")
        void goldIsInNoColumn() {
            BoosterSheet sheet = fiveColors();

            int inColumns = 0;
            for (char color : ColorBalance.columnsToFill()) {
                inColumns += ColorBalance.columnOf(sheet, color).size();
            }

            assertThat(inColumns).isEqualTo(15);
            assertThat(sheet.size()).isEqualTo(17);
        }

        @Test
        @DisplayName("a card printed twice on the sheet is twice as likely within its column")
        void weightsSurviveTheCut() {
            Map<UUID, Long> weights = new LinkedHashMap<>();
            Map<UUID, String> colors = new LinkedHashMap<>();
            UUID common = UUID.randomUUID();
            UUID rare = UUID.randomUUID();
            weights.put(common, 4L);
            weights.put(rare, 1L);
            colors.put(common, "R");
            colors.put(rare, "R");

            BoosterSheet red = ColorBalance.columnOf(
                    new BoosterSheet("s", false, false, false, weights, true, colors), 'R');

            assertThat(red.total()).isEqualTo(5);
            assertThat(red.weights().get(common)).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("what comes out of a pack")
    class Packs {

        @Test
        @DisplayName("every pack off a balanced sheet has all five colors in it")
        void everyPackCrossesTheColors() {
            BoosterSheet sheet = fiveColors();
            BoosterConfig config = new BoosterConfig("tst", "draft",
                    Map.of("common", sheet),
                    List.of(new BoosterVariant("pack", 1, Map.of("common", 10))));

            for (int pack = 0; pack < 200; pack++) {
                OpenedPack opened = BoosterOpener.open(config, seed(pack), "p" + pack);

                assertThat(colorsIn(opened, sheet))
                        .describedAs("pack %s", pack)
                        .contains('W', 'U', 'B', 'R', 'G');
                assertThat(opened.cards()).hasSize(10);
            }
        }

        @Test
        @DisplayName("without balancing, a mono-color pack turns up - which is the bug")
        void withoutBalancingItDoesNot() {
            BoosterSheet plain = new BoosterSheet(
                    "common", false, false, false, fiveColors().weights());
            BoosterSheet colored = fiveColors();
            BoosterConfig config = new BoosterConfig("tst", "draft",
                    Map.of("common", plain),
                    List.of(new BoosterVariant("pack", 1, Map.of("common", 5))));

            int allFive = 0;
            for (int pack = 0; pack < 200; pack++) {
                if (colorsIn(BoosterOpener.open(config, seed(pack), "p" + pack), colored)
                        .containsAll(List.of('W', 'U', 'B', 'R', 'G'))) {
                    allFive++;
                }
            }

            assertThat(allFive)
                    .describedAs("an unbalanced sheet must not accidentally balance itself")
                    .isLessThan(200);
        }

        @Test
        @DisplayName("a balanced pack is not laid out in color order")
        void theslotIsShuffled() {
            BoosterSheet sheet = fiveColors();
            BoosterConfig config = new BoosterConfig("tst", "draft",
                    Map.of("common", sheet),
                    List.of(new BoosterVariant("pack", 1, Map.of("common", 10))));

            int inOrder = 0;
            for (int pack = 0; pack < 50; pack++) {
                List<Character> colors = new ArrayList<>();
                for (CardIdentity card : BoosterOpener.open(config, seed(pack), "p" + pack).cards()) {
                    String letters = sheet.colorOf(card.printing().orElseThrow());
                    if (letters.length() == 1) {
                        colors.add(letters.charAt(0));
                    }
                }
                if (colors.size() >= 5 && colors.subList(0, 5).equals(
                        List.of('W', 'U', 'B', 'R', 'G'))) {
                    inOrder++;
                }
            }

            assertThat(inOrder)
                    .describedAs("no pack anybody has opened begins W, U, B, R, G")
                    .isLessThan(5);
        }

        @Test
        @DisplayName("the same seed still opens the same pack")
        void balancingIsStillReproducible() {
            BoosterConfig config = new BoosterConfig("tst", "draft",
                    Map.of("common", fiveColors()),
                    List.of(new BoosterVariant("pack", 1, Map.of("common", 10))));

            assertThat(BoosterOpener.open(config, seed(7), "x").cards())
                    .isEqualTo(BoosterOpener.open(config, seed(7), "x").cards());
            assertThat(BoosterOpener.open(config, seed(7), "x").cards())
                    .isNotEqualTo(BoosterOpener.open(config, seed(8), "x").cards());
        }
    }

    private static List<Character> colorsIn(OpenedPack pack, BoosterSheet sheet) {
        List<Character> found = new ArrayList<>();
        for (CardIdentity card : pack.cards()) {
            String letters = sheet.colorOf(card.printing().orElseThrow());
            if (letters.length() == 1) {
                found.add(letters.charAt(0));
            }
        }
        return found;
    }

    private static byte[] seed(int of) {
        byte[] seed = new byte[16];
        seed[0] = (byte) of;
        seed[1] = (byte) (of >> 8);
        return seed;
    }
}
