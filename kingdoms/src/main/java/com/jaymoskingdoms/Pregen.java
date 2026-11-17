package com.jaymoskingdoms;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The land Chunky has made ahead (Andy, 2026-11-17: "do both fixes", after the Deck lagged and crashed generating land round
 * players who started outside it). Read from Chunky's own saved task ({@code config/chunky/tasks/<namespace>/<path>.properties},
 * the one the deploy's "Pregen" starts, kept while it's paused): its centre, radius and shape, and how many chunks it has done,
 * so a pre-generation still under way counts only what it has made so far (its regions go out from the centre). Without a
 * task for a world (no Chunky, or none run there), nothing is known and {@link #inside} says yes everywhere.
 */
final class Pregen {
	/** Kept this far inside the made land's edge (blocks): a player's view (the Deck's 8 chunks) shouldn't reach past it. */
	static final int MARGIN = 128;
	/** The task is read again after this long (ms), as a running pre-generation grows. */
	static final long READ_EVERY_MS = 60_000L;

	/** The made land of one world: a square (half-width) or a circle (radius) round its centre, less {@link #MARGIN}. */
	record Area(double x, double z, double reach, boolean square) {
		boolean inside(int bx, int bz) {
			double dx = bx + 0.5 - this.x, dz = bz + 0.5 - this.z;
			return this.square ? Math.abs(dx) <= this.reach && Math.abs(dz) <= this.reach : dx * dx + dz * dz <= this.reach * this.reach;
		}
	}

	private static @Nullable Identifier readFor;
	private static @Nullable Area read;
	private static long readAt;

	private Pregen() {
	}

	/** The made land of {@code level}, or null if none is known. */
	static synchronized @Nullable Area of(ServerLevel level) {
		Identifier world = level.dimension().identifier();
		long now = System.currentTimeMillis();
		if (!world.equals(readFor) || now - readAt > READ_EVERY_MS) {
			readFor = world;
			readAt = now;
			read = load(world);
		}
		return read;
	}

	/** Whether {@code pos} is in {@code level}'s made land (true when none is known). */
	static boolean inside(ServerLevel level, BlockPos pos) {
		Area area = of(level);
		return area == null || area.inside(pos.getX(), pos.getZ());
	}

	private static @Nullable Area load(Identifier world) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("chunky").resolve("tasks").resolve(world.getNamespace())
			.resolve(world.getPath() + ".properties");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		Properties p = new Properties();
		try (Reader in = Files.newBufferedReader(file)) {
			p.load(in);
			double radius = Double.parseDouble(p.getProperty("radius", "0"));
			boolean square = "square".equals(p.getProperty("shape", "square"));
			String chunks = p.getProperty("chunks");
			if (chunks != null) {
				// (the chunks done so far, as a square or circle of land round the centre)
				double done = Double.parseDouble(chunks);
				radius = Math.min(radius, square ? 8.0 * Math.sqrt(done) : 16.0 * Math.sqrt(done / Math.PI));
			}
			double reach = radius - MARGIN;
			if (reach <= 0.0) {
				return null;
			}
			return new Area(Double.parseDouble(p.getProperty("center-x", "0")), Double.parseDouble(p.getProperty("center-z", "0")), reach, square);
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Couldn't read Chunky's task for {}", world, e);
			return null;
		}
	}
}
