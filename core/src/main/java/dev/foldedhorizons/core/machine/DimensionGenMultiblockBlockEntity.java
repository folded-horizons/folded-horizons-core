package dev.foldedhorizons.core.machine;

import java.util.UUID;
import dev.foldedhorizons.core.claim.ClaimAdapters;
import dev.foldedhorizons.core.dimension.Materializer;
import dev.foldedhorizons.core.dimension.PocketBodies;
import dev.foldedhorizons.core.dimension.PocketChunkLoader;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketGrid;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.identity.TeamIdentityProvider;
import dev.foldedhorizons.core.setup.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Dimension-Generation Multiblock block entity — consumed-on-fire jump to
 * the AE2 meteorite pocket ({@code docs/companion-mod/machines.md},
 * "LV Dimension-Generation Multiblock — consumed-in-jump").
 *
 * <p><strong>GT stand-in — the foldedhorizons-gt module re-homes this
 * machine onto the GTCEu framework (decision #13)</strong>, where the
 * controller + casing pattern and LV lock live. Core ships this vanilla
 * side: a standalone controller block that IS the machine, so the
 * core-alone pack still plays the consumed-in-jump loop. Core owns no GT
 * or AE2 imports; the meteorite content itself is a placeholder template
 * (see {@link dev.foldedhorizons.core.dimension.PocketTemplates}).</p>
 *
 * <p>Behavior per the docs: on sneak-right-click with a full-enough buffer
 * the machine fires — (1) allocate the owner's SPECIAL pocket for body
 * {@link PocketBodies#AE2_METEORITE} in the void grid via
 * {@link PocketRegistry#findOrAllocate} (address-not-door: a second
 * machine binds to the same pocket instead of allocating), (2)
 * materialize the meteorite template there (per-<strong>server</strong>
 * allocation cursor — a preview of scarcity), auto-claim it and arm its
 * permanent chunk-load tickets (docs "Claiming" policy), (3) teleport
 * players standing on the machine, (4) <strong>consume itself</strong>:
 * this block is removed (no junk drop — the pocket is the machine's
 * product; docs specify a "collapsed frame" drop which the GT module
 * adds with its casing set), and one machine = one pocket.</p>
 *
 * <p>No energy cost is specified for this machine in the docs — it stays
 * free like the docs' free-return trips; the buffer exists so the GT
 * module can wire EU power without touching the BE contract. No
 * stability counter: the pocket is finite by loot, stays materialized
 * until mined out, and does not collapse on leave.</p>
 */
public class DimensionGenMultiblockBlockEntity extends BlockEntity {

    /**
     * FE buffer capacity, generous like the breacher's so a full jump
     * could later draw from it without a rebuild of the contract.
     */
    static final int BUFFER_CAPACITY = 1_000_000;
    static final int MAX_RECEIVE = 100_000;

    private final EnergyStorage energy = new EnergyStorage(BUFFER_CAPACITY, MAX_RECEIVE);

    public DimensionGenMultiblockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIMENSION_GEN.get(), pos, state);
    }

    /** Exposed FE buffer (sided energy capability). */
    public IEnergyStorage energy() {
        return energy;
    }

    /**
     * Fire the jump for {@code player}: allocate + materialize the owner's
     * meteorite pocket, auto-claim + permanently chunk-load it, teleport
     * players standing on the machine, then consume this block.
     *
     * @return false (with an action-bar refusal message) when the jump
     *         cannot fire; the caller must not remove the block then.
     */
    public boolean tryFire(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return false;
        }
        MinecraftServer server = serverLevel.getServer();
        PocketRegistry registry = PocketRegistry.getOrCreate(server);

        TeamIdentityProvider.GroupIdentity identity =
                TeamIdentityProvider.get().identityFor(player);
        PocketInstance.OwnerKind ownerKind = identity.kind() == TeamIdentityProvider.GroupIdentity.Kind.TEAM
                ? PocketInstance.OwnerKind.TEAM : PocketInstance.OwnerKind.PLAYER;
        PocketInstance pocket = allocate(registry, identity.id(), ownerKind);
        if (pocket == null) {
            return refuse(player, "Grid exhausted — no free cell for the meteorite pocket.");
        }
        ServerLevel voidSpace = PocketOps.voidSpace(server);
        if (voidSpace == null) {
            return refuse(player, "void_space dimension missing — nothing to generate.");
        }
        PocketInstance materialized = Materializer.materialize(registry, voidSpace, pocket);
        if (materialized == null) {
            return refuse(player, "Pocket could not be materialized.");
        }

        // Claiming policy (docs "Claiming"): permanent pockets auto-claim
        // at materialization with no claim limit — Materializer.materialize
        // already called ClaimAdapters.get().claim; the permanent
        // chunk-load ticket is armed here for this cell immediately.
        PocketChunkLoader.rearm(voidSpace);

        teleportStanders(server, materialized);
        consume(serverLevel);
        PocketOps.say(player, "The machine lurches through itself — pocket "
                + materialized.cellKey() + " generated.");
        return true;
    }

    /** Action-bar refusal helper. */
    private boolean refuse(ServerPlayer player, String message) {
        PocketOps.say(player, message);
        return false;
    }

    /**
     * Allocate the owner's meteorite pocket: SPECIAL, body
     * {@link PocketBodies#AE2_METEORITE}, via the address-not-door rule
     * (a second machine binds to the same pocket). The registry's shared
     * per-dimension cursor advances — the per-server preview of scarcity.
     */
    private PocketInstance allocate(PocketRegistry registry, UUID owner,
            PocketInstance.OwnerKind ownerKind) {
        return registry.findOrAllocate(PocketDimensions.VOID_SPACE, owner, ownerKind,
                PocketBodies.AE2_METEORITE, PocketOps.ALLOC_SCAN);
    }

    /** Teleport players standing on top of the machine (the jump grabs them). */
    private void teleportStanders(MinecraftServer server, PocketInstance pocket) {
        ServerLevel voidSpace = PocketOps.voidSpace(server);
        if (voidSpace == null) {
            return;
        }
        AABB standBox = new AABB(worldPosition).expandTowards(0, 1, 0);
        for (Player stander : level.players()) {
            if (stander instanceof ServerPlayer serverStander
                    && stander.getBoundingBox().intersects(standBox)) {
                BlockPos entry = PocketOps.entryPoint(pocket);
                serverStander.teleportTo(voidSpace, entry.getX() + 0.5, entry.getY(),
                        entry.getZ() + 0.5, serverStander.getYRot(), serverStander.getXRot());
            }
        }
    }

    /**
     * Consume the machine in the jump: this controller block is removed
     * (the GT module's casing pattern removes its casing frame the same
     * way). No junk drop in core — the generated pocket is the product.
     */
    private void consume(ServerLevel level) {
        level.removeBlock(worldPosition, false);
        level.sendParticles(ParticleTypes.POOF,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.01);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    private static final String KEY_ENERGY = "energy";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_ENERGY, energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_ENERGY)) {
            energy.deserializeNBT(registries, tag.get(KEY_ENERGY));
        }
    }
}
