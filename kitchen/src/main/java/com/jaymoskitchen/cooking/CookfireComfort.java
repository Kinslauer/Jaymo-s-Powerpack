package com.jaymoskitchen.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A fire with a meal on it (Soothing Campfires' idea, CC0; our own code). While food cooks on a lit campfire, in its four
 * places or on a spit over it (or over a firepit), the warmth and the smell of supper ease whoever stands round it: players and villagers
 * within {@link #RANGE} blocks are given a gentle Regeneration, refreshed while the cooking goes on. A signal fire (hay
 * under it) carries it {@link #SIGNAL_RANGE}. An empty fire does nothing here: sitting by one is Comfortable Campfires',
 * and resting by one Jaymo's Powers' ({@code Resting}).
 */
public final class CookfireComfort {
	/** How often (ticks) a cooking fire eases those round it. */
	static final int EVERY = 50;
	/** How long each easing lasts (ticks): a little over {@link #EVERY}, so it runs on while the food cooks. */
	static final int REGEN_TICKS = 60;
	/** Its strength (0 is Regeneration I). */
	static final int REGEN_AMPLIFIER = 0;
	/** How far (blocks) a cooking fire's comfort reaches, and a signal fire's. */
	static final double RANGE = 5.0, SIGNAL_RANGE = 10.0;

	private CookfireComfort() {
	}

	/** (A lit campfire's cooking tick, mixin.CampfireComfortMixin, or a spit's over heat) food is cooking on the fire at {@code fire}. */
	public static void cooking(ServerLevel level, BlockPos fire) {
		if (Math.floorMod(level.getGameTime() + fire.asLong(), EVERY) != 0) {
			return;
		}
		BlockState state = level.getBlockState(fire);
		boolean signal = state.hasProperty(CampfireBlock.SIGNAL_FIRE) && state.getValue(CampfireBlock.SIGNAL_FIRE);
		double range = signal ? SIGNAL_RANGE : RANGE;
		Vec3 centre = Vec3.atCenterOf(fire);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(fire).inflate(range),
				e -> e.isAlive() && (e instanceof Player p && !p.isSpectator() || e instanceof AbstractVillager)
					&& e.distanceToSqr(centre) <= range * range)) {
			MobEffectInstance had = near.getEffect(MobEffects.REGENERATION);
			// (a stronger or longer Regeneration from a potion or a healer is left as it is)
			if (had == null || had.getAmplifier() <= REGEN_AMPLIFIER && had.getDuration() <= REGEN_TICKS) {
				near.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, REGEN_AMPLIFIER, true, false, true));
			}
		}
	}
}
