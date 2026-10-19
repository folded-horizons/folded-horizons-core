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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Trade Post — the lobby's trading-hall machine, crate-based core form.
 *
 * <p>Crate-based lobby handoff (this class) is the core fallback and the
 * early-game trading path: visitors drop what they want to trade away
 * into the input crate, the owner's standing offers pay out into the
 * output crate, and the auto-trade tick (every 20 ticks) matches wants to
 * offers with an atomic two-leg commit (see
 * {@link TradePostBlockEntity}). It works standalone — no AE2 network
 * required on either side; the {@code foldedhorizons-ae2} module upgrades
 * this machine to ME-backed storage-to-storage trading via the
 * StorageNetwork SPI.</p>
 *
 * <p>Interaction model (menu-less like the breacher and root binder —
 * core has no GUI): right-click reports the number of active standing
 * offers on the action bar; sneak-right-click runs one immediate trade
 * attempt ("test trade once") with the executed-count as feedback.</p>
 *
 * <p>The GT module later swaps in the GTCEu singleblock with the real
 * trade-configuration GUI.</p>
 */
public class TradePostBlock extends BaseEntityBlock {

    public TradePostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradePostBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TradePostBlockEntity tradePost)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int executed = tradePost.attemptAutoTrade();
                PocketOps.say(serverPlayer, "Trade Post: test trade — "
                        + executed + " executed");
            } else {
                PocketOps.say(serverPlayer, "Trade Post: "
                        + tradePost.activeTrades() + " offers active");
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Auto-trade ticker: the crate-based matching sweep, every 20 ticks. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.TRADE_POST.get(),
                TradePostBlockEntity::serverTick);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(TradePostBlock::new);
    }
}
