package dev.foldedhorizons.core.machine;

import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.identity.TeamIdentityProvider;
import dev.foldedhorizons.core.setup.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Root Binder block entity (core step 6) — the ownership machine.
 *
 * <p>Operates on the inserting player's identity
 * ({@code TeamIdentityProviders.get().identityFor(player)}). GUI decision
 * (documented): menu-less in core, like the breacher — right-click cycles
 * MERGE → PURGE → SPLIT, sneak-right-click confirms the shown operation
 * and schedules it in {@link PocketRegistry} with the 30-second grace
 * window; action-bar messages carry the countdown and refusals. The GT
 * module's singleblock replaces this with the real GUI.</p>
 *
 * <p>Operations (docs/design/machines.md):
 * <ul>
 *   <li><b>MERGE</b> (personal → team): only when the team has no root;
 *       the player's ROOT re-keys to the team UUID, interior preserved.</li>
 *   <li><b>PURGE</b> (personal → team's space): team already has a root —
 *       the player's personal cell is demolished, their ROOT address is
 *       re-pointed to the team's space (registry keeps one ROOT).</li>
 *   <li><b>SPLIT</b> (team → personal): a member leaving reclaims a fresh
 *       personal ROOT; the team root stays with the team.</li>
 * </ul></p>
 *
 * <p>TODO: config {@code pocket.graceWindowSeconds} (default 30) is
 * hardcoded to 30 s here — ModConfig is not yet registered on the mod
 * container, and ModConfigSpec.get() is not callable pre-registration
 * (see Materializer.DEFAULT_INTERIOR_SIZE for the same pattern).</p>
 */
public class RootBinderBlockEntity extends BlockEntity {

    /** TODO: config pocket.graceWindowSeconds; hardcoded until registration. */
    public static final int GRACE_WINDOW_SECONDS = 30;

    /** The binder's selected operation, cycled by right-click. */
    public enum BinderOperation {
        MERGE,
        PURGE,
        SPLIT;

        BinderOperation next() {
            return switch (this) {
                case MERGE -> PURGE;
                case PURGE -> SPLIT;
                case SPLIT -> MERGE;
            };
        }

        /** Chat-friendly name for action-bar messages. */
        String label() {
            return switch (this) {
                case MERGE -> "Merge (personal → team)";
                case PURGE -> "Purge (personal → team's space)";
                case SPLIT -> "Split (team → personal)";
            };
        }
    }

    private BinderOperation operation = BinderOperation.MERGE;

    public RootBinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROOT_BINDER.get(), pos, state);
    }

    public BinderOperation operation() {
        return operation;
    }

    /** Cycle the operation (server side only) and report the new one. */
    public void cycleOperation(ServerPlayer player) {
        operation = operation.next();
        setChanged();
        PocketOps.say(player, "Operation: " + operation.label());
    }

    /**
     * Validate and schedule the shown operation for {@code player} (the
     * inserting player). No registry mutation happens here — the action is
     * queued with the grace window and applied by the ConversionTicker at
     * expiry.
     */
    public boolean confirmOperation(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return false;
        }
        MinecraftServer server = serverLevel.getServer();
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        long tick = serverLevel.getGameTime();
        TeamIdentityProvider.GroupIdentity identity =
                TeamIdentityProvider.get().identityFor(player);

        return switch (operation) {
            case MERGE -> confirmMerge(registry, player, identity, tick);
            case PURGE -> confirmPurge(registry, player, identity, tick);
            case SPLIT -> confirmSplit(registry, player, identity, tick);
        };
    }

    /**
     * MERGE: requires the inserting player to be a team member and to hold
     * a personal ROOT; the team must have no root yet.
     */
    private boolean confirmMerge(PocketRegistry registry, ServerPlayer player,
            TeamIdentityProvider.GroupIdentity identity, long tick) {
        if (identity.kind() != TeamIdentityProvider.GroupIdentity.Kind.TEAM) {
            return refuse(player, "You have no team to merge into.");
        }
        PocketInstance personal = PocketOps.findRoot(registry, player.getUUID());
        if (personal == null) {
            return refuse(player, "You have no personal space to merge.");
        }
        if (PocketOps.findRoot(registry, identity.id()) != null) {
            return refuse(player, "The team already has a root space — use Purge.");
        }
        return schedule(registry, player, personal, identity.id(), tick);
    }

    /**
     * PURGE: requires a team root to already exist; the player's personal
     * cell is demolished and their space re-addresses to the team's.
     */
    private boolean confirmPurge(PocketRegistry registry, ServerPlayer player,
            TeamIdentityProvider.GroupIdentity identity, long tick) {
        if (identity.kind() != TeamIdentityProvider.GroupIdentity.Kind.TEAM) {
            return refuse(player, "You have no team whose space to join.");
        }
        PocketInstance teamRoot = PocketOps.findRoot(registry, identity.id());
        if (teamRoot == null) {
            return refuse(player, "The team has no root space yet — use Merge.");
        }
        PocketInstance personal = PocketOps.findRoot(registry, player.getUUID());
        if (personal == null) {
            return refuse(player, "You have no personal space to purge.");
        }
        if (personal.cellKey().equals(teamRoot.cellKey())) {
            return refuse(player, "Your address already points at the team's space.");
        }
        return schedule(registry, player, personal, identity.id(), tick);
    }

    /**
     * SPLIT: a team member leaving reclaims a fresh personal ROOT; requires
     * team membership (the team root itself stays untouched).
     */
    private boolean confirmSplit(PocketRegistry registry, ServerPlayer player,
            TeamIdentityProvider.GroupIdentity identity, long tick) {
        if (identity.kind() != TeamIdentityProvider.GroupIdentity.Kind.TEAM) {
            return refuse(player, "You are not in a team — nothing to split.");
        }
        if (PocketOps.findRoot(registry, identity.id()) == null) {
            return refuse(player, "The team has no root space to split from.");
        }
        if (PocketOps.findRoot(registry, player.getUUID()) != null) {
            return refuse(player, "You already have a personal space.");
        }
        // Fresh personal cell is allocated at conversion time; the pending
        // entry targets the team root so the breacher refuses it meanwhile.
        PocketInstance teamRoot = PocketOps.findRoot(registry, identity.id());
        return schedule(registry, player, teamRoot, player.getUUID(), tick);
    }

    /**
     * Queue the operation: grace window on the target cell, actor =
     * inserting player, target = the owner the space moves to. The same
     * actor may cancel before expiry (no one else may).
     */
    private boolean schedule(PocketRegistry registry, ServerPlayer player,
            PocketInstance targetCell, UUID newOwner, long tick) {
        registry.addPendingConversion(targetCell.cellKey(), tick, action(),
                player.getUUID(), newOwner, GRACE_WINDOW_SECONDS * 20);
        PocketOps.say(player, operation.label() + " scheduled — "
                + GRACE_WINDOW_SECONDS + "s to cancel (right-click again to switch).");
        return true;
    }

    /** The enum action matching the currently selected operation. */
    PocketRegistry.ConversionAction action() {
        return switch (operation) {
            case MERGE -> PocketRegistry.ConversionAction.MERGE;
            case PURGE -> PocketRegistry.ConversionAction.PURGE;
            case SPLIT -> PocketRegistry.ConversionAction.SPLIT;
        };
    }

    private boolean refuse(ServerPlayer player, String message) {
        PocketOps.say(player, message);
        return false;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    private static final String KEY_OPERATION = "operation";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_OPERATION, operation.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String name = tag.getString(KEY_OPERATION);
        for (BinderOperation op : BinderOperation.values()) {
            if (op.name().equals(name)) {
                operation = op;
                break;
            }
        }
    }
}
