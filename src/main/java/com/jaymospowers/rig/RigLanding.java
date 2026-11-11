package com.jaymospowers.rig;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one-knee landing: a fall that would have hurt (from {@link #MIN_FALL_DAMAGE} up) is caught by a full set with
 * charge to spare ({@link #COST}). The wearer drops to one knee, fist on the ground (client {@code RigPose}, move
 * {@link RigMoves#KNEE}), the ground cracks round them (the block-breaking cracks, fading), the screen shakes for those
 * near, and what stands close is thrown back and hurt a little.
 */
public final class RigLanding {
	/** A fall's damage that is caught with a landing (7 blocks is about 4), and what the catch costs in charge. */
	static final float MIN_FALL_DAMAGE = 4.0F;
	static final int COST = 40;
	/** The shockwave: how far it hurts and throws (blocks), the damage (up with the fall, capped) and the throw. */
	static final double SHOCK_RADIUS = 3.5, KNOCKBACK = 0.9;
	static final float SHOCK_DAMAGE_SHARE = 0.5F, SHOCK_DAMAGE_MAX = 8.0F;
	/** The screen shake (strength) and how far it carries (blocks); the ring of the blast. */
	static final float SHAKE = 1.0F, RING = 3.2F;
	static final double SHAKE_RANGE = 24.0;
	/** The cracks: how many, how far out (blocks), the deepest stage (0-9), and how long each stage lasts as it fades (ticks). */
	static final int CRACKS = 14, CRACK_STAGE_FROM = 4, CRACK_STAGE_TO = 9, CRACK_STEP = 14;
	static final double CRACK_RADIUS = 3.5;
	/** The dust thrown up. */
	static final int DUST = 18;

	/** A crack fading off a block. */
	private static final class Crack {
		final ServerLevel level;
		final int id;
		final BlockPos pos;
		int age, stage;

		Crack(ServerLevel level, int id, BlockPos pos, int stage) {
			this.level = level;
			this.id = id;
			this.pos = pos;
			this.stage = stage;
		}
	}

	private static final List<Crack> FADING = new ArrayList<>();

	private RigLanding() {
	}

	/** Every tick: the cracks fade a stage at a time and go. */
	static void tick(MinecraftServer server) {
		for (Iterator<Crack> it = FADING.iterator(); it.hasNext();) {
			Crack crack = it.next();
			if (++crack.age % CRACK_STEP != 0) {
				continue;
			}
			crack.stage--;
			crack.level.destroyBlockProgress(crack.id, crack.pos, crack.stage < 0 ? -1 : crack.stage);
			if (crack.stage < 0) {
				it.remove();
			}
		}
	}

	/** Whether this fall is caught (and the landing played); true cancels the damage. Server. */
	static boolean catches(ServerPlayer player, float damage) {
		if (damage < MIN_FALL_DAMAGE || !Rig.fullSet(player) || Rig.core(player).isEmpty() || !Rig.spend(player, COST)) {
			return false;
		}
		land(player, damage);
		return true;
	}

	private static void land(ServerPlayer player, float damage) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		RigMoves.start(player, RigMoves.KNEE);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), RING, RigActives.FLAME_COLOR);
		MagicFx.blast(level, at.add(0.0, 0.3, 0.0), 1.2F, RigActives.FLAME_HOT);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
		Shakes.heavy(player, at, SHAKE_RANGE, SHAKE);
		BlockPos below = player.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, DUST, 1.2, 0.1, 1.2, 0.1);
		}
		for (int i = 0; i < CRACKS; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 0.5 + level.getRandom().nextDouble() * CRACK_RADIUS;
			BlockPos pos = BlockPos.containing(at.x + Math.cos(angle) * distance, at.y - 0.5, at.z + Math.sin(angle) * distance);
			if (level.getBlockState(pos).isAir() || level.getBlockState(pos).getDestroySpeed(level, pos) < 0.0F) {
				continue;
			}
			int stage = CRACK_STAGE_FROM + level.getRandom().nextInt(CRACK_STAGE_TO - CRACK_STAGE_FROM + 1);
			// a unique id per block so each draws its own crack
			int id = -(pos.hashCode() & 0x3FFFFFFF) - 1;
			level.destroyBlockProgress(id, pos, stage);
			FADING.add(new Crack(level, id, pos, stage));
		}
		float shock = Math.min(SHOCK_DAMAGE_MAX, damage * SHOCK_DAMAGE_SHARE);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SHOCK_RADIUS), e -> e != player && e.isAlive())) {
			near.hurtServer(level, level.damageSources().playerAttack(player), shock);
			Vec3 away = near.position().subtract(at);
			near.knockback(KNOCKBACK, -away.x, -away.z, level.damageSources().playerAttack(player), shock);
			near.syncVelocity = true;
		}
	}
}
