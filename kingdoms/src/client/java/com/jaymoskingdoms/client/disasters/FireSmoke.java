package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Storms;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy smoke over big fires, on this side only (Andy, 2026-11-19: a burning forest threw almost no smoke). Any fire
 * counts, a wildfire ({@code Calamities}), an imp's blaze or a player's, but only a big one smokes: a campfire or a lone
 * burning block stays as it was.
 * <ul>
 * <li>Each tick a few columns round the camera are looked at (the top of each, where a burning canopy's fire sits).
 * What burns is tallied in 8x8 cells, which cool off on their own, so each cell knows about how many fires it holds.</li>
 * <li>A cell with enough fire puffs big dark plumes ({@link StormParticle#SMOKE}) that rise, swell, pale and lean
 * downwind as they climb; the bigger the blaze round it, the bigger the puffs and the more of them. All the plumes
 * together share one budget a tick (a third of it in low-end mode, {@code /lowend}), so a whole forest burning costs
 * no more than a few hundred puffs.</li>
 * <li>Near a big blaze and out under the sky, the air fills with smoke: the fog closes in and browns
 * ({@link #haze}, read by {@code SkiesFogMixin} with the weather's other fogs).</li>
 * </ul>
 * Not in the Nether: its roof hides the columns' tops, and its fires are everywhere.
 */
public final class FireSmoke {
	/** Columns looked at a tick (and in low-end mode), and how far round the camera (blocks, each way). */
	static final int SAMPLES = 96, LOW_SAMPLES = 40, RANGE = 112;
	/** Cells are 2^CELL blocks across. */
	static final int CELL = 3;
	/** How much of a cell's tally is left after a tick: it forgets a fire that has gone out in about ten seconds. */
	static final float COOL = 0.995F;
	/** A cell smokes once it holds about this many fires; one under half a fire is forgotten. */
	static final float SMOKES_FROM = 6.0F, FORGET = 0.5F;
	/** Puffs a tick for each fire in a cell, and the most a tick for every plume together (normal, low-end). */
	static final float PUFFS_PER_FIRE = 0.012F, BUDGET = 1.2F, LOW_BUDGET = 0.4F;
	/** A puff's size (blocks) for a small blaze and for one of BIG fires or more round it (its cell and the 8 next to it). */
	static final float SIZE_SMALL = 5.0F, SIZE_BIG = 13.0F, BIG = 120.0F;
	/** How long a puff lasts (ticks), how fast it starts rising (blocks a tick), and how much it swells by the end (times). */
	static final int LIFE_MIN = 300, LIFE_MAX = 460;
	static final float RISE_MIN = 0.1F, RISE_MAX = 0.16F, SWELL = 1.6F;
	/** Its grey (by day) at the foot and the top, and how thick it is at the foot. */
	static final float GREY_FOOT = 0.22F, GREY_TOP = 0.46F, ALPHA = 0.55F;
	/** How fast it leans with the wind once aloft, as a share of the wind's own drift. */
	static final float LEAN = 1.5F;
	/** The smoky air: fires within HAZE_RANGE blocks count (the nearer the more), HAZE_FULL of them at the camera's
	 * door make it full; how fast it comes and goes (a tick); indoors it's this much of itself. */
	static final double HAZE_RANGE = 48.0;
	static final float HAZE_FULL = 60.0F, HAZE_EASE = 0.004F, INDOORS = 0.35F;
	/** How far you can see in full smoke (blocks), and its colour by day. {@code SkiesFogMixin} reads both. */
	public static final float HAZE_SIGHT = 36.0F;
	public static final float[] HAZE_COLOUR = {0.47F, 0.39F, 0.33F};
	/** How thick the smoky air is at the camera, 0..1 (eased). */
	public static float haze;
	/** How often low-end mode is asked about (ticks). */
	static final int LOW_CHECK = 40;

	private static final class Cell {
		/** Fires found, each worth the columns one sample stands for, cooling every tick. */
		float tally;
		/** The height of the last fire found in it, and the puffs owed. */
		int top;
		float carry;

		float fires() {
			return this.tally * (1.0F - COOL);
		}
	}

	private static final Long2ObjectOpenHashMap<Cell> CELLS = new Long2ObjectOpenHashMap<>();
	private static boolean low;
	private static int clock;

	private FireSmoke() {
	}

	static void clear() {
		CELLS.clear();
		haze = 0.0F;
	}

	static void tick(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random) {
		if (level.dimension() == Level.NETHER) {
			if (!CELLS.isEmpty()) {
				CELLS.clear();
			}
			haze = Math.max(0.0F, haze - HAZE_EASE * 4.0F);
			return;
		}
		if (clock++ % LOW_CHECK == 0) {
			low = CloudDecks.lowEnd();
		}
		look(level, eye, random);
		float near = cool(eye);
		boolean open = level.canSeeSky(BlockPos.containing(eye));
		float target = Mth.clamp(near / HAZE_FULL, 0.0F, 1.0F) * (open ? 1.0F : INDOORS);
		haze = haze < target ? Math.min(target, haze + HAZE_EASE) : Math.max(target, haze - HAZE_EASE);
		if (!CELLS.isEmpty()) {
			puff(minecraft, level, random);
		}
	}

	/** Looks at a few columns' tops round the camera and tallies the fires found. */
	private static void look(ClientLevel level, Vec3 eye, RandomSource random) {
		int samples = low ? LOW_SAMPLES : SAMPLES;
		float worth = (float)((RANGE * 2 + 1) * (RANGE * 2 + 1)) / samples;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int cx = Mth.floor(eye.x), cz = Mth.floor(eye.z);
		for (int i = 0; i < samples; i++) {
			int x = cx + random.nextInt(RANGE * 2 + 1) - RANGE, z = cz + random.nextInt(RANGE * 2 + 1) - RANGE;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (!level.getBlockState(at.set(x, y, z)).is(BlockTags.FIRE)) {
				continue;
			}
			Cell cell = CELLS.computeIfAbsent(BlockPos.asLong(x >> CELL, 0, z >> CELL), k -> new Cell());
			cell.tally += worth;
			cell.top = y;
		}
	}

	/** Every cell cools, the cold or far ones are forgotten; how many fires are near the camera (the nearer the more). */
	private static float cool(Vec3 eye) {
		float near = 0.0F;
		double far = (RANGE + 16.0) * (RANGE + 16.0);
		for (Iterator<Long2ObjectMap.Entry<Cell>> it = CELLS.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
			Long2ObjectMap.Entry<Cell> e = it.next();
			Cell cell = e.getValue();
			cell.tally *= COOL;
			double x = ((BlockPos.getX(e.getLongKey()) << CELL) + 4.0) - eye.x, z = ((BlockPos.getZ(e.getLongKey()) << CELL) + 4.0) - eye.z;
			double d2 = x * x + z * z;
			if (cell.fires() < FORGET || d2 > far) {
				it.remove();
				continue;
			}
			if (d2 < HAZE_RANGE * HAZE_RANGE && Math.abs(cell.top - eye.y) < HAZE_RANGE) {
				near += cell.fires() * (float)(1.0 - Math.sqrt(d2) / HAZE_RANGE);
			}
		}
		return near;
	}

	/** Each cell that holds a big enough fire puffs its share of this tick's budget. */
	private static void puff(Minecraft minecraft, ClientLevel level, RandomSource random) {
		if (StormsClient.cloudSprites == null) {
			return;
		}
		float wanted = 0.0F;
		for (Cell cell : CELLS.values()) {
			if (cell.fires() >= SMOKES_FROM) {
				wanted += cell.fires() * PUFFS_PER_FIRE;
			}
		}
		if (wanted <= 0.0F) {
			return;
		}
		float share = Math.min(1.0F, (low ? LOW_BUDGET : BUDGET) / wanted);
		double drift = Storms.clientWindSpeed * LEAN;
		double wx = Math.cos(Storms.clientWindAngle) * drift, wz = Math.sin(Storms.clientWindAngle) * drift;
		for (Long2ObjectMap.Entry<Cell> e : CELLS.long2ObjectEntrySet()) {
			Cell cell = e.getValue();
			if (cell.fires() < SMOKES_FROM) {
				continue;
			}
			cell.carry += cell.fires() * PUFFS_PER_FIRE * share;
			int n = (int)cell.carry;
			cell.carry -= n;
			if (n == 0) {
				continue;
			}
			int gx = BlockPos.getX(e.getLongKey()), gz = BlockPos.getZ(e.getLongKey());
			float blaze = Mth.clamp(round(gx, gz) / BIG, 0.0F, 1.0F);
			for (int i = 0; i < n; i++) {
				double x = (gx << CELL) + random.nextDouble() * (1 << CELL), z = (gz << CELL) + random.nextDouble() * (1 << CELL);
				double y = cell.top + 1.0 + random.nextDouble() * 2.0;
				float size = Mth.lerp(blaze, SIZE_SMALL, SIZE_BIG) * (0.8F + random.nextFloat() * 0.4F);
				int life = Mth.nextInt(random, LIFE_MIN, LIFE_MAX);
				float rise = Mth.lerp(random.nextFloat(), RISE_MIN, RISE_MAX) * (0.85F + 0.3F * blaze);
				minecraft.particleEngine.add(new StormParticle(level, x, y, z, StormsClient.cloudSprites.get(random), StormParticle.SMOKE, 0, size, life,
					GREY_FOOT, GREY_FOOT * 0.95F, GREY_FOOT * 0.92F, ALPHA).smoke(rise, wx, wz, SWELL, GREY_FOOT, GREY_TOP));
			}
		}
	}

	/** How many fires burn in the cell at (gx, gz) and the eight round it. */
	private static float round(int gx, int gz) {
		float sum = 0.0F;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Cell c = CELLS.get(BlockPos.asLong(gx + dx, 0, gz + dz));
				if (c != null) {
					sum += c.fires();
				}
			}
		}
		return sum;
	}
}
