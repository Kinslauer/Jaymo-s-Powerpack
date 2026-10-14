package com.jaymospowers.power.smoke;

import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * G, Vapour Form: you turn to smoke for {@link #TICKS} ({@link #TICKS_MASTERED} mastered). You're unseen and quick,
 * you drift down slowly, and you leave a trail of smoke; blows, arrows and bites pass through you (fire, blasts and
 * falls still count). Mobs lose you. Hitting something brings you back at once.
 */
final class SmokeVapour {
	static final int TICKS = 80;
	static final int TICKS_MASTERED = 120;
	/** Smoke a tick you leave behind. */
	static final float TRAIL = 0.35F;
	/** Hunger a tick. */
	static final float EXHAUSTION = 0.03F;

	private SmokeVapour() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !active(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}
			// it passes through: a swirl where it went in
			ServerLevel level = player.level();
			Vec3 at = player.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.3, 0.5, 0.3, 0.02);
			SmokeField.puff(level, at, 0.8F, player);
			return false;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (player instanceof ServerPlayer server && active(server)) {
				end(server);
			}
			return InteractionResult.PASS;
		});
	}

	static boolean active(Player player) {
		return Powers.has(player, SmokePower.INSTANCE) && SmokePower.state(player).vapourUntil() > player.level().getGameTime();
	}

	static void start(ServerPlayer player) {
		int ticks = SmokePower.mastered(player) ? TICKS_MASTERED : TICKS;
		ServerLevel level = player.level();
		SmokePower.set(player, SmokePower.state(player).withVapour(level.getGameTime() + ticks));
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, false, false, false));
		Vec3 at = player.getBoundingBox().getCenter();
		SmokeField.puff(level, at, 4.0F, player);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	static void tick(ServerPlayer player) {
		long until = SmokePower.state(player).vapourUntil();
		if (until <= 0L) {
			return;
		}
		ServerLevel level = player.level();
		if (until <= level.getGameTime() || !player.isAlive()) {
			end(player);
			return;
		}
		SmokeField.puff(level, player.getBoundingBox().getCenter(), TRAIL, player);
		player.causeFoodExhaustion(EXHAUSTION);
	}

	/** Back to flesh: the effects go and the cooldown starts. */
	static void end(ServerPlayer player) {
		SmokeState state = SmokePower.state(player);
		if (state.vapourUntil() <= 0L) {
			return;
		}
		SmokePower.set(player, state.withVapour(0L));
		player.removeEffect(MobEffects.INVISIBILITY);
		player.removeEffect(MobEffects.SLOW_FALLING);
		player.removeEffect(MobEffects.SPEED);
		SmokePower.cooldown(player, SmokeState.VAPOUR, SmokePower.VAPOUR_COOLDOWN);
		Vec3 at = player.getBoundingBox().getCenter();
		SmokeField.puff(player.level(), at, 2.0F, player);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.2F);
	}
}
