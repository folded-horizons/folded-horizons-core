package dev.foldedhorizons.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

/**
 * Root Binder — the ownership machine's block, core-alone form. Same
 * interaction model as the breacher (no GUI in core; see
 * {@link RootBinderBlockEntity}): right-click cycles the operation,
 * sneak-right-click confirms/schedules it. Countdown and refusals surface
 * as action-bar messages.
 */
public class RootBinderBlock extends BaseEntityBlock {

    public RootBinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RootBinderBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RootBinderBlockEntity binder)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                binder.confirmOperation(serverPlayer);
            } else {
                binder.cycleOperation(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(RootBinderBlock::new);
    }
}
