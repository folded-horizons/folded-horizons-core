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
 * mod event bus. Materialization + claim/identity SPIs (build-order 4) are
 * self-wiring: {@code ServerLifecycle} is an {@code @EventBusSubscriber} and
 * the SPI defaults are no-op/fallback singletons until a module registers
 * its adapter.</p>
 */
@Mod(FoldedHorizons.MOD_ID)
public class FoldedHorizons {

    public static final String MOD_ID = "foldedhorizons";

    public FoldedHorizons(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.REGISTER.register(modEventBus);
        ModItems.REGISTER.register(modEventBus);
        ModBlockEntities.REGISTER.register(modEventBus);
        // Capability wiring: breacher's FE buffer (energy storage) is
        // registered by the static hook below; the dimension-gen machine's
        // buffer rides the same event class pattern.
        modEventBus.register(dev.foldedhorizons.core.machine.BreacherBlock.class);
        modEventBus.register(dev.foldedhorizons.core.machine.DimensionGenMultiblockBlock.class);
        // Permanent pocket chunk loading (docs "Claiming" policy): the
        // re-arm hook registers itself on the NeoForge bus.
        dev.foldedhorizons.core.dimension.PocketChunkLoader.register();
        // Materialization/claim/identity SPIs default to no-op + built-in
        // parties; foldedhorizons-ftb overrides them via
        // TeamIdentityProvider.register / ClaimAdapter.register at its own
        // construction. The ServerStarted hook (ServerLifecycle) self-registers
        // via @EventBusSubscriber — no explicit wiring needed here.
    }
}
