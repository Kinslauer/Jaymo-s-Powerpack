package com.AdamNew.dynamicwaters;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test tool: for the columns in a watched box, writes down every step of river carving that touched them while their
 * chunks generated, so a bad bank can be traced to the step that made it. Off until {@code /dynamicwaters watch} sets a
 * box; only chunks generated after that are noted.
 */
public final class ColumnWatch {
    private static final int MAX_LISTED = 60;
    private static final int WATER_NEAR = 4;

    private static volatile int[] box;
    private static final Map<Long, List<String>> NOTES = new ConcurrentHashMap<>();

    private ColumnWatch() {
    }

    public static boolean on(int x, int z) {
        final int[] b = box;
        return b != null && x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3];
    }

    public static void note(int x, int z, String step) {
        if (!on(x, z)) return;
        NOTES.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> Collections.synchronizedList(new ArrayList<>())).add(step);
    }

    public static String carving(HydrologyManager.RiverCarving c) {
        if (c == null) return "none";
        final StringBuilder sb = new StringBuilder();
        sb.append(c.modifies ? "cuts" : "leaves").append(" target=").append(c.targetY).append(" water=").append(c.waterLevel);
        if (c.sealFloorY != c.targetY) sb.append(" sealFloor=").append(c.sealFloorY);
        if (c.cavernRoofY > -999) sb.append(" roof=").append(c.cavernRoofY);
        if (c.wallTopY > -999) sb.append(" wallTop=").append(c.wallTopY);
        if (c.skinLevel != Integer.MIN_VALUE) sb.append(" skin=").append(c.skinLevel);
        if (c.isWatershed) sb.append(" watershed");
        if (c.isMountainRiver) sb.append(" stream");
        if (c.isWatershedWall) sb.append(" wall");
        if (c.isMainRiverWater) sb.append(" mainRiver");
        if (c.isLake) sb.append(" lake");
        if (c.isPillar) sb.append(" pillar");
        if (c.dressCut) sb.append(" dressCut");
        if (c.gorgeWall) sb.append(" gorgeWall");
        if (c.gorgeSteep) sb.append(" gorgeSteep");
        if (c.gorgeSeal) sb.append(" gorgeSeal");
        if (c.pointBar) sb.append(" pointBar");
        if (c.deltaFlat) sb.append(" deltaFlat");
        if (c.distanceFromCenter != Double.MAX_VALUE) {
            sb.append(String.format(" fromCentre=%.1f channel=%.1f waterR=%.1f slope=%.3f", c.distanceFromCenter,
                    c.channelWidth, c.waterRadius, c.channelSlope));
        }
        return sb.toString();
    }

    public static String block(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    static String watch(int x, int z, int radius) {
        NOTES.clear();
        box = new int[] {x - radius, z - radius, x + radius, z + radius};
        return String.format("Watching columns %d,%d to %d,%d. Only chunks generated from now on are noted; "
                + "go there, then /dynamicwaters watch report.", x - radius, z - radius, x + radius, z + radius);
    }

    static String stop() {
        box = null;
        NOTES.clear();
        return "Column watch off.";
    }

    static String column(ServerLevel level, int x, int z) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Column ").append(x).append(',').append(z).append(": ").append(top(level, x, z)).append('\n');
        final List<String> notes = NOTES.get(BlockPos.asLong(x, 0, z));
        if (notes == null) {
            sb.append(on(x, z) ? "  no notes (generated before the watch, or nothing touched it)"
                    : "  not in the watched box");
        } else {
            synchronized (notes) {
                for (String n : notes) sb.append("  ").append(n).append('\n');
            }
        }
        return sb.toString();
    }

    /** Finds watched columns whose top is bare rock just above water nearby, and lists what each went through. */
    static String report(ServerLevel level) {
        final int[] b = box;
        if (b == null) return "No column watch set: /dynamicwaters watch <x> <z> <radius> first.";
        final int water = level.getSeaLevel();
        int bare = 0, listed = 0, unnoted = 0;
        final Map<String, Integer> lastSteps = new TreeMap<>();
        final StringBuilder log = new StringBuilder("[DW watch] report\n");
        for (int x = b[0]; x <= b[2]; x++) {
            for (int z = b[1]; z <= b[3]; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                final BlockState top = level.getBlockState(new BlockPos(x, y, z));
                if (!rock(top) || !waterNear(level, x, z, y)) continue;
                bare++;
                final List<String> notes = NOTES.get(BlockPos.asLong(x, 0, z));
                String last = "no step (vanilla or a later feature)";
                if (notes == null) {
                    unnoted++;
                } else {
                    synchronized (notes) {
                        for (String n : notes) {
                            if (n.startsWith("placed ") || n.startsWith("cut ") || n.startsWith("dress ")
                                    || n.startsWith("skin ") || n.startsWith("boulder ") || n.startsWith("seal ")
                                    || n.startsWith("repair ") || n.startsWith("refill ") || n.startsWith("pool ")) {
                                last = n.split(" ", 3)[0] + " " + n.split(" ", 3)[1];
                            }
                        }
                    }
                }
                lastSteps.merge(last, 1, Integer::sum);
                if (listed < MAX_LISTED) {
                    listed++;
                    log.append(column(level, x, z));
                }
            }
        }
        LogUtils.getLogger().info(log.toString());
        final StringBuilder sb = new StringBuilder();
        sb.append(bare).append(" bare-rock bank columns (top is stone, water within ").append(WATER_NEAR)
                .append(" blocks, sea ").append(water).append(")");
        if (unnoted > 0) sb.append(", ").append(unnoted).append(" with no notes");
        sb.append(". Last step that touched them:\n");
        lastSteps.forEach((k, v) -> sb.append("  ").append(v).append("x ").append(k).append('\n'));
        sb.append("First ").append(listed).append(" written to the log under [DW watch].");
        return sb.toString();
    }

    private static String top(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return "chunk not loaded";
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        final StringBuilder sb = new StringBuilder("top y=").append(y);
        for (int k = 0; k < 5; k++) sb.append(k == 0 ? " " : ", ").append(block(level.getBlockState(new BlockPos(x, y - k, z))));
        return sb.toString();
    }

    private static boolean rock(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE);
    }

    private static boolean waterNear(ServerLevel level, int x, int z, int y) {
        for (int dx = -WATER_NEAR; dx <= WATER_NEAR; dx++) {
            for (int dz = -WATER_NEAR; dz <= WATER_NEAR; dz++) {
                if (!level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) continue;
                final int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
                if (ny <= y && !level.getBlockState(new BlockPos(x + dx, ny, z + dz)).getFluidState().isEmpty()) return true;
            }
        }
        return false;
    }
}
