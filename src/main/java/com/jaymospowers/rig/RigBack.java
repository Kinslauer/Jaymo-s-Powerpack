package com.jaymospowers.rig;

import com.jaymospowers.core.HitStop;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Rig's back rig (Andy's picks from the Zemo&Baki jar; ideas only, our own code): Frostborn tech, plates slide,
 * lock and fold, nothing grown or alive. Each one is a Rig menu entry ({@link RigPicks}) with a charge cost and a Mind
 * level gate:
 * <ul>
 * <li>{@link #BK_WINGS} folding wings: they spread for full flight and fold back in hover or on landing.</li>
 * <li>{@link #BK_WINGSLASH} wing slash: a thruster dash that cuts everything it passes instead of stopping at the
 * first thing.</li>
 * <li>{@link #BK_LEGS} spear legs: four mechanical legs stab out from the back plate and back in a ring.</li>
 * <li>{@link #BK_PINCER} pincer: two arms seize one creature and hold it in front of you; press again to throw it.</li>
 * <li>{@link #BK_GRAPPLE} grapple claw: a claw on a line -- zip to a wall, or yank a lighter enemy to you.</li>
 * <li>{@link #BK_HORNS} chain lightning from the helm (Mind 4): arcs to up to four targets, harder in rain, and
 * overcharges the Core.</li>
 * </ul>
 */
public final class RigBack {
	/** Action IDs, continuing RigPicks' range. */
	public static final int BK_WINGS = 36, BK_WINGSLASH = 37, BK_LEGS = 38, BK_PINCER = 39, BK_GRAPPLE = 40,
		BK_HORNS = 41;

	/** Wings: full flight only (Mind 5); the thrusters' own charge drain pays for it. */
	static final int WINGS_LEVEL = Rig.FLIGHT_LEVEL;
	/** Wing slash: charge, cooldown (ticks), the dash's speed (b/t) and damage. */
	static final int WINGSLASH_COST = 50, WINGSLASH_COOLDOWN = 40;
	static final double WINGSLASH_SPEED = 1.7;
	static final float WINGSLASH_DAMAGE = 5.0F, WINGSLASH_KNOCKBACK = 1.0F;
	/** Spear legs: charge, cooldown, the ring's radius, damage and knockback. */
	static final int SPEAR_COST = 40, SPEAR_COOLDOWN = 30;
	static final double SPEAR_RADIUS = 2.8;
	static final float SPEAR_DAMAGE = 6.0F, SPEAR_KNOCKBACK = 1.0F;
	/** Pincer: charge, cooldown, the Mind level it needs, reach (blocks), how long it holds (ticks), the throw. */
	static final int PINCER_COST = 50, PINCER_COOLDOWN = 60, PINCER_LEVEL = 2;
	static final double PINCER_REACH = 6.0;
	static final int PINCER_HOLD_TICKS = 400;
	static final float PINCER_THROW_SPEED = 1.6F, PINCER_THROW_DAMAGE = 8.0F;
	/** Grapple claw: charge, cooldown, reach (blocks), the zip's speed (b/t). */
	static final int GRAPPLE_COST = 30, GRAPPLE_COOLDOWN = 40;
	static final double GRAPPLE_REACH = 44.0, GRAPPLE_SPEED = 0.85;
	/** A catch this heavy (bounding-box volume, about a person) hauls you back instead. */
	static final double GRAPPLE_HEAVY = 1.0;
	/** Chain lightning from the helm: Mind 4, charge, cooldown, how many targets, how far each arc can reach. */
	static final int HORNS_LEVEL = Rig.UNIBEAM_LEVEL;
	static final int HORNS_COST = 160, HORNS_COOLDOWN = 100;
	static final int HORNS_CHAIN = 4;
	static final double HORNS_REACH = 16.0, HORNS_CHAIN_REACH = 12.0;
	static final float HORNS_DAMAGE = 7.0F, HORNS_RAIN = 1.3F;

	/** One wearer's back-rig state. */
	static final class BackState {
		/** Cooldowns: game time each active becomes ready again. */
		int wingsReady, slashReady, spearReady, pincerReady, grappleReady, hornsReady;
		/** The pincer's hold: the grabbed entity's id, and when the hold ends. */
		int grabbed = -1;
		long grabUntil = 0L;
		/** The wing-slash dash under way: when it ends, the direction, and what it has already cut. */
		int slashUntil = 0;
		@Nullable Vec3 slashDir = null;
		final Set<Integer> slashed = new HashSet<>();
		/** The grapple: when it ends, the point to zip to (null when yanking an entity to you), and that entity. */
		int grappleUntil = 0;
		@Nullable Vec3 grappleTo = null;
		int grappleEntity = -1;
	}

	private static final Map<UUID, BackState> STATES = new HashMap<>();

	private RigBack() {
	}

	/** Called from {@link RigPicks#init}. */
	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	/** Whether {@code action} is a back-rig active. */
	public static boolean is(int action) {
		return action >= BK_WINGS && action <= BK_HORNS;
	}

	/** A Rig menu pick: one back-rig active. */
	static void use(ServerPlayer player, int action) {
		if (player.isSpectator() || !Rig.wearsAny(player)) {
			return;
		}
		long now = player.level().getGameTime();
		BackState state = state(player);
		switch (action) {
			case BK_WINGS -> wings(player, state);
			case BK_WINGSLASH -> wingSlash(player, state, now);
			case BK_LEGS -> spearLegs(player, state, now);
			case BK_PINCER -> pincer(player, state, now);
			case BK_GRAPPLE -> grapple(player, state, now);
			case BK_HORNS -> horns(player, state, now);
		}
	}

	// ---- folding wings ----------------------------------------------------------------

	/** Spread the wings for full flight, or fold them back: the thrusters' own drain pays for flight. */
	private static void wings(ServerPlayer player, BackState state) {
		if (!Rig.fullSet(player)) {
			incomplete(player);
			return;
		}
		if (Rig.level(player) < WINGS_LEVEL) {
			needsLevel(player, WINGS_LEVEL);
			return;
		}
		RigActives.State flight = RigActives.stateOf(player);
		if (!flight.hovering) {
			if (Rig.charge(player) < RigActives.HOVER_MIN && !player.isCreative()) {
				RigMind.empty(player);
				return;
			}
			flight.hovering = true;
			flight.hoverTicks = 0;
			flight.jetPos = null;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE,
				SoundSource.PLAYERS, 0.6F, 1.6F);
			if (player.onGround() && player.level() instanceof ServerLevel level) {
				RigMoves.start(player, RigMoves.TAKEOFF);
				RigActives.later(player, RigMoves.TAKEOFF_CROUCH, () -> liftOff(level, player, RigActives.FLIGHT_RING,
					RigActives.FLIGHT_SHAKE));
			}
		}
		flight.flying = !flight.flying;
		if (flight.flying) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
				SoundSource.PLAYERS, 0.8F, 1.0F);
		} else {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
				SoundSource.PLAYERS, 0.4F, 1.4F);
		}
	}

	// ---- wing slash -------------------------------------------------------------------

	/** Dash along the look with the wings out as blades: every creature in the way is cut, once each. */
	private static void wingSlash(ServerPlayer player, BackState state, long now) {
		if (!Rig.fullSet(player)) {
			incomplete(player);
			return;
		}
		if (now < state.slashReady) {
			formWait(player, state.slashReady - now);
			return;
		}
		if (!Rig.spend(player, WINGSLASH_COST)) {
			RigMind.empty(player);
			return;
		}
		state.slashReady = (int) (now + WINGSLASH_COOLDOWN);
		state.slashUntil = (int) (now + RigMoves.DASH_TICKS);
		state.slashed.clear();
		Vec3 look = player.getLookAngle();
		Vec3 dir = player.onGround() ? new Vec3(look.x, 0.0, look.z) : look;
		if (dir.lengthSqr() < 1.0E-4) {
			dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		state.slashDir = dir.normalize();
		RigMoves.start(player, RigMoves.WINGSLASH);
		ServerLevel level = player.level();
		double speed = WINGSLASH_SPEED * Rig.frame(player).airSpeed;
		player.setDeltaMovement(dir.scale(speed).add(0.0, player.onGround() ? 0.12 : 0.0, 0.0));
		player.syncVelocity = true;
		player.resetFallDistance();
		Vec3 feet = player.position();
		MagicFx.ring(level, feet.add(0.0, 0.6, 0.0), state.slashDir, 1.2F, RigActives.FLAME_COLOR, 8);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F,
			1.3F);
		Shakes.send(player, 0.7F, feet.subtract(state.slashDir));
	}

	/** Every tick of the wing-slash: keep bursting along, and cut everything in reach. */
	private static void tickSlash(ServerPlayer player, BackState state) {
		ServerLevel level = player.level();
		Vec3 dir = state.slashDir == null ? player.getLookAngle() : state.slashDir;
		double speed = WINGSLASH_SPEED * Rig.frame(player).airSpeed;
		player.setDeltaMovement(dir.scale(speed).add(0.0, player.onGround() ? 0.1 : 0.0, 0.0));
		player.syncVelocity = true;
		player.resetFallDistance();
		if (player.onGround() || player.horizontalCollision) {
			state.slashUntil = 0;
			state.slashDir = null;
			state.slashed.clear();
			return;
		}
		DamageSource source = level.damageSources().playerAttack(player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			player.getBoundingBox().inflate(1.1),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player))) {
			if (!state.slashed.add(target.getId())) {
				continue;
			}
			float damage = (float) (WINGSLASH_DAMAGE * Rig.frame(player).shotDamage * RigActives.levelDamage(player));
			if (target.hurtServer(level, source, damage)) {
				target.knockback(WINGSLASH_KNOCKBACK, -dir.x, -dir.z, source, damage);
				target.syncVelocity = true;
				MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.5F, RigActives.FLAME_HOT);
				HitStop.hit(player, target, 1);
			}
		}
	}

	// ---- spear legs -------------------------------------------------------------------

	/** Four mechanical legs stab out from the back plate and back in a ring. */
	private static void spearLegs(ServerPlayer player, BackState state, long now) {
		if (!Rig.fullSet(player)) {
			incomplete(player);
			return;
		}
		if (now < state.spearReady) {
			formWait(player, state.spearReady - now);
			return;
		}
		if (!Rig.spend(player, SPEAR_COST)) {
			RigMind.empty(player);
			return;
		}
		state.spearReady = (int) (now + SPEAR_COOLDOWN);
		RigMoves.start(player, RigMoves.SPEAR);
		ServerLevel level = player.level();
		Vec3 feet = player.position().add(0.0, -0.3, 0.0);
		MagicFx.ring(level, feet, (float) SPEAR_RADIUS, RigActives.FLAME_COLOR);
		MagicFx.blast(level, feet.add(0.0, 0.2, 0.0), 1.2F, RigActives.FLAME_HOT);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.7F);
		Shakes.send(player, 1.0F, feet);
		DamageSource source = level.damageSources().playerAttack(player);
		float damage = (float) (SPEAR_DAMAGE * Rig.frame(player).shotDamage * RigActives.levelDamage(player));
		Vec3 center = player.position();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			new AABB(center.x - SPEAR_RADIUS, center.y - 1.0, center.z - SPEAR_RADIUS, center.x + SPEAR_RADIUS,
				center.y + 2.5, center.z + SPEAR_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player))) {
			if (target.hurtServer(level, source, damage)) {
				Vec3 away = target.position().subtract(center).normalize();
				if (away.lengthSqr() < 1.0E-4) {
					away = player.getLookAngle();
				}
				target.knockback(SPEAR_KNOCKBACK, away.x, away.z, source, damage);
				target.syncVelocity = true;
			}
		}
	}

	// ---- pincer -----------------------------------------------------------------------

	/** Seize the nearest creature in reach and hold it in front of you; press again to throw it along your look. */
	private static void pincer(ServerPlayer player, BackState state, long now) {
		if (!Rig.fullSet(player)) {
			incomplete(player);
			return;
		}
		if (Rig.level(player) < PINCER_LEVEL) {
			needsLevel(player, PINCER_LEVEL);
			return;
		}
		if (state.grabbed < 0) {
			if (now < state.pincerReady) {
				formWait(player, state.pincerReady - now);
				return;
			}
			LivingEntity target = grabTarget(player, state);
			if (target == null) {
				return;
			}
			if (!Rig.spend(player, PINCER_COST)) {
				RigMind.empty(player);
				return;
			}
			state.grabbed = target.getId();
			state.grabUntil = now + PINCER_HOLD_TICKS;
			state.pincerReady = (int) (now + PINCER_COOLDOWN);
			RigMoves.start(player, RigMoves.PINCER);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
				SoundSource.PLAYERS, 0.8F, 1.4F);
			return;
		}
		ServerLevel level = player.level();
		Entity held = level.getEntity(state.grabbed);
		if (held instanceof LivingEntity target && target.isAlive() && target.distanceTo(player) <= 12.0) {
			Vec3 look = player.getLookAngle();
			DamageSource source = level.damageSources().playerAttack(player);
			if (target.hurtServer(level, source, PINCER_THROW_DAMAGE)) {
				target.setDeltaMovement(look.scale(PINCER_THROW_SPEED).add(0.0, 0.3, 0.0));
				target.syncVelocity = true;
				MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.6F, RigActives.FLAME_HOT);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.IRON_GOLEM_ATTACK,
					SoundSource.PLAYERS, 0.9F, 0.8F);
				Shakes.send(player, 0.9F, player.position());
			}
		}
		state.grabbed = -1;
		state.grabUntil = 0L;
	}

	/** The nearest creature in reach that is not the wearer, a mount, or ridden. */
	private static @Nullable LivingEntity grabTarget(ServerPlayer player, BackState state) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDist = PINCER_REACH;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			player.getBoundingBox().inflate(PINCER_REACH),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player)
				&& !e.isPassenger() && e.getFirstPassenger() == null && player.hasLineOfSight(e))) {
			double dist = target.getBoundingBox().getCenter().distanceToSqr(eye);
			if (dist < bestDist * bestDist) {
				bestDist = Math.sqrt(dist);
				best = target;
			}
		}
		return best;
	}

	/** Every tick the pincer holds something: keep it in front of the wearer, or let go. */
	private static void tickPincer(ServerPlayer player, BackState state) {
		if (state.grabbed < 0) {
			return;
		}
		ServerLevel level = player.level();
		Entity held = level.getEntity(state.grabbed);
		LivingEntity living = null;
		if (held instanceof LivingEntity candidate && candidate.isAlive()
				&& player.level().getGameTime() < state.grabUntil && candidate.distanceTo(player) <= 12.0
				&& !candidate.isPassenger() && candidate.getFirstPassenger() == null && Rig.fullSet(player)) {
			living = candidate;
		}
		if (living != null) {
			Vec3 look = player.getLookAngle();
			Vec3 at = player.getEyePosition().add(0.0, 0.35, 0.0).add(look.scale(1.05));
			living.setPos(at.x, at.y, at.z);
			living.setDeltaMovement(0.0, 0.0, 0.0);
		} else {
			state.grabbed = -1;
			state.grabUntil = 0L;
		}
	}

	// ---- grapple claw -----------------------------------------------------------------

	/** Fire a claw on a line: zip to a block, or yank a lighter enemy to you. */
	private static void grapple(ServerPlayer player, BackState state, long now) {
		if (!Rig.fullSet(player)) {
			incomplete(player);
			return;
		}
		if (now < state.grappleReady) {
			formWait(player, state.grappleReady - now);
			return;
		}
		if (!Rig.spend(player, GRAPPLE_COST)) {
			RigMind.empty(player);
			return;
		}
		state.grappleReady = (int) (now + GRAPPLE_COOLDOWN);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(GRAPPLE_REACH)),
			player.getBoundingBox().expandTowards(look.scale(GRAPPLE_REACH)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player && !e.isPassenger()
				&& e.getFirstPassenger() == null,
			eye.distanceToSqr(eye.add(look.scale(GRAPPLE_REACH))));
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			double weight = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
			if (weight <= GRAPPLE_HEAVY) {
				// light enough: yank it to you
				state.grappleEntity = target.getId();
				state.grappleTo = null;
				state.grappleUntil = (int) (now + 40);
			} else {
				// too heavy: you go to it
				state.grappleEntity = -1;
				state.grappleTo = target.getBoundingBox().getCenter();
				state.grappleUntil = (int) (now + 60);
			}
		} else {
			BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(GRAPPLE_REACH)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (block.getType() == HitResult.Type.MISS) {
				state.grappleReady = (int) (now + 10);
				return;
			}
			state.grappleEntity = -1;
			state.grappleTo = block.getLocation();
			state.grappleUntil = (int) (now + 60);
		}
		RigMoves.start(player, RigMoves.GRAPPLE);
		player.level().playSound(null, eye.x, eye.y, eye.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS,
			0.8F, 1.4F);
		Shakes.send(player, 0.5F, player.position());
	}

	/** Every tick of the grapple: close the line. */
	private static void tickGrapple(ServerPlayer player, BackState state) {
		if (state.grappleUntil <= 0 || player.level().getGameTime() >= state.grappleUntil) {
			state.grappleUntil = 0;
			state.grappleTo = null;
			state.grappleEntity = -1;
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (state.grappleEntity >= 0) {
			Entity target = level.getEntity(state.grappleEntity);
			if (target == null || !target.isAlive() || target.distanceTo(player) > 24.0) {
				state.grappleUntil = 0;
				return;
			}
			if (state.grappleTo == null) {
				// yank the catch to you
				Vec3 dir = player.position().subtract(target.position()).normalize();
				target.setDeltaMovement(dir.scale(0.75).add(0.0, 0.15, 0.0));
				target.syncVelocity = true;
				double weight = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
				if (weight > GRAPPLE_HEAVY) {
					// it fights the line and hauls you back
					Vec3 kick = target.position().subtract(player.position()).normalize().scale(0.25).add(0.0, 0.05, 0.0);
					player.setDeltaMovement(kick);
					player.syncVelocity = true;
				}
				if (player.distanceToSqr(target) < 1.0) {
					state.grappleUntil = 0;
				}
			} else {
				// too heavy: you close to it
				Vec3 dir = state.grappleTo.subtract(player.position()).normalize();
				player.setDeltaMovement(dir.scale(GRAPPLE_SPEED).add(0.0, player.onGround() ? 0.1 : 0.0, 0.0));
				player.syncVelocity = true;
				player.resetFallDistance();
				if (player.distanceToSqr(state.grappleTo) < 1.0 || player.onGround()) {
					state.grappleUntil = 0;
				}
			}
		} else if (state.grappleTo != null) {
			Vec3 dir = state.grappleTo.subtract(player.position()).normalize();
			player.setDeltaMovement(dir.scale(GRAPPLE_SPEED).add(0.0, player.onGround() ? 0.1 : 0.0, 0.0));
			player.syncVelocity = true;
			player.resetFallDistance();
			if (player.distanceToSqr(state.grappleTo) < 1.0 || player.onGround()) {
				state.grappleUntil = 0;
			}
		}
	}

	// ---- chain lightning from the helm ------------------------------------------------

	/** Mind 4: lightning arcs from the helm to the nearest foes, harder in rain, and overcharges the Core. */
	private static void horns(ServerPlayer player, BackState state, long now) {
		if (!Rig.wears(player, Rig.Piece.HELM) || Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("gui.jaymospowers.rig_menu.no_helm_core"));
			return;
		}
		if (Rig.level(player) < HORNS_LEVEL) {
			needsLevel(player, HORNS_LEVEL);
			return;
		}
		if (now < state.hornsReady) {
			formWait(player, state.hornsReady - now);
			return;
		}
		ServerLevel level = player.level();
		List<LivingEntity> chain = chain(level, player);
		if (chain.isEmpty()) {
			return;
		}
		if (!Rig.spend(player, HORNS_COST)) {
			RigMind.empty(player);
			return;
		}
		state.hornsReady = (int) (now + HORNS_COOLDOWN);
		RigMoves.start(player, RigMoves.LIGHTNING);
		boolean rain = level.isRaining();
		float damage = HORNS_DAMAGE * RigActives.levelDamage(player) * (rain ? HORNS_RAIN : 1.0F);
		DamageSource source = level.damageSources().playerAttack(player);
		for (int i = 0; i < chain.size(); i++) {
			LivingEntity target = chain.get(i);
			if (target.hurtServer(level, source, damage)) {
				target.syncVelocity = true;
			}
			MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.4F, RigActives.OVER_COLOR);
			if (i > 0) {
				LivingEntity previous = chain.get(i - 1);
				MagicFx.stroke(level, previous.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(), 0.2F,
					RigActives.OVER_COLOR);
			}
		}
		Vec3 chest = player.position().add(0.0, 1.1, 0.0);
		level.playSound(null, chest.x, chest.y, chest.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.0F,
			1.0F);
		MagicFx.blast(level, chest, 1.0F, RigActives.OVER_COLOR);
		Shakes.send(player, 0.8F, chest);
		RigActives.overcharge(player);
	}

	/** The lightning's chain: the nearest foe, then the nearest foe to each one struck. */
	private static List<LivingEntity> chain(ServerLevel level, ServerPlayer player) {
		List<LivingEntity> chain = new ArrayList<>();
		Set<Integer> used = new HashSet<>();
		@Nullable LivingEntity previous = nearest(level, player.getEyePosition(), HORNS_REACH, player, used);
		if (previous == null) {
			return chain;
		}
		chain.add(previous);
		used.add(previous.getId());
		for (int i = 1; i < HORNS_CHAIN; i++) {
			@Nullable LivingEntity next = nearest(level, previous.getBoundingBox().getCenter(), HORNS_CHAIN_REACH,
				player, used);
			if (next == null) {
				break;
			}
			chain.add(next);
			used.add(next.getId());
			previous = next;
		}
		return chain;
	}

	/** The nearest eligible living entity to {@code center}, or null. */
	private static @Nullable LivingEntity nearest(ServerLevel level, Vec3 center, double reach, ServerPlayer player,
		Set<Integer> used) {
		LivingEntity best = null;
		double bestDist = reach;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			new AABB(center.x - reach, center.y - reach, center.z - reach, center.x + reach, center.y + reach,
			center.z + reach),
			e -> e != player && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(player) && !used.contains(e.getId()))) {
			double dist = target.getBoundingBox().getCenter().distanceToSqr(center);
			if (dist < bestDist * bestDist) {
				bestDist = Math.sqrt(dist);
				best = target;
			}
		}
		return best;
	}

	// ---- per tick ---------------------------------------------------------------------

	/** Every tick: the wing-slash, the pincer's hold and the grapple's line. */
	static void tick(ServerPlayer player) {
		BackState state = STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (state.slashUntil > 0 && now < state.slashUntil) {
			tickSlash(player, state);
		} else {
			state.slashUntil = 0;
			state.slashDir = null;
			state.slashed.clear();
		}
		tickPincer(player, state);
		tickGrapple(player, state);
	}

	// ---- helpers ----------------------------------------------------------------------

	private static void liftOff(ServerLevel level, ServerPlayer player, float ring, float shake) {
		Vec3 feet = player.position();
		MagicFx.ring(level, feet.add(0.0, 0.1, 0.0), ring, RigActives.FLAME_COLOR);
		MagicFx.blast(level, feet.add(0.0, 0.2, 0.0), 1.1F, RigActives.FLAME_HOT);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F,
			0.6F);
		Shakes.send(player, shake, feet);
	}

	private static void formWait(ServerPlayer player, long ticks) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.form_wait", (ticks + 19) / 20));
	}

	private static void needsLevel(ServerPlayer player, int level) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.needs_level", level));
	}

	private static void incomplete(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
	}

	private static BackState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new BackState());
	}
}
