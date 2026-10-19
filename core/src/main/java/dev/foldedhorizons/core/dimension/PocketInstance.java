package dev.foldedhorizons.core.dimension;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One live (or historically recorded) pocket: an address in the grid
 * architecture. Immutable snapshot — mutations (ownership transfer via the
 * Root Binder, exhaustion of an unstable pocket, expansion of a root space)
 * produce a new record and are written through {@link PocketRegistry}.
 *
 * <p>SNBT encoding lives in {@link #save(CompoundTag)} /
 * {@link #load(CompoundTag)} and matches the registry shape in
 * {@code docs/companion-mod/pocket-system.md}: {@code dim}, {@code type},
 * {@code owner} msb/lsb, {@code owner_kind}, {@code materialized},
 * {@code body}, {@code exhaust_index}, {@code return_dim} + packed
 * {@code return_pos}, {@code created}. The cellKey itself is the registry's
 * map key and is carried on the record for convenience.</p>
 *
 * @param type            which pocket classification this instance belongs to
 * @param dimension       the static dimension hosting the grid (void_space, exploit_t1, ...)
 * @param cellKey         "x,z" grid key; cell centers sit 2048 blocks apart
 * @param owner           player or team UUID — the registry key for ownership
 * @param ownerKind       whether {@code owner} is a player UUID or an FTB team UUID
 * @param materialized    whether the structure template has been placed yet
 * @param body            SPECIAL body id ("moon", ...); empty string otherwise
 * @param exhaustIndex    UNSTABLE exhaustion counter; -1 for other types
 * @param returnPoint     the source position the breach frame teleports back
 *                        to; null while unset
 * @param returnDimension the dimension {@code returnPoint} sits in (usually
 *                        the overworld); null exactly when
 *                        {@code returnPoint} is null
 * @param createdAt       epoch millis when the pocket was allocated
 * @param dying           crash-safety flag: the UNSTABLE collapse sweep was
 *                        marked but not finished (re-swept on server start)
 * @param expansionStage  index into the expansion ladder this pocket last
 *                        reached; 0 = fresh (base size), N = N expansions
 *                        applied (see walls-expansion.md "Root space
 *                        expansion")
 */
public record PocketInstance(
        PocketType type,
        ResourceKey<Level> dimension,
        String cellKey,
        UUID owner,
        OwnerKind ownerKind,
        boolean materialized,
        String body,
        int exhaustIndex,
        BlockPos returnPoint,
        ResourceKey<Level> returnDimension,
        long createdAt,
        boolean dying,
        int expansionStage
) {

    /** Whether the owner UUID denotes a player or an FTB team. */
    public enum OwnerKind {
        PLAYER,
        TEAM;

        private static final OwnerKind[] VALUES = values();

        /** Name stored in SNBT; tolerant parse defaults to {@link #PLAYER}. */
        static OwnerKind byName(String name) {
            for (OwnerKind kind : VALUES) {
                if (kind.name().equals(name)) {
                    return kind;
                }
            }
            return PLAYER;
        }
    }

    /** SNBT key for the hosting dimension id. */
    static final String KEY_DIM = "dim";
    /** SNBT key for the {@link PocketType} name. */
    static final String KEY_TYPE = "type";
    /** SNBT keys for the owner UUID's most/least significant bits. */
    static final String KEY_OWNER_MOST = "owner_msb";
    static final String KEY_OWNER_LEAST = "owner_lsb";
    /** SNBT key for the {@link OwnerKind} name. */
    static final String KEY_OWNER_KIND = "owner_kind";
    /** SNBT key for the materialized flag. */
    static final String KEY_MATERIALIZED = "materialized";
    /** SNBT key for the SPECIAL body id. */
    static final String KEY_BODY = "body";
    /** SNBT key for the UNSTABLE exhaustion counter. */
    static final String KEY_EXHAUST_INDEX = "exhaust_index";
    /** SNBT keys for the return point: dimension id + packed position. */
    static final String KEY_RETURN_DIM = "return_dim";
    static final String KEY_RETURN_POS = "return_pos";
    /** SNBT key for the allocation timestamp (epoch millis). */
    static final String KEY_CREATED = "created";
    /** SNBT key for the crash-safety dying flag (UNSTABLE collapse sweep in progress). */
    static final String KEY_DYING = "dying";
    /** SNBT key for the expansion-ladder stage index (walls-expansion.md). */
    static final String KEY_EXPANSION_STAGE = "expansion_stage";

    public PocketInstance {
        if (body == null) {
            body = "";
        }
        if (returnPoint == null) {
            returnDimension = null;
        }
    }

    /**
     * Convenience constructor for records without a dying flag (defaults
     * to false) — the canonical factory used by allocation code paths.
     */
    public PocketInstance(PocketType type, ResourceKey<Level> dimension, String cellKey,
            UUID owner, OwnerKind ownerKind, boolean materialized, String body,
            int exhaustIndex, BlockPos returnPoint, ResourceKey<Level> returnDimension,
            long createdAt) {
        this(type, dimension, cellKey, owner, ownerKind, materialized, body,
                exhaustIndex, returnPoint, returnDimension, createdAt, false, 0);
    }

    /**
     * Whether this pocket carries a body id (SPECIAL only; UNSTABLE and
     * root-family pockets have an empty body by definition).
     */
    public boolean hasBody() {
        return !body.isEmpty();
    }

    /** Crash-safety flag: the collapse sweep was marked but not finished. */
    public boolean dying() {
        return dying;
    }

    /** Copy with {@code dying = true} — the collapse sweep is about to run. */
    public PocketInstance asDying() {
        return new PocketInstance(type, dimension, cellKey, owner, ownerKind,
                materialized, body, exhaustIndex, returnPoint, returnDimension,
                createdAt, true, expansionStage);
    }

    /** Copy with the next expansion stage reached. */
    public PocketInstance withExpansionStage(int newStage) {
        return new PocketInstance(type, dimension, cellKey, owner, ownerKind,
                materialized, body, exhaustIndex, returnPoint, returnDimension,
                createdAt, dying, newStage);
    }

    /**
     * Copy with {@code materialized = true} — the structure template was
     * placed at this cell.
     */
    public PocketInstance asMaterialized() {
        return new PocketInstance(type, dimension, cellKey, owner, ownerKind,
                true, body, exhaustIndex, returnPoint, returnDimension, createdAt,
                dying, expansionStage);
    }

    /**
     * Copy with a new owner (Root Binder ownership transfer); the registry
     * re-registers claims and the by_owner index around the result.
     */
    public PocketInstance withOwner(UUID newOwner, OwnerKind newKind) {
        return new PocketInstance(type, dimension, cellKey, newOwner, newKind,
                materialized, body, exhaustIndex, returnPoint, returnDimension,
                createdAt, dying, expansionStage);
    }

    /**
     * Copy with a new return point (the breach frame moved).
     */
    public PocketInstance withReturnPoint(BlockPos newReturn, ResourceKey<Level> newReturnDim) {
        return new PocketInstance(type, dimension, cellKey, owner, ownerKind,
                materialized, body, exhaustIndex, newReturn, newReturnDim,
                createdAt, dying, expansionStage);
    }

    /**
     * Copy with an advanced UNSTABLE exhaustion counter.
     */
    public PocketInstance withExhaustIndex(int newExhaustIndex) {
        return new PocketInstance(type, dimension, cellKey, owner, ownerKind,
                materialized, body, newExhaustIndex, returnPoint, returnDimension,
                createdAt, dying, expansionStage);
    }

    /**
     * Serialize to the registry's per-cell compound tag. The cellKey is not
     * written here — it is the tag's map key in the registry. The return
     * point is stored as {@code return_dim} + packed {@code return_pos}
     * (a BlockPos carries no dimension, so the dimension is a separate field).
     */
    public CompoundTag save(CompoundTag tag) {
        tag.putString(KEY_DIM, dimension.location().toString());
        tag.putString(KEY_TYPE, type.name());
        tag.putLong(KEY_OWNER_MOST, owner.getMostSignificantBits());
        tag.putLong(KEY_OWNER_LEAST, owner.getLeastSignificantBits());
        tag.putString(KEY_OWNER_KIND, ownerKind.name());
        tag.putBoolean(KEY_MATERIALIZED, materialized);
        tag.putString(KEY_BODY, body);
        tag.putInt(KEY_EXHAUST_INDEX, exhaustIndex);
        if (returnPoint != null) {
            tag.putString(KEY_RETURN_DIM, returnDimension.location().toString());
            tag.putLong(KEY_RETURN_POS, returnPoint.asLong());
        }
        tag.putLong(KEY_CREATED, createdAt);
        if (dying) {
            tag.putBoolean(KEY_DYING, true);
        }
        if (expansionStage != 0) {
            tag.putInt(KEY_EXPANSION_STAGE, expansionStage);
        }
        return tag;
    }

    /**
     * Deserialize from the registry's per-cell compound tag. Returns null if
     * required fields are missing or the type name is unknown (corrupt or
     * hand-edited data) so the registry drops the entry and rebuilds from
     * gameplay instead of crashing the data load.
     */
    public static PocketInstance load(CompoundTag tag) {
        if (!tag.contains(KEY_DIM) || !tag.contains(KEY_TYPE)
                || !tag.contains(KEY_OWNER_MOST) || !tag.contains(KEY_OWNER_LEAST)) {
            return null;
        }
        PocketType type;
        try {
            type = PocketType.valueOf(tag.getString(KEY_TYPE));
        } catch (IllegalArgumentException e) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString(KEY_DIM)));
        UUID owner = new UUID(tag.getLong(KEY_OWNER_MOST), tag.getLong(KEY_OWNER_LEAST));
        OwnerKind ownerKind = OwnerKind.byName(tag.getString(KEY_OWNER_KIND));
        boolean materialized = tag.getBoolean(KEY_MATERIALIZED);
        String body = tag.contains(KEY_BODY) ? tag.getString(KEY_BODY) : "";
        int exhaustIndex = tag.contains(KEY_EXHAUST_INDEX) ? tag.getInt(KEY_EXHAUST_INDEX) : -1;
        BlockPos returnPoint = null;
        ResourceKey<Level> returnDimension = null;
        if (tag.contains(KEY_RETURN_DIM) && tag.contains(KEY_RETURN_POS)) {
            returnDimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString(KEY_RETURN_DIM)));
            // Packed x/y/z long from BlockPos.asLong(); unpacked by BlockPos.of.
            returnPoint = BlockPos.of(tag.getLong(KEY_RETURN_POS));
        }
        long createdAt = tag.contains(KEY_CREATED) ? tag.getLong(KEY_CREATED) : 0L;
        boolean dying = tag.getBoolean(KEY_DYING);
        int expansionStage = tag.contains(KEY_EXPANSION_STAGE) ? tag.getInt(KEY_EXPANSION_STAGE) : 0;
        // cellKey is rebound from the registry's map key on load.
        return new PocketInstance(type, dimension, "", owner, ownerKind,
                materialized, body, exhaustIndex, returnPoint, returnDimension,
                createdAt, dying, expansionStage);
    }
}
