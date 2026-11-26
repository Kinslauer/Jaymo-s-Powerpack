package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.HarshLands;
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
 * <p>The water caps are the game's along coasts, rivers and lakes (Andy 2026-11-26: the cut made for "still too many
 * mobs spawning at once in the water" came out; that was one mob overspawning). Open sea chunks ({@link SeaLife#openSea},
 * asked through {@code SeaCapsMixin}) have {@link #OPEN_WATER_AMBIENT} and {@link #OPEN_WATER_CREATURE}, so a voyage
 * meets whales, sharks, dolphins and schools of fish. Cave water creatures stay at {@link #UNDERGROUND_WATER}.
 *
 * <p>Andy (2026-11-19): spawns follow the climate map. The monster cap above holds in the temperate lands; a chunk of the
 * harsh lands ({@link HarshLands}, asked through {@code HarshCapsMixin}) keeps {@link HarshLands#MONSTER_SHARE} of vanilla's
 * by its steps, so the cold and the heat grow thicker with monsters the further out they lie.
 */
@Mixin(MobCategory.class)
abstract class MonsterCapMixin {
	/** The share of vanilla's monster cap that's left. */
	private static final float MONSTER_SHARE = 0.5F;
	/** Cave water creatures' cap (vanilla 5). */
	private static final int UNDERGROUND_WATER = 2;
	/** The open sea's water caps (vanilla 20 and 5 everywhere else): fish at vanilla's own, more room for the big ones. */
	private static final int OPEN_WATER_AMBIENT = 20, OPEN_WATER_CREATURE = 8;

	@ModifyReturnValue(method = "getMaxInstancesPerChunk", at = @At("RETURN"))
	private int jaymoskingdoms$fewerMonsters(int cap) {
		Object self = this;
		if (self == MobCategory.MONSTER) {
			// (2026-11-19) the harsh lands keep more of it, by their steps (HarshLands, asked through HarshCapsMixin)
			int steps = HarshLands.asking();
			return Math.max(1, Math.round(cap * (steps > 0 ? HarshLands.MONSTER_SHARE[steps] : MONSTER_SHARE)));
		}
		if (self == MobCategory.WATER_AMBIENT) {
			return SeaLife.askingOpen() ? OPEN_WATER_AMBIENT : cap;
		}
		if (self == MobCategory.WATER_CREATURE) {
			return SeaLife.askingOpen() ? OPEN_WATER_CREATURE : cap;
		}
		if (self == MobCategory.UNDERGROUND_WATER_CREATURE) {
			return Math.min(cap, UNDERGROUND_WATER);
		}
		return cap;
	}
}
