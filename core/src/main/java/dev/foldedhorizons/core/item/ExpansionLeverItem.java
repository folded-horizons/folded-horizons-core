package dev.foldedhorizons.core.item;

import dev.foldedhorizons.core.config.ModConfig;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketGrid;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.identity.TierProviders;
import dev.foldedhorizons.core.machine.PocketOps;
import dev.foldedhorizons.core.setup.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Expansion Lever (walls-expansion.md "Root space expansion"): the
 * explicit v1 hook that grows a root space. Use on any block inside a
 * root/repeatable pocket (or the breacher frame) — the lever resolves the
 * containing pocket, checks the owner's tier against the
 * {@code pocket.expansionThresholds} ladder via the {@link TierProviders}
 * SPI, and if the next rung qualifies, grows the interior
 * ({@link PocketGrid#expandInterior}) and bumps the registry's
 * {@code expansion_stage}.
 *
 * <p>Expansion is a shell-growing operation, not a teleport: the old
 * interior is untouched, the difference ring is swept and re-sealed with
 * {@code wall_shell}. Shrinking is impossible — the stage only climbs.</p>
 *
 * <p>With the built-in tier provider (no GT module), nobody qualifies and
 * the lever reports the current tier instead — packs without an
 * integration module keep their fixed cell size.</p>
 */
public class ExpansionLeverItem extends Item {

    public ExpansionLeverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        MinecraftServer server = level.getServer();
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        PocketInstance pocket = PocketOps.pocketContaining(registry, level,
                context.getClickedPos());
        if (pocket == null) {
            return sayResult(player,
                    "The lever hums — no folded space here to expand.");
        }
        if (pocket.type() != PocketType.ROOT && pocket.type() != PocketType.REPEATABLE) {
            return sayResult(player, "This space cannot be expanded.");
        }
        return tryExpand(server, (ServerLevel) level, player, registry, pocket);
    }

    private InteractionResult tryExpand(MinecraftServer server, ServerLevel level,
            ServerPlayer player, PocketRegistry registry, PocketInstance pocket) {
        String tier = TierProviders.get().tierFor(player);
        List<? extends String> ladder = ModConfig.EXPANSION_THRESHOLDS.get();
        if (ladder.isEmpty()) {
            return sayResult(player, "This space has reached its full extent.");
        }

        // Parse the ladder into ordered (token, size) rungs. Tokens are
        // arbitrary strings matched against TierProvider output.
        List<Rung> rungs = new ArrayList<>();
        for (String entry : ladder) {
            int sep = entry.indexOf(':');
            if (sep <= 0 || sep == entry.length() - 1) {
                continue; // malformed entry — skip, don't crash the config
            }
            try {
                rungs.add(new Rung(entry.substring(0, sep),
                        Integer.parseInt(entry.substring(sep + 1))));
            } catch (NumberFormatException ignored) {
                // malformed entry — skip
            }
        }
        if (rungs.isEmpty()) {
            return sayResult(player, "This space has reached its full extent.");
        }

        // Where does the player's tier sit on the ladder?
        int bestIndex = -1; // highest rung index the tier reaches
        for (int i = rungs.size() - 1; i >= 0; i--) {
            if (rungs.get(i).token().equalsIgnoreCase(tier)) {
                bestIndex = i;
                break;
            }
        }
        if (bestIndex < 0) {
            return sayResult(player, "Your machines are not deep enough yet (tier: "
                    + tier + ").");
        }

        int currentStage = pocket.expansionStage();
        int nextStage = currentStage + 1;
        if (nextStage > bestIndex) {
            // Either fully expanded for this tier, or next rung needs a higher tier.
            if (nextStage < rungs.size()) {
                return sayResult(player, "The next fold needs "
                        + rungs.get(nextStage).token() + "-tier machinery.");
            }
            return sayResult(player, "This space has reached its full extent.");
        }

        int currentSize = sizeForStage(currentStage, rungs);
        int newSize = sizeForStage(nextStage, rungs);
        PocketGrid.Cell cell = PocketGrid.parseKey(pocket.cellKey());
        if (cell == null) {
            return sayResult(player, "The lever cannot read this space's address.");
        }
        if (!PocketGrid.expandInterior(level, cell.x(), cell.z(), currentSize, newSize,
                ModBlocks.WALL_SHELL.get())) {
            return sayResult(player, "This space has reached its full extent.");
        }
        registry.expandRoot(pocket.dimension(), pocket.cellKey());
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.BLOCKS, 0.8f, 0.6f);
        PocketOps.say(player, "The space folds outward — interior now "
                + newSize + "×" + newSize + ".");
        return InteractionResult.SUCCESS;
    }

    /** Feedback + SUCCESS in one step (PocketOps.say is void). */
    private static InteractionResult sayResult(ServerPlayer player, String message) {
        PocketOps.say(player, message);
        return InteractionResult.SUCCESS;
    }

    /** Interior size after {@code stage} expansions: the size of the stage-th rung.
     *  Stage 0 = base size (config pocket.baseSize). */
    private static int sizeForStage(int stage, List<Rung> rungs) {
        if (stage == 0) {
            return ModConfig.POCKET_BASE_SIZE.get();
        }
        return rungs.get(stage - 1).size();
    }

    /** One ladder rung: tier token + interior size it unlocks. */
    private record Rung(String token, int size) {}
}
