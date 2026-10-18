package dev.foldedhorizons.core.item;

import net.minecraft.world.item.Item;

/**
 * Temp Key — tiered breacher consumable (tier 1 from EV onward).
 *
 * <p>Designed behavior: used in a Dimensional Breacher to open the next
 * unexhausted UNSTABLE pocket of the key's tier's dimension, at the using
 * team's per-tier offset cursor (same grid logic as root cells, separate
 * dimension and registry per tier). One instance is spent per breach; the
 * pocket collapses the moment no player remains inside and can never be
 * re-entered — the key buys a one-shot visit, not a place.</p>
 *
 * <p>TODO: attach tooltip/tier display, breacher-use interaction (consume on
 * successful breach), and per-tier dimension mapping (exploit_t1, ...).
 * Higher tiers gate deeper pockets; tier numbers grow with GT voltage.</p>
 */
public class TempKeyItem extends Item {

    private final int tier;

    public TempKeyItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}
