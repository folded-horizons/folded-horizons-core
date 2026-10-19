package dev.foldedhorizons.core.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.foldedhorizons.core.dimension.PocketInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Game events for quest integration (build-order 12).
 *
 * <p>A deliberately small, dependency-light event bus: five fire-points the
 * rest of the mod calls when notable things happen —
 * {@link #cellAllocated}, {@link #conversionMerged},
 * {@link #unstablePocketCollapsed}, {@link #tradeExecuted} and
 * {@link #shellBroken}. Listeners register with
 * {@link #addListener(Class, Consumer)}; events are plain data objects
 * (nested {@link Event} subclasses) carrying the minimal useful payload
 * (level, cell key or {@link BlockPos}, player UUID where applicable).</p>
 *
 * <p><b>FTB Quests, not KubeJS:</b> the foldedhorizons-ftb module
 * subscribes to these events and maps them onto FTB Quests task/event
 * trigger types ({@code cell_allocated}, {@code conversion_merged},
 * {@code unstable_pocket_collapsed}, {@code trade_executed},
 * {@code shell_broken}). There is intentionally no KubeJS glue — packs
 * drive quests through FTB Quests' own event triggers, per
 * docs/companion-mod/integration.md.</p>
 *
 * <p>Thread-safety: the listener list is a copy-on-write list, so firing is
 * safe from any thread and iteration never sees a concurrent mutation.
 * All current fire-points run on the server thread.</p>
 */
public final class ModEvents {

    private ModEvents() {}

    /** Marker base for all foldedhorizons core game events. */
    public static abstract class Event {
        Event() {}
    }

    /** {@code cell_allocated}: a pocket cell was allocated in the registry. */
    public static final class CellAllocated extends Event {
        private final PocketInstance pocket;
        private final UUID player;

        CellAllocated(PocketInstance pocket, UUID player) {
            this.pocket = pocket;
            this.player = player;
        }

        /** The freshly allocated (not yet necessarily materialized) pocket. */
        public PocketInstance pocket() { return pocket; }

        /** The acting player UUID (may equal {@link PocketInstance#owner()}). */
        public UUID player() { return player; }
    }

    /** {@code conversion_merged}: a Root Binder MERGE conversion applied. */
    public static final class ConversionMerged extends Event {
        private final ServerLevel voidLevel;
        private final String cellKey;
        private final UUID actor;
        private final UUID team;

        ConversionMerged(ServerLevel voidLevel, String cellKey, UUID actor, UUID team) {
            this.voidLevel = voidLevel;
            this.cellKey = cellKey;
            this.actor = actor;
            this.team = team;
        }

        public ServerLevel voidLevel() { return voidLevel; }
        /** The personal ROOT cell that re-keyed to the team. */
        public String cellKey() { return cellKey; }
        /** The leaving personal owner. */
        public UUID actor() { return actor; }
        /** The team the space merged into. */
        public UUID team() { return team; }
    }

    /** {@code unstable_pocket_collapsed}: an UNSTABLE cell swept and removed. */
    public static final class UnstablePocketCollapsed extends Event {
        private final ServerLevel level;
        private final String cellKey;
        private final UUID owner;

        UnstablePocketCollapsed(ServerLevel level, String cellKey, UUID owner) {
            this.level = level;
            this.cellKey = cellKey;
            this.owner = owner;
        }

        public ServerLevel level() { return level; }
        public String cellKey() { return cellKey; }
        /** The (team) owner whose exploit space collapsed. */
        public UUID owner() { return owner; }
    }

    /** {@code trade_executed}: a Trade Post trade moved items between networks. */
    public static final class TradeExecuted extends Event {
        private final ServerLevel level;
        private final BlockPos pos;
        private final UUID buyer;
        private final UUID seller;

        TradeExecuted(ServerLevel level, BlockPos pos, UUID buyer, UUID seller) {
            this.level = level;
            this.pos = pos;
            this.buyer = buyer;
            this.seller = seller;
        }

        public ServerLevel level() { return level; }
        /** The Trade Post block entity's position. */
        public BlockPos pos() { return pos; }
        /** The visiting player who confirmed the trade. */
        public UUID buyer() { return buyer; }
        /** The Trade Post owner (null if the link has no resolvable owner). */
        public UUID seller() { return seller; }
    }

    /**
     * {@code shell_broken}: a {@code foldedhorizons:wall_shell} block was
     * actually destroyed — late-game corrupted-space access fires this.
     */
    public static final class ShellBroken extends Event {
        private final ServerLevel level;
        private final BlockPos pos;
        private final UUID player;

        ShellBroken(ServerLevel level, BlockPos pos, UUID player) {
            this.level = level;
            this.pos = pos;
            this.player = player;
        }

        public ServerLevel level() { return level; }
        public BlockPos pos() { return pos; }
        /** The player who broke the shell, or null if not player-caused. */
        public UUID player() { return player; }
    }

    // ------------------------------------------------------------------
    // Listener registry + fire-points
    // ------------------------------------------------------------------

    private static final Map<Class<?>, List<Consumer<?>>> LISTENERS =
            new ConcurrentHashMap<>();

    /**
     * Subscribe {@code listener} to events of {@code type}. Called by the
     * foldedhorizons-ftb module at mod setup; listeners are never
     * re-registered per world, so this registry stays process-global.
     */
    public static <T extends Event> void addListener(Class<T> type, Consumer<T> listener) {
        LISTENERS.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** Remove a previously registered listener (no-op if absent). */
    public static <T extends Event> void removeListener(Class<T> type, Consumer<T> listener) {
        List<Consumer<?>> list = LISTENERS.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    /** Deliver {@code event} to every registered listener of its type. */
    @SuppressWarnings("unchecked")
    public static <T extends Event> void fire(T event) {
        List<Consumer<?>> list = LISTENERS.get(event.getClass());
        if (list == null) {
            return;
        }
        for (Consumer<?> consumer : list) {
            ((Consumer<T>) consumer).accept(event);
        }
    }

    /** Fire {@code cell_allocated}; called from {@link PocketRegistry} after the registry insert succeeds. */
    public static void cellAllocated(PocketInstance pocket, UUID player) {
        fire(new CellAllocated(pocket, player));
    }

    /** Fire {@code conversion_merged}; called from {@code ConversionTicker} when a MERGE conversion applies. */
    public static void conversionMerged(ServerLevel voidLevel, String cellKey,
            UUID actor, UUID team) {
        fire(new ConversionMerged(voidLevel, cellKey, actor, team));
    }

    /** Fire {@code unstable_pocket_collapsed}; called from {@code UnstablePocketWatcher} after the sweep completes. */
    public static void unstablePocketCollapsed(ServerLevel level, String cellKey,
            UUID owner) {
        fire(new UnstablePocketCollapsed(level, cellKey, owner));
    }

    /**
     * Fire {@code trade_executed}. Wired by step 10: the Trade Post block
     * entity (landing via the AE2 bridge step in a parallel worktree) calls
     * this when a confirmed trade actually moves items between the two ME
     * networks — not on GUI preview or offer edits.
     */
    public static void tradeExecuted(ServerLevel level, BlockPos pos,
            UUID buyer, UUID seller) {
        fire(new TradeExecuted(level, pos, buyer, seller));
    }

    /**
     * Fire {@code shell_broken}. Wired by a late-game destroy path: the
     * starter {@code wall_shell} block is unbreakable by design (chip-only,
     * see {@code StarterWallBlock}) — late-game corrupted-space access
     * fires this when the shell finally yields.
     */
    public static void shellBroken(ServerLevel level, BlockPos pos, UUID player) {
        fire(new ShellBroken(level, pos, player));
    }
}
