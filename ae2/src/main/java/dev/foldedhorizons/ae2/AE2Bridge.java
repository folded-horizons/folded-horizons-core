package dev.foldedhorizons.ae2;

/**
 * Bridge layer to Applied Energistics 2 (NEOFORGE build 19.2.x line for MC
 * 1.21.1). All AE2 touchpoints funnel through this class so the mod's AE2
 * dependency stays swappable and the rest of the codebase works against
 * plain stacks.
 *
 * <p>TODO (AE2 19.2.x API, appeng.*):</p>
 * <ul>
 *   <li>Cross-dimensional ME grid bridging: attach an {@code IGridNode} host
 *       in one dimension and expose it to machines in another — required for
 *       the Trade Post ({@link dev.foldedhorizons.core.machine.TradePostBlock})
 *       reaching ME storage from the trading hall and for the breacher-side
 *       energy draw.</li>
 *   <li>Grid host + node lifecycle for block entities ({@code IGridHost} /
 *       grid node listener registration via {@code AEGridManager}).</li>
 *   <li>Item movement between two foreign ME networks (AE2-to-AE2 trades):
 *       extract from the offerer's storage, inject into the visitor's
 *       storage respecting their import rules.</li>
 *   <li>Energy accounting for breach costs drawn through the grid.</li>
 * </ul>
 *
 * <p>Currently a stub: no AE2 dependency is wired into build.gradle yet —
 * add the AE2 maven + a compileOnly/implementation jar once the exact
 * 19.2.x artifact coordinates are chosen.</p>
 */
public final class AE2Bridge {

    private AE2Bridge() {}

    // TODO: static helpers for obtaining/attaching grid nodes across
    //       dimensions, and AE2-to-AE2 item transfer primitives.
}
