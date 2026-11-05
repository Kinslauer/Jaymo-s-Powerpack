package com.jaymoskingdoms.mixin.tweaks;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.589) Fewer hostiles. Andy (2026-11-05): "i think mobs are overspawning, reduce the rates by a ton. especially for
 * the hostile mobs", then, liking the sea full of sharks, chose to cut only the hostiles. The monster cap (vanilla's and
 * every mod's natural spawns in the monster category count against it) is cut to {@link #MONSTER_SHARE} of vanilla's 70.
 * The sea's creatures, the animals and the pack's own camps and raids are left as they are.
 */
@Mixin(MobCategory.class)
abstract class MonsterCapMixin {
	/** The share of vanilla's monster cap that's left. */
	private static final float MONSTER_SHARE = 0.5F;

	@ModifyReturnValue(method = "getMaxInstancesPerChunk", at = @At("RETURN"))
	private int jaymoskingdoms$fewerMonsters(int cap) {
		return (Object) this == MobCategory.MONSTER ? Math.max(1, Math.round(cap * MONSTER_SHARE)) : cap;
	}
}
