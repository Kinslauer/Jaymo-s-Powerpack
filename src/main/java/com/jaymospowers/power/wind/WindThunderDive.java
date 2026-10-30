package com.jaymospowers.power.wind;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (Weapon Forms patch 6) <b>Thunder Dive</b>, the Thunderhead's (ascended and corrupted, mode on) dive. C in the air,
 * looking steeply down, while not in wind flight and well clear of the ground: instead of the Cyclone Lance you plunge
 * straight down, steering a little with your aim. On landing a thunderclap shockwave runs out from you: it hurts and
 * throws everything near, harder the further you fell; with a sky holding thunder, bolts (light only, nothing burns)
 * fall round the ring.
 *
 * <p>Fall rules still apply. The dive only sets your speed: it never resets fall distance, never sets the wind's fall
 * protection and never cancels fall damage, so you take what the fall (and {@link WindFall}) says, landing in the same
 * hit as the shockwave. Hunger and the Stormbound mode's static are paid as for any ability.
 */
public final class WindThunderDive {
	// ---- tuning ----
	/** Looking down at least this many degrees of pitch (90 = straight down). */
	static final float MIN_PITCH = 40.0F;
	/** Clear air needed under you to start (blocks). */
	static final double MIN_HEIGHT = 5.0;
	/** The plunge: speed down (blocks/tick), the share of your aim's sideways pull, and the longest it runs (ticks). */
	static final double DIVE_SPEED = 1.9, STEER = 0.35;
	static final int MAX_TICKS = 100;
	/** Cooldown (ticks) and hunger (exhaustion; 4 = one hunger point). */
	public static final int COOLDOWN = 20 * 35;
	static final float EXHAUSTION = 8.0F;
	/** A fall of this many blocks (or more) gives the shockwave its full strength. */
	static final double FULL_DROP = 40.0;
	/** The shockwave: radius, damage and shove (least, most), and the lift it throws foes. */
	static final double RADIUS_MIN = 4.0, RADIUS_MAX = 9.0, LIFT = 0.55;
	static final float DAMAGE_MIN = 6.0F, DAMAGE_MAX = 20.0F;
	static final double PUSH_MIN = 0.8, PUSH_MAX = 2.0;
	/** Bolts of light round the ring under a stormy sky (fewer in the rain), and how far from you they fall. */
	static final int BOLTS = 6;
	/** The shake of the landing (least, most). */
	static final float SHAKE_MIN = 1.0F, SHAKE_MAX = 2.6F;

	/** A dive in flight: where it began and when. */
	private record Dive(double startY, int startTick) {
	}

	private static final Map<UUID, Dive> DIVES = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

