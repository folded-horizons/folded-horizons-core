package dev.foldedhorizons.core.dimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import static dev.foldedhorizons.core.dimension.PocketInstance.OwnerKind;

/**
 * Server-persistent registry of all pocket instances: every root cell,
 * repeatable annex, unstable cell and special pocket in every dimension
 * lives here — one instance on the <strong>overworld's</strong>
 * {@code DimensionDataStorage} (id {@code "foldedhorizons_pockets"}) so it
 * survives restarts without runtime dimension generation.
 *
 * <p>SNBT shape (see {@code docs/companion-mod/pocket-system.md}):</p>
 * <pre>
 * cells                dim id -> cellKey "x,z" -> PocketInstance tag
 * by_owner             uuid string -> list of cellKeys
 * cursor_by_dim        dimension id -> next_free for that dimension's grid
 * team_tier_cursor     "teamUuid|dimId" -> cellKey (per-team unstable offsets)
 * pending_conversions  cellKey -> { at_expiry, action, actor, target }
 * </pre>
 *
 * <p>The spiral cursor {@code next_free} is tracked per dimension
 * ({@code cursor_by_dim}); the lobby cell {@code "0,0"} is reserved and
 * never allocated. {@code by_owner} is the claim-authority index (an owner
 * holds one ROOT + many REPEATABLE + one SPECIAL per body); the registry
 * itself stays claim-agnostic — FTB Chunks registration is the
 * materialization path's job, re-registration the Root Binder's.</p>
 */
public class PocketRegistry extends SavedData {

    /** SavedData id on the overworld's DimensionDataStorage. */
    public static final String DATA_ID = "foldedhorizons_pockets";

    /** Reserved lobby cell key; allocation skips it. */
    private static final String LOBBY_CELL = PocketGrid.LOBBY_CELL;

    private final Map<ResourceKey<Level>, Map<String, PocketInstance>> cells = new HashMap<>();
    private final Map<UUID, List<String>> byOwner = new HashMap<>();
    private final Map<ResourceKey<Level>, Integer> cursorByDim = new HashMap<>();
    /** "teamUuid|dimId" -> cellKey of that team's most recent unstable cell. */
    private final Map<String, String> teamTierCursor = new HashMap<>();
    private final Map<String, PendingConversion> pendingConversions = new HashMap<>();

    public PocketRegistry() {}

    // ------------------------------------------------------------------
    // Pending conversion (Root Binder grace-window queue)
    // ------------------------------------------------------------------

    /** Queued root-space conversion; applied when the grace window expires. */
    public enum ConversionAction {
        MERGE,
        PURGE,
        SPLIT
    }

