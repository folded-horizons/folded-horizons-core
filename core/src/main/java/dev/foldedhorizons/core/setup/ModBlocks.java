package dev.foldedhorizons.core.setup;

import dev.foldedhorizons.core.FoldedHorizons;
import dev.foldedhorizons.core.block.StarterWallBlock;
import dev.foldedhorizons.core.machine.BreacherBlock;
import dev.foldedhorizons.core.machine.DimensionGenMultiblockBlock;
import dev.foldedhorizons.core.machine.RootBinderBlock;
import dev.foldedhorizons.core.machine.TradePostBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DeferredRegister for the mod's blocks. All machines are placeholders
 * extending plain {@link Block}; real block classes / block entity types
 * are filled in by later implementation passes.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks REGISTER =
            DeferredRegister.createBlocks(FoldedHorizons.MOD_ID);

    private ModBlocks() {}

    public static final DeferredHolder<Block, BreacherBlock> BREACHER =
            REGISTER.register("breacher",
                    () -> new BreacherBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0f, 1200.0f)
                            .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, RootBinderBlock> ROOT_BINDER =
            REGISTER.register("root_binder",
                    () -> new RootBinderBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 1200.0f)
                            .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, TradePostBlock> TRADE_POST =
            REGISTER.register("trade_post",
                    () -> new TradePostBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 1200.0f)
                            .requiresCorrectToolForDrops()));

    /** Dimension-gen multiblock controller (GT stand-in — decision #13). */
    public static final DeferredHolder<Block, DimensionGenMultiblockBlock> DIMENSION_GEN =
            REGISTER.register("dimension_gen_multiblock",
                    () -> new DimensionGenMultiblockBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0f, 1200.0f)
                            .requiresCorrectToolForDrops()));

    // ------------------------------------------------------------------
    // Starter-cell walls (build-order 7). These names MUST stay in sync
    // with the palette ids baked into data/foldedhorizons/structure/
    // starter_cell.nbt (foldedhorizons:wall_*) — the template will not
    // materialize if they drift.
    // ------------------------------------------------------------------

    /** Stone wall: chips into cobblestone + occasional raw stone. */
    public static final DeferredHolder<Block, StarterWallBlock> WALL_STONE =
            REGISTER.register("wall_stone",
                    () -> new StarterWallBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.5f, 6.0f)
                            .requiresCorrectToolForDrops()));

    /** Wood wall: chips into wood scraps / planks. */
    public static final DeferredHolder<Block, StarterWallBlock> WALL_WOOD =
            REGISTER.register("wall_wood",
                    () -> new StarterWallBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.0f, 4.0f)));

    /**
     * Metal wall: chips into filler scrap; with a pickaxe (loot-table
     * {@code match_tool} condition) into iron/copper chunks.
     */
    public static final DeferredHolder<Block, StarterWallBlock> WALL_METAL =
            REGISTER.register("wall_metal",
                    () -> new StarterWallBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 6.0f)
                            .requiresCorrectToolForDrops()));

    /**
     * Unbreakable outer shell of the starter cell. {@code destroyTime -1}
     * (via negative strength hardness) means no tool can ever mine it in
     * survival; it therefore never reaches {@code playerDestroy} and no
     * loot table is needed for it.
     */
    public static final DeferredHolder<Block, StarterWallBlock> WALL_SHELL =
            REGISTER.register("wall_shell",
                    () -> new StarterWallBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(-1.0f, 3600000.0f)));
}
