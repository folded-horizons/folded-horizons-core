package dev.foldedhorizons.core.tools;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Dev-time generator for the pocket structure templates shipped in the mod
 * jar: {@code data/foldedhorizons/structure/starter_cell.nbt} and
 * {@code lobby.nbt}.
 *
 * <p>Why this hand-assembles the NBT instead of calling
 * {@code StructureTemplate}: filling a StructureTemplate headlessly needs a
 * live world ({@code fillFromWorld}) or reflective access to private state,
 * neither of which works in a plain JVM run. The output below is byte-for-byte
 * the format {@code StructureTemplate#save} writes (gzip-compressed root
 * compound with {@code size}, {@code blocks}, {@code palette}, {@code
 * entities}, {@code DataVersion}), so the vanilla structure placement path
 * reads it unchanged.</p>
 *
 * <p>Air blocks are included (vanilla templates do the same) so placing a
 * pocket over a previously touched region still clears the interior.</p>
 *
 * <p>Run via the {@code generateTemplates} Gradle task, or directly:
 * {@code java -cp <runtime classpath> dev.foldedhorizons.core.tools.TemplateGenerator
 * [output-dir]} — default output is
 * {@code core/src/main/resources/data/foldedhorizons/structure}.</p>
 */
public final class TemplateGenerator {

    /** 1.21.1 data version; readers reject templates from newer versions. */
    private static final int DATA_VERSION = 3955;

    private static final String SHELL = "foldedhorizons:wall_shell";
    private static final String STONE = "foldedhorizons:wall_stone";
    private static final String WOOD = "foldedhorizons:wall_wood";
    private static final String METAL = "foldedhorizons:wall_metal";

    public static void main(String[] args) throws IOException {
        Path outDir = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("core/src/main/resources/data/foldedhorizons/structure");
        Files.createDirectories(outDir);
        write(outDir, "starter_cell.nbt", starterCell());
        write(outDir, "lobby.nbt", lobby());
        write(outDir, "meteorite_pocket.nbt", meteoritePocket());
    }

    private static void write(Path dir, String name, CompoundTag template) throws IOException {
        Path file = dir.resolve(name);
        NbtIo.writeCompressed(template, file);
        System.out.println("wrote " + file.toAbsolutePath());
    }

