package com.jaymoskingdoms;

import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.sailing.Winds;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Weather in the fight. In a gale (the wind at a point is {@link #GALE} or more, which a storm or a hard blow brings) an
 * arrow in flight under open sky is pushed along the way the wind blows, so a long shot goes wide, and a torch
 * standing in the open can be blown out. Rain puts out a fire arrow in flight, and shortens how far a hostile or
 * neutral mob picks you out. Deep snow (four layers or more, or powder snow) drags at whoever wades through it, a charge
 * on foot or on a horse most of all.
 */
public final class FieldWeather {
	/** The wind (as sails feel it) from which it pushes arrows and blows torches out. */
	static final float GALE = 1.0F;
	/** An arrow's sideways push each tick (blocks a tick, per tick) in the strongest wind. */
	static final double PUSH = 0.02, PUSH_LEAST = 0.004;
	/** Wind speeds above this add nothing more to the push. */
	static final float PUSH_CAP = 1.6F;
	/** An exposed torch goes out at a random tick with 1 chance in this many in a gale. */
	static final int TORCH_CHANCE = 3;
	/** Mobs see this share as far in rain, and in a thunderstorm. */
	static final double RAIN_SIGHT = 0.85, STORM_SIGHT = 0.7;
	/** Snow this many layers deep drags, by this share of speed; a charge on top of that, this many times as much. */
	static final int DEEP = 4;
	static final double DEEP_DRAG = -0.3, CHARGE = 1.4;
	/** An arrow slower than this (blocks a tick) isn't in flight. */
	static final double FLYING = 0.3;

	private static final Set<AbstractArrow> ARROWS = Collections.newSetFromMap(new WeakHashMap<>());

	private FieldWeather() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.is(EntityTypeTags.ARROWS)) {
				ARROWS.add(arrow);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(FieldWeather::tick);
	}

	/** Whether the wind at {@code pos} is a gale and the place is open to the sky. */
	static boolean gale(Level level, BlockPos pos) {
		return level.canSeeSky(pos) && Winds.strength(level, pos.getX() + 0.5, pos.getZ() + 0.5) >= GALE;
	}

	private static void tick(MinecraftServer server) {
		if (ARROWS.isEmpty()) {
			return;
		}
		for (AbstractArrow arrow : ARROWS.toArray(new AbstractArrow[0])) {
			if (arrow.isRemoved() || !(arrow.level() instanceof ServerLevel level)) {
				ARROWS.remove(arrow);
				continue;
			}
			Vec3 v = arrow.getDeltaMovement();
			if (v.length() < FLYING || arrow.onGround() || arrow.isInWater()) {
				continue;
			}
			BlockPos at = arrow.blockPosition();
			if (arrow.isOnFire() && level.isRainingAt(at)) {
				arrow.clearFire();
				level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.5F, 1.4F);
				level.sendParticles(ParticleTypes.SMOKE, arrow.getX(), arrow.getY(), arrow.getZ(), 4, 0.05, 0.05, 0.05, 0.01);
			}
			if (!level.canSeeSky(at)) {
				continue;
			}
			float wind = Winds.strength(level, arrow.getX(), arrow.getZ());
			if (wind < GALE) {
				continue;
			}
			double push = PUSH_LEAST + (PUSH - PUSH_LEAST) * Math.min(1.0, (wind - GALE) / (PUSH_CAP - GALE));
			float way = Storms.windAngle();
			arrow.setDeltaMovement(v.add(Math.cos(way) * push, 0.0, Math.sin(way) * push));
			arrow.syncVelocity = true;
		}
	}

	/** A torch's random tick: whether the wind blows it out. */
	public static boolean blowsOut(Level level, BlockPos pos, RandomSource random) {
		return gale(level, pos) && random.nextInt(TORCH_CHANCE) == 0;
	}

	/** What a hostile or neutral mob's sight is multiplied by in the rain at its place. */
	public static double sight(ServerLevel level, Mob looker) {
		BlockPos at = looker.blockPosition();
		if (!level.isRainingAt(at)) {
			return 1.0;
		}
		return level.isThundering() ? STORM_SIGHT : RAIN_SIGHT;
	}

	/** The drag of deep snow on {@code who} standing in {@code feet} (negative, a share of speed), or 0. */
	public static double deepSnow(LivingEntity who, BlockState feet) {
		boolean deep = feet.is(Blocks.POWDER_SNOW) || feet.is(Blocks.SNOW) && feet.getValue(SnowLayerBlock.LAYERS) >= DEEP;
		if (!deep) {
			return 0.0;
		}
		return who.isSprinting() || who instanceof AbstractHorse ? DEEP_DRAG * CHARGE : DEEP_DRAG;
	}
}
