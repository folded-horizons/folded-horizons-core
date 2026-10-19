package dev.foldedhorizons.core.dimension;

import net.minecraft.resources.ResourceLocation;

/**
 * Structure template ids for the pockets that ship a dedicated template.
 *
 * <p>{@link #METEORITE} is the dimension-gen multiblock's one dedicated
 * target (build-order 8): a small demo meteorite pocket built from
 * placeholder core-owned stone so core stays neutral of AE2. The
 * foldedhorizons-ae2 module (or a plain datapack) overrides the template
 * with the real skystone-crust meteorite — the four Inscriber Presses and
 * the budding certus patch — under the same id.</p>
 */
public final class PocketTemplates {

    /** Demo meteorite pocket for the consumed-on-fire dimension-gen multiblock. */
    public static final ResourceLocation METEORITE =
            ResourceLocation.fromNamespaceAndPath("foldedhorizons", "meteorite_pocket");

    private PocketTemplates() {}
}
