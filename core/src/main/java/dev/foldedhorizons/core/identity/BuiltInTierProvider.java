package dev.foldedhorizons.core.identity;

import net.minecraft.server.level.ServerPlayer;

/**
 * Core default {@link TierProvider}: nobody has a tier. Expansion
 * thresholds can never be met until an integration module (GT for the
 * Folded Horizons pack) registers a real provider — matches the
 * core-standalone goal: the pocket system works without GT, it just
 * never expands.
 */
public final class BuiltInTierProvider implements TierProvider {

    public static final BuiltInTierProvider INSTANCE = new BuiltInTierProvider();

    private BuiltInTierProvider() {}

    @Override
    public String tierFor(ServerPlayer player) {
        return TierProvider.NONE;
    }
}
