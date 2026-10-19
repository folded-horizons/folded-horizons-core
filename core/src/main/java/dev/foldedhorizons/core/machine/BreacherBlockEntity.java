package dev.foldedhorizons.core.machine;

import java.util.UUID;
import dev.foldedhorizons.core.dimension.Materializer;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketGrid;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.identity.TeamIdentityProvider;
import dev.foldedhorizons.core.setup.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Dimensional Breacher block entity (core step 5).
 *
 * <p>GUI decision (documented): the core breacher is a <strong>menu-less
 * block entity</strong>. Right-click cycling the destination (Personal →
 * Team → Lobby) plus action-bar feedback covers the core-alone flow; the
 * GT module replaces this machine with the GTCEu multiblock and its real
 * GUI, so a container menu here would be dead code for every shipped
 * pack. Server-side validation still runs exactly per
 * {@code docs/companion-mod/machines.md}: pending-conversion refusal,
 * materialization on demand, energy charge, then teleport.</p>
 *
 * <p>Energy: core is FE-based ({@code machines.md} says GT EU lives in the
 * GT module). Flat 100,000 FE for a root/lobby breach; unstable entry
 * (10,000,000 FE, config {@code pocket.breachEnergyCost}) arrives with
 * step 9's Temp Key slot — this BE carries the buffer for it. Returns
 * through a destination's breach frame are free (free return trip).</p>
 *
 * <p>Return point: breaching records the breacher's own position+dimension
 * as the pocket's return point ({@link PocketInstance#withReturnPoint}) —
 * the breacher remembers where you were; the destination's breach frame
 * (later pass) reads it back.</p>
 */
public class BreacherBlockEntity extends BlockEntity {

    /** Flat FE cost of one personal/team/lobby breach. */
    public static final int BREACH_COST_ROOT_OR_LOBBY = 100_000;
    /** FE cost of one unstable-pocket breach (Temp Key entry, step 9). */
    public static final int BREACH_COST_UNSTABLE = 10_000_000;

    /** Breacher energy buffer: two root breaches, sized for FE lines. */
    static final int BUFFER_CAPACITY = 1_000_000;
    static final int MAX_RECEIVE = 100_000;

    /** The selected destination, cycled by right-click. */
    public enum Destination {
        PERSONAL,
        TEAM,
        LOBBY;

        Destination next() {
            return switch (this) {
                case PERSONAL -> TEAM;
                case TEAM -> LOBBY;
                case LOBBY -> PERSONAL;
            };
        }

        /** Chat-friendly name for action-bar messages. */
        String label() {
            return switch (this) {
                case PERSONAL -> "Personal";
                case TEAM -> "Team";
                case LOBBY -> "Lobby";
            };
        }
    }

    private Destination destination = Destination.PERSONAL;
    private final EnergyStorage energy = new EnergyStorage(BUFFER_CAPACITY, MAX_RECEIVE);

