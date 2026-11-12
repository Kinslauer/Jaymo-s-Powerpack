package com.AdamNew.dynamicwaters.mixin.fabric;

import com.AdamNew.dynamicwaters.events.SpawningEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerFabricMixin {
	@Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
	private static void dynamicwaters$riverSpawns(ServerLevel level, StructureManager structures, ChunkGenerator generator, MobCategory category,
			BlockPos pos, CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir) {
		final WeightedList<MobSpawnSettings.SpawnerData> river = SpawningEvents.riverSpawns(level, pos, category);
		if (river != null) cir.setReturnValue(river);
	}
}
