package com.AdamNew.dynamicwaters.events;

import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;

public class SpawningEvents {
    public static WeightedList<MobSpawnSettings.SpawnerData> riverSpawns(LevelAccessor level, BlockPos pos, MobCategory category) {
        if (level == null) return null;

        if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return null;

        net.minecraft.world.level.Level mcLevel = level instanceof net.minecraft.world.level.Level ? (net.minecraft.world.level.Level) level : null;
        Vec3 flow = HydrologyManager.getRiverFlow(pos.getX(), pos.getY(), pos.getZ(), mcLevel);
        if (!flow.equals(Vec3.ZERO)) {
            try {
                Holder<Biome> riverBiome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.RIVER);

                return riverBiome.value().getAttributes()
                        .applyModifier(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY)
                        .getMobsToSpawn(category);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static void onChunkLoad(LevelAccessor level, net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (!level.isClientSide() && level instanceof net.minecraft.world.level.Level mcLevel
                && HydrologyManager.riversIn(mcLevel)) {
            net.minecraft.world.level.chunk.ChunkGenerator generator = null;
            net.minecraft.world.level.levelgen.RandomState randomState = null;

            if (mcLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                generator = com.AdamNew.dynamicwaters.compat.OneDimension.generator(serverLevel);
                randomState = com.AdamNew.dynamicwaters.compat.OneDimension.randomState(serverLevel);
            } else {
                generator = HydrologyManager.getGlobalGenerator();
                randomState = HydrologyManager.getGlobalRandomState();
            }

            if (generator != null && randomState != null) {
                int blockX = chunk.getPos().getMinBlockX() + 8;
                int blockZ = chunk.getPos().getMinBlockZ() + 8;
                com.AdamNew.dynamicwaters.MainRivers.prefetchAround(blockX, blockZ, generator, mcLevel, randomState);
            }
        }
    }
}
