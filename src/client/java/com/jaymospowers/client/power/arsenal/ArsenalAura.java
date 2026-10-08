package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The Void Arsenal's aura, spawned by every client for every corrupted arsenal user in view (from the synced power and
 * Fate state): purple motes drifting in toward the body, a faint black haze about it, and now and then a dark pulse.
 */
final class ArsenalAura {
	/** Players further than this from the camera get no aura. */
	private static final double VIEW_DISTANCE = 48.0;
	/** Motes every this many ticks (per player), this many at a time, starting this far out. */
	private static final int MOTE_INTERVAL = 2;
	private static final int MOTES = 2;
	private static final double MOTE_RADIUS = 1.3;
	/** Inward drift speed; a reverse-portal mote accelerates along it over its ~life. */
	private static final double MOTE_SPEED = 0.045;
	private static final int HAZE_INTERVAL = 4;
	/** On average one dark pulse every this many ticks. */
	private static final int PULSE_CHANCE = 110;
	private static final int PULSE_POINTS = 18;

	private ArsenalAura() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || minecraft.player == null) {
			return;
		}
		long time = level.getGameTime();
		RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			if (!ArsenalVoid.isVoid(player) || player.isInvisible() || player.isSpectator() || !player.isAlive()
				|| player.distanceToSqr(minecraft.player) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean self = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
			// in first person, keep it below the eyes so it doesn't fog your own view
			double top = self ? 1.0 : 2.0;
			long phase = time + player.getId();
			double cx = player.getX(), cy = player.getY() + 1.0, cz = player.getZ();
			if (phase % MOTE_INTERVAL == 0) {
				for (int i = 0; i < MOTES; i++) {
					double a = random.nextDouble() * Mth.TWO_PI;
					double r = MOTE_RADIUS * (0.7 + random.nextDouble() * 0.5);
					double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
					double py = player.getY() + random.nextDouble() * top;
					double oy = py - cy;
					level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + ox, py, cz + oz, -ox * MOTE_SPEED, -oy * MOTE_SPEED, -oz * MOTE_SPEED);
				}
			}
			if (phase % HAZE_INTERVAL == 0) {
				double hx = cx + (random.nextDouble() - 0.5) * 0.9;
				double hz = cz + (random.nextDouble() - 0.5) * 0.9;
				double hy = player.getY() + random.nextDouble() * top * 0.9;
				level.addParticle(ParticleTypes.SMOKE, hx, hy, hz, 0.0, 0.01, 0.0);
			}
			if (random.nextInt(PULSE_CHANCE) == 0) {
				// a dark pulse: a ring of ink thrown outward at the waist
				for (int i = 0; i < PULSE_POINTS; i++) {
					double a = i * Mth.TWO_PI / PULSE_POINTS;
					level.addParticle(ParticleTypes.SQUID_INK, cx + Math.cos(a) * 0.4, player.getY() + 0.8, cz + Math.sin(a) * 0.4,
						Math.cos(a) * 0.18, 0.0, Math.sin(a) * 0.18);
				}
				level.playLocalSound(cx, cy, cz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.25F, 0.5F, false);
			}
		}
	}
}
