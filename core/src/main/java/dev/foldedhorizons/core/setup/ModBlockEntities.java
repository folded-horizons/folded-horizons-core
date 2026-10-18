package dev.foldedhorizons.core.setup;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DeferredRegister for block entity types. Block entity classes are not
 * yet implemented — placeholders so registration order and naming are
 * locked down early.
 *
 * @see net.neoforged.neoforge.registries.DeferredRegister
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create("foldedhorizons",
                    net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE);

    private ModBlockEntities() {}

    // TODO: BreacherBlockEntity, RootBinderBlockEntity, TradePostBlockEntity.
    // DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> BREACHER = ...
}
