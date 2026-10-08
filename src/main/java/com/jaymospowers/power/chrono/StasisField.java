package com.jaymospowers.power.chrono;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A bubble of stopped time, fixed where it was cast. Mobs inside skip their whole {@code aiStep} (AI, movement and
 * gravity; see {@code LivingEntityMixin}), so nothing is written to their save data. Projectiles inside are parked:
 * their velocity is stored, then zeroed, and gravity is switched off until the field ends.
 *
 * <p>Hits on a frozen mob still land; the knockback is kept in its motion and released (capped) when time resumes.
 *
 * <p>Other players are stopped too (never the caster): the server holds them where they were caught (they can still
 * look around), and they can't attack, mine, build, use or interact until time resumes (see {@link #registerLocks}).
 */
final class StasisField {
	static final double RADIUS = 6.0;
	/** As long as Rewind's window: time stops for the same 3 seconds Rewind can undo. */
	static final int DURATION = ChronoPower.REWIND_TICKS;
	/** Released knockback is capped to this speed (blocks/tick), so stacked hits can't fling a mob across the map. */
	private static final double MAX_RELEASE_SPEED = 1.4;
	private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(0xF2C14E, 1.1F);
	private static final DustParticleOptions AMBER_DUST = new DustParticleOptions(0xFFB347, 0.8F);

	/** Everything frozen by any field, with how many fields hold it. Server thread only. */
	private static final Map<Entity, Frozen> FROZEN = new IdentityHashMap<>();

	final ServerLevel level;
	final Vec3 center;
	/** The caster, who moves freely in their own stopped time. */
	final java.util.UUID owner;
	int ticksLeft = DURATION;
	private int age;
	private final List<Entity> held = new ArrayList<>();

	StasisField(ServerLevel level, Vec3 center, java.util.UUID owner) {
		this.level = level;
		this.center = center;
		this.owner = owner;
	}