	private WindThunderDive() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WindThunderDive::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DIVES.clear();
			COOLDOWNS.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DIVES.remove(handler.player.getUUID()));
		// no fall damage is touched here; a diver who dies just stops
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> DIVES.remove(entity.getUUID()));
	}

	/** Whether C should be a Thunder Dive right now (else it is the Lance): Thunderhead, in the air, looking down, not flying. */
	static boolean wants(ServerPlayer player) {
		return WindModes.mode(player) == WindModes.BOTH && !WindPower.flying(player) && !player.onGround() && !player.isInWater()
			&& !player.isPassenger() && player.getXRot() >= MIN_PITCH;
	}

	/** Starts the dive if there is room and it is ready; false if not (the Lance stays available then). */
	static boolean start(ServerPlayer player) {
		if (DIVES.containsKey(player.getUUID())) {
			return true;
		}
		ServerLevel level = player.level();
		int now = player.tickCount;
		// (the wait runs on the world clock, so a respawn or relog doesn't stretch it)
		long time = level.getGameTime();
		Long ready = COOLDOWNS.get(player.getUUID());
		if (ready != null && ready > time) {
			// (waiting: C stays the Lance)
			return false;
		}
		Vec3 from = player.position();
		HitResult floor = level.clip(new ClipContext(from, from.add(0.0, -MIN_HEIGHT, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		if (floor.getType() != HitResult.Type.MISS) {
			// (too near the ground: C stays the Lance)
			return false;
		}
		COOLDOWNS.put(player.getUUID(), time + COOLDOWN);
		DIVES.put(player.getUUID(), new Dive(player.getY(), now));
		WindFumble.onCast(player);
		WindStorm.onCast(player);
		WindModes.onCast(player);
		player.causeFoodExhaustion(EXHAUSTION);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.3, 0.6, 0.3, 0.4);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (DIVES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Dive>> it = DIVES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Dive> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Dive dive = entry.getValue();
			if (player == null || !player.isAlive() || player.isSpectator() || WindModes.mode(player) != WindModes.BOTH) {
				it.remove();
				continue;
			}
			int age = player.tickCount - dive.startTick();
			if (player.isInWater() || player.isPassenger() || age > MAX_TICKS || WindPower.flying(player)) {
				// into water, onto a mount, out of time or into flight: the dive just ends (the fall is whatever it is)
				it.remove();
				continue;
			}
			if (age >= 2 && (player.onGround() || player.verticalCollisionBelow)) {
				it.remove();
				land(player, dive);
				continue;
			}
			// the plunge: straight down at speed, a little of your aim's heading kept
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			Vec3 steer = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(STEER);
			player.setDeltaMovement(steer.x, -DIVE_SPEED, steer.z);
			player.syncVelocity = true;
			if (age % 2 == 0) {
				player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.8, 0.3, 0.1);
				player.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2.0, player.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
			}
		}
	}

	/** The thunderclap: a shockwave from where you landed. Fall damage is not ours: the game and {@link WindFall} deal it. */
	private static void land(ServerPlayer player, Dive dive) {
		ServerLevel level = player.level();
		double dropped = Math.max(0.0, dive.startY() - player.getY());
		float power = (float)Mth.clamp(dropped / FULL_DROP, 0.0, 1.0);
		double radius = Mth.lerp(power, RADIUS_MIN, RADIUS_MAX);
		float damage = Mth.lerp(power, DAMAGE_MIN, DAMAGE_MAX) * WindModes.abilityScale(player);
		double push = Mth.lerp(power, PUSH_MIN, PUSH_MAX);
		Vec3 at = player.position();
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
		AABB area = new AABB(at, at).inflate(radius, radius * 0.6, radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && e.position().distanceToSqr(at) <= radius * radius)) {
			double distance = Math.sqrt(target.position().distanceToSqr(at));
			double falloff = 1.0 - 0.6 * distance / radius;
			target.hurtServer(level, source, (float)(damage * falloff));
			WindModes.chargeCreeper(target);
			Vec3 away = target.position().subtract(at).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			target.setDeltaMovement(target.getDeltaMovement().add(away.x * push * falloff, LIFT, away.z * push * falloff));
			target.syncVelocity = true;
		}
		// bolts of light round the ring, only out of real weather and only as a picture
		int bolts = Math.round(BOLTS * WindModes.thunderPower(level));
		for (int i = 0; i < bolts; i++) {
			double angle = (i + 0.5) * Math.PI * 2.0 / BOLTS;
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(new Vec3(at.x + Math.cos(angle) * radius * 0.8, at.y, at.z + Math.sin(angle) * radius * 0.8));
				bolt.setVisualOnly(true);
				bolt.setCause(player);
				level.addFreshEntity(bolt);
			}
		}
		// the ring itself
		int points = 36;
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2.0 / points;
			double cos = Math.cos(angle), sin = Math.sin(angle);
			level.sendParticles(ParticleTypes.CLOUD, at.x + cos * 1.0, at.y + 0.2, at.z + sin * 1.0, 0, cos * radius * 0.25, 0.03, sin * radius * 0.25, 1.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x + cos * 1.5, at.y + 0.3, at.z + sin * 1.5, 0, cos * radius * 0.2, 0.05, sin * radius * 0.2, 1.0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 0.7F);
		Shakes.heavy(player, at, 32.0, Mth.lerp(power, SHAKE_MIN, SHAKE_MAX));
	}
}
