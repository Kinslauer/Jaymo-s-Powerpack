package com.jaymospowers.power.resonance;

import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Hearing. V, Echolocation: every creature within {@code RANGE} is outlined through walls for {@code TICKS}, on the
 * user's screen only (the outline is drawn by their client); everyone nearby sees the ping ripple out and hears a faint
 * chime. The Tuning Fork does the same at its own reach. Heavy hearing: a hit from something you can't see flashes its
 * direction on your screen.
 */
final class ResonanceEcho {
	static final double RANGE = 32.0;
	static final int TICKS = 80;
	/** The ripple: rings this far apart, one a tick, out to this radius. */
	static final double RIPPLE_STEP = 1.5;
	static final double RIPPLE_RADIUS = 9.0;
	/** Heavy hearing: an attacker outside this half-angle of your view counts as unseen. */
	static final double VIEW_COS = Math.cos(Math.toRadians(60.0));

	private record Ripple(ServerLevel level, Vec3 center, double maxRadius, int[] step) {
	}

	private static final List<Ripple> RIPPLES = new ArrayList<>();

	private ResonanceEcho() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ResonanceEcho::tickRipples);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !Powers.has(player, ResonancePower.INSTANCE) || !player.isAlive()) {
				return;
			}
			Entity attacker = source.getEntity();
			if (!(attacker instanceof LivingEntity) || attacker == player) {
				return;
			}
			Vec3 to = attacker.getEyePosition().subtract(player.getEyePosition());
			boolean inView = to.lengthSqr() > 1.0E-6 && to.normalize().dot(player.getViewVector(1.0F)) >= VIEW_COS;
			if (!inView || !player.hasLineOfSight(attacker)) {
				ResonanceNet.send(player, new ResonanceNet.Heard(attacker.getX(), attacker.getY(), attacker.getZ()));
			}
		});
	}

	/** V: the ping. */
	static boolean ping(ServerPlayer player) {
		listen(player, RANGE, TICKS);
		ServerLevel level = player.level();
		RIPPLES.add(new Ripple(level, player.position().add(0.0, 0.1, 0.0), RIPPLE_RADIUS, new int[] {0}));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.4F, 1.6F);
		return true;
	}

	/** Every creature within {@code radius} is outlined on {@code player}'s screen for {@code ticks}. */
	static void listen(ServerPlayer player, double radius, int ticks) {
		send(player, around(player, radius), ticks, ResonanceNet.Sense.ECHO);
	}

	static List<LivingEntity> around(ServerPlayer player, double radius) {
		return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(player) <= radius * radius);
	}

	static void send(ServerPlayer player, List<LivingEntity> heard, int ticks, int kind) {
		int[] ids = new int[Math.min(heard.size(), 1024)];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = heard.get(i).getId();
		}
		ResonanceNet.send(player, new ResonanceNet.Sense(ids, ticks, kind));
	}

	/** A sound ripple anyone can see, spreading out from {@code center}. */
	static void ripple(ServerLevel level, Vec3 center, double radius) {
		RIPPLES.add(new Ripple(level, center, radius, new int[] {0}));
	}

	private static void tickRipples(MinecraftServer server) {
		for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext();) {
			Ripple ripple = it.next();
			double radius = ++ripple.step[0] * RIPPLE_STEP;
			if (radius > ripple.maxRadius) {
				it.remove();
				continue;
			}
			int points = (int)(radius * 6.0);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2.0 * i / points;
				ripple.level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, ripple.center.x + Math.cos(a) * radius, ripple.center.y,
					ripple.center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	static void clear() {
		RIPPLES.clear();
	}
}
