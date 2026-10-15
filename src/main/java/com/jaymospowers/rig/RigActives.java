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

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final List<Missile> MISSILES_IN_FLIGHT = new ArrayList<>();

	private static final class State {
		boolean hovering;
		int hoverTicks;
		int palmCooldown;
		int launcherCooldown;
		/** The last status sent, to send a change at once. */
		boolean sentHovering;
		boolean wasWearing;
	}

	private record Missile(ServerLevel level, ServerPlayer owner, Vec3[] at, Vec3 velocity, int[] age) {
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

	/** A Rig key from the client. */
	static void use(ServerPlayer player, int action) {
		if (player.isSpectator() || !Rig.wearsAny(player)) {
			return;
		}
		if (action == RigNet.REPORT) {
			RigMind.report(player);
			return;
		}
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		State state = state(player);
		switch (action) {
			case RigNet.HOVER -> toggleHover(player, state);
			case RigNet.PALM -> palm(player, state);
			case RigNet.LAUNCHER -> launch(player, state);
			default -> {
			}
		}
		sendStatus(player, state);
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
		state.hovering = true;
		state.hoverTicks = 0;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	private static void stopHover(ServerPlayer player, State state) {
		state.hovering = false;
		state.hoverTicks = 0;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.8F);
	}

	private static void tickHover(ServerPlayer player, State state) {
		state.hoverTicks++;
		if (!Rig.fullSet(player) || player.isFallFlying()) {
			stopHover(player, state);
			return;
		}
		if (state.hoverTicks % HOVER_DRAIN_EVERY == 0) {
			int cost = Rig.augment(player) == Rig.Augment.WIND ? Math.max(1, Math.round(HOVER_COST * WIND_HOVER_SHARE)) : HOVER_COST;
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
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() - 0.1, player.getZ(), 2, 0.12, 0.02, 0.12, 0.01);
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() - 0.3, player.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
		}
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
		ServerLevel level = player.level();
		Rig.Augment augment = Rig.augment(player);
		double range = PALM_RANGE * (augment == Rig.Augment.XENOFORM ? XENO_RANGE : 1.0);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
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
			MISSILES_IN_FLIGHT.add(new Missile(level, player, new Vec3[] {start}, velocity, new int[1]));
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
			Vec3 to = from.add(missile.velocity());
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
		if (state.hovering) {
			if (!wearing) {
				stopHover(player, state);
			} else {
				tickHover(player, state);
			}
		}
		if (wearing && (player.tickCount % STATUS_EVERY == 0 || state.sentHovering != state.hovering)) {
			sendStatus(player, state);
		}
		if (!wearing && state.wasWearing) {
			// the last piece came off: clear the HUD's state
			state.hovering = false;
			sendStatus(player, state);
		}
		state.wasWearing = wearing;
	}

	private static void sendStatus(ServerPlayer player, State state) {
		state.sentHovering = state.hovering;
		ServerPlayNetworking.send(player, new RigNet.Status(state.hovering, RigMind.warnings(player), state.palmCooldown, state.launcherCooldown));
	}
}
