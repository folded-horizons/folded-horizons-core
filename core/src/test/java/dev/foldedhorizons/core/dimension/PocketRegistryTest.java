package dev.foldedhorizons.core.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * Allocation and persistence tests for {@link PocketRegistry} and
 * {@link PocketInstance}. Pure data structures — no server, no levels.
 */
class PocketRegistryTest {

    private static final ResourceKey<Level> DIM = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse("foldedhorizons:void_space"));

    @Test
    void allocateNeverHandsOutTheLobby() {
        PocketRegistry reg = new PocketRegistry();
        UUID owner = UUID.randomUUID();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            PocketInstance p = reg.allocate(DIM, owner, PocketInstance.OwnerKind.PLAYER,
                    PocketType.ROOT, "", 4096);
            assertNotNull(p, "allocation " + i + " failed");
            assertEquals(PocketType.ROOT, p.type());
            assertFalse(PocketGrid.LOBBY_CELL.equals(p.cellKey()));
            assertTrue(keys.add(p.cellKey()), "duplicate cell " + p.cellKey());
        }
        assertEquals(50, reg.cellsIn(DIM).size());
    }

    @Test
    void allocateReturnsDistinctCellsForDifferentOwners() {
        PocketRegistry reg = new PocketRegistry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PocketInstance pa = reg.allocate(DIM, a, PocketInstance.OwnerKind.PLAYER,
                PocketType.ROOT, "", 4096);
        PocketInstance pb = reg.allocate(DIM, b, PocketInstance.OwnerKind.PLAYER,
                PocketType.ROOT, "", 4096);
        assertNotNull(pa);
        assertNotNull(pb);
        assertFalse(pa.cellKey().equals(pb.cellKey()));
    }

    @Test
    void allocateMarksUnmaterializedAndUnclaimed() {
        PocketRegistry reg = new PocketRegistry();
        PocketInstance p = reg.allocate(DIM, UUID.randomUUID(),
                PocketInstance.OwnerKind.PLAYER, PocketType.ROOT, "", 4096);
        assertNotNull(p);
        assertFalse(p.materialized(), "fresh allocation is not yet materialized");
        assertFalse(p.dying());
        assertEquals(0, p.expansionStage(), "stage 0 = base size");
        assertEquals(-1, p.exhaustIndex(), "non-unstable pockets use -1");
    }

    @Test
    void unstableAllocationUsesPerTeamOffset() {
        PocketRegistry reg = new PocketRegistry();
        UUID team = UUID.randomUUID();
        PocketInstance first = reg.allocateUnstable(DIM, team, 8, 4096);
        PocketInstance second = reg.allocateUnstable(DIM, team, 8, 4096);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(PocketType.UNSTABLE, first.type());
        assertEquals(PocketInstance.OwnerKind.TEAM, first.ownerKind());
        assertEquals(0, first.exhaustIndex());
        int firstIndex = pathIndexOf(first.cellKey());
        int secondIndex = pathIndexOf(second.cellKey());
        assertTrue(secondIndex - firstIndex >= 8,
                "teams_per_ring must space a team's pockets at least 8 apart: "
                        + firstIndex + " -> " + secondIndex);
    }

    @Test
    void ownCellsAreTracked() {
        PocketRegistry reg = new PocketRegistry();
        UUID owner = UUID.randomUUID();
        PocketInstance p = reg.allocate(DIM, owner, PocketInstance.OwnerKind.PLAYER,
                PocketType.ROOT, "", 4096);
        assertNotNull(p);
        assertTrue(reg.ownedCells(owner).contains(p.cellKey()));
        assertTrue(reg.owners().contains(owner));
    }

    @Test
    void findOrAllocateIsAddressNotDoor() {
        PocketRegistry reg = new PocketRegistry();
        UUID owner = UUID.randomUUID();
        PocketInstance first = reg.findOrAllocate(DIM, owner, PocketInstance.OwnerKind.PLAYER,
                "ae2_meteorite", 4096);
        PocketInstance second = reg.findOrAllocate(DIM, owner, PocketInstance.OwnerKind.PLAYER,
                "ae2_meteorite", 4096);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.cellKey(), second.cellKey(),
                "second binder on the same body must bind the same pocket");
        assertEquals("ae2_meteorite", first.body());
    }

    @Test
    void expandRootBumpsStageAndPersists() {
        PocketRegistry reg = new PocketRegistry();
        PocketInstance p = reg.allocate(DIM, UUID.randomUUID(),
                PocketInstance.OwnerKind.PLAYER, PocketType.ROOT, "", 4096);
        assertNotNull(p);
        PocketInstance expanded = reg.expandRoot(DIM, p.cellKey());
        assertNotNull(expanded);
        assertEquals(1, expanded.expansionStage());
        assertEquals(1, reg.get(DIM, p.cellKey()).expansionStage());
    }

    @Test
    void expandRootOnUnknownCellReturnsNull() {
        assertNull(new PocketRegistry().expandRoot(DIM, "99,99"));
    }

    @Test
    void markDyingIsPersisted() {
        PocketRegistry reg = new PocketRegistry();
        PocketInstance p = reg.allocate(DIM, UUID.randomUUID(),
                PocketInstance.OwnerKind.PLAYER, PocketType.ROOT, "", 4096);
        assertNotNull(p);
        PocketInstance dying = reg.markDying(DIM, p.cellKey());
        assertNotNull(dying);
        assertTrue(dying.dying());
        assertTrue(reg.get(DIM, p.cellKey()).dying());
        assertTrue(reg.cellsIn(DIM).containsKey(p.cellKey()),
                "markDying only flags the cell, it stays registered");
    }

    @Test
    void pendingConversionExpiresOnlyAfterTheWindow() {
        PocketRegistry reg = new PocketRegistry();
        // Grace window opens at tick 100 and lasts 600 ticks.
        reg.addPendingConversion("1,1", 100L, PocketRegistry.ConversionAction.MERGE,
                UUID.randomUUID(), UUID.randomUUID(), 600);
        assertNotNull(reg.pendingConversionFor("1,1"));
        assertTrue(reg.expiredConversions(699L).isEmpty(), "not due before the window ends");
        assertEquals(1, reg.expiredConversions(700L).size(), "due at the window edge");
        assertTrue(reg.expiredConversions(900L).isEmpty(),
                "expiredConversions drains: a due conversion is handed out once");
        reg.removePendingConversion("1,1");
        assertNull(reg.pendingConversionFor("1,1"));
        assertTrue(reg.expiredConversions(999L).isEmpty());
    }

    @Test
    void instanceSnbtRoundTripPreservesEverything() {
        UUID owner = UUID.randomUUID();
        PocketInstance original = new PocketInstance(PocketType.SPECIAL, DIM, "3,-2",
                owner, PocketInstance.OwnerKind.TEAM, true, "ae2_meteorite", 4,
                new BlockPos(10, 64, -20), DIM, 123456789L, true, 2);
        CompoundTag tag = original.save(new CompoundTag());
        PocketInstance loaded = PocketInstance.load(tag);
        assertNotNull(loaded);
        assertEquals(original.type(), loaded.type());
        assertEquals(original.dimension(), loaded.dimension());
        assertEquals(original.owner(), loaded.owner());
        assertEquals(original.ownerKind(), loaded.ownerKind());
        assertEquals(original.materialized(), loaded.materialized());
        assertEquals(original.body(), loaded.body());
        assertEquals(original.exhaustIndex(), loaded.exhaustIndex());
        assertEquals(original.returnPoint(), loaded.returnPoint());
        assertEquals(original.returnDimension(), loaded.returnDimension());
        assertEquals(original.createdAt(), loaded.createdAt());
        assertEquals(original.dying(), loaded.dying());
        assertEquals(original.expansionStage(), loaded.expansionStage());
        // The cell key is not serialized — it is the registry map key.
        assertEquals("", loaded.cellKey());
    }

    @Test
    void instanceLoadRejectsCorruptTags() {
        assertNull(PocketInstance.load(new CompoundTag()), "empty tag");
        CompoundTag badType = new CompoundTag();
        badType.putString("dim", DIM.location().toString());
        badType.putString("type", "NOT_A_TYPE");
        badType.putLong("owner_most", 1L);
        badType.putLong("owner_least", 2L);
        assertNull(PocketInstance.load(badType), "unknown pocket type must not crash");
    }

    @Test
    void copyHelpersPreserveExpansionStage() {
        PocketInstance p = new PocketInstance(PocketType.ROOT, DIM, "1,1",
                UUID.randomUUID(), PocketInstance.OwnerKind.PLAYER, false, "", -1,
                null, null, 5L, false, 3);
        assertEquals(3, p.asMaterialized().expansionStage());
        assertEquals(3, p.asDying().expansionStage());
        assertEquals(2, p.withExpansionStage(2).expansionStage());
        assertEquals(3, p.withExhaustIndex(9).expansionStage());
    }

    /** Index of a cell key on the spiral path, or -1. Mirrors the registry's own math. */
    private static int pathIndexOf(String cellKey) {
        return PocketRegistry.pathIndexOf(PocketGrid.parseKey(cellKey));
    }
}
