package dev.foldedhorizons.core.setup;

import dev.foldedhorizons.core.FoldedHorizons;
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
}
