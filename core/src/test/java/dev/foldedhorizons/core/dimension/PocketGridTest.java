package dev.foldedhorizons.core.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Pure-math tests for {@link PocketGrid}: cell keys, centers, interior
 * bounds, and the spiral allocation path. No Minecraft server needed.
 */
class PocketGridTest {

    @Test
    void pitchIsTwoChunksAligned() {
        assertEquals(2048, PocketGrid.PITCH);
        assertEquals(0, PocketGrid.PITCH % 16, "pitch must be chunk-aligned");
    }

    @Test
    void lobbyIsTheOriginCell() {
        assertEquals("0,0", PocketGrid.LOBBY_CELL);
    }

    @Test
    void cellKeyRoundTrip() {
        assertEquals("0,0", PocketGrid.cellKey(0, 0));
        assertEquals("12,-3", PocketGrid.cellKey(12, -3));
        PocketGrid.Cell parsed = PocketGrid.parseKey("12,-3");
        assertNotNull(parsed);
        assertEquals(12, parsed.x());
        assertEquals(-3, parsed.z());
    }

    @Test
    void malformedKeysReturnNull() {
        assertNull(PocketGrid.parseKey(null));
        assertNull(PocketGrid.parseKey(""));
        assertNull(PocketGrid.parseKey("12"));
        assertNull(PocketGrid.parseKey(",3"));
        assertNull(PocketGrid.parseKey("12,"));
        assertNull(PocketGrid.parseKey("a,b"));
    }

    @Test
    void cellCenterUsesPitchAndCenterY() {
        BlockPos origin = PocketGrid.cellCenter(0, 0);
        assertEquals(0, origin.getX());
        assertEquals(PocketGrid.CENTER_Y, origin.getY());
        assertEquals(0, origin.getZ());

        BlockPos far = PocketGrid.cellCenter(12, -3);
        assertEquals(12 * PocketGrid.PITCH, far.getX());
        assertEquals(PocketGrid.CENTER_Y, far.getY());
        assertEquals(-3 * PocketGrid.PITCH, far.getZ());
    }

    @Test
    void interiorBoundsAreCenteredAndSized() {
        PocketGrid.Bounds b = PocketGrid.interiorBounds(0, 0, 33);
        assertEquals(-16, b.minX());
        assertEquals(16, b.maxX());
        assertEquals(-16, b.minZ());
        assertEquals(16, b.maxZ());
        assertEquals(33, b.maxX() - b.minX() + 1);
        assertEquals(33, b.maxZ() - b.minZ() + 1);
        assertTrue(b.contains(0, 0));
        assertTrue(b.contains(16, -16));
        assertFalse(b.contains(17, 0), "outside the 33-wide interior");
    }

    @Test
    void interiorHalfSizeRejectsBadSizes() {
        assertThrows(IllegalArgumentException.class, () -> PocketGrid.interiorHalfSize(0));
        assertEquals(0, PocketGrid.interiorHalfSize(1));
        assertEquals(16, PocketGrid.interiorHalfSize(33));
        assertEquals(24, PocketGrid.interiorHalfSize(49));
    }

    @Test
    void neighborsDoNotOverlapAdjacentInteriors() {
        // Two adjacent cells with the default 33-wide interior must not
        // share a single column: 33 <= PITCH.
        PocketGrid.Bounds a = PocketGrid.interiorBounds(0, 0, 33);
        PocketGrid.Bounds b = PocketGrid.interiorBounds(1, 0, 33);
        assertTrue(a.maxX() < b.minX(), "adjacent interiors must not overlap");
    }

    @Test
    void spiralPathStartsOutsideTheLobbyAndNeverRepeats() {
        List<PocketGrid.Cell> path = PocketGrid.spiralPath(500);
        assertEquals(500, path.size());
        Set<String> seen = new HashSet<>();
        for (PocketGrid.Cell c : path) {
            String key = c.key();
            assertTrue(seen.add(key), "spiral path repeated cell " + key);
            assertFalse(PocketGrid.LOBBY_CELL.equals(key),
                    "spiral must not hand out the lobby cell");
        }
    }

    @Test
    void spiralPathIsRingOrdered() {
        // Ring 1 (radius 1) has 8 cells; ring 2 has 16; a full first
        // ring must complete before any radius-2 cell appears.
        List<PocketGrid.Cell> path = PocketGrid.spiralPath(8);
        for (PocketGrid.Cell c : path) {
            int radius = Math.max(Math.abs(c.x()), Math.abs(c.z()));
            assertEquals(1, radius, "first 8 cells belong to ring 1: " + c.key());
        }
        List<PocketGrid.Cell> wider = PocketGrid.spiralPath(24);
        for (int i = 0; i < 8; i++) {
            int radius = Math.max(Math.abs(wider.get(i).x()), Math.abs(wider.get(i).z()));
            assertEquals(1, radius);
        }
        for (int i = 8; i < 24; i++) {
            int radius = Math.max(Math.abs(wider.get(i).x()), Math.abs(wider.get(i).z()));
            assertEquals(2, radius, "cells 8..23 belong to ring 2");
        }
    }

    @Test
    void spiralPathRejectsNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> PocketGrid.spiralPath(-1));
        assertTrue(PocketGrid.spiralPath(0).isEmpty());
    }
}
