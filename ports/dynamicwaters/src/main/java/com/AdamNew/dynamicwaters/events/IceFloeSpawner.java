package com.AdamNew.dynamicwaters.events;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.DynamicWaters;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.entity.IceFloe;
import com.AdamNew.dynamicwaters.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class IceFloeSpawner {
    private record Rate(int every, int tries, int chance, int maxNear) {
    }

    private static final int GROUP_LEAST = 3;
    private static final int GROUP_MOST = 7;
    private static final double GROUP_REACH = 3.0;

    private static final int GROUP_TRIES = 6;

    private static Rate rate(Config.IceFloes how) {
        return switch (how) {
            case RARE -> new Rate(200, 3, 8, 8);
            case NORMAL -> new Rate(60, 4, 2, 24);
            case OFTEN -> new Rate(40, 6, 1, 40);
            case MANY -> new Rate(20, 8, 1, 64);
            default -> null;
        };
    }

    private static final int[] LAB_WHY = new int[7];

    public static void onLevelTick(ServerLevel level) {
        final Rate rate = rate(Config.RIVER_ICE_FLOES.get());
        if (rate == null || level.getGameTime() % rate.every() != 0 || !HydrologyManager.riversIn(level)) return;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) spawnNear(level, player.getX(), player.getZ(), rate);
        }
        final String lab = System.getProperty("dynamicwaters.labFloeAt");
        if (lab != null) {
            final String[] p = lab.split(",");
            spawnNear(level, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), rate);
            com.mojang.logging.LogUtils.getLogger().info("LABFLOE tries: no node {}, unloaded {}, not open water {}, falling {}, warm {}, no river {}, spawned {}",
                    LAB_WHY[0], LAB_WHY[1], LAB_WHY[2], LAB_WHY[3], LAB_WHY[4], LAB_WHY[5], LAB_WHY[6]);
        }
    }

    private static void spawnNear(ServerLevel level, double px, double pz, Rate rate) {
        final AABB near = new AABB(px - 96.0, level.getMinY(), pz - 96.0, px + 96.0, (level.getMaxY() + 1), pz + 96.0);
        if (level.getEntitiesOfClass(IceFloe.class, near).size() >= rate.maxNear()) return;
        final List<double[]> nodes = HydrologyManager.mountainRiverNodesNear(px, pz, 16.0, 64.0);
        if (nodes.isEmpty()) {
            LAB_WHY[0]++;
            return;
        }
        final RandomSource rnd = level.getRandom();
        for (int t = 0; t < rate.tries(); t++) {
            final double[] node = nodes.get(rnd.nextInt(nodes.size()));
            final int x = Mth.floor(node[0]), z = Mth.floor(node[1]);
            if (!level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z))) {
                LAB_WHY[1]++;
                continue;
            }
            final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            final BlockPos at = new BlockPos(x, top, z);
            final FluidState fluid = level.getFluidState(at);
            if (!fluid.is(FluidTags.WATER)) {
                LAB_WHY[2]++;
                continue;
            }
            if (fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) {
                LAB_WHY[3]++;
                continue;
            }
            if (!level.getBiome(at).value().coldEnoughToSnow(at, com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel())) {
                LAB_WHY[4]++;
                continue;
            }
            if (!HydrologyManager.isMountainRiverWater(x, top, z)) {
                LAB_WHY[5]++;
                continue;
            }
            if (rnd.nextInt(rate.chance()) != 0) continue;
            setGroupAfloat(level, x, top, z, rnd);
            LAB_WHY[6]++;
            return;
        }
    }

    private static void setGroupAfloat(ServerLevel level, int x, int top, int z, RandomSource rnd) {
        final int count = GROUP_LEAST + rnd.nextInt(GROUP_MOST - GROUP_LEAST + 1);
        int laid = 0;
        for (int i = 0; i < count * GROUP_TRIES && laid < count; i++) {
            final double angle = rnd.nextDouble() * Math.PI * 2.0, out = i == 0 ? 0.0 : 0.8 + rnd.nextDouble() * GROUP_REACH;
            final double fx = x + 0.5 + Math.cos(angle) * out, fz = z + 0.5 + Math.sin(angle) * out;
            final float size = IceFloe.randomSize(rnd);
            if (!clearOfFloes(level, fx, top, fz, size)) continue;
            final int cx = Mth.floor(fx), cz = Mth.floor(fz);
            if (!level.hasChunkAt(new BlockPos(cx, top, cz))) continue;
            final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) - 1;
            final BlockPos at = new BlockPos(cx, y, cz);
            final FluidState fluid = level.getFluidState(at);
            if (Math.abs(y - top) > 1 || !fluid.is(FluidTags.WATER)) continue;
            if (fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) continue;
            final IceFloe floe = ModEntities.ICE_FLOE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            if (floe == null) return;
            floe.setSize(size);
            floe.snapTo(fx, y + fluid.getHeight(level, at) + IceFloe.ABOVE_WATER - IceFloe.thickness(size), fz,
                    rnd.nextFloat() * 360.0f, 0.0f);
            level.addFreshEntity(floe);
            laid++;
        }
    }

    private static boolean clearOfFloes(ServerLevel level, double x, int y, double z, float size) {
        final double reach = IceFloe.keepApart(size, IceFloe.LARGEST);
        for (IceFloe other : level.getEntitiesOfClass(IceFloe.class, new AABB(x - reach, y - 3.0, z - reach, x + reach, y + 3.0, z + reach))) {
            if (Math.hypot(other.getX() - x, other.getZ() - z) < IceFloe.keepApart(size, other.size())) return false;
        }
        return true;
    }
}
