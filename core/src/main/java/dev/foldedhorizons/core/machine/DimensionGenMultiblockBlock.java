package dev.foldedhorizons.core.machine;

import dev.foldedhorizons.core.setup.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Dimension-Generation Multiblock controller block — the vanilla-side
 * stand-in for the GT machine.
 *
 * <p><strong>GT stand-in — the foldedhorizons-gt module re-homes this
 * machine onto the GTCEu framework (decision #13)</strong> with its
 * controller + casing pattern and LV lock; core ships a single
 * controller block so the core-alone pack plays the consumed-in-jump
 * loop. Sneak-right-click fires the jump (see
 * {@link DimensionGenMultiblockBlockEntity#tryFire}); plain right-click
 * reports the machine's state on the action bar.</p>
 */
public class DimensionGenMultiblockBlock extends BaseEntityBlock {

    public DimensionGenMultiblockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DimensionGenMultiblockBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DimensionGenMultiblockBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                machine.tryFire(serverPlayer);
            } else {
                PocketOps.say(serverPlayer, "Dimension-Generation Multiblock — sneak-fire to jump.");
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Energy capability: the machine's internal FE buffer, all sides. */
    @net.neoforged.bus.api.SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.DIMENSION_GEN.get(),
                (be, side) -> be.energy());
    }

    /** Codec for the block state map (BaseEntityBlock contract). */
    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DimensionGenMultiblockBlock::new);
    }
}