    /**
     * Sealed starter cell: 13x13x9 shell, 9x9 interior. Bottom and top caps
     * plus the outer ring are unbreakable {@code wall_shell}; the inner ring
     * the player mines is {@code wall_stone}/{@code wall_wood}/
     * {@code wall_metal}; floor is {@code wall_stone}. No crafting table;
     * a flower pot stands in for the bonsai pot until it is implemented.
     */
    public static CompoundTag starterCell() {
        Builder b = new Builder(13, 9, 13);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 13; x++) {
                for (int z = 0; z < 13; z++) {
                    boolean outer = x == 0 || x == 12 || z == 0 || z == 12;
                    boolean innerRing = x == 1 || x == 11 || z == 1 || z == 11;
                    if (outer) {
                        b.set(x, y, z, SHELL);
                    } else if (innerRing) {
                        b.set(x, y, z, innerWall(x, z));
                    } else if (y == 1) {
                        b.set(x, y, z, STONE); // interior floor
                    } else {
                        b.set(x, y, z, "minecraft:air");
                    }
                }
            }
        }
        b.set(6, 2, 6, "minecraft:flower_pot"); // bonsai pot placeholder
        b.set(3, 2, 3, "minecraft:lantern");    // only light source
        return b.build();
    }

    /**
     * Lobby plaza: 32x32 open platform, iron-block border, flush grid of
     * routing markers every 8 blocks, lit corner pillars, clear center for
     * player-placed breachers and trade posts.
     */
    public static CompoundTag lobby() {
        Builder b = new Builder(32, 4, 32);
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                boolean border = x == 0 || x == 31 || z == 0 || z == 31;
                if (border) {
                    b.set(x, 0, z, "minecraft:iron_block");
                } else if (x % 8 == 0 && z % 8 == 0) {
                    b.set(x, 0, z, "minecraft:light_gray_concrete"); // routing marker
                } else {
                    b.set(x, 0, z, "minecraft:stone_bricks");
                }
            }
        }
        for (int[] p : new int[][]{{2, 2}, {29, 2}, {2, 29}, {29, 29}}) {
            b.set(p[0], 1, p[1], "minecraft:stone_bricks");
            b.set(p[0], 2, p[1], "minecraft:stone_bricks");
            b.set(p[0], 3, p[1], "minecraft:lantern");
        }
        return b.build();
    }

    private static String innerWall(int x, int z) {
        boolean xEdge = x == 1 || x == 11;
        boolean zEdge = z == 1 || z == 11;
        if (xEdge && zEdge) {
            return METAL; // corners
        }
        return xEdge ? WOOD : STONE;
    }

    /**
     * Demo meteorite pocket (dimension-gen multiblock's one dedicated
     * target): a ~9x9 rock cluster rising from a small crater floor, with
     * a chest of demo loot and a light. Core owns no AE2 blocks, so the
     * "skystone" is placeholder stone and the loot is plain — the
     * foldedhorizons-ae2 module or a datapack replaces this template with
     * the real skystone crust, Inscriber Presses and budding certus patch
     * under the same id ({@code foldedhorizons:meteorite_pocket}).
     */
    public static CompoundTag meteoritePocket() {
        Builder b = new Builder(9, 6, 9);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                b.set(x, 0, z, STONE); // crater floor
            }
        }
        // The meteorite blob: a dome-ish cluster of "skystone" placeholder.
        int[][] layers = {
                {2, 6, 2, 6},   // y=1: 5x5 base (x..x, z..z inclusive)
                {3, 5, 3, 5},   // y=2: 3x3
                {4, 4, 4, 4},   // y=3: 1x1 tip
        };
        for (int i = 0; i < layers.length; i++) {
            int[] layer = layers[i];
            for (int x = layer[0]; x <= layer[1]; x++) {
                for (int z = layer[2]; z <= layer[3]; z++) {
                    b.set(x, 1 + i, z, METAL);
                }
            }
        }
        // Demo loot + light at the crater edge.
        b.set(1, 1, 1, "minecraft:chest");
        b.set(7, 1, 7, "minecraft:lantern");
        return b.build();
    }

    /** Accumulates non-air-deduplicated palette entries and block positions. */
    private static final class Builder {
        private final int sx;
        private final int sy;
        private final int sz;
        private final Map<String, Integer> palette = new HashMap<>();
        private final ListTag paletteList = new ListTag();
        private final ListTag blocks = new ListTag();

        Builder(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        void set(int x, int y, int z, String block) {
            String key = block;
            Integer index = palette.get(key);
            if (index == null) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Name", block);
                index = paletteList.size();
                palette.put(key, index);
                paletteList.add(entry);
            }
            CompoundTag blockTag = new CompoundTag();
            blockTag.put("pos", pos(x, y, z));
            blockTag.put("state", IntTag.valueOf(index));
            blocks.add(blockTag);
        }

        CompoundTag build() {
            CompoundTag root = new CompoundTag();
            root.put("size", intList(sx, sy, sz));
            root.put("palette", paletteList);
            root.put("entities", new ListTag());
            root.put("blocks", blocks);
            root.putInt("DataVersion", DATA_VERSION);
            return root;
        }

        /**
         * A vector as {@code ListTag<TAG_INT>} — the shape
         * {@code StructureTemplate.load} reads. It uses
         * {@code getList("size", TAG_INT)} / {@code getList("pos", TAG_INT)},
         * so an {@code IntArrayTag} silently reads back as an empty list, which
         * makes every template load with size (0,0,0) and place nothing.
         */
        private static ListTag intList(int... values) {
            ListTag list = new ListTag();
            for (int value : values) {
                list.add(IntTag.valueOf(value));
            }
            return list;
        }

        private ListTag pos(int x, int y, int z) {
            return intList(x, y, z);
        }
    }

    private TemplateGenerator() {}
}
