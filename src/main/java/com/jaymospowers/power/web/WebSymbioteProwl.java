package com.jaymospowers.power.web;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * The night prowl: at night, under the open sky, the black strain now and then takes the body (the takeover system:
 * {@code WebSymbiote.prowl}) to stalk one hostile mob it can see and kill it for sport, and then gives the body back.
 * Only the black one: the white mends and the red hunts on its own terms.
 */
final class WebSymbioteProwl {
	/** It considers a prowl this often (ticks), and goes this often when it does. */
	static final int CHECK_EVERY = 200;
	static final float CHANCE = 0.05F;
	/** The mob it picks is within this and in sight, has no more than this health, and is no named one. */
	static final double RANGE = 20.0;
	static final float MAX_PREY_HEALTH = 40.0F;
	/** It leaves a hurt host alone: it needs this share of your health. */
	static final float MIN_HEALTH_SHARE = 0.6F;
	static final int LINES = 3, DONE_LINES = 2;

	private WebSymbioteProwl() {
	}

	static void tick(ServerPlayer player, WebSymbioteState state, long now) {
		if (player.tickCount % CHECK_EVERY != 0 || state.kind() != WebSymbioteStrain.BLACK || player.isSleeping() || player.isCreative()
			|| player.getHealth() < player.getMaxHealth() * MIN_HEALTH_SHARE || player.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || !level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
			return;
		}
		LivingEntity prey = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
				m -> m instanceof Enemy && m.isAlive() && !m.hasCustomName() && m.getMaxHealth() <= MAX_PREY_HEALTH && player.hasLineOfSight(m)).stream()
			.min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
		if (prey != null) {
			WebSymbiote.prowl(player, prey);
		}
	}
}
