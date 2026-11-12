package com.jaymospowers.power.resonance;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Resonance's cries, every one a variant of a key or a passive:
 * <ul>
 * <li>sneak + R, held: <b>Gauge Shriek</b>, a cry that builds on a gauge and goes out as a cone, harder the longer it was held.</li>
 * <li>V in mid-air: <b>Lifting Cry</b>, a cry that lifts you into the air and lets you down softly.</li>
 * <li>sneak + G: <b>Barrier Punch</b>, a dash-punch that breaks the sound barrier in a cone with a hard knockback.</li>
 * <li>passive: when hit there is a chance of a <b>Panic Cry</b> that slows and bleeds those near.</li>
 * </ul>
 */
final class ResonancePicks {
	private static final String TABLE = "resonance_picks";
	private static final int SHRIEK = 0, LIFT = 1, PUNCH = 2, PANIC = 3;

	/** Gauge Shriek: ticks to a full gauge, the least that counts, reach (short and full), cone half-angle (degrees), damage (low and full), knock, cooldown. */
	static final int FULL_TICKS = 60, MIN_TICKS = 8, SHRIEK_COOLDOWN = 120;
	static final double REACH_MIN = 6.0, REACH_MAX = 18.0, CONE_DEGREES = 38.0, KNOCK_MIN = 0.6, KNOCK_MAX = 2.0;
	static final float DAMAGE_MIN = 2.0F, DAMAGE_MAX = 11.0F;
	static final int GAUGE_WIDTH = 20;
	/** Lifting Cry: the lift (blocks/tick), ticks of slow fall after, cooldown. */
	static final double LIFT_SPEED = 1.0;
	static final int LIFT_SLOW_FALL = 80, LIFT_COOLDOWN = 160;
	/** Barrier Punch: dash speed, ticks it may run, how close a foe sets it off, cone reach and half-angle, damage, knock, cooldown. */
	static final double PUNCH_SPEED = 1.5, PUNCH_TRIGGER = 1.9, PUNCH_REACH = 8.0, PUNCH_DEGREES = 45.0, PUNCH_KNOCK = 2.2;
	static final int PUNCH_TICKS = 7, PUNCH_COOLDOWN = 220;
	static final float PUNCH_DAMAGE = 9.0F;
	/** Panic Cry: the chance when hit, radius, slow ticks, cooldown. */
	static final float PANIC_CHANCE = 0.25F;
	static final double PANIC_RADIUS = 6.0;
	static final int PANIC_SLOW = 80, PANIC_COOLDOWN = 400;
	static final int VOICE = 0xB48CF0, EDGE = 0xE9DCFF;

	private record Punch(long until, Vec3 dir) {
	}

	private static final Map<UUID, Long> SHRIEKING = new HashMap<>();
	private static final Map<UUID, Punch> PUNCHING = new HashMap<>();

