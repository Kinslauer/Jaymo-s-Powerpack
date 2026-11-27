package com.jaymospowers.noise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * FastNoiseLite (Jordan Peck, MIT; licenses/fastnoiselite) for the hand-made parts of our worldgen, where the game's own
 * noise isn't doing the work: the Aether's cloudbed and cloud lanes, the Pale Moon's craters. One {@link Seeded} is one
 * kind of noise, set up once; {@link Seeded#at} hands out its FastNoiseLite for a world seed, made the first time that
 * seed asks and only read after. FastNoiseLite's GetNoise keeps no state, so worldgen threads share one safely
 * (DomainWarp writes only to the vector it is given).
 */
public final class Noises {
	private Noises() {
	}

	/** A kind of noise: its setup, and the salt that keeps it apart from other kinds on the same world seed. */
	public static final class Seeded {
		private final long salt;
		private final Consumer<FastNoiseLite> setup;
		private final Map<Long, FastNoiseLite> made = new ConcurrentHashMap<>();

		public Seeded(long salt, Consumer<FastNoiseLite> setup) {
			this.salt = salt;
			this.setup = setup;
		}

		/** This noise for a world seed (and an extra salt, for several layers of the same kind). */
		public FastNoiseLite at(long seed, int layer) {
			long key = seed ^ salt * 0x9E3779B97F4A7C15L ^ layer * 0xC2B2AE3D27D4EB4FL;
			return made.computeIfAbsent(key, k -> {
				FastNoiseLite noise = new FastNoiseLite((int)(k ^ k >>> 32));
				setup.accept(noise);
				return noise;
			});
		}
	}
}