	/** A stopped player can't act: no attacking, mining, building, using items or interacting. */
	static void registerLocks() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
			isFrozen(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
			isFrozen(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
			isFrozen(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) ->
			isFrozen(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
			isFrozen(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !isFrozen(player));
	}

	/** True for a mob that a stasis field is holding still (server side). */
	static boolean isFrozen(Entity entity) {
		return FROZEN.containsKey(entity);
	}

	/** Server stopping: restore every projectile and forget everything. */
	static void releaseAll() {
		for (Map.Entry<Entity, Frozen> e : new ArrayList<>(FROZEN.entrySet())) {
			e.getValue().restore(e.getKey());
		}
		FROZEN.clear();
	}

	void start() {
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
		level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z, 40, 0.6, 0.6, 0.6, 0.25);
		capture();
		shell(48);
	}

	/** One tick. Returns false once the field has ended (and released everything). */
	boolean tick() {
		age++;
		if (--ticksLeft <= 0) {
			end();
			return false;
		}
		if (age % 2 == 0) {
			capture();
		}
		for (int i = held.size() - 1; i >= 0; i--) {
			Entity entity = held.get(i);
			Frozen frozen = FROZEN.get(entity);
			if (entity.isRemoved() || frozen == null) {
				held.remove(i);
				drop(entity);
				continue;
			}
			frozen.hold(entity);
			if (age % 10 == i % 10) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
					2, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.0);
			}
		}
		if (age % 2 == 0) {
			shell(22);
		}
		if (age % 10 == 0) {
			// tick... tock...
			boolean tick = (age / 10) % 2 == 1;
			level.playSound(null, center.x, center.y, center.z, SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.9F, tick ? 1.9F : 1.4F);
		}
		return true;
	}

	/** Ends the field now, letting everything it holds go. */
	void end() {
		for (Entity entity : held) {
			drop(entity);
		}
		held.clear();
		ticksLeft = 0;
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F);
		shell(60);
	}

	private void capture() {
		AABB box = new AABB(center, center).inflate(RADIUS);
		for (Entity entity : level.getEntities((Entity) null, box, e -> freezable(e) && !e.getUUID().equals(owner))) {
			if (entity.position().distanceToSqr(center) > RADIUS * RADIUS || held.contains(entity)) {
				continue;
			}
			Frozen frozen = FROZEN.get(entity);
			if (frozen == null) {
				frozen = Frozen.of(entity);
				FROZEN.put(entity, frozen);
				level.sendParticles(ParticleTypes.WAX_ON, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
					4, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.4, 0.0);
			}
			frozen.holders++;
			held.add(entity);
		}
	}

	private static boolean freezable(Entity entity) {
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof Mob) {
			return !(entity instanceof EnderDragon) && !(entity instanceof WitherBoss);
		}
		if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
			return !player.isSpectator();
		}
		if (entity instanceof Projectile) {
			// only projectiles in flight (arrows stuck in the ground have no motion)
			return !(entity instanceof FishingHook) && !(entity instanceof FireworkRocketEntity)
				&& entity.getDeltaMovement().lengthSqr() > 1.0E-4;
		}
		return false;
	}

	private static void drop(Entity entity) {
		Frozen frozen = FROZEN.get(entity);
		if (frozen != null && --frozen.holders <= 0) {
			FROZEN.remove(entity);
			if (!entity.isRemoved()) {
				frozen.restore(entity);
			}
		}
	}

	/** A golden sphere shell, slowly turning (Fibonacci points with a time-based twist), plus the 12 hour marks. */
	private void shell(int points) {
		double twist = age * 0.07;
		double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		for (int i = 0; i < points; i++) {
			double y = 1.0 - (i + 0.5) / points * 2.0;
			double r = Math.sqrt(1.0 - y * y);
			double a = golden * i + twist + (age % 4) * 0.4;
			double px = center.x + Math.cos(a) * r * RADIUS;
			double py = center.y + 1.0 + y * RADIUS;
			double pz = center.z + Math.sin(a) * r * RADIUS;
			level.sendParticles(i % 7 == 0 ? AMBER_DUST : GOLD_DUST, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (age % 10 == 0) {
			for (int h = 0; h < 12; h++) {
				double a = h / 12.0 * Math.PI * 2.0 - twist * 0.25;
				level.sendParticles(h % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WAX_ON,
					center.x + Math.sin(a) * RADIUS, center.y + 1.0, center.z - Math.cos(a) * RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** What a frozen entity looked like before, to put it back. */
	private static final class Frozen {
		final boolean projectile;
		final Vec3 position;
		final Vec3 velocity;
		final boolean noGravity;
		int holders;

		private Frozen(boolean projectile, Vec3 position, Vec3 velocity, boolean noGravity) {
			this.projectile = projectile;
			this.position = position;
			this.velocity = velocity;
			this.noGravity = noGravity;
		}

		static Frozen of(Entity entity) {
			boolean projectile = !(entity instanceof LivingEntity);
			Frozen frozen = new Frozen(projectile, entity.position(), entity.getDeltaMovement(), entity.isNoGravity());
			entity.setDeltaMovement(Vec3.ZERO);
			if (projectile) {
				entity.setNoGravity(true);
			}
			entity.needsSync = true;
			return frozen;
		}

		/** Every tick while frozen. Mobs are already held still by the mixin; projectiles are parked in place. */
		void hold(Entity entity) {
			if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
				// players move themselves, so pull them back to where time stopped (keeping where they look)
				if (player.position().distanceToSqr(position) > 1.0E-4) {
					player.connection.teleport(position.x, position.y, position.z, player.getYRot(), player.getXRot());
				}
				player.setDeltaMovement(Vec3.ZERO);
				player.resetFallDistance();
			} else if (projectile) {
				entity.setPos(position.x, position.y, position.z);
				if (entity.getDeltaMovement().lengthSqr() > 0.0) {
					entity.setDeltaMovement(Vec3.ZERO);
					entity.needsSync = true;
				}
			} else if (entity instanceof Creeper creeper && creeper.getSwellDir() > 0) {
				creeper.setSwellDir(-1);
			}
		}

		void restore(Entity entity) {
			if (projectile) {
				entity.setNoGravity(noGravity);
				entity.setDeltaMovement(velocity);
			} else {
				// release the knockback the mob took while frozen
				Vec3 pending = entity.getDeltaMovement();
				if (pending.length() > MAX_RELEASE_SPEED) {
					entity.setDeltaMovement(pending.normalize().scale(MAX_RELEASE_SPEED));
				}
			}
			entity.needsSync = true;
		}
	}
}
