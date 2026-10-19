package dev.foldedhorizons.core.dimension;

/**
 * Body ids for SPECIAL pockets that ship dedicated templates or module
 * content. A body id is the stable address of a special pocket ("address
 * not door" rule) and doubles as the key into per-body content.
 */
public final class PocketBodies {

    /**
     * The AE2 meteorite pocket — the dimension-gen multiblock's one
     * dedicated target. Content is module-owned: the core template
     * ({@link PocketTemplates#METEORITE}) is a placeholder rock cluster;
     * foldedhorizons-ae2 (or a datapack) replaces it with the real
     * skystone crust, Inscriber Presses and budding certus patch.
     */
    public static final String AE2_METEORITE = "ae2_meteorite";

    private PocketBodies() {}
}
