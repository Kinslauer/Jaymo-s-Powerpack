package com.jaymoskingdoms.whaling;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.206) Whaling, stage 4: sharks come to a carcass. The blood in the water draws great white sharks (Naturalist's,
 * when it's in the pack; without it, none come): every {@link #DRAW_EVERY} ticks one may arrive from out of sight, up to
 * {@link #MOST} at a carcass, and those near it close in and tear at it, each bite taking a stroke's worth of flesh that
 * no one will cut now. Cut in quickly, or there's little left to cut.
 */
public final class Sharks {
	static final Identifier SHARK = Identifier.fromNamespaceAndPath("naturalist", "great_white_shark");
	/** How often (ticks) one may come, the chance it does, how many at most, from how far (blocks), and how far round it's seen. */
	static final int DRAW_EVERY = 400, MOST = 3;
	static final float DRAW_CHANCE = 0.35F;
	static final double ARRIVE_MIN = 16.0, ARRIVE_MAX = 24.0, RANGE = 32.0;
	/** How often (ticks) the sharks by it are looked over, the chance each near enough bites, and how near that is (beyond its side). */
	static final int BITE_EVERY = 40;
	static final float BITE_CHANCE = 0.1F;
	static final double BITE_REACH = 2.5;
	static final double SWIM = 1.2;
	/** (0.7.437) Out on a whaler's far grounds ({@link WhaleCarcass#wildness}): this many more sharks at most for each step out, and this much likelier to come. */
	static final float MORE_PER_WILD = 0.5F, CHANCE_PER_WILD = 0.08F;

	private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A1010, 1.6F);

	private Sharks() {
	}

	private static @Nullable EntityType<?> shark() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(SHARK).orElse(null);
	}

	/** The carcass's turn: a shark drawn in now and then, and those by it close in and bite. */
	static void tick(ServerLevel level, WhaleCarcass carcass, int age) {
		boolean draw = age % DRAW_EVERY == 0, bite = age % BITE_EVERY == 0;
		if (!draw && !bite || !carcass.isInWater()) {
			return;
		}
		EntityType<?> type = shark();
		if (type == null) {
			return;
		}
		List<Entity> sharks = level.getEntities((Entity) null, carcass.getBoundingBox().inflate(RANGE), e -> e.getType() == type && e.isAlive());
		int most = MOST + Math.round(carcass.wildness * MORE_PER_WILD);
		float chance = Math.min(0.9F, DRAW_CHANCE + carcass.wildness * CHANCE_PER_WILD);
		if (draw && sharks.size() < most && carcass.getRandom().nextFloat() < chance) {
			arrive(level, carcass, type);
		}
		if (!bite) {
			return;
		}
		double reach = carcass.getBbWidth() * 0.5 + BITE_REACH;
		for (Entity e : sharks) {
			if (e instanceof Mob shark) {
				shark.getNavigation().moveTo(carcass.getX(), carcass.getY() - 0.5, carcass.getZ(), SWIM);
			}
			if (e.distanceToSqr(carcass) <= reach * reach && carcass.getRandom().nextFloat() < BITE_CHANCE && carcass.bitten(level)) {
				level.sendParticles(BLOOD, e.getX(), e.getY() + 0.5, e.getZ(), 16, 0.6, 0.4, 0.6, 0.0);
				level.sendParticles(ParticleTypes.SPLASH, e.getX(), carcass.getY() + 1.0, e.getZ(), 12, 0.6, 0.1, 0.6, 0.1);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
			}
		}
	}

	/**
	 * (0.7.437) A shark come up out of the deep near {@code at} (a whaleboat out on the far grounds), set on {@code prey};
	 * false if there's no Naturalist shark, or no water deep enough there.
	 */
	public static boolean hunt(ServerLevel level, Vec3 at, @Nullable LivingEntity prey) {
		EntityType<?> type = shark();
		if (type == null) {
			return false;
		}
		for (int tries = 0; tries < 6; tries++) {
			float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
			double d = Mth.nextDouble(level.getRandom(), ARRIVE_MIN, ARRIVE_MAX);
			BlockPos pos = BlockPos.containing(at.x + Mth.cos(angle) * d, level.getSeaLevel() - 4, at.z + Mth.sin(angle) * d);
			if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.below()).is(FluidTags.WATER)
				&& level.getFluidState(pos.above()).is(FluidTags.WATER)) {
				Entity shark = type.spawn(level, pos, EntitySpawnReason.EVENT);
				if (shark instanceof Mob mob && prey != null) {
					mob.setTarget(prey);
				}
				return shark != null;
			}
		}
		return false;
	}

	/** One comes in from out where it can't be seen arriving. */
	private static void arrive(ServerLevel level, WhaleCarcass carcass, EntityType<?> type) {
		for (int tries = 0; tries < 6; tries++) {
			float angle = carcass.getRandom().nextFloat() * Mth.TWO_PI;
			double d = Mth.nextDouble(carcass.getRandom(), ARRIVE_MIN, ARRIVE_MAX);
			BlockPos at = BlockPos.containing(carcass.getX() + Mth.cos(angle) * d, carcass.getY() - 3.0, carcass.getZ() + Mth.sin(angle) * d);
			if (level.getFluidState(at).is(FluidTags.WATER) && level.getFluidState(at.below()).is(FluidTags.WATER)
				&& level.getFluidState(at.above()).is(FluidTags.WATER)) {
				type.spawn(level, at, EntitySpawnReason.EVENT);
				return;
			}
		}
	}
}
