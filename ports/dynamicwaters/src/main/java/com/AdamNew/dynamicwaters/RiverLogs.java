package com.AdamNew.dynamicwaters;

import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RiverLogs {
    private static final int MIN_GAP = 3;
    private static final int MAX_GAP = 10;

    private static final int MAX_REST = 3;

    private static final int MAX_GROUND = 2;

    private static final int ROW_EDGE = 3;

    private static final int PROBE = 4;

    private static final int MOSS_ONE_IN = 3;
    private static final int WATER = Integer.MIN_VALUE;
    private static final int NEITHER = Integer.MIN_VALUE + 1;

    private RiverLogs() {
    }

    public static boolean wanted(WorldGenLevel level) {
        return Config.RIVER_FALLEN_LOGS.get().chance > 0.0 && Config.RIVER_SPLITS.get().chance > 0.0
                && Config.dynamicNormalRivers() && !Config.NORMAL_RIVER_TUNNELS.get()
                && HydrologyManager.riversIn(level.getLevel());
    }

    public static void lay(WorldGenLevel level, ChunkAccess chunk) {
        final long t0 = System.nanoTime();
        final ChunkPos pos = chunk.getPos();
        final long hash = HashCommon.mix(level.getSeed() ^ 0x70661E5L
                ^ (pos.x() * 341873128712L) ^ (pos.z() * 132897987541L));
        if ((hash >>> 11) * 0x1.0p-53 < Config.RIVER_FALLEN_LOGS.get().chance && hasIsland(pos)) {
            final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            final int water = level.getSeaLevel() - 1;
            final int[] ground = surface(chunk, pos, water, at);
            final int start = (int) (hash >>> 3) & 15;
            final boolean xFirst = (hash & 1L) == 0L;
            boolean laid = false;
            for (int pass = 0; pass < 2 && !laid; pass++) {
                final boolean alongX = (pass == 0) == xFirst;

                for (int n = 0; n < 16 && !laid; n++) {
                    final int row = (start + n * 7) & 15;
                    laid = row >= ROW_EDGE && row <= 15 - ROW_EDGE && layInRow(chunk, pos, ground, row, alongX, water, hash, at);
                }
            }
            if (laid) WatershedStats.riverLogs.increment();
        }
        WatershedStats.riverLogNanos.add(System.nanoTime() - t0);
        WatershedStats.riverLogsAsked.increment();
    }

    private static boolean hasIsland(ChunkPos pos) {
        for (int i = PROBE / 2; i < 16; i += PROBE) {
            for (int j = PROBE / 2; j < 16; j += PROBE) {
                if (HydrologyManager.riverIsland(pos.getMinBlockX() + i + 0.5, pos.getMinBlockZ() + j + 0.5)) return true;
            }
        }
        return false;
    }

    private static int[] surface(ChunkAccess chunk, ChunkPos pos, int water, BlockPos.MutableBlockPos at) {
        final int[] ground = new int[256];
        for (int i = 0; i < 256; i++) {
            final int x = pos.getMinBlockX() + (i & 15), z = pos.getMinBlockZ() + (i >> 4);
            final int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            final BlockState state = chunk.getBlockState(at.set(x, top, z));
            if (top == water && (state.getFluidState().is(FluidTags.WATER) || state.is(Blocks.ICE))) {
                ground[i] = WATER;
            } else if (top >= water && top <= water + MAX_GROUND && state.isSolid() && state.getFluidState().isEmpty()
                    && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                ground[i] = top;
            } else {
                ground[i] = NEITHER;
            }
        }
        return ground;
    }

    private static int column(int[] ground, int row, int i, boolean alongX) {
        return alongX ? ground[row * 16 + i] : ground[i * 16 + row];
    }

    private static boolean isGround(int column) {
        return column != WATER && column != NEITHER;
    }

    private static boolean layInRow(ChunkAccess chunk, ChunkPos pos, int[] ground, int row, boolean alongX, int water, long hash,
            BlockPos.MutableBlockPos at) {
        int i = 0;
        while (i < 15) {
            if (!isGround(column(ground, row, i, alongX)) || column(ground, row, i + 1, alongX) != WATER) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < 16 && column(ground, row, j, alongX) == WATER) j++;
            final int gap = j - i - 1;
            if (j < 16 && isGround(column(ground, row, j, alongX)) && gap >= MIN_GAP && gap <= MAX_GAP
                    && layTrunk(chunk, pos, ground, row, i, j, alongX, hash, at)) {
                return true;
            }
            i = j;
        }
        return false;
    }

    private static int blockX(ChunkPos pos, int row, int i, boolean alongX) {
        return pos.getMinBlockX() + (alongX ? i : row);
    }

    private static int blockZ(ChunkPos pos, int row, int i, boolean alongX) {
        return pos.getMinBlockZ() + (alongX ? row : i);
    }

    private static boolean layTrunk(ChunkAccess chunk, ChunkPos pos, int[] ground, int row, int a, int b, boolean alongX, long hash,
            BlockPos.MutableBlockPos at) {
        final double ax = blockX(pos, row, a, alongX) + 0.5, az = blockZ(pos, row, a, alongX) + 0.5;
        final double bx = blockX(pos, row, b, alongX) + 0.5, bz = blockZ(pos, row, b, alongX) + 0.5;
        if (!HydrologyManager.riverIsland(ax, az) && !HydrologyManager.riverIsland(bx, bz)) return false;
        if (!HydrologyManager.inLowlandWater((ax + bx) * 0.5, (az + bz) * 0.5)) return false;
        final int y = Math.max(column(ground, row, a, alongX), column(ground, row, b, alongX)) + 1;

        final int rest = 1 + (int) ((hash >>> 20) & 0xFF) % MAX_REST;
        int from = a, to = b;
        for (int k = 0; k < rest && from > 0 && restsOn(column(ground, row, from - 1, alongX), y); k++) from--;
        for (int k = 0; k < rest && to < 15 && restsOn(column(ground, row, to + 1, alongX), y); k++) to++;
        for (int i = from; i <= to; i++) {
            final BlockState state = chunk.getBlockState(at.set(blockX(pos, row, i, alongX), y, blockZ(pos, row, i, alongX)));
            if (!state.isAir() && (!state.canBeReplaced() || state.getBlock() instanceof DoublePlantBlock)) return false;
        }
        final BlockState wood = wood(chunk, pos, y, at).setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        final boolean cold = chunk.getNoiseBiome(QuartPos.fromBlock(blockX(pos, row, a, alongX)), QuartPos.fromBlock(y),
                QuartPos.fromBlock(blockZ(pos, row, a, alongX))).value().coldEnoughToSnow(at.set(ax, y, az), com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel());
        for (int i = from; i <= to; i++) {
            final int x = blockX(pos, row, i, alongX), z = blockZ(pos, row, i, alongX);
            chunk.setBlockState(at.set(x, y, z), wood, 3);
            if (cold || HashCommon.mix(hash + i) % MOSS_ONE_IN != 0) continue;
            if (chunk.getBlockState(at.set(x, y + 1, z)).isAir()) chunk.setBlockState(at, Blocks.MOSS_CARPET.defaultBlockState(), 3);
        }
        return true;
    }

    private static boolean restsOn(int column, int y) {
        return isGround(column) && column < y && column >= y - 2;
    }

    private static BlockState wood(ChunkAccess chunk, ChunkPos pos, int y, BlockPos.MutableBlockPos at) {
        for (int i = 0; i < 256; i++) {
            final int x = pos.getMinBlockX() + (i & 15), z = pos.getMinBlockZ() + (i >> 4);
            final BlockState state = chunk.getBlockState(at.set(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
            if (state.is(BlockTags.LOGS) && state.hasProperty(RotatedPillarBlock.AXIS)) {
                final String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                if (name.endsWith("_log") && !name.startsWith("stripped_")) return state.getBlock().defaultBlockState();
            }
        }
        final boolean cold = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getMinBlockX()), QuartPos.fromBlock(y),
                QuartPos.fromBlock(pos.getMinBlockZ())).value().coldEnoughToSnow(at.set(pos.getMinBlockX(), y, pos.getMinBlockZ()), com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel());
        return (cold ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).defaultBlockState();
    }
}
