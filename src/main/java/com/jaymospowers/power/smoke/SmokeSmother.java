package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B (hold), Smother: smoke wraps the creature you aim at (within {@link #RANGE} blocks) for as long as you hold B, up to
 * {@link #MAX_TICKS}. It can't see (Blindness) and wades (Slowness II); its air runs out as if under water, and then it
 * chokes, a heart a second, through armour. Creatures that don't breathe (the undead, fish) are only blinded and
 * slowed. Lose sight of it past {@link #KEEP_RANGE} and it ends. The cooldown grows with how long it lasted.
 */
final class SmokeSmother {
	static final double RANGE = 24.0;
	static final double KEEP_RANGE = 32.0;
	static final int MAX_TICKS = 120;
	/** Smoke a tick round its head. */
	static final float WRAP = 1.1F;
	/** Air taken a tick (vanilla gives back 4 a tick out of water; a full breath is 300). */
	static final int AIR_DRAIN = 19;
	static final float CHOKE = 2.0F;
	/** Smother's choking: through armour, drowning's hurt sound. */
	static final ResourceKey<DamageType> CHOKE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("smoke_choke"));

	private record Hold(int target, long from) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();

	private SmokeSmother() {
	}

	static void start(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(RANGE)),
			player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof ArmorStand) && e != player, RANGE * RANGE);
		if (aimed == null || !(aimed.getEntity() instanceof LivingEntity target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_smother_nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		long now = player.level().getGameTime();
		HOLDS.put(player.getUUID(), new Hold(target.getId(), now));
		SmokePower.set(player, SmokePower.state(player).withSmother(now));
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.4F);
	}

	/** Lets go; with {@code cooldown}, the cooldown starts (longer the longer it lasted). */
	static void stop(ServerPlayer player, boolean cooldown) {
		Hold hold = HOLDS.remove(player.getUUID());
		if (hold == null) {
			return;
		}
		SmokePower.set(player, SmokePower.state(player).withSmother(-1L));
		if (cooldown) {
			float share = Mth.clamp((player.level().getGameTime() - hold.from) / (float)MAX_TICKS, 0.0F, 1.0F);
			SmokePower.cooldown(player, SmokeState.SMOTHER,
				Math.round(Mth.lerp(share, SmokePower.SMOTHER_COOLDOWN_MIN, SmokePower.SMOTHER_COOLDOWN_MAX)));
		}
	}

	static void tick(ServerPlayer player) {
		Hold hold = HOLDS.get(player.getUUID());
		if (hold == null) {
			return;
		}
		ServerLevel level = player.level();
		Entity entity = level.getEntity(hold.target);
		long held = level.getGameTime() - hold.from;
		if (!player.isAlive() || !(entity instanceof LivingEntity target) || !target.isAlive() || held >= MAX_TICKS
			|| target.distanceToSqr(player) > KEEP_RANGE * KEEP_RANGE) {
			stop(player, true);
			return;
		}
		SmokeField.puff(level, target.getEyePosition(), WRAP, player);
		if (held % 20 == 0) {
			target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), player);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
		}
		if (!target.canBreatheUnderwater()) {
			int air = Math.max(-20, target.getAirSupply() - AIR_DRAIN);
			target.setAirSupply(air);
			if (air <= 0 && held % 20 == 0) {
				target.hurtServer(level, player.damageSources().source(CHOKE_TYPE, player), CHOKE);
			}
		}
		player.causeFoodExhaustion(0.02F);
	}

	static void clear() {
		HOLDS.clear();
	}
}
