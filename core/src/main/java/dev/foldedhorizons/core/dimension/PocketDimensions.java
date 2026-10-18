package dev.foldedhorizons.core.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Resource keys for the mod's static dimensions. No dimension is created or
 * deleted at runtime — every pocket lives in one of these datapack-shipped
 * dimensions ({@code data/foldedhorizons/dimension/*.json}), so the mod only
 * ever holds keys and looks levels up on the server.
 */
public final class PocketDimensions {

    /** The shared void grid dimension hosting the lobby, root and repeatable cells. */
    public static final ResourceKey<Level> VOID_SPACE = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("foldedhorizons", "void_space"));

    private PocketDimensions() {}
}
