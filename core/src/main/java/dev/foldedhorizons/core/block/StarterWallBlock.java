package dev.foldedhorizons.core.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import dev.foldedhorizons.core.event.ModEvents;
import dev.foldedhorizons.core.setup.ModBlocks;
import java.util.List;

/**
 * A starter-cell wall block that never breaks: "mining" it chips it.
 *
 * <p>Wall-start mechanic (build-order 7 / docs/companion-mod/
 * walls-expansion.md): a finished break attempt rolls the block's loot
 * table, drops the rolled items into the world, applies a small tool
 * durability cost, plays a chip sound, and leaves the block in place —
 * infinite yield, per-block material table. The starter-cell template
 * references these ids in its palette, so the registry names must stay
 * exactly {@code foldedhorizons:wall_stone}/{@code wall_wood}/
 * {@code wall_metal}/{@code wall_shell}.</p>
 *
 * <p>Implementation choice: a {@link Block#playerDestroy} override. At
 * that hook the vanilla pipeline has already removed the block, so we
 * immediately restore it with {@code setBlock(pos, state)} and then run
 * the chip effect. This is simpler and more robust than an event handler
 * (no ordering concerns with other break-event listeners) and keeps the
 * behavior per-block rather than per-dimension.</p>
 *
 * <p>Loot is resolved from the server's block-loot registry under this
 * block's registry id, so the tables shipped in
 * {@code data/foldedhorizons/loot_table/blocks/} (datapack-overridable)
 * apply, including the {@code minecraft:match_tool} condition that gates
 * {@code wall_metal}'s iron/copper chunk drops behind a pickaxe.</p>
 */
public class StarterWallBlock extends Block {

    public static final MapCodec<StarterWallBlock> CODEC =
            simpleCodec(StarterWallBlock::new);

    public StarterWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, BlockEntity blockEntity, ItemStack tool) {
        // The vanilla break pipeline has already set the block to air;
        // put it right back so the wall is effectively infinite.
        level.setBlock(pos, state, Block.UPDATE_ALL);

        if (level instanceof ServerLevel serverLevel) {
            if (state.is(ModBlocks.WALL_SHELL.get())) {
                // Shell blocks are unbreakable in survival (destroyTime -1);
                // reaching playerDestroy at all means a late-game/cheat path
                // broke through — the reserved shell_broken event.
                ModEvents.shellBroken(serverLevel, pos.immutable(), player.getUUID());
            }
            chip(serverLevel, player, pos, tool);
        }
    }

    /**
     * Rolls this block's loot table at {@code pos}, spawns the drops,
     * damages the used tool by 1 and plays the chip sound.
     */
    private void chip(ServerLevel level, Player player, BlockPos pos, ItemStack tool) {
        ResourceLocation tableId = builtInRegistryHolder().key().location();
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, pos.getCenter())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.TOOL, tool)
                .create(LootTable.DEFAULT_PARAM_SET);

        List<ItemStack> drops = table.getRandomItems(params);
        for (ItemStack drop : drops) {
            Block.popResource(level, pos, drop);
        }

        tool.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6f, 1.1f);
    }
}
