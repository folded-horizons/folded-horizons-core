package dev.foldedhorizons.core.machine;

import net.minecraft.world.level.block.Block;

/**
 * Dimension-Generation Multiblock — the LV-era, one-target ancestor of the
 * breacher tech. Designed to generate the AE2 meteorite dimension pocket:
 * skystone, the four Inscriber Presses and budding certus quartz.
 *
 * <p>Designed behavior:</p>
 * <ul>
 *   <li>Tier-locked to LV; multiblock formed of controller + casing
 *       pattern (GTCEu Modern).</li>
 *   <li>On activation with full energy + required inputs the pocket
 *       materializes and the multiblock is CONSUMED in the jump — every
 *       block replaced with air, a "collapsed frame" item dropped.
 *       One machine per pocket; each pocket costs a full rebuild.</li>
 *   <li>No stability counter; the pocket is finite by loot scarcity —
 *       once mined out it is exhausted, previewing the exploit cycle
 *       before exploit dimensions open at EV+.</li>
 * </ul>
 *
 * <p>TODO: implement as a GT multiblock with consumed-on-use override of
 * the formed/on-use hook; pocket template materialization lives in
 * {@link dev.foldedhorizons.core.dimension.PocketGrid}. Currently an
 * inert block placeholder.</p>
 */
public class DimensionGenMultiblock extends Block {

    public DimensionGenMultiblock(Properties properties) {
        super(properties);
        // TODO: consumed-on-jump behavior, meteorite pocket template wiring.
    }
}
