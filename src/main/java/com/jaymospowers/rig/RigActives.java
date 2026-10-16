package com.jaymospowers.rig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig's actives (whole set and charge needed): <b>Hover</b> on the thrusters (a toggle; the client holds the height,
 * Jump rises, Sneak sinks; it drains charge and ends on landing or when the charge runs out), the <b>palm blast</b> (an
 * instant beam from the Core through the palm), and the <b>shoulder launcher</b> (a pair of mini-missiles that burst on
 * impact without breaking blocks). The Mind's report is here too ({@link RigNet#REPORT}).
 *
 * <p>(0.11.87) Stage 2: <b>lock-on</b> (Mind level 2; hold the Rig key: the target under the crosshair; palm blasts aim
 * at it when it's roughly ahead, missiles home on it), <b>scans</b> and <b>suit calling</b> (level 3, {@link RigScan},
 * {@link RigCall}), and <b>full flight</b> (level 5, whole set: double-tap Jump while hovering; faster, drains more,
 * costs hunger, and hitting a wall at speed hurts).
 */
public final class RigActives {
	/** Hover: the charge it takes to start; what it drains every {@link #HOVER_DRAIN_EVERY} ticks; landing ends it after this long. */
	static final int HOVER_MIN = 40, HOVER_COST = 5, HOVER_DRAIN_EVERY = 5, HOVER_LAND_AFTER = 10;
	/** Wind's augment: hover drains this share. */
	static final float WIND_HOVER_SHARE = 0.6F;
	/** Palm blast: charge, cooldown (ticks), reach (blocks), damage, knockback. */
	static final int PALM_COST = 60, PALM_COOLDOWN = 15;
	static final double PALM_RANGE = 24.0, PALM_KNOCKBACK = 0.9;
	static final float PALM_DAMAGE = 6.0F;
	/** Augments: Xenoform's reach, Resonance's knockback, Web's slow (ticks, amplifier), Smoke's blinding (ticks). */
	static final double XENO_RANGE = 1.5, RESONANCE_KNOCKBACK = 1.5;
	static final int WEB_SLOW_TICKS = 60, WEB_SLOW_LEVEL = 1, SMOKE_BLIND_TICKS = 40;
	/** Shoulder launcher: charge, cooldown, missiles (Arsenal: one more), speed (b/t), life (ticks), burst size, spread. */
	static final int LAUNCHER_COST = 120, LAUNCHER_COOLDOWN = 60, MISSILES = 2, MISSILE_LIFE = 60;
	static final double MISSILE_SPEED = 1.6, MISSILE_SPREAD = 0.04;
	static final float MISSILE_BURST = 1.6F;
	/** How often the HUD's status goes out (ticks). */
	static final int STATUS_EVERY = 5;
	/** (0.11.87) Lock-on: how far a lock can be taken, and how far it holds before it breaks. */
	public static final double LOCK_RANGE = 32.0, LOCK_KEEP = 48.0;
	/** A palm blast bends to the lock when it's within this of the look (dot of the directions: 0.8 is about 37 degrees). */
	static final double PALM_LOCK_DOT = 0.8;
	/** How hard missiles turn toward the lock each tick (share of the way to straight at it). */
	static final double MISSILE_HOMING = 0.25;
	/** Full flight: what it drains every {@link #HOVER_DRAIN_EVERY} ticks (instead of hover's), and its hunger each tick. */
	static final int FLIGHT_COST = 9;
	static final float FLIGHT_EXHAUSTION = 0.02F;
	/** Full flight into a wall: a sudden loss of more than this much speed (b/t) hurts, by the speed lost squared. */
	static final double CRASH_DROP = 0.35, CRASH_DAMAGE = 14.0;
	/** (0.11.107) The jets: how far out the boot soles and (hovering) the palms are from the middle (blocks). */
	static final double BOOT_SPREAD = 0.13, PALM_SPREAD = 0.5;

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final List<Missile> MISSILES_IN_FLIGHT = new ArrayList<>();

