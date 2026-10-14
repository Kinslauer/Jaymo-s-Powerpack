package com.jaymospowers.client.power.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Lines and tendrils don't appear in full: they shoot out from where they start and take time to reach the end, at a
 * speed (blocks a tick) of their own. Tracked per line, by a key, from when it was first drawn.
 */
final class WebGrowth {
	/** A plain web shoots fast; the symbiote's ropes slower; a reaching tendril slowest of all. */
	static final double WEB_SPEED = 5.0, ROPE_SPEED = 2.2;

	private record Entry(Vec3 end, long start, long seen) {
	}

	private static final Map<Long, Entry> LINES = new HashMap<>();
	private static long lastSweep;

	private WebGrowth() {
	}

	/**
	 * How far out (0-1) line {@code key} has got. It starts over when the line is new, or (if {@code moving} is false)
	 * when its end jumps elsewhere: a fresh web.
	 */
	static float fraction(long key, Vec3 end, double length, double speed, boolean moving) {
		long now = System.nanoTime();
		Entry entry = LINES.get(key);
		if (entry == null || now - entry.seen() > 150_000_000L || !moving && entry.end().distanceToSqr(end) > 2.0) {
			entry = new Entry(end, now, now);
		} else {
			entry = new Entry(moving ? entry.end() : end, entry.start(), now);
		}
		LINES.put(key, entry);
		if (now - lastSweep > 2_000_000_000L) {
			lastSweep = now;
			for (Iterator<Entry> it = LINES.values().iterator(); it.hasNext(); ) {
				if (now - it.next().seen() > 2_000_000_000L) {
					it.remove();
				}
			}
		}
		double ticks = (now - entry.start()) / 50_000_000.0;
		return length < 1.0E-3 ? 1.0F : (float)Math.min(1.0, ticks * speed / length);
	}

	/** Ticks since line {@code key} started shooting out (call after {@link #fraction} this frame), or 0 if unknown. */
	static double ticks(long key) {
		Entry entry = LINES.get(key);
		return entry == null ? 0.0 : (System.nanoTime() - entry.start()) / 50_000_000.0;
	}
}
