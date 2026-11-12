/*
 * Adapted from Dynamic Waters Async Rivers (https://github.com/erutobusiness/dynamicwaters-asyncrivers),
 * specifically net.erutobusiness.asyncrivers.MountainRegionStore.
 * Original work Copyright (c) 2026 erutobusiness, licensed under the MIT License;
 * the licence text is in THIRD_PARTY_NOTICES.md.
 */
package com.AdamNew.dynamicwaters.async;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.WatershedPath;
import com.AdamNew.dynamicwaters.terrain.WatershedLake;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import com.AdamNew.dynamicwaters.platform.Platform;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps built mountain-river regions on disk, so a region is computed once for the life of a world
 * instead of once per server start.
 *
 * <p><b>Why.</b> Dynamic Waters' region store is memory-only. Every restart throws it away, and
 * whoever touches a region first pays to rebuild it. Writing the result down turns "once per boot"
 * into "once, ever".
 *
 * <p><b>Layout.</b> {@code <world>/data/asyncrivers/v3/<dimension>/r.<x>.<z>.bin}, one file per
 * 256-block region: the region's river reaches followed by its lake masks.
 *
 * <p><b>Staleness.</b> Every file carries a stamp covering the world seed, the installed Dynamic
 * Waters version and the bytes of its config file. A file whose stamp does not match is ignored
 * and rebuilt, so a changed seed, a mod update, or an edited config can never hand back paths that
 * no longer match the terrain. Editing an unrelated setting also invalidates the cache; rebuilding
 * costs time, handing back stale paths costs correctness.
 *
 * <p><b>Torn files.</b> Each region is written to a sibling {@code .tmp} and moved into place, so a
 * crash mid-write leaves either the old file or none. A file that fails to read is treated as absent.
 */
public final class MountainRegionStore {

    /** Bump when the field layout below changes. Old files then fail the stamp check and rebuild. */
    private static final int FORMAT_VERSION = 4;

    private static final int MAGIC = 0x41535231; // "ASR1"

    private final Path root;
    private final long stamp;

    /**
     * Regions we already know are on disk carrying {@link #stamp}. {@link #writeIfAbsent} is called
     * from world generation once per region query; without this set every call would open a file
     * just to answer "already there?". Membership only grows, and only from places that have just
     * proven the file is good. Deliberately one-way: a region wrongly believed on disk is rebuilt
     * next start, which is still correct.
     */
    private final Set<Long> onDisk = ConcurrentHashMap.newKeySet();

    private MountainRegionStore(Path root, long stamp) {
        this.root = root;
        this.stamp = stamp;
    }

