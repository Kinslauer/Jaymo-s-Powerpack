package com.jaymospowers.power.resonance;

import com.jaymospowers.core.Fate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R, Pulse: a cone of sound in front of you. Every creature in it (in sight) takes {@code DAMAGE} (through armour on
 * sculk-sensitive creatures), is knocked back {@code KNOCKBACK} and stops blocking, eating or drinking; glass panes
 * in it shatter. Tuned low it knocks back harder; tuned high it hits harder. Shrieker (corrupted): sculk spreads where it
 * lands.
 */
final class ResonancePulse {
	static final double RANGE = 8.0;
	static final double RANGE_MASTERED = 12.0;
	/** The cone's half-angle: cos(35°). */
	static final double CONE_COS = Math.cos(Math.toRadians(35.0));
	static final float DAMAGE = 5.0F;
	static final double KNOCKBACK = 1.5;
	static final double KNOCKBACK_UP = 0.35;
	/** Tuned high: damage +25% per step (+50% at the top). */
	static final float HIGH_DAMAGE_PER_STEP = 0.25F;
	/** Tuned low: knockback +33% per step. */
	static final double LOW_KNOCKBACK_PER_STEP = 0.33;
	/** An interrupted shield, meal or drink can't be raised again for this long (ticks). */
	static final int INTERRUPT_TICKS = 20;
	/** Shrieker: sculk patches this wide (tries) where it lands, at most this many landings. */
	static final int SCULK_TRIES = 5;
	static final int SCULK_LANDINGS = 4;

	private ResonancePulse() {
	}

	static double range(ServerPlayer player) {
		return ResonancePower.mastered(player) ? RANGE_MASTERED : RANGE;
	}

	static boolean fire(ServerPlayer player) {
		ServerLevel level = player.level();
		double range = range(player);
		int tune = ResonancePower.tune(player);
		float damage = DAMAGE * (1.0F + HIGH_DAMAGE_PER_STEP * Math.max(0, tune));
		double knockback = KNOCKBACK * (1.0 + LOW_KNOCKBACK_PER_STEP * Math.max(0, -tune));
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		List<BlockPos> landings = new ArrayList<>();

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > range || distance < 1.0E-3 || to.scale(1.0 / distance).dot(look) < CONE_COS || !player.hasLineOfSight(target)) {
				continue;
			}
			boolean deep = ResonanceDamage.sensitive(target);
			target.hurtServer(level, ResonanceDamage.source(player, deep ? ResonanceDamage.PULSE_DEEP : ResonanceDamage.PULSE), damage);
			ResonanceDamage.push(target, to, knockback, KNOCKBACK_UP);
			interrupt(target);
			level.sendParticles(ParticleTypes.NOTE, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 1, 0.0, 0.0, 0.0,
				(tune + 2) / 4.0);
			if (landings.size() < SCULK_LANDINGS) {
				landings.add(target.blockPosition().below());
			}
		}
		shatterPanes(player, level, eye, look, range);

		// the wave, visible to everyone: rings of pops widening down the cone
		RandomSource random = level.getRandom();
		for (double d = 1.0; d <= range; d += 1.0) {
			double spread = d * Math.tan(Math.acos(CONE_COS)) * 0.8;
			Vec3 at = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y, at.z, 2 + (int)d, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
		}
		if (random.nextBoolean()) {
			level.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x * 1.5, eye.y + look.y * 1.5, eye.z + look.z * 1.5, 1, 0.0, 0.0, 0.0, 0.0);
		}
		float pitch = ResonancePower.pitch(tune);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, pitch);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, pitch);

		if (Fate.isCorrupted(player)) {
			BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				landings.add(hit.getBlockPos());
			}
			for (BlockPos landing : landings) {
				sculk(player, level, landing);
			}
		}
		return true;
	}

	/** Stops whatever the creature is using (a raised shield, a meal, a drink); a player can't raise it again at once. */
	static void interrupt(LivingEntity target) {
		if (!target.isUsingItem()) {
			return;
		}
		ItemStack using = target.getUseItem();
		target.stopUsingItem();
		if (target instanceof Player player && !using.isEmpty()) {
			player.getCooldowns().addCooldown(using, INTERRUPT_TICKS);
		}
	}

	/** Glass panes in the cone (and in sight of nothing but air) shatter. */
	private static void shatterPanes(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 look, double range) {
		AABB box = new AABB(eye, eye).inflate(range);
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX),
			Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (!isPane(state)) {
				continue;
			}
			Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
			double distance = to.length();
			if (distance > range || distance < 1.0E-3 || to.scale(1.0 / distance).dot(look) < CONE_COS) {
				continue;
			}
			BlockPos at = pos.immutable();
			if (ResonanceDamage.mayBreak(player, level, at, state)) {
				ResonanceDamage.breakBlock(player, level, at, state, true);
			}
		}
	}

	static boolean isPane(BlockState state) {
		return state.getBlock() instanceof IronBarsBlock && state.getSoundType() == SoundType.GLASS;
	}

	/** Shrieker: a patch of sculk takes the ground where the pulse landed. */
	private static void sculk(ServerPlayer player, ServerLevel level, BlockPos around) {
		RandomSource random = level.getRandom();
		boolean spread = false;
		for (int i = 0; i < SCULK_TRIES; i++) {
			BlockPos pos = around.offset(random.nextInt(3) - 1, random.nextInt(2) - 1, random.nextInt(3) - 1);
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.SCULK_REPLACEABLE) || state.is(Blocks.SCULK) || !level.getBlockState(pos.above()).isAir()
				|| !ResonanceDamage.mayBreak(player, level, pos, state)) {
				continue;
			}
			level.setBlockAndUpdate(pos, Blocks.SCULK.defaultBlockState());
			level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.02);
			spread = true;
		}
		if (spread) {
			level.playSound(null, around, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}
}
