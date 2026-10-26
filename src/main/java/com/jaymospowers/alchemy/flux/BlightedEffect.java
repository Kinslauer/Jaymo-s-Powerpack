package com.jaymospowers.alchemy.flux;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Blighted: the sickness of an animal that has lived in flux-soaked land. Once a second it picks out the nearest player
 * within {@link #RANGE} blocks, walks at them and, close enough, bites for {@link #BITE} damage. Done as a plain effect
 * tick (path to the player, then a direct hit) rather than by adding goals to the animal, so it works on every passive
 * mob without touching its AI, and leaves the animal untouched when the effect runs out. Players and monsters ignore it.
 */
public class BlightedEffect extends MobEffect {
	// ---- tuning ----
	/** How far it notices a player, how fast it comes (a walking-speed multiplier), how close it must be to bite, and the bite. */
	public static final double RANGE = 12.0, SPEED = 1.3, REACH_SQR = 2.6;
	public static final float BITE = 1.0F;
	/** The effect ticks once in this many game ticks. */
	public static final int INTERVAL = 20;

	public BlightedEffect() {
		super(MobEffectCategory.HARMFUL, 0x6D5A86, ParticleTypes.WITCH);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		if (!(entity instanceof Mob mob) || entity instanceof Enemy || entity instanceof Player) {
			return true;
		}
		Player player = level.getNearestPlayer(mob, RANGE);
		if (player == null || player.isCreative() || player.isSpectator()) {
			return true;
		}
		mob.getNavigation().moveTo(player, SPEED);
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (mob.distanceToSqr(player) < REACH_SQR) {
			player.hurtServer(level, mob.damageSources().mobAttack(mob), BITE);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration % INTERVAL == 0;
	}
}
