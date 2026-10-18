package dev.foldedhorizons.core.dimension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Standalone self-check for {@link PocketGrid} — plain {@code main()}, no
 * JUnit dependency (the build has no test source set). Run with:
 * {@code java -cp core/build/classes/java/main dev.foldedhorizons.core.dimension.PocketGridCheck}
 */
public final class PocketGridCheck {

    private PocketGridCheck() {}

    public static void main(String[] args) {
        // 2000 cells spans rings 1..8 (sum 8r for r<=8 = 288) and beyond, so
        // multi-ring adjacency and ring transitions are covered.
        List<PocketGrid.Cell> path = PocketGrid.spiralPath(2000);

        // 1. No duplicates and no lobby anywhere on the path.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < path.size(); i++) {
            String key = path.get(i).key();
            if (!seen.add(key)) {
                throw new AssertionError("duplicate cell " + key + " at index " + i);
            }
            if (PocketGrid.LOBBY_CELL.equals(key)) {
                throw new AssertionError("lobby allocated at index " + i);
            }
        }

        // 2. Known ring-1 prefix, counter-clockwise from (1, -(1-1)).
        String[] expectedFirst8 = {
                "1,0", "1,1", "0,1", "-1,1", "-1,0", "-1,-1", "0,-1", "1,-1"
        };
        for (int i = 0; i < 8; i++) {
            if (!path.get(i).key().equals(expectedFirst8[i])) {
                throw new AssertionError("spiral[" + i + "]=" + path.get(i).key()
                        + " expected " + expectedFirst8[i]);
            }
        }

        // 3. Adjacent indices are spatially adjacent (Manhattan distance 1),
        //    including across ring boundaries (e.g. (1,-1) -> (2,0)).
        for (int i = 1; i < path.size(); i++) {
            PocketGrid.Cell a = path.get(i - 1);
            PocketGrid.Cell b = path.get(i);
            int d = Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
            if (d != 1) {
                throw new AssertionError("non-adjacent step at " + i + ": "
                        + a.key() + " -> " + b.key());
            }
        }

        // 4. Ring membership: indices [4r(r-1), 4r(r+1)) lie on ring r.
        int idx = 0;
        for (int r = 1; idx < path.size(); r++) {
            int ringLen = 8 * r;
            for (int i = idx; i < Math.min(idx + ringLen, path.size()); i++) {
                PocketGrid.Cell c = path.get(i);
                int ring = Math.max(Math.abs(c.x()), Math.abs(c.z()));
                if (ring != r) {
                    throw new AssertionError("index " + i + " cell " + c.key()
                            + " not on ring " + r);
                }
            }
            idx += ringLen;
        }

        // 5. cellCenter math: pitch multiplication and the fixed Y.
        PocketGrid.Cell c12m3 = PocketGrid.parseKey("12,-3");
        if (c12m3 == null || c12m3.x() != 12 || c12m3.z() != -3) {
            throw new AssertionError("parseKey wrong");
        }
        if (PocketGrid.cellCenter(12, -3).getX() != 12 * PocketGrid.PITCH
                || PocketGrid.cellCenter(12, -3).getZ() != -3 * PocketGrid.PITCH
                || PocketGrid.cellCenter(12, -3).getY() != PocketGrid.CENTER_Y) {
            throw new AssertionError("cellCenter math wrong");
        }

        // 6. Interior bounds with the default 33: ±16 around the center.
        PocketGrid.Bounds b = PocketGrid.interiorBounds(1, 0, 33);
        int center = PocketGrid.PITCH;
        if (b.minX() != center - 16 || b.maxX() != center + 16
                || b.minZ() != -16 || b.maxZ() != 16) {
            throw new AssertionError("interiorBounds(1,0,33)=" + b);
        }
        if (!b.contains(center, 0) || b.contains(center + 17, 0)
                || !b.contains(center - 16, 16)) {
            throw new AssertionError("bounds.contains wrong");
        }

        // 7. parseKey rejects malformed keys.
        for (String bad : new String[] {null, "", "bogus", "12,", ",3", "1,2,3", "a,b"}) {
            if (PocketGrid.parseKey(bad) != null) {
                throw new AssertionError("parseKey accepted " + bad);
            }
        }

        System.out.println("PocketGridCheck: all checks passed (" + path.size() + " cells)");
    }
}