    public BreacherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BREACHER.get(), pos, state);
    }

    public Destination destination() {
        return destination;
    }

    /** Cycle the destination (server side only) and report the new one. */
    public void cycleDestination(ServerPlayer player) {
        destination = destination.next();
        setChanged();
        PocketOps.say(player, "Destination: " + destination.label());
    }

    /** Exposed FE buffer (sided energy capability). */
    public IEnergyStorage energy() {
        return energy;
    }

    /**
     * Execute a breach to the currently selected destination for
     * {@code player}. Validates the full refusal chain before spending any
     * energy; returns false on any refusal (message shown to the player).
     */
    public boolean tryBreach(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return false;
        }
        MinecraftServer server = serverLevel.getServer();
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        long tick = serverLevel.getGameTime();
        return switch (destination) {
            case PERSONAL -> breachPersonal(server, registry, player, tick);
            case TEAM -> breachTeam(server, registry, player, tick);
            case LOBBY -> breachLobby(server, registry, player, tick);
        };
    }

    /**
     * Personal breach: the player's ROOT in void_space, allocated and
     * materialized on demand (first visit builds the space).
     */
    private boolean breachPersonal(MinecraftServer server, PocketRegistry registry,
            ServerPlayer player, long tick) {
        TeamIdentityProvider.GroupIdentity identity =
                TeamIdentityProvider.get().identityFor(player);
        if (identity.kind() != TeamIdentityProvider.GroupIdentity.Kind.PLAYER) {
            return refuse(player, "You are in a party — Personal resolves to "
                    + identity.name() + "'s shared space; use Team.");
        }
        PocketInstance pocket = PocketOps.findRoot(registry, identity.id());
        if (pocket == null) {
            ServerLevel voidSpace = PocketOps.voidSpace(server);
            if (voidSpace == null) {
                return refuse(player, "void_space dimension missing — nothing to breach to.");
            }
            pocket = registry.allocate(PocketDimensions.VOID_SPACE, identity.id(),
                    PocketInstance.OwnerKind.PLAYER, PocketType.ROOT, "",
                    PocketOps.ALLOC_SCAN);
            if (pocket == null) {
                return refuse(player, "Grid exhausted — no free cell for your space.");
            }
            pocket = Materializer.materialize(registry, voidSpace, pocket);
        }
        return enter(registry, player, pocket, tick);
    }

    /**
     * Team breach: the identity's ROOT when the identity is TEAM-kind
     * (shared space); the team space is born here when the team has none.
     */
    private boolean breachTeam(MinecraftServer server, PocketRegistry registry,
            ServerPlayer player, long tick) {
        TeamIdentityProvider.GroupIdentity identity =
                TeamIdentityProvider.get().identityFor(player);
        if (identity.kind() != TeamIdentityProvider.GroupIdentity.Kind.TEAM) {
            return refuse(player, "You have no team — Personal is your space.");
        }
        PocketInstance pocket = PocketOps.findRoot(registry, identity.id());
        if (pocket == null) {
            ServerLevel voidSpace = PocketOps.voidSpace(server);
            if (voidSpace == null) {
                return refuse(player, "void_space dimension missing — nothing to breach to.");
            }
            pocket = registry.allocate(PocketDimensions.VOID_SPACE, identity.id(),
                    PocketInstance.OwnerKind.TEAM, PocketType.ROOT, "",
                    PocketOps.ALLOC_SCAN);
            if (pocket == null) {
                return refuse(player, "Grid exhausted — no free cell for the team space.");
            }
            pocket = Materializer.materialize(registry, voidSpace, pocket);
        }
        return enter(registry, player, pocket, tick);
    }

    /** Lobby breach: teleport to the lobby cell (0,0); lobby is never claimed. */
    private boolean breachLobby(MinecraftServer server, PocketRegistry registry,
            ServerPlayer player, long tick) {
        ServerLevel voidSpace = PocketOps.voidSpace(server);
        if (voidSpace == null) {
            return refuse(player, "void_space dimension missing — nothing to breach to.");
        }
        Materializer.materializeLobby(voidSpace);
        PocketInstance lobby = registry.get(PocketDimensions.VOID_SPACE, PocketGrid.LOBBY_CELL);
        if (lobby == null) {
            // The lobby has no registry entry (allocation skips it forever);
            // build a transient snapshot purely for the return-point record.
            lobby = new PocketInstance(PocketType.SPECIAL, PocketDimensions.VOID_SPACE,
                    PocketGrid.LOBBY_CELL, player.getUUID(),
                    PocketInstance.OwnerKind.PLAYER, true, "", -1, null, null,
                    System.currentTimeMillis(), false, 0);
        }
        if (!charge(player, BREACH_COST_ROOT_OR_LOBBY)) {
            return false;
        }
        recordReturnPoint(registry, player, lobby);
        BlockPos spawn = PocketGrid.cellCenter(0, 0).above(2);
        player.teleportTo(voidSpace, spawn.getX() + 0.5, spawn.getY(),
                spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        PocketOps.say(player, "Breached to Lobby.");
        return true;
    }

    /**
     * Shared entry path: refusal checks (pending conversion, not
     * materialized, insufficient energy), return-point record, teleport.
     */
    private boolean enter(PocketRegistry registry, ServerPlayer player,
            PocketInstance pocket, long tick) {
        if (pocket == null) {
            return refuse(player, "Space not found.");
        }
        int seconds = PocketOps.pendingSecondsRemaining(registry, pocket.cellKey(), tick);
        if (seconds >= 0) {
            return refuse(player, "Space is re-addressing (" + seconds + "s remaining)");
        }
        if (!pocket.materialized()) {
            ServerLevel voidSpace = PocketOps.voidSpace(player.server);
            if (voidSpace == null || Materializer.materialize(registry, voidSpace, pocket) == null) {
                return refuse(player, "Space could not be materialized.");
            }
            pocket = registry.get(pocket.dimension(), pocket.cellKey());
        }
        if (!charge(player, BREACH_COST_ROOT_OR_LOBBY)) {
            return false;
        }
        recordReturnPoint(registry, player, pocket);
        BlockPos entry = PocketOps.entryPoint(pocket);
        ServerLevel voidSpace = PocketOps.voidSpace(player.server);
        player.teleportTo(voidSpace, entry.getX() + 0.5, entry.getY(),
                entry.getZ() + 0.5, player.getYRot(), player.getXRot());
        PocketOps.say(player, "Breached to " + destination.label() + " space.");
        return true;
    }

    /**
     * Record the breacher's own position+dimension as this player's return
     * point for the pocket ("the breacher remembers where you were").
     * Persisted on the instance via {@link PocketInstance#withReturnPoint}.
     */
    private void recordReturnPoint(PocketRegistry registry, ServerPlayer player,
            PocketInstance pocket) {
        // Per-player return points ride on the pocket instance of the
        // breacher's own space; for the lobby (shared, unowned) the return
        // point cannot be stored per player on the instance, so it is keyed
        // on the instance only when the player owns the cell. Return trips
        // are free and read by the destination's breach frame (step 8+).
        PocketInstance current = registry.get(pocket.dimension(), pocket.cellKey());
        if (current != null && current.owner().equals(player.getUUID())) {
            registry.put(current.dimension(), current.withReturnPoint(
                    worldPosition.immutable(), level.dimension()));
        }
    }

    /** Charge {@code cost} FE; refusal message on shortfall. */
    private boolean charge(ServerPlayer player, int cost) {
        int stored = energy.getEnergyStored();
        if (stored < cost) {
            return refuse(player, "Insufficient energy (" + stored + " / " + cost + " FE)");
        }
        energy.extractEnergy(cost, false);
        return true;
    }

    private boolean refuse(ServerPlayer player, String message) {
        PocketOps.say(player, message);
        return false;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_ENERGY = "energy";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_DESTINATION, destination.name());
        tag.put(KEY_ENERGY, energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String name = tag.getString(KEY_DESTINATION);
        for (Destination d : Destination.values()) {
            if (d.name().equals(name)) {
                destination = d;
                break;
            }
        }
        if (tag.contains(KEY_ENERGY)) {
            energy.deserializeNBT(registries, tag.get(KEY_ENERGY));
        }
    }

    /** Registry owner snapshot for tests and the GT module's tier logic. */
    UUID pocketOwnerFor(TeamIdentityProvider.GroupIdentity identity) {
        return identity.id();
    }
}