	static final class State {
		boolean hovering;
		int hoverTicks;
		int palmCooldown;
		int launcherCooldown;
		/** The last status sent, to send a change at once. */
		boolean sentHovering;
		boolean wasWearing;
		/** (0.11.87) Full flight (only while hovering). */
		boolean flying;
		boolean sentFlying;
		/** The locked target's entity id, -1 for none. */
		int lock = -1;
		int sentLock = -1;
		int scanCooldown;
		/** Game time a hover starts by itself (the takeover save's catch), 0: none. */
		long hoverAt;
		/** Full flight's last position and horizontal speed (b/t), for crashes. */
		@Nullable Vec3 lastPos;
		double lastSpeed;
		/** (0.11.107) Ticks into the Unibeam (-1: none), and its cooldown. */
		int unibeam = -1;
		int unibeamCooldown;
	}

	private record Missile(ServerLevel level, ServerPlayer owner, Vec3[] at, Vec3[] velocity, int[] age, @Nullable Entity target) {
	}

	private RigActives() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			STATES.remove(handler.player.getUUID());
			RigMind.forget(handler.player.getUUID());
		});
	}

	private static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new State());
	}

	static boolean hovering(Player player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.hovering;
	}

	static boolean flying(Player player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.hovering && state.flying;
	}

	/** (0.11.87) The locked target, or null. */
	static @Nullable LivingEntity locked(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.lock < 0) {
			return null;
		}
		return player.level().getEntity(state.lock) instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	/** Whether the worn Core's Mind has reached {@code level}; if not, says so. */
	static boolean needs(ServerPlayer player, int level) {
		if (Rig.level(player) >= level) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.needs_level", level));
		return false;
	}

	/** A Rig key from the client. */
	static void use(ServerPlayer player, int action) {
		if (player.isSpectator()) {
			return;
		}
		if (action == RigNet.CALL) {
			// calling works with nothing worn: the parked suit comes back
			RigCall.use(player);
			return;
		}
		if (!Rig.wearsAny(player)) {
			return;
		}
		if (action == RigNet.FACEPLATE) {
			RigHelm.toggleFace(player);
			return;
		}
		if (action == RigNet.NIGHT) {
			RigHelm.toggleNight(player);
			return;
		}
		if (action == RigNet.REPORT) {
			RigMind.report(player);
			return;
		}
		State state = state(player);
		if (action == RigNet.SCAN) {
			// the Helm's eyes and the Core's Mind are enough
			if (Rig.wears(player, Rig.Piece.HELM) && !Rig.core(player).isEmpty()) {
				if (needs(player, Rig.SCAN_LEVEL)) {
					RigScan.scan(player, state);
				}
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.needs_helm_core"));
			}
			sendStatus(player, state);
			return;
		}
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		switch (action) {
			case RigNet.HOVER -> toggleHover(player, state);
			case RigNet.PALM -> palm(player, state);
			case RigNet.LAUNCHER -> launch(player, state);
			case RigNet.FLIGHT -> toggleFlight(player, state);
			case RigNet.UNIBEAM -> RigUnibeam.use(player, state);
			default -> {
			}
		}
		sendStatus(player, state);
	}

	/** (0.11.87) The Lock packet: lock on to {@code id}, or let go (-1). */
	static void lock(ServerPlayer player, int id) {
		if (player.isSpectator() || !Rig.wears(player, Rig.Piece.HELM) || Rig.core(player).isEmpty()) {
			return;
		}
		State state = state(player);
		if (id < 0) {
			if (state.lock >= 0) {
				state.lock = -1;
				RigMind.say(player, "unlock", 0.8F);
			}
			sendStatus(player, state);
			return;
		}
		if (!needs(player, Rig.LOCK_LEVEL)) {
			return;
		}
		if (!(player.level().getEntity(id) instanceof LivingEntity target) || !target.isAlive() || target == player || target.isSpectator()
			|| target.distanceTo(player) > LOCK_RANGE * 1.25 || !player.hasLineOfSight(target)) {
			return;
		}
		state.lock = id;
		RigMind.sayAlways(player, "lock", 1.6F, target.getDisplayName());
		sendStatus(player, state);
	}

	/** Takeover save's catch: hover by itself in {@code delay} ticks (if still airborne and charged). */
	static void hoverSoon(ServerPlayer player, int delay) {
		state(player).hoverAt = player.level().getGameTime() + delay;
	}

	/** Every tick: drop a lock that's dead, gone, too far, or with no Helm to hold it. */
	private static void tickLock(ServerPlayer player, State state) {
		if (state.lock < 0) {
			return;
		}
		Entity target = player.level().getEntity(state.lock);
		if (!Rig.wears(player, Rig.Piece.HELM) || Rig.core(player).isEmpty() || !(target instanceof LivingEntity living) || !living.isAlive()
			|| living.distanceTo(player) > LOCK_KEEP) {
			state.lock = -1;
			if (target instanceof LivingEntity living && !living.isAlive()) {
				RigMind.say(player, "lock_down", 1.2F);
			}
		}
	}

	// ---- hover --------------------------------------------------------------------------------------------------------

	private static void toggleHover(ServerPlayer player, State state) {
		if (state.hovering) {
			stopHover(player, state);
			return;
		}
		if (Rig.charge(player) < HOVER_MIN && !player.isCreative()) {
			RigMind.empty(player);
			return;
		}
		if (com.jaymospowers.core.FlightRoll.powerFlying(player)) {
			// already flying on a power: the thrusters stay out of its way
			return;
		}
		state.hovering = true;
		state.hoverTicks = 0;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	private static void stopHover(ServerPlayer player, State state) {
		state.hovering = false;
		state.flying = false;
		state.hoverTicks = 0;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.8F);
	}

	/** (0.11.87) Double-tap Jump while hovering: full flight on, or back to hover. */
	private static void toggleFlight(ServerPlayer player, State state) {
		if (!state.hovering) {
			return;
		}
		if (state.flying) {
			state.flying = false;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.4F);
			return;
		}
		if (!needs(player, Rig.FLIGHT_LEVEL)) {
			return;
		}
		state.flying = true;
		state.lastPos = null;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 0.8F);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 14, 0.2, 0.05, 0.2, 0.08);
		}
	}

	private static void tickHover(ServerPlayer player, State state) {
		state.hoverTicks++;
		if (!Rig.fullSet(player) || player.isFallFlying() || com.jaymospowers.core.FlightRoll.powerFlying(player)) {
			stopHover(player, state);
			return;
		}
		if (state.flying && Rig.level(player) < Rig.FLIGHT_LEVEL) {
			state.flying = false;
		}
		if (state.flying) {
			tickFlight(player, state);
		}
		if (state.hoverTicks % HOVER_DRAIN_EVERY == 0) {
			int base = state.flying ? FLIGHT_COST : HOVER_COST;
			int cost = Rig.augment(player) == Rig.Augment.WIND ? Math.max(1, Math.round(base * WIND_HOVER_SHARE)) : base;
			if (!Rig.spend(player, cost)) {
				stopHover(player, state);
				RigMind.empty(player);
				return;
			}
		}
		if (player.onGround() && state.hoverTicks > HOVER_LAND_AFTER) {
			stopHover(player, state);
			return;
		}
		// a held, slow descent at most: no fall damage builds up
		player.resetFallDistance();
		if (state.hoverTicks % 2 == 0 && player.level() instanceof ServerLevel level) {
			jets(level, player, state.flying);
		}
	}

	/**
	 * (0.11.107) The jets: a flame under each boot sole, and while hovering a repulsor wash under each palm too (the
	 * hands held down and out to steady it, client RigPose); in full flight the palms are swept back, and so is their wash.
	 */
	private static void jets(ServerLevel level, ServerPlayer player, boolean flying) {
		double yaw = Math.toRadians(player.yBodyRot);
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		Vec3 back = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw));
		Vec3 feet = player.position();
		for (int side = -1; side <= 1; side += 2) {
			Vec3 boot = feet.add(right.scale(side * BOOT_SPREAD)).add(0.0, -0.05, 0.0);
			level.sendParticles(ParticleTypes.FLAME, boot.x, boot.y, boot.z, flying ? 2 : 1, 0.03, 0.02, 0.03, 0.01);
			level.sendParticles(ParticleTypes.SMALL_FLAME, boot.x, boot.y - 0.15, boot.z, 1, 0.04, 0.05, 0.04, 0.01);
			Vec3 palm = flying ? feet.add(right.scale(side * 0.35)).add(back.scale(0.3)).add(0.0, 0.75, 0.0)
				: feet.add(right.scale(side * PALM_SPREAD)).add(0.0, 0.7, 0.0);
			Vec3 wash = flying ? back.scale(0.12) : new Vec3(0.0, -0.12, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, palm.x, palm.y, palm.z, 0, wash.x, wash.y, wash.z, 1.0);
		}
		level.sendParticles(ParticleTypes.SMOKE, feet.x, feet.y - 0.3, feet.z, 1, 0.1, 0.05, 0.1, 0.01);
	}

	/** Full flight's own costs: hunger, and a crash when a wall stops you short. */
	private static void tickFlight(ServerPlayer player, State state) {
		if (!player.isCreative()) {
			player.causeFoodExhaustion(FLIGHT_EXHAUSTION);
		}
		Vec3 pos = player.position();
		double speed = state.lastPos == null ? 0.0 : pos.subtract(state.lastPos).horizontalDistance();
		if (state.lastPos != null && player.horizontalCollision && state.lastSpeed - speed > CRASH_DROP && !player.isCreative()) {
			double lost = state.lastSpeed - speed;
			ServerLevel level = player.level();
			player.hurtServer(level, level.damageSources().flyIntoWall(), (float)(lost * lost * CRASH_DAMAGE));
			com.jaymospowers.core.Shakes.send(player, (float)(lost * 6.0));
			state.flying = false;
		}
		state.lastPos = pos;
		state.lastSpeed = speed;
	}

	// ---- palm blast ---------------------------------------------------------------------------------------------------

	private static void palm(ServerPlayer player, State state) {
		if (state.palmCooldown > 0) {
			return;
		}
		if (!Rig.spend(player, PALM_COST)) {
			RigMind.empty(player);
			return;
		}
		state.palmCooldown = PALM_COOLDOWN;
		RigMoves.start(player, RigMoves.PALM);
		ServerLevel level = player.level();
		Rig.Augment augment = Rig.augment(player);
		double range = PALM_RANGE * (augment == Rig.Augment.XENOFORM ? XENO_RANGE : 1.0);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity lock = locked(player);
		if (lock != null) {
			// (0.11.87) the lock bends the beam on to it, when it's roughly ahead and in sight
			Vec3 to = lock.getBoundingBox().getCenter().subtract(eye);
			if (to.length() <= range && to.normalize().dot(look) > PALM_LOCK_DOT && player.hasLineOfSight(lock)) {
				look = to.normalize();
			}
		}
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, eye.distanceToSqr(end));
		if (hit != null) {
			end = hit.getLocation();
		}
		// the beam: from the right palm, out along the look
		Vec3 from = eye.add(look.scale(0.6)).add(0.0, -0.35, 0.0);
		double length = from.distanceTo(end);
		Vec3 dir = end.subtract(from).normalize();
		for (double d = 0.0; d < length; d += 0.5) {
			Vec3 p = from.add(dir.scale(d));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 8, 0.15, 0.15, 0.15, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.9F);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			DamageSource source = level.damageSources().playerAttack(player);
			target.hurtServer(level, source, PALM_DAMAGE);
			double knock = PALM_KNOCKBACK * (augment == Rig.Augment.RESONANCE ? RESONANCE_KNOCKBACK : 1.0);
			target.knockback(knock, -look.x, -look.z, source, PALM_DAMAGE);
			target.syncVelocity = true;
			if (augment == Rig.Augment.WEB) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WEB_SLOW_TICKS, WEB_SLOW_LEVEL), player);
				level.sendParticles(ParticleTypes.WHITE_SMOKE, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.01);
			} else if (augment == Rig.Augment.SMOKE) {
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SMOKE_BLIND_TICKS, 0), player);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 8, 0.3, 0.3, 0.3, 0.01);
			}
		}
	}

	// ---- shoulder launcher --------------------------------------------------------------------------------------------

	private static void launch(ServerPlayer player, State state) {
		if (state.launcherCooldown > 0) {
			return;
		}
		if (!Rig.spend(player, LAUNCHER_COST)) {
			RigMind.empty(player);
			return;
		}
		state.launcherCooldown = LAUNCHER_COOLDOWN;
		RigMoves.start(player, RigMoves.LAUNCHER);
		ServerLevel level = player.level();
		int count = MISSILES + (Rig.augment(player) == Rig.Augment.ARSENAL ? 1 : 0);
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 shoulder = player.getEyePosition().add(0.0, 0.25, 0.0);
		for (int i = 0; i < count; i++) {
			// alternate shoulders, a touch of spread
			double side = (i % 2 == 0 ? 0.45 : -0.45) * (1 + i / 2);
			Vec3 start = shoulder.add(right.scale(side)).add(look.scale(0.4));
			Vec3 velocity = look.add(level.getRandom().nextGaussian() * MISSILE_SPREAD, level.getRandom().nextGaussian() * MISSILE_SPREAD,
				level.getRandom().nextGaussian() * MISSILE_SPREAD).normalize().scale(MISSILE_SPEED);
			MISSILES_IN_FLIGHT.add(new Missile(level, player, new Vec3[] {start}, new Vec3[] {velocity}, new int[1], locked(player)));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	static void tickMissiles(MinecraftServer server) {
		for (Iterator<Missile> it = MISSILES_IN_FLIGHT.iterator(); it.hasNext();) {
			Missile missile = it.next();
			ServerLevel level = missile.level();
			if (++missile.age()[0] > MISSILE_LIFE) {
				it.remove();
				continue;
			}
			Vec3 from = missile.at()[0];
			Entity target = missile.target();
			if (target != null && target.isAlive() && target.level() == level) {
				// (0.11.87) homing on the lock
				Vec3 want = target.getBoundingBox().getCenter().subtract(from).normalize().scale(MISSILE_SPEED);
				missile.velocity()[0] = missile.velocity()[0].lerp(want, MISSILE_HOMING).normalize().scale(MISSILE_SPEED);
			}
			Vec3 to = from.add(missile.velocity()[0]);
			if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(to))) {
				it.remove();
				continue;
			}
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, missile.owner()));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, missile.owner(), from, end,
				new AABB(from, end).inflate(0.5), e -> e.isAlive() && !e.isSpectator() && e.isPickable() && e != missile.owner(), 0.3F);
			if (hit != null || block.getType() != HitResult.Type.MISS) {
				Vec3 at = hit != null ? hit.getLocation() : end;
				burst(level, missile.owner(), at);
				it.remove();
				continue;
			}
			missile.at()[0] = to;
			level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 2, 0.03, 0.03, 0.03, 0.0);
			level.sendParticles(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static void burst(ServerLevel level, @Nullable Entity owner, Vec3 at) {
		// hurts and throws, but leaves the blocks alone
		level.explode(owner, at.x, at.y, at.z, MISSILE_BURST, Level.ExplosionInteraction.NONE);
	}

	// ---- per tick -----------------------------------------------------------------------------------------------------

	static void tick(ServerPlayer player, boolean wearing) {
		State state = STATES.get(player.getUUID());
		if (state == null) {
			if (!wearing) {
				return;
			}
			state = state(player);
		}
		if (state.palmCooldown > 0) {
			state.palmCooldown--;
		}
		if (state.launcherCooldown > 0) {
			state.launcherCooldown--;
		}
		if (state.scanCooldown > 0) {
			state.scanCooldown--;
		}
		if (state.unibeamCooldown > 0) {
			state.unibeamCooldown--;
		}
		RigUnibeam.tick(player, state);
		tickLock(player, state);
		if (state.hoverAt != 0 && player.level().getGameTime() >= state.hoverAt) {
			state.hoverAt = 0;
			if (wearing && !state.hovering && !player.onGround() && Rig.fullSet(player)) {
				toggleHover(player, state);
			}
		}
		if (state.hovering) {
			if (!wearing) {
				stopHover(player, state);
			} else {
				tickHover(player, state);
			}
		}
		if (wearing && (player.tickCount % STATUS_EVERY == 0 || state.sentHovering != state.hovering || state.sentFlying != state.flying
			|| state.sentLock != state.lock)) {
			sendStatus(player, state);
		}
		if (!wearing && state.wasWearing) {
			// the last piece came off: clear the HUD's state
			state.hovering = false;
			state.flying = false;
			state.lock = -1;
			state.unibeam = -1;
			sendStatus(player, state);
		}
		state.wasWearing = wearing;
		RigMoves.tick(player, !wearing || !state.hovering ? RigMoves.THRUST_NONE : state.flying ? RigMoves.THRUST_FLIGHT : RigMoves.THRUST_HOVER);
	}

	static void sendStatus(ServerPlayer player, State state) {
		state.sentHovering = state.hovering;
		state.sentFlying = state.flying;
		state.sentLock = state.lock;
		ServerPlayNetworking.send(player, new RigNet.Status(state.hovering, state.hovering && state.flying, RigMind.warnings(player), state.palmCooldown,
			state.launcherCooldown, state.scanCooldown, state.lock, RigTakeover.secondsLeft(player), state.unibeamCooldown));
	}

	static State stateOf(ServerPlayer player) {
		return state(player);
	}
}
