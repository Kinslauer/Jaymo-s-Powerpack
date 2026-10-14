package com.jaymospowers.client.power.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * How a web line hangs, end to end, so it reads as a real elastic line rather than a rod: slack line sags under its
 * own weight, a web snakes through the air as it shoots out, a line that sticks (or is caught taut after hanging
 * slack) twangs and settles, and an over-stretched line hums and thins. Our own take on grappling-rope feel.
 */
final class WebLineShape {
	// ===== the knobs ================================================================================================
	/** Deepest a slack line hangs below its straight path (blocks). */
	static final double SAG_MAX = 6.0;
	/**
	 * Shooting out, the web snakes: {@code WAVE_AMP} blocks across (fading as it reaches), {@code WAVE_WAVES} waves along
	 * it, travelling at {@code WAVE_SPEED} radians a tick.
	 */
	static final double WAVE_AMP = 0.35, WAVE_WAVES = 2.0, WAVE_SPEED = 1.6;
	/**
	 * Sticking, or caught taut after hanging slack, the line twangs: {@code TWANG_AMP} blocks (more on a long line, up
	 * to {@code TWANG_LENGTH_MAX} times), dying away over about {@code TWANG_DECAY} ticks, at {@code TWANG_FREQ} radians a tick.
	 */
	static final double TWANG_AMP = 0.18, TWANG_DECAY = 5.0, TWANG_FREQ = 2.4, TWANG_LENGTH_MAX = 1.6;
	/** A line that hung at least this slack (blocks) and then went taut twangs as it catches you. */
	static final double SNAP_SLACK = 0.6;
	/** An over-stretched line hums (blocks across, radians a tick) and thins (share per block stretched, up to the max). */
	static final double HUM_AMP = 0.035, HUM_FREQ = 9.0;
	static final double STRETCH_THIN = 0.25, STRETCH_THIN_MAX = 0.4;
	// ================================================================================================================

	/** A line's shape: the point at {@code t} (0 where it starts, 1 where it sticks). */
	interface Shape {
		Vec3 at(double t);
	}

	private WebLineShape() {
	}

	static Shape straight(Vec3 from, Vec3 to) {
		return t -> from.lerp(to, t);
	}

	/**
	 * The shape of a line from {@code from} to {@code to}: {@code slack} blocks of extra line sagging, {@code stretch}
	 * blocks over its length (humming), {@code wave} (0-1) still snaking out, {@code twang} (0-1) of a twang, at
	 * {@code time} (game ticks); {@code seed} keeps two lines from moving in step.
	 */
	static Shape of(Vec3 from, Vec3 to, double slack, double stretch, double wave, double twang, double time, int seed) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-3 || slack <= 0.0 && stretch <= 0.0 && wave <= 0.0 && twang <= 0.0) {
			return straight(from, to);
		}
		Vec3 dir = delta.scale(1.0 / length);
		Vec3 across = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 across2 = dir.cross(across).normalize();
		// gravity, less what pulls along the line: the way a slack line falls
		Vec3 down = new Vec3(0.0, -1.0, 0.0).add(dir.scale(dir.y));
		double downLength = down.length();
		down = downLength < 0.05 ? across : down.scale(1.0 / downLength);
		// a line (length + slack) hung between points (length) apart dips by about sqrt(3/8 × length × slack)
		double sag = slack <= 0.0 ? 0.0 : Math.min(SAG_MAX, Math.sqrt(0.375 * length * slack)) * Math.min(1.0, 0.3 + downLength);
		double snake = wave * WAVE_AMP * Math.min(1.0, length / 4.0);
		double ring = twang * TWANG_AMP * Math.min(TWANG_LENGTH_MAX, length / 8.0);
		double hum = Math.min(1.0, stretch) * HUM_AMP;
		double phase = (seed & 0xFF) * 0.37;
		double ringA = Math.cos(time * TWANG_FREQ + phase) * ring, ringB = Math.sin(time * TWANG_FREQ * 1.3 + phase) * ring * 0.6;
		double humA = Math.sin(time * HUM_FREQ + phase) * hum;
		Vec3 sagDir = down, side = across, side2 = across2;
		return t -> {
			double belly = Math.sin(Math.PI * t);
			Vec3 p = from.add(delta.scale(t));
			if (sag > 0.0) {
				p = p.add(sagDir.scale(4.0 * sag * t * (1.0 - t)));
			}
			if (snake > 0.0) {
				// a wave running out along the line toward the tip
				p = p.add(side.scale(snake * belly * Math.sin(t * WAVE_WAVES * Math.PI * 2.0 - time * WAVE_SPEED + phase)));
			}
			if (ring != 0.0) {
				p = p.add(side.scale(ringA * belly)).add(side2.scale(ringB * belly));
			}
			if (hum != 0.0) {
				p = p.add(side2.scale(humA * belly));
			}
			return p;
		};
	}

	/** How much thinner a line stretched {@code stretch} blocks is drawn (a share of its width to keep). */
	static float thin(double stretch) {
		return (float)(1.0 - Math.min(STRETCH_THIN_MAX, Math.max(0.0, stretch) * STRETCH_THIN));
	}

	/** The twang a line has {@code age} ticks after it stuck (0-1). */
	static double stuckTwang(double age) {
		return age < 0.0 ? 0.0 : Math.exp(-age / TWANG_DECAY);
	}

	private static final class Taut {
		double slack;
		double snappedAt = -1.0E9;
		double strength;
		long seen;
	}

	private static final Map<Long, Taut> TAUT = new HashMap<>();
	private static long lastSweep;

	/**
	 * The twang (0-1) line {@code key} has from being caught taut after hanging slack: it's {@code slack} blocks slack
	 * now, at {@code time}. Call once a frame per line.
	 */
	static double caughtTwang(long key, double slack, double time) {
		long now = System.nanoTime();
		Taut taut = TAUT.computeIfAbsent(key, k -> new Taut());
		if (now - taut.seen > 250_000_000L) {
			// a new line: it hasn't been slack yet
			taut.slack = 0.0;
		}
		taut.seen = now;
		if (slack > 0.05) {
			taut.slack = Math.max(taut.slack, slack);
		} else if (taut.slack > SNAP_SLACK) {
			taut.snappedAt = time;
			taut.strength = Math.min(1.0, taut.slack / 3.0);
			taut.slack = 0.0;
		} else {
			taut.slack = 0.0;
		}
		if (now - lastSweep > 2_000_000_000L) {
			lastSweep = now;
			for (Iterator<Taut> it = TAUT.values().iterator(); it.hasNext(); ) {
				if (now - it.next().seen > 2_000_000_000L) {
					it.remove();
				}
			}
		}
		return taut.strength * stuckTwang(time - taut.snappedAt);
	}
}
