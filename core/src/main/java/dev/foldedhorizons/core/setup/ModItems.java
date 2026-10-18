package dev.foldedhorizons.core.setup;

import dev.foldedhorizons.core.item.TempKeyItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DeferredRegister for the mod's items. Only the tiered Temp Key for now;
 * additional items (config cards, linkers, ...) arrive with later passes.
 */
public final class ModItems {

    public static final DeferredRegister.Items REGISTER =
            DeferredRegister.createItems("foldedhorizons");

    private ModItems() {}

    public static final DeferredHolder<Item, TempKeyItem> TEMP_KEY_T1 =
            REGISTER.register("temp_key_t1",
                    () -> new TempKeyItem(new Item.Properties().stacksTo(1), 1));

    public static final DeferredHolder<Item, TempKeyItem> TEMP_KEY_T2 =
            REGISTER.register("temp_key_t2",
                    () -> new TempKeyItem(new Item.Properties().stacksTo(1), 2));

    public static final DeferredHolder<Item, TempKeyItem> TEMP_KEY_T3 =
            REGISTER.register("temp_key_t3",
                    () -> new TempKeyItem(new Item.Properties().stacksTo(1), 3));
}
