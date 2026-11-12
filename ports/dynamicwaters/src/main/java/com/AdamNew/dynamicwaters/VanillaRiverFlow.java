package com.AdamNew.dynamicwaters;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaRiverFlow {
    public static final byte NONE = (byte) 255;

    private static final double STEP = 4.0;

    private static final double LOOK = 48.0;
    private static final int CACHE_LIMIT = 1 << 16;

    private static final ConcurrentHashMap<Long, Float> SERVER = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Long, byte[]> CLIENT = new ConcurrentHashMap<>();

    private VanillaRiverFlow() {
    }

    public static boolean active() {
        try {
            return Config.VANILLA_RIVER_FLOW.get() && Config.vanillaNormalRivers();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    public static Vec3 flowAt(Level level, double x, int y, double z) {
        final int qx = Mth.floor(x) >> 2, qz = Mth.floor(z) >> 2;
        final double angle;
        if (level instanceof ServerLevel server) {
            if (!active()) return null;
            final Holder<Biome> biome = server.getNoiseBiome(qx, (server.getSeaLevel() - 1) >> 2, qz);
            if (!biome.is(BiomeTags.IS_RIVER)) return null;
            angle = serverAngle(server, qx, qz);
        } else {
            angle = clientAngle(qx, qz);
        }
        if (Double.isNaN(angle)) return null;
        return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
    }

    private static double serverAngle(ServerLevel level, int qx, int qz) {
        final long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
        final Float cached = SERVER.get(key);
        if (cached != null) return cached;
        final float angle = (float) computeAngle(com.AdamNew.dynamicwaters.compat.OneDimension.randomState(level),
                qx * 4 + 2, qz * 4 + 2, level.getSeaLevel());
        if (SERVER.size() > CACHE_LIMIT) SERVER.clear();
        SERVER.put(key, angle);
        return angle;
    }

    static double computeAngle(net.minecraft.world.level.levelgen.RandomState randomState, double x, double z, int y) {
        final NoiseRouter router = com.AdamNew.dynamicwaters.terrain.WiredRouter.of(randomState);
        final DensitySampler ridges = randomState.getSampler(router.ridges()), continents = randomState.getSampler(router.continents());
        final double gx = sample(ridges, x + STEP, y, z) - sample(ridges, x - STEP, y, z);
        final double gz = sample(ridges, x, y, z + STEP) - sample(ridges, x, y, z - STEP);
        final double len = Math.sqrt(gx * gx + gz * gz);
        if (len < 1.0E-9) return Double.NaN;
        double tx = -gz / len, tz = gx / len;
        if (sample(continents, x + LOOK * tx, y, z + LOOK * tz) > sample(continents, x - LOOK * tx, y, z - LOOK * tz)) {
            tx = -tx;
            tz = -tz;
        }
        return Math.atan2(tz, tx);
    }

    private static double sample(DensitySampler f, double x, int y, double z) {
        return f.sampleValue(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED, Mth.floor(x), y, Mth.floor(z));
    }

    public static byte[] chunkAngles(ServerLevel level, LevelChunk chunk) {
        if (!active()) return null;
        final ChunkPos cp = chunk.getPos();
        final int qy = (level.getSeaLevel() - 1) >> 2;
        byte[] out = null;
        for (int lz = 0; lz < 4; lz++) {
            for (int lx = 0; lx < 4; lx++) {
                final int qx = (cp.x() << 2) + lx, qz = (cp.z() << 2) + lz;
                if (!chunk.getNoiseBiome(qx, qy, qz).is(BiomeTags.IS_RIVER)) continue;
                if (out == null) {
                    out = new byte[16];
                    Arrays.fill(out, NONE);
                }
                final double angle = serverAngle(level, qx, qz);
                if (!Double.isNaN(angle)) out[lx + lz * 4] = encode(angle);
            }
        }
        return out;
    }

    public static void putClientChunk(int chunkX, int chunkZ, byte[] angles) {
        if (angles == null || angles.length != 16) return;
        if (CLIENT.size() > CACHE_LIMIT) CLIENT.clear();
        CLIENT.put(ChunkPos.pack(chunkX, chunkZ), angles);
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    public static void clearServer() {
        SERVER.clear();
    }

    private static double clientAngle(int qx, int qz) {
        final byte[] angles = CLIENT.get(ChunkPos.pack(qx >> 2, qz >> 2));
        if (angles == null) return Double.NaN;
        final byte b = angles[(qx & 3) + (qz & 3) * 4];
        return b == NONE ? Double.NaN : decode(b);
    }

    static byte encode(double angle) {
        double a = angle % (Math.PI * 2.0);
        if (a < 0) a += Math.PI * 2.0;
        return (byte) (Math.round(a / (Math.PI * 2.0) * 255.0) % 255);
    }

    static double decode(byte b) {
        return (b & 0xFF) / 255.0 * Math.PI * 2.0;
    }
}
