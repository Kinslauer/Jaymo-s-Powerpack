package com.jaymospowers.power.darktide;

import com.jaymospowers.core.Mastery;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * X (hold, release), Gedan: Stillwater. Katana only, once all four abilities are unlocked. Hold to take the low stance:
 * a field spreads to {@link #FIELD_RADIUS} blocks over {@link #FIELD_GROW_TICKS} and you can't move. Attacked meanwhile,
 * you parry (+1 slash for every enemy, up to {@link #MAX_EXTRA_SLASHES}; the attacker stunned and reeling) and step to the
 * next kamae. Release: you sink, surface under every enemy in the field (up to {@link #MAX_TARGETS}) for a slash each,
 * and come back to where you stood. Untouchable while sunken.
 */
final class DarktideGedan {
	public static final double FIELD_RADIUS = 10.0;
	public static final int FIELD_GROW_TICKS = 100;
	static final int MAX_TARGETS = 8;
	static final float SLASH_DAMAGE = 6.0F, SLASH_PER_SECOND = 1.0F;
	/** Seconds held that count toward the slash damage. */
	static final int MAX_SECONDS = 5;
	static final int MAX_EXTRA_SLASHES = 2;
	static final int COOLDOWN = 1200;
	/** Ticks between surfacing under one enemy and the next. */
	static final int SLASH_EVERY = 4;
	/** A hold this long releases itself. */
	static final int MAX_HOLD = 300;
	static final int PARRY_STUN = 24;
	static final float PARRY_FLOW = 8.0F;

	private DarktideGedan() {
	}

	static void key(ServerPlayer player, boolean down) {
		DarktideLive live = DarktidePower.live(player);
		if (!down) {
			if (live.gedan != null && live.gedan.released < 0) {
				release(player, live);
			}
			return;
		}
		if (live.gedan != null) {
			return;
		}
		if (Mastery.unlocked(player) < Mastery.ALL) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (DarktideWeapons.kit(player) != DarktideWeapons.Kit.KATANA) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.katana_only").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!DarktidePower.ready(player, DarktideState.GEDAN)) {
			return;
		}
		DarktideGuard.drop(player);
		ServerLevel level = player.level();
		live.gedan = new DarktideLive.Gedan(level.getGameTime(), player.position(), player.getYRot());
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		DarktideMove.play(player, DarktideMove.STANCE, MAX_HOLD + 40, DarktideMove.GEDAN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** The field's reach after {@code ticks} held. */
	static double radius(long ticks) {
		return FIELD_RADIUS * Math.min(1.0, ticks / (double) FIELD_GROW_TICKS);
	}

	static void parry(ServerPlayer player, DarktideLive live, LivingEntity attacker) {
		DarktideLive.Gedan gedan = live.gedan;
		if (gedan == null || gedan.released >= 0) {
			return;
		}
		ServerLevel level = player.level();
		if (level.getGameTime() < live.parryReadyAt) {
			// the same blow landing twice in one swing
			return;
		}
		live.parryReadyAt = level.getGameTime() + 6;
		if (gedan.parries < MAX_EXTRA_SLASHES) {
			gedan.parries++;
		}
		gedan.stance = (gedan.stance + 1) % 4;
		DarktideStatus.stun(attacker, PARRY_STUN);
		DarktideStatus.reel(attacker, player.position());
		DarktidePower.addFlow(player, PARRY_FLOW);
		DarktidePower.countParry(player);
		DarktideMove.play(player, DarktideMove.STANCE, MAX_HOLD + 40, gedan.stance + 4 * gedan.parries);
		// the stance keeps its start (the field's growth): only the kamae changes
		player.setAttached(DarktideState.MOVE, new DarktideState.Move(DarktideMove.STANCE, gedan.start, MAX_HOLD + 40, gedan.stance + 4 * gedan.parries));
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8));
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFBFE6FF), at.x, at.y - 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y - 0.3, at.z, 14, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.9F);
		DarktideNet.fx(player, player, new DarktideNet.Fx(DarktideNet.Fx.PARRY, player.getId(), attacker.getId()));
	}

	private static void release(ServerPlayer player, DarktideLive live) {
		DarktideLive.Gedan gedan = live.gedan;
		ServerLevel level = player.level();
		long held = level.getGameTime() - gedan.start;
		gedan.seconds = (int) Math.min(MAX_SECONDS, held / 20);
		double r = radius(held);
		List<LivingEntity> inField = level.getEntitiesOfClass(LivingEntity.class, new AABB(gedan.origin, gedan.origin).inflate(r, 4.0, r),
			e -> e.isAlive() && !e.isSpectator() && !DarktideMoves.friendly(player, e) && e.position().distanceTo(gedan.origin) <= r);
		inField.sort(Comparator.comparingDouble(e -> e.distanceToSqr(gedan.origin)));
		for (int i = 0; i < Math.min(MAX_TARGETS, inField.size()); i++) {
			gedan.targets.add(inField.get(i).getId());
		}
		gedan.released = level.getGameTime();
		DarktidePower.cooldown(player, DarktideState.GEDAN, COOLDOWN);
		if (gedan.targets.isEmpty()) {
			end(player, live);
			return;
		}
		DarktideMove.play(player, DarktideMove.STILLWATER, gedan.targets.size() * SLASH_EVERY + 6, 0);
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.6, 0.05, 0.6, 0.2);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.3, player.getZ(), 30, 0.5, 0.2, 0.5, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.setInvisible(true);
	}

	static void tick(ServerPlayer player, DarktideLive live) {
		DarktideLive.Gedan gedan = live.gedan;
		if (gedan == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (gedan.released < 0) {
			if (DarktideWeapons.kit(player) != DarktideWeapons.Kit.KATANA || DarktideStatus.stunned(player)) {
				cancel(player);
				return;
			}
			if (now - gedan.start >= MAX_HOLD) {
				release(player, live);
				return;
			}
			// rooted: the client holds the keys; this catches anything else (knockback, ice)
			Vec3 v = player.getDeltaMovement();
			if (v.x * v.x + v.z * v.z > 1.0E-4) {
				player.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
				player.syncVelocity = true;
			}
			if (now % 10 == 0) {
				double r = radius(now - gedan.start);
				var random = level.getRandom();
				for (int i = 0; i < 6; i++) {
					double a = random.nextDouble() * Math.PI * 2.0;
					double d = Math.sqrt(random.nextDouble()) * r;
					level.sendParticles(ParticleTypes.FALLING_WATER, gedan.origin.x + Math.cos(a) * d, gedan.origin.y + 0.2, gedan.origin.z + Math.sin(a) * d,
						1, 0.0, 0.0, 0.0, 0.0);
				}
			}
			return;
		}
		long t = now - gedan.released;
		if (t % SLASH_EVERY != SLASH_EVERY - 1) {
			return;
		}
		if (gedan.next >= gedan.targets.size()) {
			end(player, live);
			return;
		}
		Entity entity = level.getEntity(gedan.targets.get(gedan.next++));
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		// surface right beneath it, cut, and sink again
		player.teleportTo(level, target.getX(), target.getY(), target.getZ(), Set.of(), player.getYRot(), player.getXRot(), false);
		float damage = SLASH_DAMAGE + SLASH_PER_SECOND * gedan.seconds;
		int slashes = 1 + gedan.parries;
		for (int i = 0; i < slashes && target.isAlive(); i++) {
			DarktideMoves.hit(player, target, damage);
		}
		DarktideStatus.reel(target, target.position().add(player.getLookAngle().scale(-1.0)));
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 0.1, target.getZ(), 30, 0.4, 0.05, 0.4, 0.3);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F + level.getRandom().nextFloat() * 0.2F);
	}

	private static void end(ServerPlayer player, DarktideLive live) {
		DarktideLive.Gedan gedan = live.gedan;
		live.gedan = null;
		if (gedan == null) {
			return;
		}
		if (gedan.released >= 0) {
			ServerLevel level = player.level();
			player.teleportTo(level, gedan.origin.x, gedan.origin.y, gedan.origin.z, Set.of(), gedan.yaw, player.getXRot(), false);
			player.setInvisible(false);
			level.sendParticles(ParticleTypes.SPLASH, gedan.origin.x, gedan.origin.y + 0.1, gedan.origin.z, 30, 0.5, 0.05, 0.5, 0.2);
		}
		DarktideMove.clear(player);
	}

	/** Ends the stance (or the slashes, bringing you back) without striking. */
	static void cancel(ServerPlayer player) {
		DarktideLive live = DarktidePower.liveIfAny(player);
		if (live != null && live.gedan != null) {
			end(player, live);
		}
	}
}
