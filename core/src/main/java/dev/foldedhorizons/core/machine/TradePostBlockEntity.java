package dev.foldedhorizons.core.machine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import dev.foldedhorizons.core.event.ModEvents;
import dev.foldedhorizons.core.setup.ModBlockEntities;

/**
 * Trade Post block entity (core build-order step 10) — the crate-based
 * lobby trading hall machine.
 *
 * <p><strong>Crate-based core fallback (this class):</strong> the post
 * carries two physical inventories — an input crate (visitor offers go
 * in) and an output crate (the owner's goods come out) — plus up to
 * {@value #MAX_TRADES} standing trades, each a pure barter pair
 * {@code {offer: item+count, want: item+count}} (no currency). Trading
 * works from day one of the lobby, no AE2 network required on either
 * side. The {@code foldedhorizons-ae2} module later upgrades the same
 * machine to ME-backed storage-to-storage trading via the StorageNetwork
 * SPI; this crate path stays as the standalone/early-game fallback.</p>
 *
 * <p><strong>Auto-trade (every {@value #CADENCE_TICKS} ticks, server
 * side):</strong> for each standing trade with matching want-items in the
 * input crate and room for the offer in the output crate, the trade
 * executes. Both legs are staged before commit and committed atomically —
 * the output insertion is simulated first; only when the full offer fits
 * are the want items removed and the offer added, so a trade either moves
 * both stacks or neither (per {@code docs/companion-mod/machines.md}).</p>
 *
 * <p>Menu-less like the breacher and root binder (core has no GUI): plain
 * right-click reports the number of active offers on the action bar,
 * sneak-right-click runs one trade attempt immediately ("test trade
 * once"). Trades are configured by the owner programmatically
 * ({@link #setTrade}) — the GT module's real GUI (or a later pass) feeds
 * these setters; the data persists in the BE's NBT.</p>
 *
 * <p>Every executed trade fires {@link ModEvents#tradeExecuted} so the
 * game-event pass (step 12) can emit {@code trade_executed}.</p>
 */
public class TradePostBlockEntity extends BlockEntity {

    /** Slots per crate (input and output each). */
    public static final int CRATE_SLOTS = 9;
    /** Maximum number of standing trades. */
    public static final int MAX_TRADES = 9;
    /** Auto-trade cadence: attempt every 20 ticks (1 s). */
    static final int CADENCE_TICKS = 20;

    /** Visitor-side crate: offers to trade away land here. */
    private final ItemStackHandler input = new ItemStackHandler(CRATE_SLOTS);
    /** Owner-side crate: traded goods are paid out here. */
    private final ItemStackHandler output = new ItemStackHandler(CRATE_SLOTS);

    /** Standing trades: {@code offers[i]} paired with {@code wants[i]}; EMPTY = slot unused. */
    private final ItemStack[] offers = new ItemStack[MAX_TRADES];
    private final ItemStack[] wants = new ItemStack[MAX_TRADES];

