package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Downed;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.69) Andy: "the symbiote should always takeover if you go down, healing you and commenting on how close you got
 * to death, it also Jumps as high as possible and web zips away before giving you control. It will only not takeover if
 * it is too low on hunger, and the bond is low."
 * <p>
 * You go down ({@link Downed}) with a symbiote bonded: after a beat ({@code BEAT_TICKS}) it gets you up with
 * {@code HEALTH} of your most health ({@link Downed#rouse}), for {@code SATIETY_COST} of its hunger, and says how close that
 * was: by how much health you had before the blow that felled you ({@code TIER_*}: a single blow from high up, or you
 * whittled to a thread), naming what felled you if something did. It has the wheel: it coils and leaps as high as it can
 * (the client, {@code WebSymbioteClient.tickRescue}), and at the top of the leap it fires a zip
 * ({@link WebPower#takeoverZip}) at the spot furthest from what's after you ({@code ZIP_*}); once the zip lets go (or
 * there was nothing to zip to and you've landed), it hands you back the controls with a line. No falls hurt you in
 * between. It refuses only when it's starving ({@link WebSymbiote#STARVING} or under) and the bond is under
 * {@code LOW_BOND}: then it says it's too weak, and you lie there as anyone would.
 */
public final class WebSymbioteRescue {
	static final int BEAT_TICKS = 15;
	static final float HEALTH = 0.5F;
	static final int SATIETY_COST = 20;
	static final int LOW_BOND = 30;
	/** Health share before the felling blow: at least {@code TIER_HIGH}, one blow took it all; under {@code TIER_LOW}, a thread. */
	static final float TIER_HIGH = 0.5F, TIER_LOW = 0.2F;
	/** The leap: how long it may take to reach the top (coil included), and the longest wait after for a landing (ticks). */
	static final int APEX_MAX_TICKS = 70, FALL_MAX_TICKS = 100;
	/** After the zip lets go, this long before you have the controls back (ticks). */
	static final int HAND_BACK_TICKS = 6;
	/** The zip: rays fanned every {@code ZIP_YAWS} round, at these pitches; a spot nearer than {@code ZIP_MIN} won't do. */
	static final int ZIP_YAWS = 16;
	static final float[] ZIP_PITCHES = {-35.0F, -18.0F, 0.0F, 15.0F, 30.0F};
	static final double ZIP_MIN = 8.0, THREAT_RANGE = 24.0;
	static final int LINES = 3;

	private static final int NONE = 0, WAITING = 1, LEAPING = 2, ZIPPING = 3, LANDING = 4;

	private static final class Rescue {
		int phase = NONE;
		long phaseAt;
		double peak;
		boolean rising;
		boolean refused;
		float healthWas = 1.0F;
	}

	private static final Map<UUID, Rescue> RESCUES = new HashMap<>();

	private WebSymbioteRescue() {
	}

	private static Rescue of(ServerPlayer player) {
		return RESCUES.computeIfAbsent(player.getUUID(), id -> new Rescue());
	}

	/** It has the wheel for a rescue right now (both sides know: the client has the legs). */
	public static boolean rescuing(ServerPlayer player) {
		Rescue rescue = RESCUES.get(player.getUUID());
		return rescue != null && rescue.phase != NONE;
	}

	/** From the symbiote's tick (bonded, alive): true while a rescue has the wheel (the rest of its tick waits). */
	static boolean tick(ServerPlayer player, WebSymbioteState state, long now) {
		Rescue rescue = of(player);
		boolean down = Downed.isDowned(player);
		if (rescue.phase == NONE) {
			if (!down) {
				rescue.refused = false;
				rescue.healthWas = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
				return false;
			}
			if (rescue.refused || player.isCreative() || player.isSpectator()) {
				return false;
			}
			if (state.satiety() <= WebSymbiote.STARVING && state.bond() < LOW_BOND) {
				// too weak to: you lie there as anyone would
				rescue.refused = true;
				WebSymbiote.whisper(player, "rescue.weak." + (1 + player.getRandom().nextInt(LINES)));
				return false;
			}
			WebSymbiote.standDown(player);
			rescue.phase = WAITING;
			rescue.phaseAt = now;
			WebSymbiote.dread(player, BEAT_TICKS + 20, true, true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.1F);
			return true;
		}
		if (!player.isAlive()) {
			rescue.phase = NONE;
			return false;
		}
		// nothing it does hurts you on the way down
		player.resetFallDistance();
		switch (rescue.phase) {
			case WAITING -> {
				if (now - rescue.phaseAt >= BEAT_TICKS) {
					rise(player, state, rescue, now);
				}
			}
			case LEAPING -> {
				if (down) {
					// felled again before it got you off the ground: it starts over
					rescue.phase = WAITING;
					rescue.phaseAt = now;
					return true;
				}
				double y = player.getY();
				if (y > rescue.peak + 0.05) {
					rescue.peak = y;
					rescue.rising = true;
				}
				boolean apex = rescue.rising && y < rescue.peak - 0.15;
				if (apex || now - rescue.phaseAt > APEX_MAX_TICKS) {
					rescue.phaseAt = now;
					rescue.phase = zipAway(player) ? ZIPPING : LANDING;
				}
			}
			case ZIPPING -> {
				if (now - rescue.phaseAt > 3 && !WebPower.pulling(player)) {
					rescue.phase = LANDING;
					rescue.phaseAt = now - FALL_MAX_TICKS + HAND_BACK_TICKS;
				}
			}
			case LANDING -> {
				if (player.onGround() || player.isInWater() || now - rescue.phaseAt >= FALL_MAX_TICKS) {
					handBack(player, rescue);
				}
			}
			default -> rescue.phase = NONE;
		}
		return rescue.phase != NONE;
	}

	/** Up you get: healed, told how close that was, and the leap starts. */
	private static void rise(ServerPlayer player, WebSymbioteState state, Rescue rescue, long now) {
		ServerLevel level = player.level();
		LivingEntity foe = foe(player);
		Downed.rouse(level, player, player.getMaxHealth() * HEALTH);
		player.setHealth(Math.max(player.getHealth(), player.getMaxHealth() * HEALTH));
		WebSymbiote.set(player, state.withSatiety(Math.max(0, state.satiety() - SATIETY_COST)));
		String tier = rescue.healthWas >= TIER_HIGH ? "outright" : rescue.healthWas >= TIER_LOW ? "close" : "thread";
		int line = 1 + player.getRandom().nextInt(LINES);
		if (foe != null) {
			WebSymbiote.whisper(player, "rescue." + tier + ".foe." + line, foe.getDisplayName());
		} else {
			WebSymbiote.whisper(player, "rescue." + tier + "." + line);
		}
		WebSymbiote.hush(player, now + 120);
		WebSymbiote.dread(player, APEX_MAX_TICKS + 40, true, true);
		Shakes.send(player, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.7F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(WebSymbiote.dust(1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
		rescue.phase = LEAPING;
		rescue.phaseAt = now;
		rescue.peak = player.getY();
		rescue.rising = false;
		ServerPlayNetworking.send(player, new WebNet.Rescue(true));
	}

	/** What felled you, if something did and it's still about. */
	private static @Nullable LivingEntity foe(ServerPlayer player) {
		LivingEntity by = player.getLastHurtByMob();
		return by != null && by.isAlive() && by.distanceTo(player) <= THREAT_RANGE ? by : null;
	}

	/**
	 * At the top of the leap: a zip at the spot furthest from what's after you (a top to land on liked best), at least
	 * {@code ZIP_MIN} off. True if it fired. Only a web slinger's body has the zip (a symbiote in anyone else just falls).
	 */
	private static boolean zipAway(ServerPlayer player) {
		if (!Powers.has(player, WebPower.INSTANCE)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		// where the danger is: what felled you and anything after you
		Vec3 danger = null;
		int count = 0;
		Vec3 sum = Vec3.ZERO;
		LivingEntity foe = foe(player);
		if (foe != null) {
			sum = sum.add(foe.position());
			count++;
		}
		List<Mob> after = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(THREAT_RANGE),
			m -> m.isAlive() && (m.getTarget() == player || m instanceof net.minecraft.world.entity.monster.Enemy));
		for (Mob mob : after) {
			sum = sum.add(mob.position());
			count++;
		}
		if (count > 0) {
			danger = sum.scale(1.0 / count);
		}
		double range = WebPower.ZIP_RANGE * WebSymbiote.reachScale(player);
		BlockHitResult best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		float spin = player.getRandom().nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < ZIP_YAWS; i++) {
			float a = spin + i * Mth.TWO_PI / ZIP_YAWS;
			for (float pitch : ZIP_PITCHES) {
				double p = pitch * Mth.DEG_TO_RAD;
				Vec3 ray = new Vec3(Mth.cos(a) * Math.cos(p), Math.sin(p), Mth.sin(a) * Math.cos(p));
				BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(ray.scale(range)));
				if (hit.getType() == HitResult.Type.MISS) {
					continue;
				}
				Vec3 spot = hit.getLocation();
				double gone = spot.distanceTo(eye);
				if (gone < ZIP_MIN) {
					continue;
				}
				double score = danger == null ? gone : spot.distanceTo(danger) * 2.0 - danger.distanceTo(eye);
				if (hit.getDirection() == Direction.UP) {
					score += 4.0;
				}
				if (score > bestScore) {
					bestScore = score;
					best = hit;
				}
			}
		}
		if (best == null) {
			return false;
		}
		WebPower.takeoverZip(player, best);
		return true;
	}

	/** Yours again: a parting line, and the client lets go of the legs. */
	private static void handBack(ServerPlayer player, Rescue rescue) {
		rescue.phase = NONE;
		player.resetFallDistance();
		WebSymbiote.whisper(player, "rescue.release." + (1 + player.getRandom().nextInt(LINES)));
		ServerPlayNetworking.send(player, new WebNet.Rescue(false));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	/** Logged out or the bond gone: no rescue carries over. */
	public static void forget(ServerPlayer player) {
		RESCUES.remove(player.getUUID());
	}
}
