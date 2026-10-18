package dev.foldedhorizons.core;

import dev.foldedhorizons.core.setup.ModBlockEntities;
import dev.foldedhorizons.core.setup.ModBlocks;
import dev.foldedhorizons.core.setup.ModItems;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point for Folded Horizons Core.
 *
 * <p>This mod implements the companion-mod systems described in
 * {@code docs/companion-mod/} and {@code docs/design/}:
 * root spaces on a shared void grid, unstable exploit pockets, repeatable
 * annex spaces, and stable special dimensions, plus the machines that gate
 * them (Dimensional Breacher, Root Binder, Trade Post).</p>
 *
 * <p>Constructor registers all DeferredRegister holders ({@link ModBlocks}, {@link ModItems}, {@link ModBlockEntities}) on the
 * mod event bus. Gameplay wiring (grid allocation, pocket lifecycle, AE2
 * bridging) lands in later passes.</p>
 */
@Mod(FoldedHorizons.MOD_ID)
public class FoldedHorizons {

    public static final String MOD_ID = "foldedhorizons";

    public FoldedHorizons(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.REGISTER.register(modEventBus);
        ModItems.REGISTER.register(modEventBus);
        ModBlockEntities.REGISTER.register(modEventBus);
        // TODO: register creative tab, network payloads, SavedData attachments,
        //       and AE2 integration listeners (ModConfig is wired via the
        //       modContainer in a later pass).
    }
}
