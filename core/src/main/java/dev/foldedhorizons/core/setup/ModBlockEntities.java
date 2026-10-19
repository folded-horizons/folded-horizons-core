package dev.foldedhorizons.core.setup;

import java.util.Set;
import dev.foldedhorizons.core.machine.BreacherBlockEntity;
import dev.foldedhorizons.core.machine.RootBinderBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DeferredRegister for block entity types.
 *
 * <p>BE types are constructed directly with
 * {@code new BlockEntityType<>(supplier, validBlocks, null)} rather than
 * {@link BlockEntityType.Builder}: vanilla's register() fetches a DataFixer
 * type we cannot provide for modded types, and the null dataType overload
 * is the modded-correct form.</p>
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(
                    net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,
                    dev.foldedhorizons.core.FoldedHorizons.MOD_ID);

    private ModBlockEntities() {}

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BreacherBlockEntity>> BREACHER =
            REGISTER.register("breacher", () -> new BlockEntityType<>(
                    BreacherBlockEntity::new, Set.of(dev.foldedhorizons.core.setup.ModBlocks.BREACHER.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RootBinderBlockEntity>> ROOT_BINDER =
            REGISTER.register("root_binder", () -> new BlockEntityType<>(
                    RootBinderBlockEntity::new, Set.of(dev.foldedhorizons.core.setup.ModBlocks.ROOT_BINDER.get()), null));
}
