package com.jaymospowers.power.chrono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Paradox Cut, Timeless Chrono's ascended ability (C): a slash in front of you that hits now and, exactly
 * {@link #ECHO_DELAY} ticks later, hits every target again wherever it has gone. The second hit can't be outrun:
 * the target is found by UUID in any dimension. Until then, each target carries a ticking mark.
 */
final class ParadoxCut {
	// ---- tuning ----
	static final double REACH = 4.0;
	/** Half the slash's width, in degrees either side of where you look (horizontally). */
	static final double HALF_ARC_DEGREES = 60.0;
	/** Targets more than this far above or below your eyes are out of the slash. */
	static final double VERTICAL_REACH = 2.5;
	static final float DAMAGE = 9.0F;
	static final float ECHO_DAMAGE = 9.0F;
	/** 1.5 s from the cut to its echo. */
	static final int ECHO_DELAY = 30;
	static final int COOLDOWN = 160;
	private static final DustParticleOptions EDGE = new DustParticleOptions(0xFFE8A0, 1.0F);
	private static final DustParticleOptions MARK = new DustParticleOptions(0xA8E8FF, 0.9F);

	/** A target waiting for the second hit. */
	private record Mark(UUID target, UUID caster, int dueTick) {
	}

	private static final List<Mark> MARKS = new ArrayList<>();

	private ParadoxCut() {
	}

	/** Cuts now. Returns true (the cooldown starts) even if it hit nothing: the cut still happened. */
	static boolean cut(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		double minDot = Math.cos(Math.toRadians(HALF_ARC_DEGREES));
		int due = level.getServer().getTickCount() + ECHO_DELAY;

		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		slashFx(level, eye, flat);

		DamageSource source = player.damageSources().playerAttack(player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			if (target instanceof TamableAnimal pet && pet.isOwnedBy(player)) {
				continue;
			}
			Vec3 center = target.getBoundingBox().getCenter();
			Vec3 to = center.subtract(eye);
			if (Math.abs(to.y) > VERTICAL_REACH + target.getBbHeight() * 0.5) {
				continue;
			}
			Vec3 toFlat = new Vec3(to.x, 0.0, to.z);
			double distance = toFlat.length();
			if (distance > REACH + target.getBbWidth() * 0.5) {
				continue;
			}
			if (distance > 0.5 && toFlat.normalize().dot(flat) < minDot) {
				continue;
			}
			if (!player.hasLineOfSight(target)) {
				continue;
			}
			target.damageCooldownTime = 0;
			target.hurtServer(level, source, DAMAGE);
			MARKS.add(new Mark(target.getUUID(), player.getUUID(), due));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(MARK, center.x, center.y, center.z, 8, 0.25, 0.4, 0.25, 0.0);
		}
		return true;
	}

	/** End of every server tick: the marks tick, and the due ones are cut again. */
	static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (int i = MARKS.size() - 1; i >= 0; i--) {
			Mark mark = MARKS.get(i);
			LivingEntity target = find(server, mark.target());
			if (target == null || !target.isAlive()) {
				if (now >= mark.dueTick()) {
					MARKS.remove(i); // gone for good (or unloaded): nothing left to cut
				}
				continue;
			}
			ServerLevel level = (ServerLevel) target.level();
			Vec3 c = target.getBoundingBox().getCenter();
			int left = mark.dueTick() - now;
			if (left > 0) {
				markFx(level, target, c, left);
				continue;
			}
			MARKS.remove(i);
			ServerPlayer caster = server.getPlayerList().getPlayer(mark.caster());
			DamageSource source = caster != null ? level.damageSources().indirectMagic(caster, caster) : level.damageSources().magic();
			target.damageCooldownTime = 0;
			target.hurtServer(level, source, ECHO_DAMAGE);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 16, 0.3, 0.5, 0.3, 0.2);
			level.sendParticles(MARK, c.x, c.y, c.z, 20, 0.35, 0.6, 0.35, 0.0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.6F);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.8F);
		}
	}

	static void clear() {
		MARKS.clear();
	}

	private static @Nullable LivingEntity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof LivingEntity living) {
				return living;
			}
		}
		return null;
	}

	/** A pale tick mark that hangs over the target, ticking down to the second cut. */
	private static void markFx(ServerLevel level, LivingEntity target, Vec3 c, int left) {
		if (left % 3 == 0) {
			double top = target.getY() + target.getBbHeight() + 0.35;
			// a little clock hand, sweeping round as the moment comes back
			double a = (ECHO_DELAY - left) / (double) ECHO_DELAY * Math.PI * 2.0;
			for (int r = 0; r <= 3; r++) {
				double d = r * 0.1;
				level.sendParticles(MARK, target.getX() + Math.sin(a) * d, top + Math.cos(a) * d, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(MARK, c.x, c.y, c.z, 1, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.0);
		}
		if (left % 10 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.7F, left % 20 == 0 ? 1.9F : 1.4F);
		}
	}

	/** A gold crescent in front of the caster, plus the sweep and a bright chime. */
	private static void slashFx(ServerLevel level, Vec3 eye, Vec3 flat) {
		double baseYaw = Math.atan2(flat.z, flat.x);
		double half = Math.toRadians(HALF_ARC_DEGREES);
		for (int i = 0; i <= 16; i++) {
			double a = baseYaw - half + (2.0 * half) * i / 16.0;
			double r = REACH * 0.8;
			double tilt = (i - 8) / 8.0 * 0.4;
			level.sendParticles(EDGE, eye.x + Math.cos(a) * r, eye.y - 0.3 + tilt, eye.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, eye.x + Math.cos(a) * r * 0.7, eye.y - 0.4, eye.z + Math.sin(a) * r * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6F, 1.5F);
	}
}
