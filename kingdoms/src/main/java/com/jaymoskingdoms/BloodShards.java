package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightForms;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blood flechettes (Night's Blood, patch 4). Andy: "blood flechettes can be added, but draw from vampire hunger (blood measured
 * in Mb". A move on the skill bar ({@code night_flechettes}): a fan of up to {@link #FAN} shards of their own blood, each one
 * paid for out of the reserve ({@link #SHARD_MB}), so the reserve is the ammunition and nothing is ever free: with blood for
 * three they throw three, with blood for none, none. A creature a shard hits is hurt, and cannot heal for {@link #NO_HEAL_TICKS}
 * ({@link #unhealing}); the thrower's next blow on it gives a little blood back ({@link #BACK_MB}). Players only where players
 * may fight ({@link NightTargets}). The shards are not entities: they are carried on in a list, a few blocks a tick, drawn with
 * strokes and motes.
 */
public final class BloodShards {
	/** The most shards in a fan, what each costs (mB), the blood they must keep after paying, and the wait (ticks). */
	static final int FAN = 5, SHARD_MB = 40, KEEP_MB = 100, COOLDOWN = 50;
	/** The fan's spread (degrees from the look to the outermost). */
	static final double SPREAD = 14.0;
	/** A shard: its speed (blocks a tick), its life (ticks), the harm it does. */
	static final double SPEED = 1.6;
	static final int LIFE = 14;
	static final float DAMAGE = 3.0F;
	/** What a hit does: no healing for this long (ticks); the thrower's next blow gives this back (mB) within this long (ticks). */
	static final int NO_HEAL_TICKS = 160, BACK_TICKS = 300, BACK_MB = 60;

	private static final class Shard {
		final UUID owner;
		Vec3 at;
		final Vec3 velocity;
		int left = LIFE;

		Shard(UUID owner, Vec3 at, Vec3 velocity) {
			this.owner = owner;
			this.at = at;
			this.velocity = velocity;
		}
	}

	/** Set while a shard's own wound lands, so it never pays itself back. */
	private static boolean shooting;
	private static final List<Shard> SHARDS = new ArrayList<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Who cannot heal until when, and who is owed a drink of blood by whom until when. */
	private static final Map<LivingEntity, Long> NO_HEAL = new WeakHashMap<>();
	private static final Map<LivingEntity, Map<UUID, Long>> OWED = new WeakHashMap<>();

	private BloodShards() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BloodShards::tick);
		// the next blow on one a shard marked gives its thrower blood back
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			Map<UUID, Long> owed = OWED.get(victim);
			if (shooting || owed == null || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer by) || source.getDirectEntity() != by) {
				return;
			}
			Long until = owed.get(by.getUUID());
			if (until != null && victim.level().getGameTime() <= until) {
				owed.remove(by.getUUID());
				VampireBlood.add(by, BACK_MB);
				if (victim.level() instanceof ServerLevel level) {
					Fx.burst(level, by.getX(), by.getY(0.6), by.getZ(), NightForms.BLOOD, 0.8F, 6, 0.3, 0.3, 0.3);
					level.playSound(null, by.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHARDS.clear();
			READY.clear();
			NO_HEAL.clear();
			OWED.clear();
		});
	}

	/** Whether {@code e} cannot heal now (read by the heal mixin). */
	public static boolean unhealing(LivingEntity e) {
		Long until = NO_HEAL.get(e);
		return until != null && e.level().getGameTime() < until;
	}

	/** The move off the bar: whether it went. */
	public static boolean cast(ServerPlayer p) {
		if (!VampireBlood.full(p) || !(p.level() instanceof ServerLevel level) || PowersCompat.snuffed(p) || p.isPassenger() || !NightForms.form(p).isEmpty()) {
			return false;
		}
		long now = level.getGameTime();
		if (now < READY.getOrDefault(p.getUUID(), 0L)) {
			return false;
		}
		// as many shards as the reserve can pay for above what they must keep, up to the fan
		int shards = Math.min(FAN, (VampireBlood.get(p) - KEEP_MB) / SHARD_MB);
		if (shards < 1 && !p.isCreative()) {
			VampireBlood.lack(p);
			return false;
		}
		shards = Math.max(1, shards);
		if (!VampireBlood.pay(p, SHARD_MB * shards)) {
			return false;
		}
		READY.put(p.getUUID(), now + COOLDOWN);
		Vec3 eye = p.getEyePosition().add(0.0, -0.2, 0.0);
		for (int i = 0; i < shards; i++) {
			double offset = shards == 1 ? 0.0 : (i - (shards - 1) / 2.0) / ((shards - 1) / 2.0) * SPREAD;
			Vec3 dir = turned(p.getLookAngle(), Math.toRadians(offset));
			SHARDS.add(new Shard(p.getUUID(), eye.add(dir.scale(0.6)), dir.scale(SPEED)));
		}
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.1F, 0.5F);
		return true;
	}

	/** {@code v} turned about the vertical by {@code angle} radians. */
	private static Vec3 turned(Vec3 v, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	private static void tick(MinecraftServer server) {
		if (SHARDS.isEmpty()) {
			return;
		}
		for (Iterator<Shard> it = SHARDS.iterator(); it.hasNext();) {
			Shard s = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
			if (owner == null || !owner.isAlive() || !(owner.level() instanceof ServerLevel level) || s.left-- <= 0) {
				it.remove();
				continue;
			}
			Vec3 next = s.at.add(s.velocity);
			var block = level.clip(new ClipContext(s.at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
			LivingEntity struck = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.at, end).inflate(0.5), e -> NightTargets.target(owner, e))) {
				var in = e.getBoundingBox().inflate(0.15).clip(s.at, end);
				if (in.isPresent() && in.get().distanceToSqr(s.at) < best) {
					best = in.get().distanceToSqr(s.at);
					struck = e;
				}
			}
			Fx.stroke(level, s.at, end, 0.1F, NightForms.BLOOD);
			if (struck != null) {
				hit(owner, level, struck, s);
				it.remove();
			} else if (block.getType() != HitResult.Type.MISS) {
				Fx.burst(level, end.x, end.y, end.z, NightForms.BLOOD, 0.6F, 4, 0.1, 0.1, 0.1);
				it.remove();
			} else {
				s.at = end;
			}
		}
	}

	private static void hit(ServerPlayer owner, ServerLevel level, LivingEntity e, Shard s) {
		e.setInvulnerableTime(0);
		boolean hurt;
		shooting = true;
		try {
			hurt = e.hurtServer(level, owner.damageSources().playerAttack(owner), NightTargets.harm(owner, DAMAGE));
		} finally {
			shooting = false;
		}
		if (hurt) {
			long now = level.getGameTime();
			NO_HEAL.put(e, now + NO_HEAL_TICKS);
			OWED.computeIfAbsent(e, k -> new HashMap<>()).put(owner.getUUID(), now + BACK_TICKS);
		}
		Fx.burst(level, s.at.x, s.at.y, s.at.z, NightForms.BLOOD, 0.9F, 6, 0.2, 0.2, 0.2);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.9F, 0.6F);
	}
}
