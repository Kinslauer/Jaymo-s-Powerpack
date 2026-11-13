package com.jaymospowers.client.tundra;

import com.jaymospowers.tundra.world.TundraWorld;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * The tundra's grass fades as a gradient. Both biomes carry one flat colour in their data; here the colour of a blade of
 * grass in either of them is worked out from how much tundra lies round it: all the tundra's own colour deep inside it,
 * all the woodland's in the outer rim of the lichen woodland, and every shade between through the woodland toward the
 * tundra. (The game's biome blending still softens the edges where either meets some other land.) Applied by
 * {@code TundraGrassMixin} before Kitchen's seasonal tint, so the year's colours sit on top of the gradient.
 *
 * <p>How much tundra is round a spot is read from the biomes themselves, sampled on rings round it. Chunks that aren't
 * loaded yet count for nothing, and a spot looked at before its surroundings were all loaded is not remembered.
 */
public final class TundraGrass {
	// ---- tuning ----
	/** The two biomes' own grass colours (keep in step with {@code worldgen/biome/tundra.json} and {@code lichen_woodland.json}). */
	static final int TUNDRA = 0xA7A763, WOODLAND = 0x94AB78;
	/** Rings sampled round a spot (blocks), and the points on each; the spot itself counts as much as a ring. */
	static final int[] RINGS = {10, 20, 30, 44};
	static final int POINTS = 8;
	/** The height the biomes are read at, above sea level (the surface biomes are the same up and down here). */
	static final int LOOK_AT = 12;
	/** Spots are remembered in squares of this many blocks, for this many ticks. */
	static final int CELL = 4, REMEMBER_TICKS = 600;

	private static final ConcurrentHashMap<Long, Integer> KNOWN = new ConcurrentHashMap<>();
	private static volatile Biome tundra, woodland;
	private static volatile ClientLevel seen;
	private static int clock;

	private TundraGrass() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forget());
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (++clock % REMEMBER_TICKS == 0) {
				KNOWN.clear();
			}
		});
	}

	private static void forget() {
		KNOWN.clear();
		tundra = woodland = null;
		seen = null;
	}

	/** {@code colour} as the grass of {@code biome} at ({@code x}, {@code z}) has it; {@code colour} itself for any other biome. */
	public static int grass(Biome biome, double x, double z, int colour) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return colour;
		}
		if (seen != level) {
			var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
			tundra = biomes.getValue(TundraWorld.TUNDRA);
			woodland = biomes.getValue(TundraWorld.LICHEN_WOODLAND);
			seen = level;
			KNOWN.clear();
		}
		if (biome != tundra && biome != woodland) {
			return colour;
		}
		int bx = Mth.floor(x), bz = Mth.floor(z);
		long key = (long)Math.floorDiv(bx, CELL) << 32 | Math.floorDiv(bz, CELL) & 0xFFFFFFFFL;
		Integer known = KNOWN.get(key);
		if (known != null) {
			return known;
		}
		float[] share = new float[2];
		boolean whole = share(level, bx, bz, share);
		float t = Mth.clamp(share[1] > 0.0F ? share[0] / share[1] : biome == tundra ? 1.0F : 0.0F, 0.0F, 1.0F);
		t = t * t * (3.0F - 2.0F * t);
		int result = mix(WOODLAND, TUNDRA, t);
		if (whole) {
			KNOWN.put(key, result);
		}
		return result;
	}

	/** Fills {@code into} with the weight of tundra and the weight of everything sampled round the spot; false if some of the ring wasn't loaded. */
	private static boolean share(ClientLevel level, int x, int z, float[] into) {
		boolean whole = true;
		float tundraWeight = 0.0F, landWeight = 0.0F;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int y = level.getSeaLevel() + LOOK_AT;
		// the spot itself weighs as much as a ring does, and each ring a little less than the one inside it
		for (int ring = -1; ring < RINGS.length; ring++) {
			int points = ring < 0 ? 1 : POINTS;
			float weight = 1.0F / (1.0F + Math.max(0, ring) * 0.35F) / points;
			for (int i = 0; i < points; i++) {
				double angle = Mth.TWO_PI * (i + (ring & 1) * 0.5) / POINTS;
				int sx = ring < 0 ? x : x + Mth.floor(Math.cos(angle) * RINGS[ring]);
				int sz = ring < 0 ? z : z + Mth.floor(Math.sin(angle) * RINGS[ring]);
				if (!level.hasChunk(sx >> 4, sz >> 4)) {
					whole = false;
					continue;
				}
				Biome there = level.getBiome(at.set(sx, y, sz)).value();
				landWeight += weight;
				if (there == tundra) {
					tundraWeight += weight;
				}
			}
		}
		into[0] = tundraWeight;
		into[1] = landWeight;
		return whole;
	}

	private static int mix(int from, int to, float t) {
		int r = Mth.floor(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Mth.floor(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Mth.floor(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
