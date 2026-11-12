package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseChunkGeneratorMixin {
    private static final ThreadLocal<int[][]> ORIGINAL_HEIGHT_CACHE = ThreadLocal.withInitial(() -> new int[16][16]);

    @org.spongepowered.asm.mixin.Unique
    private static final int[] DYNAMICWATERS$SIDE_X = {1, -1, 0, 0};
    @org.spongepowered.asm.mixin.Unique
    private static final int[] DYNAMICWATERS$SIDE_Z = {0, 0, 1, -1};

    @org.spongepowered.asm.mixin.Unique
    private static void dynamicwaters$containWater(ChunkAccess chunk, int startX, int startZ,
            HydrologyManager.RiverCarving[] carvings, int[] original, boolean[] repaired, java.util.BitSet[] protectedMap,
            int minH) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        final int[] bed = new int[256];
        for (int i = 0; i < 256; i++) {
            final HydrologyManager.RiverCarving c = carvings[i];
            bed[i] = Integer.MAX_VALUE;
            if (c == null || !(HydrologyManager.holdsWater(c) || repaired[i])) continue;
            final int floor = c.modifies ? c.targetY : original[i];
            if (c.isWatershedWall || c.waterLevel <= floor) continue;
            bed[i] = floor;
            low = Math.min(low, floor + 1);
            high = Math.max(high, c.waterLevel);
        }
        if (low > high) return;
        HydrologyManager.rememberWaterBand(chunk.getPos().pack(), low, high);
        final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            if (bed[i] == Integer.MAX_VALUE) continue;
            final int x = i & 15, z = i >> 4;
            for (int y = bed[i] + 1; y <= carvings[i].waterLevel; y++) {
                if (!chunk.getBlockState(at.set(startX + x, y, startZ + z)).is(Blocks.WATER)) continue;
                for (int d = 0; d < 4; d++) {
                    final int nx = x + DYNAMICWATERS$SIDE_X[d], nz = z + DYNAMICWATERS$SIDE_Z[d];
                    if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
                    final int j = nx + nz * 16;
                    final HydrologyManager.RiverCarving n = carvings[j];
                    if (HydrologyManager.holdsWater(n) && y > n.waterLevel) continue;
                    final java.util.BitSet p = protectedMap[j];
                    if (p != null && y >= minH && p.get(y - minH)) continue;
                    at.set(startX + nx, y, startZ + nz);
                    if (chunk.getBlockState(at).isAir()) chunk.setBlockState(at, HydrologyManager.holdFill(chunk, at), 3);
                }
            }
        }
    }

    /**
     * 26.3 builds noise, surface and carvers in one async task that no longer hands the region and structure manager
     * to the surface step, so buildTerrain leaves them here for the chunk it is building.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final java.util.Map<ChunkAccess, Object[]> DYNAMICWATERS$TERRAIN =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    @org.spongepowered.asm.mixin.Unique
    private void dynamicwaters$setRiverBiome(ChunkAccess chunk, int x, int y, int z, Holder<Biome> riverBiome, Holder<Biome> frozenRiverBiome) {
    }

    @org.spongepowered.asm.mixin.Unique
    private static java.util.BitSet[] dynamicwaters$buildProtectionMap(ChunkAccess level, StructureManager structureManager,
            ChunkPos chunkPos, int startX, int startZ) {
        java.util.List<net.minecraft.world.level.levelgen.structure.StructureStart> chunkStructures = structureManager.startsForStructure(chunkPos.x(), chunkPos.z(), s -> true);
        java.util.BitSet[] protectedBlocksMap = new java.util.BitSet[256];
        int minH = level.getMinY();

        for (net.minecraft.world.level.levelgen.structure.StructureStart start : chunkStructures) {
            if (com.AdamNew.dynamicwaters.AbridgedCompat.isBridge(start.getStructure())) continue;
            net.minecraft.world.level.levelgen.structure.BoundingBox sBox = start.getBoundingBox();
            int pad = 8;

            if (sBox.maxX() + pad >= startX && sBox.minX() - pad <= startX + 15 &&
                sBox.maxZ() + pad >= startZ && sBox.minZ() - pad <= startZ + 15) {
                for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                    net.minecraft.world.level.levelgen.structure.BoundingBox pBox = piece.getBoundingBox();
                    if (pBox.maxX() + pad >= startX && pBox.minX() - pad <= startX + 15 &&
                        pBox.maxZ() + pad >= startZ && pBox.minZ() - pad <= startZ + 15) {
                        int pMinX = Math.max(0, pBox.minX() - startX);
                        int pMaxX = Math.min(15, pBox.maxX() - startX);
                        int pMinZ = Math.max(0, pBox.minZ() - startZ);
                        int pMaxZ = Math.min(15, pBox.maxZ() - startZ);

                        for (int px = pMinX; px <= pMaxX; px++) {
                            for (int pz = pMinZ; pz <= pMaxZ; pz++) {
                                int idx = px + pz * 16;
                                if (protectedBlocksMap[idx] == null) {
                                    protectedBlocksMap[idx] = new java.util.BitSet((level.getMaxY() + 1) - minH + 1);
                                }
                                for (int sy = pBox.minY(); sy <= pBox.maxY(); sy++) {
                                    int bitIndex = sy - minH;
                                    if (bitIndex >= 0) {
                                        protectedBlocksMap[idx].set(bitIndex);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return protectedBlocksMap;
    }

    @Inject(method = "buildTerrain", at = @At("HEAD"))
    private void dynamicwaters$prefetchRivers(ChunkAccess chunk, net.minecraft.world.level.levelgen.blending.Blender blender,
            RandomState randomState, StructureManager structureManager, net.minecraft.world.level.biome.BiomeManager biomeManager,
            WorldGenRegion region, java.util.Set<Holder<Biome>> possibleBiomes,
            CallbackInfoReturnable<java.util.concurrent.CompletableFuture<ChunkAccess>> cir) {
        // One Dimension's CustomChunks passes no region for a band, but its structure manager was made for the band's region
        final WorldGenRegion terrainRegion = region != null ? region
                : ((StructureManagerAccessor) structureManager).dynamicwaters$level() instanceof WorldGenRegion bandRegion ? bandRegion : null;
        if (terrainRegion != null) DYNAMICWATERS$TERRAIN.put(chunk, new Object[] {terrainRegion, structureManager});

        if ((Object) this != HydrologyManager.getGlobalGenerator()) {
            return;
        }

        net.minecraft.world.level.ChunkPos pos = chunk.getPos();
        com.AdamNew.dynamicwaters.MainRivers.prefetchAround(
            pos.getMiddleBlockX(), pos.getMiddleBlockZ(),
            HydrologyManager.getGlobalGenerator(), chunk, randomState);

        com.AdamNew.dynamicwaters.HydrologyManager.prefetchWatershedsAround(
            pos.getMiddleBlockX(), pos.getMiddleBlockZ(),
            HydrologyManager.getGlobalGenerator(), chunk, randomState);
    }

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void dynamicwaters$carveAndFillRiver(ChunkAccess chunk, net.minecraft.world.level.levelgen.NoiseChunk noiseChunk,
            RandomState random, net.minecraft.world.level.biome.BiomeManager biomeManager, java.util.Set<Holder<Biome>> possibleBiomes,
            net.minecraft.world.level.levelgen.material.rule.MaterialRule materialRule, CallbackInfo ci) {
        final Object[] terrain = DYNAMICWATERS$TERRAIN.get(chunk);
        if (terrain == null) return;
        final WorldGenRegion level = (WorldGenRegion) terrain[0];
        final StructureManager structureManager = (StructureManager) terrain[1];
        if (level.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD
                || !com.AdamNew.dynamicwaters.compat.OneDimension.carves((net.minecraft.world.level.chunk.ChunkGenerator) (Object) this)) {
            return;
        }

        HydrologyManager.setSeed(level.getLevel().getSeed());
        HydrologyManager.setSeaLevel(((NoiseBasedChunkGenerator)(Object)this).getSeaLevel());

        net.minecraft.core.Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        Holder<Biome> riverBiome = biomeRegistry.getOrThrow(net.minecraft.world.level.biome.Biomes.RIVER);
        HydrologyManager.riverBiomeHolder = riverBiome;
        Holder<Biome> frozenRiverBiome = biomeRegistry.getOrThrow(net.minecraft.world.level.biome.Biomes.FROZEN_RIVER);

        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();

        int seaLevel = ((NoiseBasedChunkGenerator)(Object)this).getSeaLevel();

        int minH = chunk.getMinY();
        java.util.BitSet[] protectedBlocksMap = dynamicwaters$buildProtectionMap(chunk, structureManager, chunkPos, startX, startZ);

        final int[] preCarve = new int[256];
        HydrologyManager.rememberPreCarveHeights(chunkPos.pack(), preCarve);

        final BlockState poolGround = ((NoiseBasedChunkGenerator) (Object) this).generatorSettings().value().defaultBlock();

        final com.AdamNew.dynamicwaters.SurfaceGround surfaceGround = new com.AdamNew.dynamicwaters.SurfaceGround(
                (NoiseBasedChunkGenerator) (Object) this, level, chunk, random,
                () -> noiseChunk);

        @SuppressWarnings("unchecked")
        final Holder<Biome>[] columnBiomes = new Holder[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int originalY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);

                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = chunk.getNoiseBiome(x >> 2, originalY >> 2, z >> 2);

                if (originalY < seaLevel - 1 && HydrologyManager.poolFilled(startX + x, startZ + z, originalY, seaLevel,
                        biomeHolder, (net.minecraft.world.level.chunk.ChunkGenerator) (Object) this, random)) {
                    final java.util.BitSet guard = protectedBlocksMap[x + z * 16];
                    final int g0 = Math.max(0, originalY - 2 - minH), g1 = Math.max(0, seaLevel + 2 - minH);
                    if (guard == null || guard.nextSetBit(g0) < 0 || guard.nextSetBit(g0) > g1) {
                        originalY = HydrologyManager.fillPool(chunk, startX + x, startZ + z, originalY, seaLevel, poolGround);
                    }
                }
                preCarve[x + z * 16] = originalY;
                columnBiomes[x + z * 16] = biomeHolder;
            }
        }
        com.AdamNew.dynamicwaters.LakeHollows.setChunkGround(startX, startZ, preCarve);

        final HydrologyManager.RiverCarving[] carvings = new HydrologyManager.RiverCarving[256];
        for (int i = 0; i < 256; i++) {
            if (HydrologyManager.isOceanBiome(columnBiomes[i]) && preCarve[i] < seaLevel - 4) continue;
            carvings[i] = HydrologyManager.calculateRiverCarving(startX + (i & 15), startZ + (i >> 4), preCarve[i], seaLevel,
                    (net.minecraft.world.level.chunk.ChunkGenerator) (Object) this, chunk, random);
        }
        final int[] guard = HydrologyManager.caveGuard(carvings);

        final boolean[] mouths = new boolean[256];
        boolean refilled = false;
        for (int i = 0; i < 256; i++) {
            final int sx = startX + (i & 15), sz = startZ + (i >> 4);
            final HydrologyManager.RiverCarving shaft = carvings[i];
            if (shaft == null || !HydrologyManager.dryHollow(chunk, sx, sz, preCarve[i], seaLevel)) continue;

            final boolean lays = shaft.modifies
                    ? HydrologyManager.holdsWater(shaft) && shaft.targetY >= preCarve[i]
                    : shaft.waterLevel > preCarve[i] && HydrologyManager.repairsColumn(sx, sz,
                            (net.minecraft.world.level.chunk.ChunkGenerator) (Object) this, chunk, random);
            if (lays) {
                mouths[i] = true;
                guard[i] = Math.max(guard[i], shaft.waterLevel);
                continue;
            }
            if (guard[i] == Integer.MIN_VALUE) continue;
            final int was = preCarve[i];
            preCarve[i] = HydrologyManager.fillHollow(chunk, sx, sz, was, seaLevel - 1, poolGround, protectedBlocksMap[i], minH);
            if (preCarve[i] == was) continue;
            refilled = true;

            int rim = preCarve[i];
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    final int nx = (i & 15) + dx, nz = (i >> 4) + dz;
                    if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) rim = Math.max(rim, preCarve[nx + nz * 16]);
                }
            }
            HydrologyManager.gorgeDress(chunk, sx, sz, preCarve[i], seaLevel - 1, false, protectedBlocksMap[i], minH,
                    surfaceGround, rim);
        }

        final int[] laidBed = new int[256];
        System.arraycopy(preCarve, 0, laidBed, 0, 256);
        final boolean[] channelBed = new boolean[256];
        final boolean[] unknown = new boolean[256];
        for (int i = 0; i < 256; i++) {
            if (!mouths[i]) continue;
            final int water = carvings[i].waterLevel;
            final HydrologyManager.RiverCarving bed = HydrologyManager.calculateRiverCarving(startX + (i & 15), startZ + (i >> 4),
                    water + 1, seaLevel, (net.minecraft.world.level.chunk.ChunkGenerator) (Object) this, chunk, random);
            if (bed.modifies && !bed.isWatershedWall && bed.waterLevel >= water && bed.targetY < water - 1) {
                laidBed[i] = bed.targetY;
                channelBed[i] = true;
            } else {
                unknown[i] = true;
            }
        }
        for (int i = 0; i < 256; i++) {
            if (!mouths[i]) continue;
            final int was = preCarve[i];
            final int sx = startX + (i & 15), sz = startZ + (i >> 4);
            if (channelBed[i]) {
                final int floor = Math.min(laidBed[i], HydrologyManager.shaftFloor(i, Integer.MIN_VALUE, carvings[i].waterLevel,
                        laidBed, carvings, unknown, channelBed));
                preCarve[i] = floor < was ? HydrologyManager.digHollow(chunk, sx, sz, was, floor, protectedBlocksMap[i], minH)
                        : HydrologyManager.fillHollow(chunk, sx, sz, was, floor, poolGround, protectedBlocksMap[i], minH);
            } else {
                final int floor = HydrologyManager.shaftFloor(i, was, carvings[i].waterLevel, laidBed, carvings, unknown, channelBed);
                preCarve[i] = HydrologyManager.fillHollow(chunk, sx, sz, was, floor, poolGround, protectedBlocksMap[i], minH);
            }
            if (preCarve[i] != was) refilled = true;
        }
        for (int i = 0; i < 256; i++) {
            if (guard[i] == Integer.MIN_VALUE) continue;
            HydrologyManager.cancelCaves(chunk, startX + (i & 15), startZ + (i >> 4), preCarve[i], guard[i], poolGround,
                    protectedBlocksMap[i], minH);
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double worldX = startX + x;
                double worldZ = startZ + z;

                int originalY = preCarve[x + z * 16];
                final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = columnBiomes[x + z * 16];
                if (HydrologyManager.isOceanBiome(biomeHolder) && originalY < seaLevel - 4) {
                    continue;
                }

                HydrologyManager.RiverCarving carving = !refilled && carvings[x + z * 16] != null ? carvings[x + z * 16]
                        : HydrologyManager.calculateRiverCarving(worldX, worldZ, originalY, seaLevel, ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this), chunk, random);
                int targetY = carving.targetY;
                int cavernRoofY = carving.cavernRoofY;
                int carveTopY = Math.min(originalY, cavernRoofY);

                java.util.BitSet protectedBlocks = protectedBlocksMap[x + z * 16];
                if (protectedBlocks == null) {
                    protectedBlocks = new java.util.BitSet(0);
                }

                boolean hasSurfaceStructure = false;
                if (!protectedBlocks.isEmpty()) {
                    for (int sy = targetY - 2; sy <= carveTopY + 5; sy++) {
                        int bitIndex = sy - minH;
                        if (bitIndex >= 0 && protectedBlocks.get(bitIndex)) {
                            hasSurfaceStructure = true;
                            break;
                        }
                    }
                }

                if (carving.gorgeSeal && !hasSurfaceStructure) {
                    HydrologyManager.gorgeSeal(chunk, startX + x, startZ + z, seaLevel, originalY, protectedBlocks, minH);
                }

                if (carving.modifies) {
                    BlockState topBlock = Blocks.GRASS_BLOCK.defaultBlockState();
                    BlockState fillerBlock = Blocks.DIRT.defaultBlockState();
                    BlockState baseBlock = Blocks.STONE.defaultBlockState();

                    for (int ty = originalY; ty > Math.max(chunk.getMinY(), originalY - 10); ty--) {
                        BlockState s = chunk.getBlockState(new BlockPos(startX + x, ty, startZ + z));
                        boolean isSOre = s.is(com.AdamNew.dynamicwaters.platform.ModTags.ORES);
                        if (s.isSolid() && !isSOre) {
                            topBlock = s;
                            fillerBlock = s;
                            baseBlock = s;

                            for (int fy = ty - 1; fy > Math.max(chunk.getMinY(), ty - 6); fy--) {
                                BlockState fs = chunk.getBlockState(new BlockPos(startX + x, fy, startZ + z));
                                boolean isFsOre = fs.is(com.AdamNew.dynamicwaters.platform.ModTags.ORES);
                                if (fs.isSolid() && !fs.equals(topBlock) && !isFsOre) {
                                    fillerBlock = fs;
                                    baseBlock = fs;

                                    for (int by = fy - 1; by > Math.max(chunk.getMinY(), fy - 15); by--) {
                                        BlockState bs = chunk.getBlockState(new BlockPos(startX + x, by, startZ + z));
                                        boolean isBsOre = bs.is(com.AdamNew.dynamicwaters.platform.ModTags.ORES);
                                        if (bs.isSolid() && !bs.equals(fillerBlock) && !isBsOre) {
                                            baseBlock = bs;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    if (hasSurfaceStructure) {
                        continue;
                    }

                    if (carving.isWatershedWall && targetY < originalY) {
                        for (int y = carveTopY; y > targetY; y--) {
                            if (y >= minH && protectedBlocks.get(y - minH)) continue;
                            chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), Blocks.AIR.defaultBlockState(), 3);
                        }
                        if (targetY >= minH && !protectedBlocks.get(targetY - minH)) {
                            boolean steep = (carving.waterLevel > targetY + 3);
                            if (steep) {
                                chunk.setBlockState(new BlockPos(startX + x, targetY, startZ + z), Blocks.GRAVEL.defaultBlockState(), 3);
                                chunk.setBlockState(new BlockPos(startX + x, targetY - 1, startZ + z), baseBlock, 3);
                                chunk.setBlockState(new BlockPos(startX + x, targetY - 2, startZ + z), baseBlock, 3);
                            } else {
                                chunk.setBlockState(new BlockPos(startX + x, targetY, startZ + z), topBlock, 3);
                                chunk.setBlockState(new BlockPos(startX + x, targetY - 1, startZ + z), fillerBlock, 3);
                                chunk.setBlockState(new BlockPos(startX + x, targetY - 2, startZ + z), fillerBlock, 3);
                            }
                        }
                    }

                    if (carving.isWatershedWall && targetY > originalY) {
                        for (int y = originalY + 1; y <= targetY; y++) {
                            if (y >= minH && protectedBlocks.get(y - minH)) continue;
                            if (y == targetY) {
                                chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), topBlock, 3);
                            } else if (y >= targetY - 2) {
                                chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), fillerBlock, 3);
                            } else {
                                chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), baseBlock, 3);
                            }
                        }
                    }

                    if (originalY >= 90) {
                        for (int y = seaLevel; y <= originalY; y++) {
                            if (y >= minH && protectedBlocks.get(y - minH)) continue;
                            BlockState currentState = chunk.getBlockState(new BlockPos(startX + x, y, startZ + z));
                            if (currentState.isAir()) {
                                chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), baseBlock, 3);
                            }
                        }
                    }

                    int fillFrom = Math.max(minH, Math.min(carving.sealFloorY, targetY));

                    for (int scan = fillFrom; scan <= targetY; scan++) {
                        if (scan >= minH && protectedBlocks.get(scan - minH)) continue;
                        BlockPos pos = new BlockPos(startX + x, scan, startZ + z);
                        BlockState s = chunk.getBlockState(pos);

                        boolean isBuildingUp = scan > originalY;
                        boolean canPlace = s.isAir() || (!s.getFluidState().isEmpty() && (!isBuildingUp || !carving.isWatershedWall));

                        if (canPlace) {
                            BlockState stateToPlace;
                            if (carving.isWatershedWall) {
                                boolean sealSteep = (carving.waterLevel > targetY + 3);
                                if (scan == targetY) {
                                    stateToPlace = sealSteep ? Blocks.GRAVEL.defaultBlockState() : topBlock;
                                } else if (scan >= targetY - 2) {
                                    stateToPlace = sealSteep ? baseBlock : fillerBlock;
                                } else {
                                    stateToPlace = baseBlock;
                                }
                            } else {
                                stateToPlace = (originalY > carving.waterLevel + 2) ? baseBlock : fillerBlock;
                            }

                            if (stateToPlace.getBlock() instanceof net.minecraft.world.level.block.FallingBlock && scan - 1 >= minH) {
                                BlockPos belowPos = new BlockPos(startX + x, scan - 1, startZ + z);
                                BlockState belowS = chunk.getBlockState(belowPos);
                                if (belowS.isAir() || !belowS.getFluidState().isEmpty()) {
                                    chunk.setBlockState(belowPos, Blocks.STONE.defaultBlockState(), 3);
                                }
                            }

                            chunk.setBlockState(pos, stateToPlace, 3);
                        }
                    }

                    if (targetY < carveTopY) {
                        for (int y = carveTopY; y > targetY; y--) {
                            if (y >= minH && protectedBlocks.get(y - minH)) continue;
                            chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }

                    if (targetY > originalY + 4 && !carving.isWatershed) {
                        continue;
                    } else if (targetY > originalY && !carving.isWatershed) {
                        targetY = originalY;
                    }

                    if (targetY <= carving.waterLevel && !carving.isWatershedWall) {
                        for (int y = carving.waterLevel; y >= targetY + 1; y--) {
                            if (y >= minH && protectedBlocks.get(y - minH)) continue;

                            BlockState riverWater = Blocks.WATER.defaultBlockState();

                            if (carving.isMountainRiver && y < carving.waterLevel) {
                                riverWater = riverWater.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL, 8);
                            }

                            if (!carving.isWatershed && carving.channelWidth >= 4.0 && carving.distanceFromCenter < carving.channelWidth - 3.5) {
                                dynamicwaters$setRiverBiome(chunk, startX + x, y, startZ + z, riverBiome, frozenRiverBiome);
                            }
                            chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), riverWater, 3);

                            if (carving.isMountainRiver && y == carving.waterLevel) {
                                chunk.markPosForPostProcessing(new BlockPos(startX + x, y, startZ + z));
                            }
                        }
                        if (carving.isWatershed) {
                            int depth = carving.waterLevel - targetY;
                            int hash = (int)(startX + x) * 31337 + (int)(startZ + z) * 42069;
                            hash ^= (hash >> 16);
                            int hashAbs = Math.abs(hash % 100);
                            if (depth >= 3 && hashAbs < 5 && targetY >= minH - 1 && !protectedBlocks.get(targetY + 1 - minH) && !protectedBlocks.get(targetY + 2 - minH)) {
                                chunk.setBlockState(new BlockPos(startX + x, targetY + 1, startZ + z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
                                chunk.setBlockState(new BlockPos(startX + x, targetY + 2, startZ + z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
                            } else if (depth >= 2 && hashAbs < 15 && targetY >= minH - 1 && !protectedBlocks.get(targetY + 1 - minH)) {
                                chunk.setBlockState(new BlockPos(startX + x, targetY + 1, startZ + z), Blocks.SEAGRASS.defaultBlockState(), 3);
                            }
                        }

                        if (targetY < originalY && (targetY < minH || !protectedBlocks.get(targetY - minH))) {
                            chunk.setBlockState(new BlockPos(startX + x, targetY, startZ + z), Blocks.STONE.defaultBlockState(), 3);
                            int hash = (int)(startX + x) * 31337 + (int)(startZ + z) * 42069;
                            hash ^= (hash >> 16);
                            hash ^= (hash << 5);
                            int hashAbs = Math.abs(hash % 100);
                            if (targetY < seaLevel - 2) {
                                if (hashAbs < 3 && targetY < seaLevel - 3 && targetY >= minH - 1 && !protectedBlocks.get(targetY + 1 - minH) && !protectedBlocks.get(targetY + 2 - minH)) {
                                    chunk.setBlockState(new BlockPos(startX + x, targetY + 1, startZ + z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
                                    chunk.setBlockState(new BlockPos(startX + x, targetY + 2, startZ + z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
                                } else if (hashAbs < 10 && targetY >= minH - 1 && !protectedBlocks.get(targetY + 1 - minH)) {
                                    chunk.setBlockState(new BlockPos(startX + x, targetY + 1, startZ + z), Blocks.SEAGRASS.defaultBlockState(), 3);
                                }
                            }
                        }
                    }

                    if ((carving.gorgeWall || carving.dressCut) && targetY >= carving.waterLevel && targetY < originalY) {
                        HydrologyManager.gorgeDress(chunk, startX + x, startZ + z, targetY, carving.waterLevel,
                                carving.gorgeSteep, protectedBlocks, minH, surfaceGround, originalY);
                    }
                }
            }
        }
        com.AdamNew.dynamicwaters.LakeHollows.clearChunkGround();
    }

    @Inject(method = "generateCarvers", at = @At("HEAD"))
    private void dynamicwaters$saveHeightsBeforeCarvers(ChunkAccess chunk, net.minecraft.world.level.levelgen.blending.Blender blender,
            net.minecraft.world.level.levelgen.NoiseChunk noiseChunk, RandomState randomState,
            net.minecraft.world.level.biome.BiomeManager biomeManager, WorldGenRegion carverRegion,
            net.minecraft.world.level.levelgen.material.rule.MaterialRule materialRule, CallbackInfo ci) {
        final Object[] terrain = DYNAMICWATERS$TERRAIN.get(chunk);
        final WorldGenRegion level = carverRegion != null ? carverRegion : terrain != null ? (WorldGenRegion) terrain[0] : null;
        if (level == null || level.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD
                || !com.AdamNew.dynamicwaters.compat.OneDimension.carves((net.minecraft.world.level.chunk.ChunkGenerator) (Object) this)) {
            return;
        }
        {
            int[][] heights = ORIGINAL_HEIGHT_CACHE.get();

            final int[] pre = HydrologyManager.takePreCarveHeights(chunk.getPos().pack());
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    heights[x][z] = pre != null ? pre[x + z * 16] : chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                }
            }
        }
    }

    @Inject(method = "generateCarvers", at = @At("RETURN"))
    private void dynamicwaters$repairRiverAfterCarvers(ChunkAccess chunk, net.minecraft.world.level.levelgen.blending.Blender blender,
            net.minecraft.world.level.levelgen.NoiseChunk noiseChunk, RandomState randomState,
            net.minecraft.world.level.biome.BiomeManager biomeManager, WorldGenRegion carverRegion,
            net.minecraft.world.level.levelgen.material.rule.MaterialRule materialRule, CallbackInfo ci) {
        final Object[] terrain = DYNAMICWATERS$TERRAIN.remove(chunk);
        final WorldGenRegion level = carverRegion != null ? carverRegion : terrain != null ? (WorldGenRegion) terrain[0] : null;
        if (level == null || terrain == null || level.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD
                || !com.AdamNew.dynamicwaters.compat.OneDimension.carves((net.minecraft.world.level.chunk.ChunkGenerator) (Object) this)) {
            return;
        }
        final StructureManager structureManager = (StructureManager) terrain[1];

        HydrologyManager.setSeed(level.getLevel().getSeed());

        net.minecraft.core.Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        Holder<Biome> riverBiome = biomeRegistry.getOrThrow(net.minecraft.world.level.biome.Biomes.RIVER);
        HydrologyManager.riverBiomeHolder = riverBiome;
        Holder<Biome> frozenRiverBiome = biomeRegistry.getOrThrow(net.minecraft.world.level.biome.Biomes.FROZEN_RIVER);

        {
            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();
            int seaLevel = ((NoiseBasedChunkGenerator)(Object)this).getSeaLevel();
            int minH = chunk.getMinY();
            java.util.BitSet[] protectedBlocksMap2 = dynamicwaters$buildProtectionMap(chunk, structureManager, chunkPos, startX, startZ);

            final HydrologyManager.RiverCarving[] skinCarvings = new HydrologyManager.RiverCarving[256];
            final int[] skinOriginal = new int[256];

            final boolean[] repairedWater = new boolean[256];

            final int[][] preHeights = ORIGINAL_HEIGHT_CACHE.get();
            final int[] repairGround = new int[256];
            for (int i = 0; i < 256; i++) repairGround[i] = preHeights[i & 15][i >> 4];
            com.AdamNew.dynamicwaters.LakeHollows.setChunkGround(startX, startZ, repairGround);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    double worldX = startX + x;
                    double worldZ = startZ + z;

                    net.minecraft.world.level.chunk.ChunkGenerator generator = ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this);
                    boolean inRiver = HydrologyManager.repairsColumn(worldX, worldZ, generator, chunk, randomState);

                    com.AdamNew.dynamicwaters.river.RiverContext macroInfo =
                        com.AdamNew.dynamicwaters.MainRivers.findNearest(worldX, worldZ, generator, chunk, randomState);
                    final double macroWidth = macroInfo != null ? Math.sqrt(macroInfo.widthSq()) : 0.0;

                    boolean skinOnly = false;
                    if (!inRiver) {
                        if (macroInfo == null) continue;
                        final double skinR = HydrologyManager.normalRiverSkinRadius(macroWidth);
                        if (macroInfo.distSq() >= skinR * skinR) continue;
                        skinOnly = true;
                    }

                    int originalY = ORIGINAL_HEIGHT_CACHE.get()[x][z];

                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = chunk.getNoiseBiome(x >> 2, originalY >> 2, z >> 2);

                    if (HydrologyManager.isOceanBiome(biomeHolder) && originalY < seaLevel - 15) {
                        continue;
                    }

                    HydrologyManager.RiverCarving carving = HydrologyManager.calculateRiverCarving(worldX, worldZ, originalY, seaLevel, ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this), chunk, randomState);
                    int targetY = carving.modifies ? carving.targetY : originalY;
                    int cavernRoofY = carving.cavernRoofY;
                    int carveTopY = Math.min(originalY, cavernRoofY);

                    java.util.BitSet protectedBlocks2 = protectedBlocksMap2[x + z * 16];
                    if (protectedBlocks2 == null) {
                        protectedBlocks2 = new java.util.BitSet(0);
                    }
                    boolean hasSurfaceStructure2 = false;
                    if (!protectedBlocks2.isEmpty()) {
                        for (int sy = targetY - 2; sy <= carveTopY + 5; sy++) {
                            int bitIndex = sy - minH;
                            if (bitIndex >= 0 && protectedBlocks2.get(bitIndex)) {
                                hasSurfaceStructure2 = true;
                                break;
                            }
                        }
                    }

                    if (hasSurfaceStructure2) {
                        continue;
                    }
                    skinCarvings[x + z * 16] = carving;
                    skinOriginal[x + z * 16] = originalY;
                    if (skinOnly) continue;

                    if (carving.isPillar) {
                        for (int y = originalY; y >= seaLevel - 15; y--) {
                            if (y >= minH && protectedBlocks2.get(y - minH)) continue;
                            BlockState s = chunk.getBlockState(new BlockPos(startX + x, y, startZ + z));
                            if (s.isAir() || s.is(Blocks.WATER)) {
                                chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), Blocks.STONE.defaultBlockState(), 3);
                            }
                        }
                        continue;
                    }

                    if (!carving.isWatershedWall && targetY == originalY && carving.waterLevel <= originalY) {
                        continue;
                    }
                    if (!carving.isWatershedWall && carving.waterLevel > targetY) repairedWater[x + z * 16] = true;

                    BlockState topBlock2 = Blocks.GRASS_BLOCK.defaultBlockState();
                    BlockState fillerBlock2 = Blocks.DIRT.defaultBlockState();
                    BlockState baseBlock2 = Blocks.STONE.defaultBlockState();
                    for (int ty2 = Math.max(originalY, targetY + 1); ty2 > Math.max(chunk.getMinY(), Math.max(originalY, targetY + 1) - 12); ty2--) {
                        BlockState s2 = chunk.getBlockState(new BlockPos(startX + x, ty2, startZ + z));
                        boolean isOre2 = s2.is(com.AdamNew.dynamicwaters.platform.ModTags.ORES);
                        if (s2.isSolid() && !isOre2 && !s2.is(Blocks.WATER)) {
                            topBlock2 = s2;
                            fillerBlock2 = s2;
                            baseBlock2 = s2;
                            for (int fy2 = ty2 - 1; fy2 > Math.max(chunk.getMinY(), ty2 - 6); fy2--) {
                                BlockState fs2 = chunk.getBlockState(new BlockPos(startX + x, fy2, startZ + z));
                                boolean isFsOre2 = fs2.is(com.AdamNew.dynamicwaters.platform.ModTags.ORES);
                                if (fs2.isSolid() && !fs2.equals(topBlock2) && !isFsOre2) {
                                    fillerBlock2 = fs2;
                                    baseBlock2 = fs2;
                                    for (int by2 = fy2 - 1; by2 > Math.max(chunk.getMinY(), fy2 - 15); by2--) {
                                        BlockState bs2 = chunk.getBlockState(new BlockPos(startX + x, by2, startZ + z));
                                        boolean isBsOre2 = bs2.is(com.AdamNew.dynamicwaters.platform.ModTags.ORES);
                                        if (bs2.isSolid() && !bs2.equals(fillerBlock2) && !isBsOre2) {
                                            baseBlock2 = bs2;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    int sealedFloorY = targetY;
                    for (int scan = targetY; scan >= minH; scan--) {
                        if (protectedBlocks2.get(scan - minH)) break;
                        BlockPos scanPos = new BlockPos(startX + x, scan, startZ + z);
                        BlockState s = chunk.getBlockState(scanPos);

                        if (s.isAir() || s.is(Blocks.WATER) || s.is(Blocks.LAVA) || !s.isSolidRender()) {
                            BlockState sealState = (targetY - scan < 3) ? fillerBlock2 : baseBlock2;
                            chunk.setBlockState(scanPos, sealState, 3);
                            sealedFloorY = scan;
                        } else {
                            break;
                        }
                    }

                    int repairBottomY = Math.min(sealedFloorY, carving.waterLevel - 8);
                    if (carving.isWatershedWall) {
                        int maxPipeDepth = carving.waterLevel - 5;
                        repairBottomY = Math.max(maxPipeDepth, Math.min(repairBottomY, originalY));
                    }

                    int repairTopY = carving.isWatershedWall ? Math.max(originalY, targetY + 2) : Math.max(carveTopY, carving.waterLevel);

                    for (int y = repairTopY; y > repairBottomY; y--) {
                        if (y >= minH && protectedBlocks2.get(y - minH)) continue;
                        BlockPos pos = new BlockPos(startX + x, y, startZ + z);

                        BlockState stateToPlace = null;
                        boolean postProcessWater = false;

                        if (y > targetY) {
                            if (!carving.isWatershedWall && y <= carving.waterLevel) {
                                boolean isMainTunnel = (!carving.isWatershed && carving.cavernRoofY < originalY - 2 && originalY > carving.waterLevel + 5);
                                boolean isStoneWall = (carving.distanceFromCenter >= carving.channelWidth + 2.0);
                                if (isMainTunnel && isStoneWall) {
                                    stateToPlace = Blocks.STONE.defaultBlockState();
                                } else {
                                    stateToPlace = Blocks.WATER.defaultBlockState();
                                    postProcessWater = carving.isMountainRiver;

                                    if (!carving.isWatershed && carving.channelWidth >= 4.0 && carving.distanceFromCenter < carving.channelWidth - 3.5) {
                                        dynamicwaters$setRiverBiome(chunk, startX + x, y, startZ + z, riverBiome, frozenRiverBiome);
                                    }
                                }
                            } else {
                                stateToPlace = Blocks.AIR.defaultBlockState();
                            }
                        } else {
                            if (!carving.isWatershedWall) {
                                stateToPlace = (originalY > carving.waterLevel + 2) ? baseBlock2 : fillerBlock2;
                            } else {
                                if (y == targetY && targetY >= carving.waterLevel) {
                                    stateToPlace = topBlock2;
                                } else {
                                    stateToPlace = (originalY > carving.waterLevel + 2) ? baseBlock2 : fillerBlock2;
                                }
                            }
                        }

                        BlockState currentState = chunk.getBlockState(pos);
                        boolean shouldOverwrite = (currentState.isAir() || currentState.is(Blocks.WATER) || currentState.is(Blocks.LAVA) || !currentState.isSolidRender() || y > targetY);

                        if (shouldOverwrite && stateToPlace != null) {
                            chunk.setBlockState(pos, stateToPlace, 3);
                            if (postProcessWater) {
                                chunk.markPosForPostProcessing(pos);
                            }
                        }
                    }

                    if (repairTopY >= originalY && repairTopY > targetY) {
                        for (int y = repairTopY + 1; y < (chunk.getMaxY() + 1); y++) {
                            if (y >= minH && protectedBlocks2.get(y - minH)) break;
                            final BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                            final BlockState s = chunk.getBlockState(pos);
                            if (s.isAir() || !s.getFluidState().isEmpty()) break;
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }

            dynamicwaters$containWater(chunk, startX, startZ, skinCarvings, skinOriginal, repairedWater, protectedBlocksMap2, minH);
            com.AdamNew.dynamicwaters.LakeHollows.clearChunkGround();
            com.AdamNew.dynamicwaters.shore.ShoreSkin.apply(chunk, skinCarvings, skinOriginal, repairedWater, protectedBlocksMap2, minH, startX, startZ,
                    ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this).getBiomeSource(), com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState));

            com.AdamNew.dynamicwaters.shore.WaterPlants.plant(chunk, skinCarvings, skinOriginal, protectedBlocksMap2, minH, startX, startZ,
                    ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this).getBiomeSource(), com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState));
            com.AdamNew.dynamicwaters.shore.Boulders.place(chunk, skinCarvings, protectedBlocksMap2, minH, startX, startZ,
                    ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this).getBiomeSource(), randomState);
        }
    }
}
