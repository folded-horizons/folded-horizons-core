package dev.foldedhorizons.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import dev.foldedhorizons.core.setup.ModBlockEntities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Dimensional Breacher — the gateway machine, core-alone form (the GT
 * module swaps in the GTCEu multiblock controller).
 *
 * <p>Right-click interaction (no GUI in core — documented choice on
 * {@link BreacherBlockEntity}): plain right-click cycles the destination
 * (Personal → Team → Lobby → Personal), sneak-right-click executes the
 * breach. Both give action-bar feedback; refusals (pending conversion,
 * insufficient energy, grid exhausted) are shown there too.</p>
 */
public class BreacherBlock extends BaseEntityBlock {

    public BreacherBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreacherBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BreacherBlockEntity breacher)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                breacher.tryBreach(serverPlayer);
            } else {
                breacher.cycleDestination(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Energy capability: the breacher's internal FE buffer, all sides. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.BREACHER.get(),
                (be, side) -> be.energy());
    }

    /** Codec for the block state map (BaseEntityBlock contract). */
    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(BreacherBlock::new);
    }
}
