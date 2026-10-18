package dev.foldedhorizons.core.machine;

import net.minecraft.world.level.block.Block;

/**
 * Trade Post — custom machine placed in the lobby's trading hall, linked to
 * the owner's AE2 network.
 *
 * <p>Designed behavior:</p>
 * <ul>
 *   <li>Owner links the Trade Post to their own AE2 network — the bridge
 *       reaches into ME storage from anywhere, the same cross-dimensional
 *       tech as the breacher ({@link dev.foldedhorizons.ae2.AE2Bridge},
 *       integration module; core talks to it via the StorageNetwork SPI).</li>
 *   <li>Owner configures standing trades: what they offer from their network
 *       and what they want in return (item + amount). Trades are editable
 *       or disableable by the owner.</li>
 *   <li>Trades execute AE2-to-AE2: a visitor selects a trade and items move
 *       directly between the two ME networks — no chests, no manual
 *       transfer, no physical meeting. Incoming items land in the visitor's
 *       system per normal ME import rules.</li>
 *   <li>Standing offers are visible to everyone in the hall and stay active
 *       while the Trade Post is powered.</li>
 *   <li>No currency: pure barter between networks.</li>
 * </ul>
 *
 * <p>TODO: implement as a block entity with AE2 grid-node host, trade config
 * GUI, and cross-network item movement. Currently an inert block
 * placeholder.</p>
 */
public class TradePostBlock extends Block {

    public TradePostBlock(Properties properties) {
        super(properties);
        // TODO: AE2 grid host node, trade configuration UI, powered-state gating.
    }
}
