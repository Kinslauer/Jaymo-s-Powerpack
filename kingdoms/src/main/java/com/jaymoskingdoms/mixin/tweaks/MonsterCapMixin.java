package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.seas.SeaLife;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.589) Fewer hostiles. Andy (2026-11-05): "i think mobs are overspawning, reduce the rates by a ton. especially for
 * the hostile mobs", then, liking the sea full of sharks, chose to cut only the hostiles. The monster cap (vanilla's and
 * every mod's natural spawns in the monster category count against it) is cut to {@link #MONSTER_SHARE} of vanilla's 70.
 * The animals and the pack's own camps and raids are left as they are.
 *
 * <p>(0.7.644) Andy (2026-11-07): "still too many mobs spawning at once in the water". The water caps come down too: fish
 * and other water ambients to {@link #WATER_AMBIENT} (vanilla 20), squid, dolphins, sharks and the like to
 * {@link #WATER_CREATURE} (vanilla 5), cave water creatures to {@link #UNDERGROUND_WATER} (vanilla 5); and each spawn
 * brings at most {@link WaterClusterMixin#WATER_CLUSTER} of them at once.
 *
 * <p>Andy (2026-11-19), across an ocean: "only saw dolphins". Those caps now hold along coasts, rivers and lakes; open sea
 * chunks ({@link SeaLife#openSea}, asked through {@code SeaCapsMixin}) have {@link #OPEN_WATER_AMBIENT} and
 * {@link #OPEN_WATER_CREATURE} instead, so a voyage meets whales, sharks, dolphins and schools of fish.
 */
@Mixin(MobCategory.class)
abstract class MonsterCapMixin {
	/** The share of vanilla's monster cap that's left. */
	private static final float MONSTER_SHARE = 0.5F;
	/** The water caps. */
	private static final int WATER_AMBIENT = 6, WATER_CREATURE = 3, UNDERGROUND_WATER = 2;
	/** The open sea's water caps (vanilla 20 and 5 everywhere). */
	private static final int OPEN_WATER_AMBIENT = 16, OPEN_WATER_CREATURE = 8;

	@ModifyReturnValue(method = "getMaxInstancesPerChunk", at = @At("RETURN"))
	private int jaymoskingdoms$fewerMonsters(int cap) {
		Object self = this;
		if (self == MobCategory.MONSTER) {
			return Math.max(1, Math.round(cap * MONSTER_SHARE));
		}
		if (self == MobCategory.WATER_AMBIENT) {
			return SeaLife.askingOpen() ? OPEN_WATER_AMBIENT : Math.min(cap, WATER_AMBIENT);
		}
		if (self == MobCategory.WATER_CREATURE) {
			return SeaLife.askingOpen() ? OPEN_WATER_CREATURE : Math.min(cap, WATER_CREATURE);
		}
		if (self == MobCategory.UNDERGROUND_WATER_CREATURE) {
			return Math.min(cap, UNDERGROUND_WATER);
		}
		return cap;
	}
}
