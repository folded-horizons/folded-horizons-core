package dev.foldedhorizons.core.dimension;

/**
 * Pocket classification for the exploit/special dimension system.
 *
 * <ul>
 *   <li>{@link #ROOT} — the persistent personal/team root space on the void
 *       grid. One per owner (player or team UUID); allocated by spiral from
 *       the lobby at cell (0,0); never collapses.</li>
 *   <li>{@link #REPEATABLE} — empty pocket space identical in permanence to
 *       a root space but not unique: the opening multiblock may be built
 *       (even recursively) as many times as wanted. Annexes, farms,
 *       outposts.</li>
 *   <li>{@link #UNSTABLE} — tiered exploit pocket opened by a Temp Key via
 *       the Dimensional Breacher. Collapses the moment no player remains
 *       inside; that instance is spent and cannot be re-entered. Per-team
 *       offset into the tier's static dimension, so teams never collide.</li>
 *   <li>{@link #SPECIAL} — stable celestial-body pockets (Moon, Mars,
 *       Mercury, ... plus the black-hole signature pocket). One per
 *       player/group — a second copy of the multiblock links into the same
 *       pocket instead of spawning a duplicate. Same expansion mechanic as
 *       the root space; no stability counter, no collapse.</li>
 * </ul>
 *
 * TODO: attach per-type behavior (stability counters, reset cycles,
 * expansion thresholds) once {@link PocketInstance} and {@link PocketRegistry}
 * are implemented.
 */
public enum PocketType {
    ROOT,
    REPEATABLE,
    UNSTABLE,
    SPECIAL
}
