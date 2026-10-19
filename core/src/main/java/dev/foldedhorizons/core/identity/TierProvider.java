package dev.foldedhorizons.core.identity;

import net.minecraft.server.level.ServerPlayer;

/**
 * Core-owned SPI: "what tier token has this player reached?"
 *
 * <p>Drives the {@code EXPANSION_THRESHOLDS} ladder in
 * {@link dev.foldedhorizons.core.config.ModConfig} — entries like
 * {@code "EV:33"} match the tier token this provider reports. Tier tokens
 * are arbitrary strings: a non-GT pack redefines the ladder
 * ({@code ["steel:25","endgame:65"]}) and ships a provider that reports
 * its own tokens.</p>
 *
 * <p>Core default ({@link BuiltInTierProvider}) reports
 * {@link #NONE} so nobody qualifies until an integration module is
 * present. The foldedhorizons-gt module replaces this with real GT
 * voltage-tier detection (docs/companion-mod/architecture.md).</p>
 *
 * <p>Compare tiers by <em>ordered position in the ladder</em>, never by
 * string equality against a hardcoded list — the provider and the
 * thresholds must agree on the token alphabet, but core does not assume
 * which alphabet that is.</p>
 */
public interface TierProvider {

    /** Sentinel reported by the built-in provider: no tier reached. */
    String NONE = "none";

    /**
     * The tier token this player has reached, e.g. {@code "EV"}, or
     * {@link #NONE}. Must never return null; called on the server thread.
     */
    String tierFor(ServerPlayer player);
}