    private static long key(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    /**
     * Builds the store for a level, or returns {@code null} if we cannot (in which case the caller
     * simply computes every time, as before).
     */
    public static MountainRegionStore open(ServerLevel level) {
        try {
            Identifier dim = level.dimension().identifier();
            Path dir = level.getServer().getWorldPath(LevelResource.ROOT).normalize()
                    .resolve("data").resolve("asyncrivers").resolve("v" + FORMAT_VERSION)
                    .resolve(dim.getNamespace() + "_" + dim.getPath())
                    .toAbsolutePath().normalize();
            Files.createDirectories(dir);
            long stamp = stampFor(level);
            com.mojang.logging.LogUtils.getLogger().info("Async Rivers: region store at {} (stamp {}).",
                    dir, Long.toHexString(stamp));
            return new MountainRegionStore(dir, stamp);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("Async Rivers: no region store - regions will be rebuilt every start.", t);
            return null;
        }
    }

    private static long stampFor(ServerLevel level) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        DataOutputStream d = new DataOutputStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                md.update((byte) b);
            }
        });
        d.writeInt(FORMAT_VERSION);
        d.writeInt(com.AdamNew.dynamicwaters.terrain.BasinTile.ALGORITHM_VERSION);
        d.writeLong(level.getSeed());
        d.writeUTF(Platform.modVersion("dynamicwaters"));

        Path cfg = Platform.configDir()
                .resolve("dynamicwaters-common.toml");
        if (Files.isReadable(cfg)) {
            d.write(Files.readAllBytes(cfg));
        } else {
            d.writeUTF("no-config");
        }
        d.flush();

        byte[] h = md.digest();
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (h[i] & 0xFFL);
        }
        return v;
    }

    private Path fileFor(int regionX, int regionZ) {
        return root.resolve("r." + regionX + "." + regionZ + ".bin");
    }

    /** Returns the stored region, or {@code null} if there is nothing usable on disk. */
    public HydrologyManager.WatershedRegionData read(int regionX, int regionZ) {
        Path p = fileFor(regionX, regionZ);
        if (!Files.isReadable(p)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(p);
             DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in))) {
            if (d.readInt() != MAGIC || d.readInt() != FORMAT_VERSION || d.readLong() != stamp) {
                return null;   // another world / version / config: rebuild
            }
            int count = d.readInt();
            List<WatershedPath> paths = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int nodeCount = d.readInt();
                int validNodeCount = d.readInt();
                boolean hitMainRiver = d.readBoolean();
                double[] xs = readDoubles(d);
                double[] zs = readDoubles(d);
                int[] ys = readInts(d);
                int[] terrainYs = readInts(d);
                double[] widths = readDoubles(d);
                paths.add(new WatershedPath(nodeCount, validNodeCount, hitMainRiver, xs, zs, ys, terrainYs, widths));
            }
            int lakeCount = d.readInt();
            List<WatershedLake> lakes = new ArrayList<>(lakeCount);
            for (int i = 0; i < lakeCount; i++) {
                int waterY = d.readInt();
                int minCellX = d.readInt();
                int minCellZ = d.readInt();
                int w = d.readInt();
                int hh = d.readInt();
                int n = d.readInt();
                long[] bits = new long[n];
                for (int k = 0; k < n; k++) bits[k] = d.readLong();
                int cn = d.readInt();
                short[] coarse = new short[cn];
                for (int k = 0; k < cn; k++) coarse[k] = d.readShort();
                lakes.add(new WatershedLake(waterY, minCellX, minCellZ, w, hh, bits, coarse, d.readInt()));
            }
            onDisk.add(key(regionX, regionZ));
            return new HydrologyManager.WatershedRegionData(paths, lakes, regionX, regionZ);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("Async Rivers: could not read region {},{} - rebuilding it.", regionX, regionZ, t);
            return null;
        }
    }

    /** Writes a region only if there is no usable file for it yet. */
    public void writeIfAbsent(int regionX, int regionZ, HydrologyManager.WatershedRegionData data) {
        if (onDisk.contains(key(regionX, regionZ))) {
            return;
        }
        if (hasCurrent(regionX, regionZ)) {
            onDisk.add(key(regionX, regionZ));
            return;
        }
        write(regionX, regionZ, data);
    }

    /** True when a file exists for this region and carries our stamp. */
    private boolean hasCurrent(int regionX, int regionZ) {
        Path p = fileFor(regionX, regionZ);
        if (!Files.isReadable(p)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(p);
             DataInputStream d = new DataInputStream(in)) {
            return d.readInt() == MAGIC && d.readInt() == FORMAT_VERSION && d.readLong() == stamp;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Writes a region. Failures are logged and ignored; the cache is an optimisation, not state. */
    public void write(int regionX, int regionZ, HydrologyManager.WatershedRegionData data) {
        Path dst = fileFor(regionX, regionZ);
        Path tmp = dst.resolveSibling(dst.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp);
                 DataOutputStream d = new DataOutputStream(new java.io.BufferedOutputStream(out))) {
                d.writeInt(MAGIC);
                d.writeInt(FORMAT_VERSION);
                d.writeLong(stamp);
                List<WatershedPath> paths = data.allPaths;
                d.writeInt(paths.size());
                for (WatershedPath path : paths) {
                    d.writeInt(path.nodeCount);
                    d.writeInt(path.validNodeCount);
                    d.writeBoolean(path.hitMainRiver);
                    writeDoubles(d, path.segmentsX);
                    writeDoubles(d, path.segmentsZ);
                    writeInts(d, path.segmentsY);
                    writeInts(d, path.segmentsTerrainY);
                    writeDoubles(d, path.segmentWidths);
                }
                List<WatershedLake> lakes = data.lakes;
                d.writeInt(lakes.size());
                for (WatershedLake lake : lakes) {
                    d.writeInt(lake.waterY);
                    d.writeInt(lake.minCellX);
                    d.writeInt(lake.minCellZ);
                    d.writeInt(lake.cellsW);
                    d.writeInt(lake.cellsH);
                    d.writeInt(lake.bits.length);
                    for (long b : lake.bits) d.writeLong(b);
                    d.writeInt(lake.coarseH.length);
                    for (short s : lake.coarseH) d.writeShort(s);
                    d.writeInt(lake.dig);
                }
            }
            try {
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            onDisk.add(key(regionX, regionZ));
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("Async Rivers: could not write region {},{}.", regionX, regionZ, t);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // the next write overwrites it
            }
        }
    }

    private static double[] readDoubles(DataInputStream d) throws IOException {
        int n = d.readInt();
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = d.readDouble();
        }
        return a;
    }

    private static int[] readInts(DataInputStream d) throws IOException {
        int n = d.readInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = d.readInt();
        }
        return a;
    }

    private static void writeDoubles(DataOutputStream d, double[] a) throws IOException {
        d.writeInt(a.length);
        for (double v : a) {
            d.writeDouble(v);
        }
    }

    private static void writeInts(DataOutputStream d, int[] a) throws IOException {
        d.writeInt(a.length);
        for (int v : a) {
            d.writeInt(v);
        }
    }
}
