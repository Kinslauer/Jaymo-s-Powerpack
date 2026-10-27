package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.wounds.Downed;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.54, Andy's NOEA picks) A Verdant Z Fighter drinks the water in. Standing in rain ({@link #RAIN_HEAL_EVERY}), in
 * water ({@link #WATER_HEAL_EVERY}) or under it ({@link #UNDER_HEAL_EVERY}) he gets back a health point that often (while
 * fed, on top of his usual mending, {@link ZFighterLineage#mend}) and ki every tick ({@link #RAIN_KI}, {@link #WATER_KI},
 * {@link #UNDER_KI}), in any form and while charging. Not while downed.
 */
public final class ZFighterVerdantWater {
	static final int RAIN_HEAL_EVERY = 60, WATER_HEAL_EVERY = 30, UNDER_HEAL_EVERY = 15;
	static final float RAIN_KI = 0.02F, WATER_KI = 0.04F, UNDER_KI = 0.07F;

	private ZFighterVerdantWater() {
	}

	/** Every tick, per Verdant Z Fighter. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (!player.isAlive() || Downed.isDowned(player)) {
			return;
		}
		ServerLevel level = player.level();
		int every;
		float ki;
		if (player.isUnderWater()) {
			every = UNDER_HEAL_EVERY;
			ki = UNDER_KI;
		} else if (player.isInWater()) {
			every = WATER_HEAL_EVERY;
			ki = WATER_KI;
		} else if (level.isRainingAt(player.blockPosition())) {
			every = RAIN_HEAL_EVERY;
			ki = RAIN_KI;
		} else {
			return;
		}
		live.ki += ki;
		long now = level.getGameTime();
		if (now % every == 0 && player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() > ZFighterPower.HUNGRY_FOOD) {
			player.heal(1.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.6, 0.3, 0.0);
		}
	}
}
