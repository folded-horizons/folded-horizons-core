package dev.foldedhorizons.core.setup;

import dev.foldedhorizons.core.item.ExpansionLeverItem;
import dev.foldedhorizons.core.item.TempKeyItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DeferredRegister for the mod's items. Tiered Temp Keys plus the
 * wall-chip drops (build-order 7): iron/copper chunks bootstrap a
 * steam-age GT chain, wood/stone scrap is the no-pickaxe filler from the
 * wall loot tables.
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

    // Wall-chip drops (see StarterWallBlock + loot_table/blocks/*.json).

    /** Iron chunk — wall_metal chip drop when a pickaxe is used. */
    public static final DeferredHolder<Item, Item> IRON_CHUNK =
            REGISTER.registerSimpleItem("iron_chunk");

    /** Copper chunk — wall_metal chip drop when a pickaxe is used. */
    public static final DeferredHolder<Item, Item> COPPER_CHUNK =
            REGISTER.registerSimpleItem("copper_chunk");

    /** Wood scrap — wall_wood chip filler (crafts into planks-ish loop). */
    public static final DeferredHolder<Item, Item> WOOD_SCRAP =
            REGISTER.registerSimpleItem("wood_scrap");

    /** Stone scrap — wall_metal chip filler when no pickaxe is used. */
    public static final DeferredHolder<Item, Item> STONE_SCRAP =
            REGISTER.registerSimpleItem("stone_scrap");

    /**
     * Expansion Lever — grows a root/repeatable pocket to the next rung of
     * the {@code pocket.expansionThresholds} ladder (build-order 11).
     */
    public static final DeferredHolder<Item, ExpansionLeverItem> EXPANSION_LEVER =
            REGISTER.register("expansion_lever",
                    () -> new ExpansionLeverItem(new Item.Properties().stacksTo(1)));
}
