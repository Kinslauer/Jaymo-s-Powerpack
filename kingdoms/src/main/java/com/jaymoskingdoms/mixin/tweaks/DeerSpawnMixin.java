package com.jaymoskingdoms.mixin.tweaks;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.608) Fewer deer, and none in the Nether. Andy (2026-11-05): "why are there deer in the nether? they clearly
 * overspawn up in the overworld too". Naturalist's deer that spawn on their own (naturally or with a new chunk) never do
 * in a Nether biome (the stacked world's Nether band included) or the Nether itself, and elsewhere only
 * {@link #DEER_SHARE} of the spots that would have taken one still do. Spawn eggs, spawners and commands are untouched.
 */
@Mixin(SpawnPlacements.class)
abstract class DeerSpawnMixin {
	/** The share of natural deer spawns that still happen outside the Nether. */
	@Unique
	private static final float DEER_SHARE = 0.3F;
	@Unique
	private static final Identifier DEER = Identifier.fromNamespaceAndPath("naturalist", "deer");

	@ModifyReturnValue(method = "checkSpawnRules", at = @At("RETURN"))
	private static boolean jaymoskingdoms$fewerDeer(boolean ok, EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!ok || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
			|| !DEER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
			return ok;
		}
		if (level.getLevel().dimension() == Level.NETHER || level.getBiome(pos).is(BiomeTags.IS_NETHER)) {
			return false;
		}
		return random.nextFloat() < DEER_SHARE;
	}
}
