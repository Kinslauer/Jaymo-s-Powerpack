package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (Weapon Forms patch 6) The lightning Sorcerer's two extra moves, on the keys the Imbue already has ({@link Spellcraft}).
 * Nothing new is bound; a lightning Sorcerer's {@code J} gains:
 * <ul>
 * <li><b>Bolt Step</b>: sneak and {@code J} with nothing living in your sights (with a foe in sight it is still the
 * element's bolt). You blink along your look line, up to {@link #STEP_RANGE} blocks and stopping short of a wall, and
 * every foe you pass through is struck for {@link #STEP_DAMAGE}, with a flash of light on each. Your fall is untouched:
 * the blink never resets how far you have fallen.</li>
 * <li><b>Storm Ring</b>: {@code J} with no blade in your hand (with a blade it is still the Imbue). A ring of
 * {@link #RING_BOLTS} bolts of light falls round you at {@link #RING_RADIUS} blocks, hurting what stands in each and
 * throwing it back a little. It sets nothing alight and spares players and you.</li>
 * </ul>
 * Both cost mana ({@link Mana#COST}), keep their own cooldowns, and count as sorcery ({@link Spellcraft#afterCast}).
 */
public final class StormArts {
	// ---- tuning ----
	/** Bolt Step: how far it carries (blocks), the damage to each foe passed through, how wide the path is (blocks each side), the cooldown (ticks). */
	static final double STEP_RANGE = 9.0, STEP_WIDTH = 0.9;
	static final float STEP_DAMAGE = 6.0F;
	static final int STEP_COOLDOWN = 20 * 4;
	/** Storm Ring: the number of bolts, how far out they fall, the damage and reach of each, the shove, the cooldown (ticks). */
	static final int RING_BOLTS = 8, RING_COOLDOWN = 20 * 10;
	static final double RING_RADIUS = 4.5, RING_STRIKE_RADIUS = 2.0, RING_PUSH = 0.6;
	static final float RING_DAMAGE = 5.0F;

	/** Sorcerer → game time the move is ready again. */
	private static final Map<UUID, Long> STEP_READY = new HashMap<>();
	private static final Map<UUID, Long> RING_READY = new HashMap<>();

	private StormArts() {
	}

	static void clear() {
		STEP_READY.clear();
		RING_READY.clear();
	}

	/**
	 * Sneak and {@code J} for a lightning Sorcerer: Bolt Step if no one is in sight, else false (let the bolt go).
	 */
	static boolean stepKey(ServerPlayer player, ServerLevel level) {
		if (Arcana.sighted(player) != null) {
			return false;
		}
		long now = level.getGameTime();
		if (STEP_READY.getOrDefault(player.getUUID(), 0L) > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.storm.step_wait", (STEP_READY.get(player.getUUID()) - now + 19) / 20));
			return true;
		}
		Vec3 start = player.position();
		Vec3 end = destination(level, player);
		if (end == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.storm.step_blocked"));
			return true;
		}
		if (!Mana.cast(player, "bolt_step")) {
			return true;
		}
		STEP_READY.put(player.getUUID(), now + STEP_COOLDOWN);
		// the foes on the line, struck as you pass
		Vec3 line = end.subtract(start);
		AABB sweep = new AABB(start, end).inflate(STEP_WIDTH).expandTowards(0.0, player.getBbHeight(), 0.0);
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && !e.isSpectator()
			&& !(e instanceof ServerPlayer) && !player.isAlliedTo(e))) {
			Optional<Vec3> hit = foe.getBoundingBox().inflate(STEP_WIDTH).clip(start.add(0.0, player.getBbHeight() * 0.5, 0.0),
				end.add(0.0, player.getBbHeight() * 0.5, 0.0));
			if (hit.isPresent()) {
				foe.hurtServer(level, level.damageSources().indirectMagic(player, player), STEP_DAMAGE);
				flash(level, foe.position());
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, foe.getX(), foe.getY(0.6), foe.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
			}
		}
		// the trail it leaves, then the step itself (how far you have fallen goes with you)
		int n = Math.max(2, (int)(line.length() * 3.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = start.add(line.scale(i / (double)n)).add(0.0, player.getBbHeight() * 0.5, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.1, 0.05, 0.02);
		}
		double fallen = player.fallDistance;
		player.teleportTo(level, end.x, end.y, end.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.fallDistance = fallen;
		flash(level, start);
		flash(level, end);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.9F);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Spellcraft.afterCast(player, level, "lightning");
		return true;
	}

	/** Where the step lands: along the look line, short of any wall, somewhere there is room to stand; null if there isn't. */
	private static Vec3 destination(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(STEP_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = hit.getType() == HitResult.Type.MISS ? STEP_RANGE : Math.max(0.0, eye.distanceTo(hit.getLocation()) - 0.6);
		for (double d = reach; d >= 1.5; d -= 0.5) {
			Vec3 to = player.position().add(look.scale(d));
			if (level.noCollision(player, player.getBoundingBox().move(to.subtract(player.position())))) {
				return to;
			}
		}
		return null;
	}

	/**
	 * {@code J} for a lightning Sorcerer with no blade in hand: the Storm Ring. Always handles the key (so the
	 * "hold a blade" word is for the other elements).
	 */
	static boolean ring(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		if (RING_READY.getOrDefault(player.getUUID(), 0L) > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.storm.ring_wait", (RING_READY.get(player.getUUID()) - now + 19) / 20));
			return true;
		}
		if (!Mana.cast(player, "storm_ring")) {
			return true;
		}
		RING_READY.put(player.getUUID(), now + RING_COOLDOWN);
		Vec3 centre = player.position();
		Set<Entity> struck = new HashSet<>();
		for (int i = 0; i < RING_BOLTS; i++) {
			double angle = i * Math.PI * 2.0 / RING_BOLTS;
			Vec3 at = centre.add(Math.cos(angle) * RING_RADIUS, 0.0, Math.sin(angle) * RING_RADIUS);
			flash(level, at);
			for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RING_STRIKE_RADIUS, 2.0, RING_STRIKE_RADIUS),
				e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ServerPlayer) && !player.isAlliedTo(e)
					&& e.position().distanceToSqr(at) <= RING_STRIKE_RADIUS * RING_STRIKE_RADIUS)) {
				if (struck.add(foe)) {
					foe.hurtServer(level, level.damageSources().indirectMagic(player, player), RING_DAMAGE);
					Vec3 away = foe.position().subtract(centre).multiply(1.0, 0.0, 1.0);
					away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
					foe.setDeltaMovement(foe.getDeltaMovement().add(away.x * RING_PUSH, 0.3, away.z * RING_PUSH));
					foe.syncVelocity = true;
				}
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 14, 0.3, 0.8, 0.3, 0.25);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y + 1.0, centre.z, 30, 0.5, 0.8, 0.5, 0.3);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.2F);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Spellcraft.afterCast(player, level, "lightning");
		return true;
	}

	/** A bolt of light only: it burns nothing, charges nothing and hurts no one. */
	private static void flash(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setVisualOnly(true);
			bolt.setPos(at.x, at.y, at.z);
			level.addFreshEntity(bolt);
		}
	}
}
