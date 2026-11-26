package com.AdamNew.dynamicwaters;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keeps other mods' cliff features off low river and lake banks. William Wyther's Overhauled Overworld's
 * {@code wythers:stony_cliffs} turns any steep slope's grass to bare stone, and the few blocks a river bank stands over
 * its water read as a cliff to it, so every stream and pool through grassland got a bare stone rim. The banks a guarded
 * feature changes are put back as they were; real gorges and mountain cliffs stand higher than {@link #MAX_RISE} and
 * keep their stone, and sea coasts are left to it. Ours, for Jaymo's Powers' pack.
 */
public final class BankGuard {
    /** Features kept off river banks. */
    private static final Set<Identifier> GUARDED = Set.of(Identifier.fromNamespaceAndPath("wythers", "stony_cliffs"));

    /** How far (blocks, square) from river or lake water a column counts as its bank. */
    private static final int REACH = 5;
    /** A bank top more than this over the water is a real cliff or gorge wall, and keeps what the feature made. */
    private static final int MAX_RISE = 8;
    /** Blocks put back, from the bank's top down. */
    private static final int DEPTH = 6;

    private BankGuard() {
    }

    public static boolean guards(Identifier feature) {
        return feature != null && GUARDED.contains(feature);
    }

    /** The low banks next to inland water in a chunk and the ring a feature can reach, with their top blocks. */
    public static Object[] before(WorldGenLevel level, int chunkX, int chunkZ) {
        final int x0 = (chunkX - 1) << 4, z0 = (chunkZ - 1) << 4, size = 48;
        final int[] top = new int[size * size];
        final int[] water = new int[size * size];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < top.length; i++) {
            top[i] = Integer.MIN_VALUE;
            water[i] = Integer.MIN_VALUE;
            final int x = x0 + i % size, z = z0 + i / size;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            top[i] = y;
            if (level.getBlockState(pos.set(x, y, z)).getFluidState().isEmpty()) continue;
            // the chunk's own noise biome: the level's smoothed lookup reads neighbours the decoration region may not hold
            if (HydrologyManager.isOceanBiome(level.getChunk(x >> 4, z >> 4).getNoiseBiome(x >> 2, y >> 2, z >> 2))) continue;
            water[i] = y;
        }
        final List<int[]> where = new ArrayList<>();
        final List<BlockState[]> was = new ArrayList<>();
        for (int i = 0; i < top.length; i++) {
            if (top[i] == Integer.MIN_VALUE || water[i] != Integer.MIN_VALUE) continue;
            final int cx = i % size, cz = i / size;
            int near = Integer.MIN_VALUE;
            for (int dz = -REACH; dz <= REACH && near == Integer.MIN_VALUE; dz++) {
                for (int dx = -REACH; dx <= REACH; dx++) {
                    final int nx = cx + dx, nz = cz + dz;
                    if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                    final int w = water[nx + nz * size];
                    if (w != Integer.MIN_VALUE) {
                        near = w;
                        break;
                    }
                }
            }
            if (near == Integer.MIN_VALUE || top[i] - near > MAX_RISE || top[i] < near) continue;
            final int x = x0 + cx, z = z0 + cz;
            final BlockState[] column = new BlockState[DEPTH + 1];
            for (int k = 0; k <= DEPTH; k++) column[k] = level.getBlockState(pos.set(x, top[i] + 1 - k, z));
            where.add(new int[] {x, top[i] + 1, z});
            was.add(column);
        }
        return where.isEmpty() ? null : new Object[] {where, was};
    }

    /** Puts back what the guarded feature changed on those banks. */
    @SuppressWarnings("unchecked")
    public static void after(WorldGenLevel level, Object[] snapshot) {
        if (snapshot == null) return;
        final List<int[]> where = (List<int[]>) snapshot[0];
        final List<BlockState[]> was = (List<BlockState[]>) snapshot[1];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < where.size(); k++) {
            final int[] w = where.get(k);
            final BlockState[] column = was.get(k);
            for (int d = 0; d < column.length; d++) {
                pos.set(w[0], w[1] - d, w[2]);
                if (level.getBlockState(pos) != column[d]) level.setBlock(pos, column[d], 2);
            }
        }
    }
}
