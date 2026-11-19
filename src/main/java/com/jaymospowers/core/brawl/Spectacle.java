package com.jaymospowers.core.brawl;

import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Impact you can see and feel (NPC fights patch 3, row F11): what a heavy blow, a clash or a slam leaves on the ground (a ring
 * of dust, cracks running out of it in the ground's own debris) and in the air (a shockwave ring, a boom), the dust trail behind
 * a thrown body, and the shake every player near feels (only {@link Shakes}, never a second system).
 */
public final class Spectacle {
	/** Cracks: how many spokes at power 0 and 1, how far they run (blocks) at power 1, and the step between their debris. */
	static final int CRACKS_MIN = 4, CRACKS_MAX = 8;
	static final double CRACK_LENGTH = 3.5, CRACK_STEP = 0.55;
	/** The dust ring's reach (blocks, as {@link Debris#ring} takes it) and the shockwave ring's size, at power 0 and 1. */
	static final int DUST_RADIUS_MIN = 1, DUST_RADIUS_MAX = 3;
	static final float SHOCK_MIN = 1.5F, SHOCK_MAX = 4.0F;
	/** The camera shake (degrees) and how far it carries (blocks), at power 0 and 1. */
	static final float SHAKE_MIN = 0.4F, SHAKE_MAX = 1.5F;
	static final double SHAKE_RANGE = 18.0;
	/** A thrown body's trail: debris puffs a tick, and how close to the ground it must be (blocks) to drag them. */
	static final int TRAIL_PUFFS = 3;
	static final double TRAIL_GROUND = 3.0;
	public static final int SHOCK_COLOR = 0xFFE9C0;

	private Spectacle() {
	}

	/** The block {@code at} stands on (or the nearest solid one just under it), or null over a long drop. */
	public static @Nullable BlockState ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y - 0.3, at.z);
		for (int i = 0; i < 4; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty()) {
				return state;
			}
			pos = pos.below();
		}
		return null;
	}

	/**
	 * A hard blow or a slam at {@code at}, {@code power} (0 to 1) strong: a shockwave ring, a ring of dust and cracks running
	 * out of it when there is ground under it, the boom, and a shake for everyone near.
	 */
	public static void impact(ServerLevel level, Vec3 at, float power) {
		power = Mth.clamp(power, 0.0F, 1.0F);
		MagicFx.ring(level, at, null, Mth.lerp(power, SHOCK_MIN, SHOCK_MAX), SHOCK_COLOR, MagicFx.RING_TICKS);
		BlockState ground = ground(level, at);
		if (ground != null) {
			BlockPos under = BlockPos.containing(at.x, at.y - 0.3, at.z);
			Debris.ring(level, under, Math.round(Mth.lerp(power, (float)DUST_RADIUS_MIN, (float)DUST_RADIUS_MAX)), 0.7F + power * 0.6F);
			cracks(level, at, ground, power);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.5F + 0.6F * power, 1.5F - 0.5F * power);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.6F);
		Shakes.around(level, at, SHAKE_RANGE, Mth.lerp(power, SHAKE_MIN, SHAKE_MAX));
	}

	/** Cracks running out of {@code at} along the ground: spokes of the ground's own crumbling debris. */
	public static void cracks(ServerLevel level, Vec3 at, BlockState ground, float power) {
		RandomSource random = level.getRandom();
		int spokes = Math.round(Mth.lerp(power, (float)CRACKS_MIN, (float)CRACKS_MAX));
		double length = CRACK_LENGTH * (0.5 + 0.5 * power);
		double turn = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < spokes; i++) {
			double angle = turn + Math.PI * 2.0 * i / spokes + (random.nextDouble() - 0.5) * 0.4;
			double reach = length * (0.6 + 0.4 * random.nextDouble());
			for (double d = 0.5; d <= reach; d += CRACK_STEP) {
				double bend = (random.nextDouble() - 0.5) * 0.25;
				double x = at.x + Mth.cos((float)(angle + bend)) * d, z = at.z + Mth.sin((float)(angle + bend)) * d;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, ground), true, false, x, at.y + 0.05, z, 2, 0.08, 0.0, 0.08, 0.0);
			}
		}
	}

	/** A body flying (thrown by a blow or a finisher) leaves a dust trail: the ground's debris when it is low, a cloud always. */
	public static void trail(ServerLevel level, LivingEntity body) {
		Vec3 at = body.position().add(0.0, body.getBbHeight() * 0.4, 0.0);
		BlockState ground = ground(level, body.position());
		if (ground != null && !level.getBlockState(BlockPos.containing(body.getX(), body.getY() - TRAIL_GROUND, body.getZ())).isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, false, at.x, at.y, at.z, TRAIL_PUFFS, 0.25, 0.3, 0.25, 0.05);
		}
		level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.01);
	}
}
