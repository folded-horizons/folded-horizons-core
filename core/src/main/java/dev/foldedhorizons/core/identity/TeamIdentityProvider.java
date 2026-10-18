package dev.foldedhorizons.core.identity;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * SPI: maps a player to a stable group identity — the UUID the pocket
 * registry keys ownership by and the name shown in UI. Core's default (see
 * {@link BuiltInPartyRegistry}) keys by player: a player is their own party.
 * The foldedhorizons-ftb module replaces this with FTB team identity, so
 * team members share one registry owner and claims follow the team.
 *
 * <p>Returned ids must be stable across restarts and — for team-backed
 * implementations — across members joining/leaving, since
 * {@code PocketRegistry.by_owner} and claim registration key on them.</p>
 */
public interface TeamIdentityProvider {

    /** A stable group identity: registry owner id + display name. */
    record GroupIdentity(UUID id, String name, Kind kind) {

        /** Whether {@code id} denotes a player or a shared team. */
        public enum Kind {
            PLAYER,
            TEAM
        }
    }

    /**
     * Resolve {@code player}'s group identity. Never returns null; a
     * resolution failure falls back to the player's own UUID.
     */
    GroupIdentity identityFor(Player player);

    /** Accessor for the active provider (never null). */
    static TeamIdentityProvider get() {
        return TeamIdentityProviders.get();
    }

    /**
     * Install a provider (foldedhorizons-ftb does this at construction).
     * Registering replaces the previous provider; null restores the
     * built-in party fallback.
     */
    static void register(TeamIdentityProvider provider) {
        TeamIdentityProviders.register(provider);
    }
}
