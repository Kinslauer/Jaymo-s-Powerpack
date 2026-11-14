package com.jaymoskingdoms.mixin.tweaks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fish laid down with the land come and go like any other fish. William Wythers' Overhauled Overworld places schools
 * of tropical fish, cod and the like in its reefs, rivers and ponds as worldgen templates, and they come out of the
 * template kept for good (persistent), so they never swim off. A new world on the Steam Deck had 17,925 of them in the
 * seven regions around spawn, 3,007 loaded when Andy joined, and ticking them all held the server a minute
 * (watchdog crash 2026-11-13 22:41).
 * <p>
 * The first time such a fish (a vanilla one, kept, not from a bucket, not named) is checked for despawning, it stops
 * being kept: it leaves when no one is near, as fish do, and natural spawning brings fish back within its caps
 * ({@link MonsterCapMixin}, {@link WaterClusterMixin}). If its chunk already has {@link #CHUNK_FISH} fish it goes at
 * once, so a crowded school thins to a few. Fish from a bucket or with a name tag are kept as before.
 */
@Mixin(Mob.class)
abstract class WorldgenFishMixin {
	/** A worldgen fish goes at once if its chunk already has this many fish. */
	private static final int CHUNK_FISH = 6;

	@Shadow
	private boolean persistenceRequired;

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$worldgenFishSwimOff(CallbackInfo ci) {
		if (!this.persistenceRequired || !((Object) this instanceof AbstractFish fish) || fish.fromBucket() || fish.hasCustomName()
			|| !"minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(fish.getType()).getNamespace())) {
			return;
		}
		this.persistenceRequired = false;
		ChunkPos chunk = fish.chunkPosition();
		AABB box = new AABB(chunk.getMinBlockX(), fish.level().getMinY(), chunk.getMinBlockZ(),
			chunk.getMaxBlockX() + 1, fish.level().getMaxY() + 1, chunk.getMaxBlockZ() + 1);
		if (fish.level().getEntitiesOfClass(AbstractFish.class, box, f -> f != fish && !f.isRemoved()).size() >= CHUNK_FISH) {
			fish.discard();
			ci.cancel();
		}
	}
}
