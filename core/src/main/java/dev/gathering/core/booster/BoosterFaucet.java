package dev.gathering.core.booster;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A kind of pack, as an acquisition path.
 * <p>What it reaches is not every printing on every sheet it carries. It is every printing on
 * the sheets some arrangement actually draws from - a sheet a config holds and no variant
 * names is a sheet no pack was ever cut from, and counting it would be the auditor reporting
 * coverage that does not exist. That is the worse failure of the two: a card wrongly listed
 * as uncovered turns up in the Archive Pack, which is a slightly larger archive; a card
 * wrongly listed as covered is a card nobody can ever get, and nothing will say so.
 */
public record BoosterFaucet(BoosterConfig config) implements Faucet {

    public BoosterFaucet {
        if (config == null) {
            throw new IllegalArgumentException("A booster faucet needs a config");
        }
    }

    @Override
    public String name() {
        return config.id();
    }

    @Override
    public Set<UUID> reaches() {
        Set<UUID> reached = new LinkedHashSet<>();
        if (!config.isUsable()) {
            // A config that cannot open anything produces nothing, whatever it carries. A
            // half-loaded feed must not be read as a set already covered.
            return reached;
        }
        for (BoosterVariant variant : config.variants()) {
            for (String sheetName : variant.slots().keySet()) {
                BoosterSheet sheet = config.sheets().get(sheetName);
                if (sheet != null) {
                    reached.addAll(sheet.printings());
                }
            }
        }
        return reached;
    }
}