    /**
     * One queued conversion: at server tick {@code atExpiry} the binder
     * applies {@code action} to {@code target}'s space, on {@code actor}'s
     * request. Keyed by the target cellKey in {@code pending_conversions}.
     */
    public record PendingConversion(long atExpiry, ConversionAction action,
            UUID actor, UUID target) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("at_expiry", atExpiry);
            tag.putString("action", action.name());
            tag.putUUID("actor", actor);
            tag.putUUID("target", target);
            return tag;
        }

        static PendingConversion load(CompoundTag tag) {
            ConversionAction action;
            try {
                action = ConversionAction.valueOf(tag.getString("action"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (!tag.hasUUID("actor") || !tag.hasUUID("target")) {
                return null;
            }
            return new PendingConversion(tag.getLong("at_expiry"), action,
                    tag.getUUID("actor"), tag.getUUID("target"));
        }
    }

    /** The queued conversion for a cellKey, or null. Read-only accessor. */
    public PendingConversion pendingConversionFor(String cellKey) {
        return pendingConversions.get(cellKey);
    }

    /** All pending conversions, read-only view (breacher refusal checks). */
    public Map<String, PendingConversion> pendingConversionsView() {
        return Collections.unmodifiableMap(pendingConversions);
    }

    /** Remove a pending conversion (actor cancel before expiry). */
    public void removePendingConversion(String cellKey) {
        if (pendingConversions.remove(cellKey) != null) {
            setDirty();
        }
    }

    /**
     * Queue a conversion for the grace window: the Root Binder's ticker
     * applies it once the server tick passes expiry. Re-queueing the same
     * cell replaces the pending entry (a re-fired binder restarts the
     * window rather than queueing a second conversion).
     */
    public void addPendingConversion(String cellKey, long currentTick,
            ConversionAction action, UUID actor, UUID target, int graceWindowTicks) {
        pendingConversions.put(cellKey, new PendingConversion(
                currentTick + graceWindowTicks, action, actor, target));
        setDirty();
    }

    /**
     * Remove and return every pending conversion whose grace window has
     * expired at {@code currentTick}. The binder's ticker calls this and
     * applies each action; empty list when nothing is due.
     */
    public List<Map.Entry<String, PendingConversion>> expiredConversions(long currentTick) {
        List<Map.Entry<String, PendingConversion>> expired = new ArrayList<>();
        for (Map.Entry<String, PendingConversion> e : pendingConversions.entrySet()) {
            if (e.getValue().atExpiry() <= currentTick) {
                expired.add(e);
            }
        }
        if (!expired.isEmpty()) {
            expired.forEach(e -> pendingConversions.remove(e.getKey()));
            setDirty();
        }
        return expired;
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /** All pockets in one dimension, keyed "x,z". Read-only view. */
    public Map<String, PocketInstance> cellsIn(ResourceKey<Level> dimension) {
        return Collections.unmodifiableMap(cells.computeIfAbsent(dimension, k -> new HashMap<>()));
    }

    /** The pocket occupying a cell, or null. */
    public PocketInstance get(ResourceKey<Level> dimension, String cellKey) {
        Map<String, PocketInstance> dim = cells.get(dimension);
        return dim == null ? null : dim.get(cellKey);
    }

    /** Every cellKey the owner holds across all dimensions. Read-only view. */
    public List<String> ownedCells(UUID owner) {
        return Collections.unmodifiableList(byOwner.getOrDefault(owner, List.of()));
    }

    /** All owners with at least one cell. Read-only view. */
    public Set<UUID> owners() {
        return Collections.unmodifiableSet(byOwner.keySet());
    }

    /** A team's most recent unstable cell in a tier dimension, or null. */
    public String teamTierCursor(UUID teamUuid, ResourceKey<Level> dimension) {
        return teamTierCursor.get(teamTierKey(teamUuid, dimension));
    }

    /**
     * Every materialized UNSTABLE cell in {@code dimension}, keyed "x,z".
     * The collapse watcher iterates this per tick; the map is a live view of
     * the registry, so entries deleted mid-iteration are seen by later ticks.
     */
    public Map<String, PocketInstance> unstableCells(ResourceKey<Level> dimension) {
        Map<String, PocketInstance> result = new HashMap<>();
        Map<String, PocketInstance> dim = cells.get(dimension);
        if (dim == null) {
            return result;
        }
        for (Map.Entry<String, PocketInstance> e : dim.entrySet()) {
            if (e.getValue().type() == PocketType.UNSTABLE && e.getValue().materialized()) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /**
     * Mark {@code cellKey} as "dying": the collapse sweep is starting. The
     * flag persists (it rides the saved instance) so a crash mid-sweep is
     * repaired on restart by re-sweeping dying cells.
     */
    public PocketInstance markDying(ResourceKey<Level> dimension, String cellKey) {
        PocketInstance instance = get(dimension, cellKey);
        if (instance == null || instance.dying()) {
            return instance;
        }
        PocketInstance dying = instance.asDying();
        put(dimension, dying);
        return dying;
    }

    // ------------------------------------------------------------------
    // Allocation
    // ------------------------------------------------------------------

    /**
     * Allocate a new pocket cell for {@code owner} in {@code dimension} on
     * the spiral: advance the dimension's persisted cursor, skipping the
     * lobby and occupied cells. The instance is created unmaterialized with
     * an empty return point; materialization flips the flag via
     * {@link #markMaterialized}.
     *
     * @param maxScan guard against a pathological full grid: the scan may
     *                consume at most this many path positions per call
     * @return the allocated instance, or null if the scan window found no
     *         free cell (grid exhausted)
     */
    public PocketInstance allocate(ResourceKey<Level> dimension, UUID owner,
            PocketInstance.OwnerKind ownerKind, PocketType type, String body, int maxScan) {
        Map<String, PocketInstance> dimCells = cells.computeIfAbsent(dimension, k -> new HashMap<>());
        int start = cursorOrZero(dimension);
        List<PocketGrid.Cell> path = PocketGrid.spiralPath(start + maxScan);
        for (int i = start; i < path.size(); i++) {
            String key = path.get(i).key();
            if (LOBBY_CELL.equals(key) || dimCells.containsKey(key)) {
                continue;
            }
            cursorByDim.put(dimension, i + 1);
            PocketInstance instance = new PocketInstance(type, dimension, key, owner,
                    ownerKind, false, body, type == PocketType.UNSTABLE ? 0 : -1,
                    null, null, System.currentTimeMillis());
            dimCells.put(key, instance);
            registerOwner(owner, key);
            setDirty();
            return instance;
        }
        return null;
    }

    /**
     * Per-team unstable allocation: a team's first breach in a tier
     * dimension starts at the dimension's shared cursor; every subsequent
     * breach jumps {@code teamsPerRing} cells further out — different teams
     * strip different pockets without colliding (the per-team offset rule).
     * The shared cursor also advances past each allocation so unowned
     * transient cells are consumed in spiral order.
     *
     * @param teamsPerRing config {@code teams_per_ring}, default 8
     * @param maxScan      per-call scan guard, as in {@link #allocate}
     * @return the allocated instance, or null if no free cell was found
     */
    public PocketInstance allocateUnstable(ResourceKey<Level> dimension, UUID teamUuid,
            int teamsPerRing, int maxScan) {
        String cursorKey = teamTierKey(teamUuid, dimension);
        int start = cursorOrZero(dimension);
        String previous = teamTierCursor.get(cursorKey);
        if (previous != null) {
            PocketGrid.Cell prevCell = PocketGrid.parseKey(previous);
            if (prevCell != null) {
                int prevIndex = pathIndexOf(prevCell);
                if (prevIndex >= 0) {
                    start = Math.max(start, prevIndex + teamsPerRing);
                }
            }
        }
        Map<String, PocketInstance> dimCells = cells.computeIfAbsent(dimension, k -> new HashMap<>());
        List<PocketGrid.Cell> path = PocketGrid.spiralPath(start + maxScan);
        for (int i = start; i < path.size(); i++) {
            String key = path.get(i).key();
            if (LOBBY_CELL.equals(key) || dimCells.containsKey(key)) {
                continue;
            }
            cursorByDim.put(dimension, i + 1);
            teamTierCursor.put(cursorKey, key);
            PocketInstance instance = new PocketInstance(PocketType.UNSTABLE, dimension,
                    key, teamUuid, PocketInstance.OwnerKind.TEAM, false, "", 0, null, null,
                    System.currentTimeMillis());
            dimCells.put(key, instance);
            registerOwner(teamUuid, key);
            setDirty();
            return instance;
        }
        return null;
    }

    /**
     * SPECIAL "address not door" rule: return the owner's existing pocket
     * for {@code body} if one exists; otherwise allocate a new SPECIAL cell.
     * A second copy of e.g. the Moon multiblock binds as a second link into
     * the same pocket instead of spawning a duplicate.
     */
    public PocketInstance findOrAllocate(ResourceKey<Level> dimension, UUID owner,
            PocketInstance.OwnerKind ownerKind, String body, int maxScan) {
        Map<String, PocketInstance> dimCells = cells.get(dimension);
        if (dimCells != null) {
            for (PocketInstance instance : dimCells.values()) {
                if (instance.type() == PocketType.SPECIAL
                        && instance.body().equals(body)
                        && instance.owner().equals(owner)
                        && instance.ownerKind() == ownerKind) {
                    return instance;
                }
            }
        }
        return allocate(dimension, owner, ownerKind, PocketType.SPECIAL, body, maxScan);
    }

    /**
     * Spiral path index of a cell, for per-team cursor math; -1 for the
     * lobby (never on the path). Inverse of the {@link PocketGrid#spiralPath}
     * walk order — validated against the path in {@link PocketGridCheck}.
     */
    static int pathIndexOf(PocketGrid.Cell cell) {
        int ring = Math.max(Math.abs(cell.x()), Math.abs(cell.z()));
        if (ring == 0) {
            return -1;
        }
        // Ring r starts after rings 1..r-1: 8*(1+...+(r-1)) = 4r(r-1) cells.
        // Each edge holds 2r cells, walked right/top/left/bottom with corner
        // ownership that never overlaps (see PocketGrid.spiralPath):
        //   right  x == r,  z in [-(r-1), r]
        //   top    z == r,  x in [-r, r-1]
        //   left   x == -r, z in [-r, r-1]
        //   bottom z == -r, x in [-r+1, r]
        // Corner (r,-r) belongs to the bottom edge, so the right/left tests
        // exclude z == -r.
        int ringStart = 4 * ring * (ring - 1);
        int r = ring;
        int x = cell.x();
        int z = cell.z();
        if (x == r && z != -r) {
            return ringStart + (z + r - 1);
        } else if (z == r) {
            return ringStart + 2 * r + (r - 1 - x);
        } else if (x == -r && z != -r) {
            return ringStart + 4 * r + (r - 1 - z);
        } else {
            return ringStart + 6 * r + (x + r - 1);
        }
    }

    private static String teamTierKey(UUID teamUuid, ResourceKey<Level> dimension) {
        return teamUuid + "|" + dimension.location();
    }

    private int cursorOrZero(ResourceKey<Level> dimension) {
        Integer cursor = cursorByDim.get(dimension);
        return cursor == null ? 0 : cursor;
    }

    // ------------------------------------------------------------------
    // Mutation
    // ------------------------------------------------------------------

    /**
     * Mark a pocket materialized (structure template placed at the cell).
     * The caller registers the FTB claim around this; the registry only
     * flips the flag. Returns the updated instance, or null if the cell
     * holds no pocket.
     */
    public PocketInstance markMaterialized(ResourceKey<Level> dimension, String cellKey) {
        PocketInstance instance = get(dimension, cellKey);
        if (instance == null || instance.materialized()) {
            return instance;
        }
        PocketInstance updated = instance.asMaterialized();
        put(dimension, updated);
        return updated;
    }

    /** Insert or replace a pocket instance (mutations go through here). */
    public void put(ResourceKey<Level> dimension, PocketInstance instance) {
        cells.computeIfAbsent(dimension, k -> new HashMap<>()).put(instance.cellKey(), instance);
        setDirty();
    }

    /**
     * Delete a pocket (unstable collapse, purge): drops the cell and its
     * by_owner entry. Callers sweep the interior region themselves; the
     * cell is never re-entered because the cursor has moved past it.
     */
    public void remove(ResourceKey<Level> dimension, String cellKey) {
        Map<String, PocketInstance> dimCells = cells.get(dimension);
        PocketInstance instance = dimCells == null ? null : dimCells.remove(cellKey);
        if (instance != null) {
            unregisterOwner(instance.owner(), cellKey);
            setDirty();
        }
    }

    // ------------------------------------------------------------------
    // by_owner index authority
    // ------------------------------------------------------------------

    /** Register a cell under its owner's by_owner list (idempotent). */
    public void registerOwner(UUID owner, String cellKey) {
        List<String> list = byOwner.computeIfAbsent(owner, k -> new ArrayList<>());
        if (!list.contains(cellKey)) {
            list.add(cellKey);
            setDirty();
        }
    }

    /** Drop a cell from its owner's by_owner list (idempotent). */
    public void unregisterOwner(UUID owner, String cellKey) {
        List<String> list = byOwner.get(owner);
        if (list != null) {
            list.remove(cellKey);
            if (list.isEmpty()) {
                byOwner.remove(owner);
            }
            setDirty();
        }
    }

    /**
     * Re-index after an ownership transfer: moves cellKey from the old
     * owner's list to the new owner's. Claim re-registration (FTB) is the
     * caller's job; this maintains only the registry's authority index.
     */
    public void transferOwnership(UUID oldOwner, UUID newOwner, String cellKey) {
        unregisterOwner(oldOwner, cellKey);
        registerOwner(newOwner, cellKey);
    }

    // ------------------------------------------------------------------
    // SavedData plumbing
    // ------------------------------------------------------------------

    /**
     * Get-or-create the registry on the overworld's data storage. Every
     * dimension's pockets share this one instance; never attach per-dimension.
     */
    public static PocketRegistry getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(PocketRegistry::new, PocketRegistry::load),
                        DATA_ID);
    }

    /** Convenience overload for callers already holding the overworld level. */
    public static PocketRegistry getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage()
                .computeIfAbsent(new Factory<>(PocketRegistry::new, PocketRegistry::load),
                        DATA_ID);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag cellsTag = new CompoundTag();
        for (Map.Entry<ResourceKey<Level>, Map<String, PocketInstance>> dimEntry
                : cells.entrySet()) {
            CompoundTag dimTag = new CompoundTag();
            for (Map.Entry<String, PocketInstance> cellEntry : dimEntry.getValue().entrySet()) {
                dimTag.put(cellEntry.getKey(), cellEntry.getValue().save(new CompoundTag()));
            }
            cellsTag.put(dimEntry.getKey().location().toString(), dimTag);
        }
        tag.put("cells", cellsTag);

        CompoundTag byOwnerTag = new CompoundTag();
        for (Map.Entry<UUID, List<String>> e : byOwner.entrySet()) {
            ListTag list = new ListTag();
            e.getValue().forEach(cellKey -> list.add(StringTag.valueOf(cellKey)));
            byOwnerTag.put(e.getKey().toString(), list);
        }
        tag.put("by_owner", byOwnerTag);

        CompoundTag cursorsTag = new CompoundTag();
        for (Map.Entry<ResourceKey<Level>, Integer> e : cursorByDim.entrySet()) {
            cursorsTag.putInt(e.getKey().location().toString(), e.getValue());
        }
        tag.put("cursor_by_dim", cursorsTag);

        CompoundTag teamCursorsTag = new CompoundTag();
        teamTierCursor.forEach(teamCursorsTag::putString);
        tag.put("team_tier_cursor", teamCursorsTag);

        CompoundTag pendingTag = new CompoundTag();
        for (Map.Entry<String, PendingConversion> e : pendingConversions.entrySet()) {
            pendingTag.put(e.getKey(), e.getValue().save());
        }
        tag.put("pending_conversions", pendingTag);
        return tag;
    }

    /** Deserialize; malformed entries are dropped, never fatal. */
    public static PocketRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        PocketRegistry registry = new PocketRegistry();

        CompoundTag cellsTag = tag.getCompound("cells");
        for (String dimId : cellsTag.getAllKeys()) {
            ResourceKey<Level> dimension;
            try {
                dimension = ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(dimId));
            } catch (Exception e) {
                continue; // malformed dimension id
            }
            CompoundTag dimTag = cellsTag.getCompound(dimId);
            Map<String, PocketInstance> dimCells = registry.cells
                    .computeIfAbsent(dimension, k -> new HashMap<>());
            for (String cellKey : dimTag.getAllKeys()) {
                PocketInstance instance = PocketInstance.load(dimTag.getCompound(cellKey));
                if (instance != null) {
                    // Rebind the cellKey from the map key, not the payload.
                    dimCells.put(cellKey, instance);
                }
            }
        }

        CompoundTag byOwnerTag = tag.getCompound("by_owner");
        for (String ownerStr : byOwnerTag.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(ownerStr);
                ListTag list = byOwnerTag.getList(ownerStr, Tag.TAG_STRING);
                List<String> cellKeys = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    cellKeys.add(list.getString(i));
                }
                registry.byOwner.put(owner, cellKeys);
            } catch (IllegalArgumentException e) {
                // skip malformed owner key
            }
        }

        CompoundTag cursorsTag = tag.getCompound("cursor_by_dim");
        for (String dimId : cursorsTag.getAllKeys()) {
            try {
                registry.cursorByDim.put(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(dimId)), cursorsTag.getInt(dimId));
            } catch (Exception e) {
                // skip malformed dim key
            }
        }

        CompoundTag teamCursorsTag = tag.getCompound("team_tier_cursor");
        for (String key : teamCursorsTag.getAllKeys()) {
            registry.teamTierCursor.put(key, teamCursorsTag.getString(key));
        }

        CompoundTag pendingTag = tag.getCompound("pending_conversions");
        for (String cellKey : pendingTag.getAllKeys()) {
            PendingConversion pending = PendingConversion.load(pendingTag.getCompound(cellKey));
            if (pending != null) {
                registry.pendingConversions.put(cellKey, pending);
            }
        }
        return registry;
    }
}
