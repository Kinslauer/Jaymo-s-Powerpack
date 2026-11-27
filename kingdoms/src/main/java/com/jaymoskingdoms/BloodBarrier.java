package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightForms;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Blood barrier (Night's Blood, patch 4). Andy's plan: "Three rings of blood circle you for a while, each soaking one blow,
 * and a heavy blow breaks two. Costs mB." A move on the skill bar ({@code night_barrier}). Each ring circles at its own height
 * and tilt; a blow from a creature, a shot or an explosion (never fire, a fall or hunger) is soaked whole by one ring, or by
 * two if it is a heavy one ({@link #HEAVY}); with the last gone the barrier is spent. It ends by itself after
 * {@link #TICKS}.
 */
public final class BloodBarrier {
	/** What it costs (mB), how long it lasts and how long they wait (ticks), the rings, and the damage that breaks two. */
	static final int COST_MB = 350, TICKS = 600, COOLDOWN = 400, RINGS = 3;
	static final float HEAVY = 8.0F;
	/** How far from them the rings circle (blocks), their heights as shares of the body, and how fast they turn (radians a tick). */
	static final double RADIUS = 1.1, TURN = 0.22;
	static final double[] HEIGHTS = {0.2, 0.55, 0.9};

	private static final class State {
		int rings = RINGS;
		final long until;

		State(long until) {
			this.until = until;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private BloodBarrier() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p) || !soak(p, source, amount));
		ServerTickEvents.END_SERVER_TICK.register(BloodBarrier::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			READY.clear();
		});
	}

	/** Rings left round {@code p}. */
	public static int rings(ServerPlayer p) {
		State s = STATES.get(p.getUUID());
		return s == null ? 0 : s.rings;
	}

	/** The move off the bar: whether it went. */
	public static boolean cast(ServerPlayer p) {
		if (!VampireBlood.full(p) || !(p.level() instanceof ServerLevel level) || PowersCompat.snuffed(p)) {
			return false;
		}
		long now = level.getGameTime();
		if (now < READY.getOrDefault(p.getUUID(), 0L) || STATES.containsKey(p.getUUID())) {
			return false;
		}
		if (!VampireBlood.pay(p, COST_MB)) {
			return false;
		}
		STATES.put(p.getUUID(), new State(now + TICKS));
		READY.put(p.getUUID(), now + TICKS + COOLDOWN);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		Fx.ring(level, p.position().add(0.0, 0.2, 0.0), 2.5F, NightForms.BLOOD);
		return true;
	}

	/** Whether the barrier soaks this blow on {@code p} (which then does nothing). */
	private static boolean soak(ServerPlayer p, DamageSource source, float amount) {
		State s = STATES.get(p.getUUID());
		if (s == null || amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| !(source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE))) {
			return false;
		}
		int broken = Math.min(s.rings, amount >= HEAVY ? 2 : 1);
		s.rings -= broken;
		if (p.level() instanceof ServerLevel level) {
			Vec3 at = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0);
			Fx.burst(level, at.x, at.y, at.z, NightForms.BLOOD, 1.2F, 10 * broken, 0.5, 0.6, 0.5);
			level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
			if (s.rings <= 0) {
				STATES.remove(p.getUUID());
				Fx.blast(level, at, 1.6F, NightForms.BLOOD);
			}
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, State> e : new java.util.ArrayList<>(STATES.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			State s = e.getValue();
			if (p == null || !p.isAlive() || !(p.level() instanceof ServerLevel level) || level.getGameTime() >= s.until) {
				STATES.remove(e.getKey());
				continue;
			}
			long t = level.getGameTime();
			if (t % 2 != 0) {
				continue;
			}
			for (int i = 0; i < s.rings; i++) {
				double angle = t * TURN + i * (Math.PI * 2.0 / RINGS);
				double height = p.getBbHeight() * HEIGHTS[i % HEIGHTS.length];
				Vec3 at = p.position().add(Math.cos(angle) * RADIUS, height, Math.sin(angle) * RADIUS);
				Fx.orb(level, at, 0.22F, NightForms.BLOOD, 4);
				level.sendParticles(Fx.mote(NightForms.BLOOD, 0.6F), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}
}
