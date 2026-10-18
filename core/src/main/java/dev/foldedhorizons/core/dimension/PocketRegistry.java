package dev.foldedhorizons.core.dimension;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-persistent registry of all pocket instances (root cells, repeatable
 * annexes, unstable pocket cursors, special pockets). Backs the design doc's
 * {@code root_registry} shape:
 *
 * <pre>
 * cells:   cellKey -> PocketInstance
 * nextFree spiral allocation cursor
 * byOwner: ownerUUID -> cellKey (one root per player AND per team)
 * </pre>
 *
 * Stored as {@link net.minecraft.world.level.saveddata.SavedData} on the
 * overworld's DimensionDataStorage so it survives restarts without runtime
 * dimension generation. Also drives chunk-claim (FTB Chunks) registration at
 * materialization and re-registration on Root Binder ownership transfer.
 */
public class PocketRegistry extends SavedData {

    private final Map<String, PocketInstance> cells = new HashMap<>();
    private final Map<UUID, String> byOwner = new HashMap<>();
    private int nextFree;

    // TODO: NBT codec (SavedData.Factory), spiral allocation helper,
    //       ownership conversion (merge / purge / split with the 30-second
    //       grace window), exhaustion tracking for UNSTABLE pockets, and
    //       FTB Chunks claim hooks.

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // TODO: serialize cells / byOwner / nextFree
        return tag;
    }

    public static PocketRegistry get(net.minecraft.server.level.ServerLevel level) {
        // TODO: level.getServer().overworld().getDataStorage().computeIfAbsent(...)
        return null;
    }
}