    public TradePostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADE_POST.get(), pos, state);
        for (int i = 0; i < MAX_TRADES; i++) {
            offers[i] = ItemStack.EMPTY;
            wants[i] = ItemStack.EMPTY;
        }
    }

    /** The visitor-side input crate (9 slots). */
    public ItemStackHandler input() {
        return input;
    }

    /** The owner-side output crate (9 slots). */
    public ItemStackHandler output() {
        return output;
    }

    /** The offer stack of standing trade {@code index}, or EMPTY. */
    public ItemStack offer(int index) {
        return validIndex(index) ? offers[index] : ItemStack.EMPTY;
    }

    /** The want stack of standing trade {@code index}, or EMPTY. */
    public ItemStack want(int index) {
        return validIndex(index) ? wants[index] : ItemStack.EMPTY;
    }

    /**
     * Configure standing trade {@code index} (owner mode; no GUI in core).
     * Passing EMPTY for either side clears the trade. Both stacks are
     * copied — the BE never aliases caller-owned stacks.
     */
    public void setTrade(int index, ItemStack offer, ItemStack want) {
        if (!validIndex(index)) {
            return;
        }
        offers[index] = offer.isEmpty() ? ItemStack.EMPTY : offer.copy();
        wants[index] = want.isEmpty() ? ItemStack.EMPTY : want.copy();
        setChanged();
    }

    /** Number of fully configured standing trades (both sides present). */
    public int activeTrades() {
        int n = 0;
        for (int i = 0; i < MAX_TRADES; i++) {
            if (!offers[i].isEmpty() && !wants[i].isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Block-entity tick entry point (wired from
     * {@link TradePostBlock#getTicker}): every {@value #CADENCE_TICKS}
     * ticks, run one auto-trade sweep. No player feedback — automation
     * runs silently.
     */
    static void serverTick(Level level, BlockPos pos, BlockState state,
            TradePostBlockEntity tradePost) {
        if (level.isClientSide() || level.getGameTime() % CADENCE_TICKS != 0) {
            return;
        }
        tradePost.attemptAutoTrade();
    }

    /**
     * Run one auto-trade sweep: for each standing trade (in order), if the
     * input crate holds enough want-items and the output crate can take
     * the full offer, execute the trade. Returns the number of trades
     * executed this sweep.
     */
    public int attemptAutoTrade() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return 0;
        }
        int executed = 0;
        for (int i = 0; i < MAX_TRADES; i++) {
            if (offers[i].isEmpty() || wants[i].isEmpty()) {
                continue;
            }
            if (executeTrade(serverLevel, offers[i], wants[i])) {
                executed++;
            }
        }
        if (executed > 0) {
            setChanged();
        }
        return executed;
    }

    /**
     * Atomic two-leg commit: simulate inserting {@code offer} into the
     * output crate first; only when the full stack fits are the want items
     * extracted from the input crate and the offer inserted for real.
     * Either both legs move, or neither does.
     */
    private boolean executeTrade(ServerLevel serverLevel, ItemStack offer, ItemStack want) {
        if (countIn(input, want) < want.getCount()) {
            return false;
        }
        ItemStack staged = offer.copy();
        if (!fitsIn(output, staged)) {
            return false; // output crate full — no partial commit
        }
        removeFrom(input, want);
        insertAll(output, staged);
        ModEvents.tradeExecuted(serverLevel, worldPosition.immutable());
        return true;
    }

    /** Total count of {@code stack}-matching items across {@code handler}. */
    private static int countIn(ItemStackHandler handler, ItemStack stack) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(inSlot, stack)) {
                count += inSlot.getCount();
            }
        }
        return count;
    }

    /** Simulation-only: can {@code stack} be inserted into {@code handler} in full? */
    private static boolean fitsIn(ItemStackHandler handler, ItemStack stack) {
        ItemStack remainder = insertAll(handler, stack);
        return remainder.isEmpty();
    }

    /**
     * Insert {@code stack} across {@code handler}'s slots and return the
     * remainder. Pass a copy when the caller must keep its stack intact.
     */
    private static ItemStack insertAll(ItemStackHandler handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    /** Remove {@code count} of {@code stack}-matching items from {@code handler}. */
    private static void removeFrom(ItemStackHandler handler, ItemStack stack, int count) {
        int needed = count;
        for (int slot = 0; slot < handler.getSlots() && needed > 0; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameComponents(inSlot, stack)) {
                continue;
            }
            int take = Math.min(needed, inSlot.getCount());
            handler.extractItem(slot, take, false);
            needed -= take;
        }
    }

    /** Convenience overload: remove exactly {@code stack.getCount()} items. */
    private static void removeFrom(ItemStackHandler handler, ItemStack stack) {
        removeFrom(handler, stack, stack.getCount());
    }

    private static boolean validIndex(int index) {
        return index >= 0 && index < MAX_TRADES;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    private static final String KEY_INPUT = "input";
    private static final String KEY_OUTPUT = "output";
    private static final String KEY_OFFERS = "offers";
    private static final String KEY_WANTS = "wants";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INPUT, input.serializeNBT(registries));
        tag.put(KEY_OUTPUT, output.serializeNBT(registries));
        tag.put(KEY_OFFERS, saveIndexedStacks(offers, registries));
        tag.put(KEY_WANTS, saveIndexedStacks(wants, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INPUT)) {
            input.deserializeNBT(registries, tag.getCompound(KEY_INPUT));
        }
        if (tag.contains(KEY_OUTPUT)) {
            output.deserializeNBT(registries, tag.getCompound(KEY_OUTPUT));
        }
        loadIndexedStacks(tag.getList(KEY_OFFERS, Tag.TAG_COMPOUND), offers, registries);
        loadIndexedStacks(tag.getList(KEY_WANTS, Tag.TAG_COMPOUND), wants, registries);
    }

    /** Sparse stack array → list of {@code {slot, item}} compounds. */
    private static ListTag saveIndexedStacks(ItemStack[] stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i].isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("slot", (byte) i);
            entry.put("item", stacks[i].save(registries));
            list.add(entry);
        }
        return list;
    }

    /** List of {@code {slot, item}} compounds → stack array (missing slots EMPTY). */
    private static void loadIndexedStacks(ListTag list, ItemStack[] stacks,
            HolderLookup.Provider registries) {
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = ItemStack.EMPTY;
        }
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.getByte("slot") & 0xFF;
            if (slot >= stacks.length) {
                continue;
            }
            stacks[slot] = ItemStack.parse(registries, entry.getCompound("item"))
                    .orElse(ItemStack.EMPTY);
        }
    }

    /** Read-only snapshot of the configured trades, for status/tests. */
    public List<ItemStack> configuredOffers() {
        List<ItemStack> snapshot = new ArrayList<>();
        for (int i = 0; i < MAX_TRADES; i++) {
            if (!offers[i].isEmpty() && !wants[i].isEmpty()) {
                snapshot.add(offers[i].copy());
            }
        }
        return snapshot;
    }
}
