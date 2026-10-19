package dev.foldedhorizons.core.item;

import dev.foldedhorizons.core.dimension.Materializer;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.identity.TeamIdentityProvider;
import dev.foldedhorizons.core.machine.PocketOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Temp Key — tiered breacher consumable (tier 1 from EV onward).
 *
 * <p>Used in a Dimensional Breacher's Temp Key slot (GT module) or
 * right-clicked directly (core-alone flow): opens the identity's next
 * UNSTABLE pocket of the key's tier dimension, at the per-tier offset
 * cursor (see {@link PocketRegistry#allocateUnstable}), and breaches the
 * user there. One key is spent per breach; the pocket collapses the moment
 * no player remains inside ({@code UnstablePocketWatcher}) and can never
 * be re-entered — the key buys a one-shot visit, not a place.</p>
 *
 * <p>Exploit cells are never owned and take no return-point record
 * ("temporary" is a gameplay state — docs/companion-mod/pocket-system.md).
 * Refusals (tier dimension missing, grid exhausted) leave the key
 * unconsumed. Grace-window checks do not apply: pending conversions only
 * ever target root-family cells in {@code void_space}.</p>
 */
public class TempKeyItem extends Item {

    /**
     * Per-team unstable cursor stride for a temp-key breach: matches the
     * registry default ({@code teams_per_ring}, 8). ModConfig has no field
     * for it and is not registered yet — same lead as Materializer's
     * hardcoded interior size.
     */
    private static final int TEAMS_PER_RING = 8;

    private final int tier;

    public TempKeyItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!breach(serverPlayer)) {
            return InteractionResultHolder.fail(stack); // refusal: key stays
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Allocate + materialize + enter an UNSTABLE pocket of this key's tier
     * dimension. Returns false (with action-bar feedback) on any refusal.
     */
    private boolean breach(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel tierLevel = server.getLevel(PocketDimensions.exploitTier(tier));
        if (tierLevel == null) {
            return refuse(player, "exploit_t" + tier
                    + " dimension missing — this key has no target.");
        }
        TeamIdentityProvider.GroupIdentity identity =
                TeamIdentityProvider.get().identityFor(player);
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        PocketInstance pocket = registry.allocateUnstable(
                PocketDimensions.exploitTier(tier), identity.id(),
                TEAMS_PER_RING, PocketOps.ALLOC_SCAN);
        if (pocket == null) {
            return refuse(player, "Grid exhausted — no free cell in exploit_t"
                    + tier + ".");
        }
        pocket = Materializer.materialize(registry, tierLevel, pocket);
        if (pocket == null) {
            return refuse(player, "Exploit space could not be materialized.");
        }
        BlockPos entry = PocketOps.entryPoint(pocket);
        player.teleportTo(tierLevel, entry.getX() + 0.5, entry.getY(),
                entry.getZ() + 0.5, player.getYRot(), player.getXRot());
        PocketOps.say(player, "Breached to tier " + tier
                + " exploit space — it collapses when you leave.");
        return true;
    }

    private boolean refuse(ServerPlayer player, String message) {
        PocketOps.say(player, message);
        return false;
    }
}