	private ResonancePicks() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && player.isAlive() && Powers.has(player, ResonancePower.INSTANCE)
				&& Mastery.isUnlocked(player, PowerKey.ABILITY_1) && player.getRandom().nextFloat() < PANIC_CHANCE && PickKit.ready(TABLE, player, PANIC)) {
				panic(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
	}

	static void forget(ServerPlayer player) {
		SHRIEKING.remove(player.getUUID());
		PUNCHING.remove(player.getUUID());
		PickKit.forget(player);
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_picks." + key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
	}

	/** R or G, down or up. True if it was one of the cries (the key then does nothing else). */
	static boolean key(ServerPlayer player, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1 && !down) {
			Long since = SHRIEKING.remove(player.getUUID());
			if (since != null) {
				release(player, player.level().getGameTime() - since);
				return true;
			}
			return false;
		}
		if (!down || !player.isShiftKeyDown() || player.isSpectator() || !Mastery.isUnlocked(player, key)) {
			return false;
		}
		if (key == PowerKey.ABILITY_1) {
			if (!PickKit.ready(TABLE, player, SHRIEK)) {
				say(player, "cooling");
				return true;
			}
			SHRIEKING.put(player.getUUID(), player.level().getGameTime());
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.7F, 1.5F);
			return true;
		}
		if (key == PowerKey.ABILITY_2) {
			if (!PickKit.ready(TABLE, player, PUNCH)) {
				say(player, "cooling");
				return true;
			}
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			if (flat.lengthSqr() < 1.0E-4) {
				return true;
			}
			PUNCHING.put(player.getUUID(), new Punch(player.level().getGameTime() + PUNCH_TICKS, flat.normalize()));
			PickKit.spend(TABLE, player, PUNCH, PUNCH_COOLDOWN);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.6F);
			return true;
		}
		return false;
	}

	/** V pressed in mid-air: the cry that lifts. True if it did. */
	static boolean lift(ServerPlayer player) {
		if (player.isShiftKeyDown() || player.onGround() || player.isInWater() || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) {
			return false;
		}
		if (!PickKit.ready(TABLE, player, LIFT)) {
			return false;
		}
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, LIFT, LIFT_COOLDOWN);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, Math.max(v.y, LIFT_SPEED), v.z);
		player.syncVelocity = true;
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LIFT_SLOW_FALL, 0, true, false));
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), null, 2.4F, VOICE, 12);
		MagicFx.blast(level, player.position().add(0.0, 0.3, 0.0), 1.4F, EDGE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.8F);
		player.causeFoodExhaustion(1.0F);
		return true;
	}

	/** Foes in a cone from the player's eyes. */
	private static java.util.List<LivingEntity> cone(ServerPlayer player, Vec3 dir, double reach, double degrees) {
		Vec3 eye = player.getEyePosition();
		double cos = Math.cos(Math.toRadians(degrees));
		return player.level().getEntitiesOfClass(LivingEntity.class, PickKit.around(eye.add(dir.scale(reach * 0.5)), reach * 0.6 + 1.0), e -> {
			if (!PickKit.foe(player, e)) {
				return false;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			return d <= reach && (d < 1.5 || to.normalize().dot(dir) >= cos);
		});
	}

	private static void release(ServerPlayer player, long held) {
		ServerLevel level = player.level();
		if (held < MIN_TICKS) {
			return;
		}
		float s = Mth.clamp(held / (float)FULL_TICKS, 0.0F, 1.0F);
		double reach = Mth.lerp(s, REACH_MIN, REACH_MAX);
		Vec3 dir = player.getLookAngle();
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
		if (flat.lengthSqr() > 1.0E-4) {
			MagicFx.wave(level, player.position().add(0.0, 0.1, 0.0), flat, reach, (float)(1.5 + 3.0 * s), VOICE, 14);
		}
		MagicFx.beam(level, player.getEyePosition().add(dir.scale(0.5)), player.getEyePosition().add(dir.scale(reach)), 0.15F + 0.25F * s, EDGE, 8);
		for (LivingEntity e : cone(player, dir, reach, CONE_DEGREES)) {
			e.hurtServer(level, ResonanceDamage.source(player, ResonanceDamage.PULSE), Mth.lerp(s, DAMAGE_MIN, DAMAGE_MAX));
			ResonanceDamage.push(e, dir, Mth.lerp(s, KNOCK_MIN, KNOCK_MAX), 0.25);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F + 0.8F * s, 1.4F - 0.5F * s);
		Shakes.around(level, player.position(), 20.0, 0.5F + 1.0F * s);
		player.causeFoodExhaustion(1.0F + 2.0F * s);
		PickKit.spend(TABLE, player, SHRIEK, SHRIEK_COOLDOWN);
	}

	private static void punchTick(ServerPlayer player, ServerLevel level, long now) {
		Punch punch = PUNCHING.get(player.getUUID());
		if (punch == null) {
			return;
		}
		boolean boom = now >= punch.until;
		Vec3 eye = player.getEyePosition();
		if (!boom) {
			player.setDeltaMovement(punch.dir.x * PUNCH_SPEED, Math.max(player.getDeltaMovement().y, 0.0), punch.dir.z * PUNCH_SPEED);
			player.syncVelocity = true;
			player.resetFallDistance();
			level.sendParticles(MagicFx.spark(EDGE, 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(player.position().add(punch.dir.scale(1.0)), PUNCH_TRIGGER), c -> PickKit.foe(player, c))) {
				boom = true;
				break;
			}
		}
		if (!boom) {
			return;
		}
		PUNCHING.remove(player.getUUID());
		Vec3 dir = new Vec3(punch.dir.x, 0.0, punch.dir.z).normalize();
		MagicFx.wave(level, player.position().add(0.0, 0.1, 0.0), dir, PUNCH_REACH, 4.0F, EDGE, 16);
		MagicFx.blast(level, eye.add(dir.scale(1.2)), 2.4F, VOICE);
		for (LivingEntity e : cone(player, dir, PUNCH_REACH, PUNCH_DEGREES)) {
			e.hurtServer(level, ResonanceDamage.source(player, ResonanceDamage.SONIC_BOOM), PUNCH_DAMAGE);
			ResonanceDamage.push(e, dir, PUNCH_KNOCK, 0.35);
		}
		player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
		player.syncVelocity = true;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
		Shakes.around(level, player.position(), 24.0, 1.4F);
		player.causeFoodExhaustion(2.0F);
	}

	private static void panic(ServerPlayer player) {
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, PANIC, PANIC_COOLDOWN);
		Vec3 at = player.position();
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), null, (float)PANIC_RADIUS, VOICE, 14);
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), 2.0F, EDGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, PANIC_RADIUS), c -> PickKit.foe(player, c))) {
			if (e.distanceToSqr(at) > PANIC_RADIUS * PANIC_RADIUS) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PANIC_SLOW, 2));
			Bleeding.open(e, BodyPart.HEAD, false);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.6F, 1.8F);
		Shakes.around(level, at, 16.0, 0.8F);
		say(player, "panic");
	}

	static void tick(ServerPlayer player) {
		if (SHRIEKING.isEmpty() && PUNCHING.isEmpty()) {
			return;
		}
		if (!player.isAlive()) {
			forget(player);
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long since = SHRIEKING.get(player.getUUID());
		if (since != null) {
			float s = Mth.clamp((now - since) / (float)FULL_TICKS, 0.0F, 1.0F);
			player.sendOverlayMessage(Component.literal("Shriek " + PickKit.gauge(s, GAUGE_WIDTH)).withStyle(ChatFormatting.LIGHT_PURPLE));
			if ((now - since) % 6 == 0) {
				level.sendParticles(MagicFx.rune(VOICE, 0.8F), player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.5, 0.5, 0.5, 0.0);
			}
		}
		punchTick(player, level, now);
	}
}
