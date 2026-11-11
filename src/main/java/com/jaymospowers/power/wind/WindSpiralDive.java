package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung) <b>Spiral Dive</b>: a tap of the power's special key (X) in the air, looking steeply down, well clear of the
 * ground, from {@link #MIN_MASTERY} unlocked abilities on. The wind lets go of you and you plunge, spinning, a spiral
 * of cloud winding round you, steering a little with your aim. Where you land a ring of wind runs out along the
 * ground: it shoves everything near away from you and lifts it, harder the further you fell, and blows out fire. It
 * is a way down and a way to clear a landing, not a damage move: the ring's damage is light.
 *
 * <p>It works out of wind flight too (the flight is dropped first). Taking flight again a moment into the dive pulls
 * you out of it, with no ring. The costs: hunger, a cooldown that is spent whether or not the ring catches anything,
 * and the landing leaves you winded, rooted where you stand for a moment (longer after a longer fall).
 *
 * <p>Fall rules are untouched, as with {@link WindThunderDive}: the dive only sets your speed, so the game and
 * {@link WindFall} decide what the landing costs ({@link #MIN_MASTERY} is {@link WindFall#SAFE_MASTERY}, from which
 * a Wind user's falls don't hurt). The Thunderhead's Thunder Dive stays on C.
 */
public final class WindSpiralDive {
	// ---- tuning ----
	/** Unlocked abilities needed. */
	static final int MIN_MASTERY = WindFall.SAFE_MASTERY;
	/** Looking down at least this many degrees of pitch (90 = straight down). */
	static final float MIN_PITCH = 50.0F;
	/** Clear air needed under you to start (blocks). */
	static final double MIN_HEIGHT = 6.0;
	/** The plunge: speed down at the start and at full (blocks/tick), what it gains a tick, and the share of your aim's sideways pull. */
	static final double DIVE_SPEED_START = 0.7, DIVE_SPEED = 1.6, DIVE_ACCEL = 0.09, STEER = 0.45;
	/** The longest a dive runs (ticks). */
	static final int MAX_TICKS = 100;
	/** For this long (ticks) a dive started out of flight keeps the flight dropped; after it, flying again ends the dive. */
	static final int FLIGHT_GRACE = 10;
	/** Cooldown (ticks) and hunger (exhaustion; 4 = one hunger point). */
	static final int COOLDOWN = 20 * 20;
	static final float EXHAUSTION = 4.0F;
	/** A fall of this many blocks (or more) gives the ring its full strength. */
	static final double FULL_DROP = 30.0;
	/** The ring: radius, damage and shove (least, most), and the lift it gives what it throws. */
	static final double RADIUS_MIN = 3.5, RADIUS_MAX = 7.0, LIFT = 0.45;
	static final float DAMAGE_MIN = 2.0F, DAMAGE_MAX = 5.0F;
	static final double PUSH_MIN = 0.7, PUSH_MAX = 1.6;
	/** The damage and shove at the ring's edge, as a share of those at its middle. */
	static final double EDGE_SHARE = 0.4;
	/** Winded after the landing: rooted this long (ticks; least, most). */
	static final int WINDED_MIN = 12, WINDED_MAX = 30;
	/** The shake of the landing (least, most). */
	static final float SHAKE_MIN = 0.6F, SHAKE_MAX = 1.6F;
	/** The spiral round the diver: its radius (blocks), how far it turns a tick (radians) and the puffs in each of its two strands. */
	static final double SPIRAL_RADIUS = 0.9, SPIRAL_TURN = 0.7;
	static final int SPIRAL_PUFFS = 3;
	/** The spin's pose is renewed this often (ticks), before it runs out. */
	static final int SPIN_RENEW = WindMoves.SPIN_TICKS - 4;

	private static final Identifier WINDED = JaymosPowers.id("wind_spiral_dive_winded");

	/** A dive under way: where it began and when. */
	private record Dive(double startY, int startTick) {
	}

	private static final Map<UUID, Dive> DIVES = new HashMap<>();
	/** player → the game time the dive is ready again. */
	private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
	/** player → ticks of being winded left. */
	private static final Map<UUID, Integer> WINDED_LEFT = new HashMap<>();

	private WindSpiralDive() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WindSpiralDive::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DIVES.clear();
			COOLDOWNS.clear();
			WINDED_LEFT.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DIVES.remove(handler.player.getUUID());
			// (the root is a transient modifier: it isn't saved, so there's nothing to take off)
			WINDED_LEFT.remove(handler.player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			DIVES.remove(entity.getUUID());
			WINDED_LEFT.remove(entity.getUUID());
		});
	}

	/** Whether {@code player} is in a Spiral Dive now. */
	public static boolean isDiving(ServerPlayer player) {
		return DIVES.containsKey(player.getUUID());
	}

	/** Whether a tap of X should be a Spiral Dive right now (else it is the Tempest Kick): mastered enough, in the air, looking steeply down. */
	static boolean wants(ServerPlayer player) {
		return Mastery.unlocked(player) >= MIN_MASTERY && !player.onGround() && !player.isInWater() && WindFlight.canFly(player)
			&& player.getXRot() >= MIN_PITCH;
	}

	/** Starts the dive if there is room and it is ready; false if not (X stays the Tempest Kick then). */
	static boolean start(ServerPlayer player) {
		if (DIVES.containsKey(player.getUUID())) {
			return true;
		}
		ServerLevel level = player.level();
		// (the wait runs on the world clock, so a respawn or relog doesn't stretch it)
		long time = level.getGameTime();
		Long ready = COOLDOWNS.get(player.getUUID());
		if (ready != null && ready > time) {
			return false;
		}
		Vec3 from = player.position();
		HitResult floor = level.clip(new ClipContext(from, from.add(0.0, -MIN_HEIGHT, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		if (floor.getType() != HitResult.Type.MISS) {
			return false;
		}
		// out of wind flight: the wind lets go
		WindPower.INSTANCE.dropFlight(player);
		COOLDOWNS.put(player.getUUID(), time + WindCold.cooldown(player, COOLDOWN));
		DIVES.put(player.getUUID(), new Dive(player.getY(), player.tickCount));
		WindFumble.onCast(player);
		WindStorm.onCast(player);
		WindModes.onCast(player);
		player.causeFoodExhaustion(EXHAUSTION);
		WindMoves.restart(player, WindMoves.SPIN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.9F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		return true;
	}

	/** Ends a dive with no ring (downed, the power lost). */
	static void stop(ServerPlayer player) {
		if (DIVES.remove(player.getUUID()) != null) {
			WindMoves.unpose(player, WindMoves.SPIN);
		}
		if (WINDED_LEFT.remove(player.getUUID()) != null) {
			unroot(player);
		}
	}

	/** Debug: the dive is ready again. */
	static void clearCooldown(ServerPlayer player) {
		COOLDOWNS.remove(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		winded(server);
		if (DIVES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Dive>> it = DIVES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Dive> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Dive dive = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || !Powers.has(player, WindPower.INSTANCE)) {
				it.remove();
				WindMoves.unpose(player, WindMoves.SPIN);
				continue;
			}
			int age = player.tickCount - dive.startTick();
			if (WindPower.flying(player)) {
				if (age <= FLIGHT_GRACE) {
					// a flight report that was already on its way when the dive began
					WindPower.INSTANCE.dropFlight(player);
				} else {
					// taking flight again pulls you out of it
					it.remove();
					WindMoves.unpose(player, WindMoves.SPIN);
					continue;
				}
			}
			if (player.isInWater() || !WindFlight.canFly(player) || age > MAX_TICKS || age < 0) {
				// into water, onto a mount, picked up, downed or out of time: the dive just ends (the fall is whatever it is)
				it.remove();
				WindMoves.unpose(player, WindMoves.SPIN);
				continue;
			}
			if (age >= 2 && (player.onGround() || player.verticalCollisionBelow)) {
				it.remove();
				land(player, dive);
				continue;
			}
			// the plunge: down, faster as it goes, a little of your aim's heading kept
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			Vec3 steer = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(STEER);
			double speed = Math.min(DIVE_SPEED, DIVE_SPEED_START + age * DIVE_ACCEL);
			player.setDeltaMovement(steer.x, -speed, steer.z);
			player.syncVelocity = true;
			if (age > 0 && age % SPIN_RENEW == 0) {
				WindMoves.restart(player, WindMoves.SPIN);
			}
			spiral(player, age);
		}
	}

	/** The spiral of cloud round the diver: two strands winding round, trailing up behind the fall. */
	private static void spiral(ServerPlayer player, int age) {
		ServerLevel level = player.level();
		double turn = age * SPIRAL_TURN;
		for (int strand = 0; strand < 2; strand++) {
			for (int puff = 0; puff < SPIRAL_PUFFS; puff++) {
				double angle = turn + strand * Math.PI - puff * 0.45;
				double x = player.getX() + Math.cos(angle) * SPIRAL_RADIUS;
				double y = player.getY() + 0.9 + puff * 0.7;
				double z = player.getZ() + Math.sin(angle) * SPIRAL_RADIUS;
				level.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
		if (age % 4 == 0) {
			level.sendParticles(ParticleTypes.SMALL_GUST, player.getX(), player.getY() + 2.2, player.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
		}
		if (age % 10 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.8F, 1.3F);
		}
	}

	/** The ring of wind from where you landed. Fall damage is not ours: the game and {@link WindFall} deal it. */
	private static void land(ServerPlayer player, Dive dive) {
		ServerLevel level = player.level();
		WindMoves.unpose(player, WindMoves.SPIN);
		double dropped = Math.max(0.0, dive.startY() - player.getY());
		float power = (float)Mth.clamp(dropped / FULL_DROP, 0.0, 1.0);
		double radius = Mth.lerp(power, RADIUS_MIN, RADIUS_MAX);
		float damage = Mth.lerp(power, DAMAGE_MIN, DAMAGE_MAX) * WindModes.abilityScale(player);
		// the weather governs the shove, as it does the gusts
		double push = Mth.lerp(power, PUSH_MIN, PUSH_MAX) * WindWeather.factor(player);
		Vec3 at = player.position();
		AABB area = new AABB(at, at).inflate(radius, radius * 0.6, radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && e.position().distanceToSqr(at) <= radius * radius)) {
			double distance = Math.sqrt(target.position().distanceToSqr(at));
			double falloff = 1.0 - (1.0 - EDGE_SHARE) * distance / radius;
			target.hurtServer(level, player.damageSources().windCharge(player, player), (float)(damage * falloff));
			double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = target.position().subtract(at).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			target.setDeltaMovement(target.getDeltaMovement().add(away.x * push * falloff * resist, LIFT * resist, away.z * push * falloff * resist));
			target.syncVelocity = true;
		}
		// wind blows fire out
		WindDouse.douse(level, at, radius, player);
		// the ring itself, running out along the ground
		int points = 28;
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2.0 / points;
			double cos = Math.cos(angle), sin = Math.sin(angle);
			level.sendParticles(ParticleTypes.CLOUD, at.x + cos * 0.8, at.y + 0.2, at.z + sin * 0.8, 0, cos * radius * 0.22, 0.02, sin * radius * 0.22, 1.0);
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.6F);
		Shakes.heavy(player, at, 24.0, Mth.lerp(power, SHAKE_MIN, SHAKE_MAX));
		// winded: rooted where you landed, longer after a longer fall
		WINDED_LEFT.put(player.getUUID(), Math.round(Mth.lerp(power, (float)WINDED_MIN, (float)WINDED_MAX)));
		root(player);
	}

	/** Counts down everyone who is winded, and lets them go. */
	private static void winded(MinecraftServer server) {
		if (WINDED_LEFT.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Integer>> it = WINDED_LEFT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int left = entry.getValue() - 1;
			if (player == null) {
				it.remove();
			} else if (left <= 0 || !player.isAlive()) {
				it.remove();
				unroot(player);
			} else {
				entry.setValue(left);
			}
		}
	}

	/** Winded: no walking, no jumping (attribute changes reach the client, which moves the player). */
	private static void root(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(WINDED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.addOrUpdateTransientModifier(new AttributeModifier(WINDED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void unroot(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(WINDED);
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(WINDED);
		}
	}
}
