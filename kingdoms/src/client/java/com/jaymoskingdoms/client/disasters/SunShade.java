package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.astronomy.SunMoon;
import com.jaymoskingdoms.client.DarkNights;
import com.jaymoskingdoms.disasters.Storms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sun shadows and water caustics for a world no shader pack is drawing (Andy: "you can build those"; ImmersiveVFX's
 * ideas, none of its code or art). Both are laid on the ground round the camera as flat quads in the game's own
 * entity-shadow layer, and both go the moment a shader pack is on, since a pack casts its own.
 * <ul>
 * <li><b>Pixel shadows</b>: the top of each block near you is cut into {@link #CELLS} by {@link #CELLS} squares, and
 * each square looks toward the Sun ({@link SunMoon#sun}): whatever solid stands in the way shades it. So walls, roofs,
 * cliffs and trunks throw hard, blocky shadows that swing round and stretch through the day, and leaves throw a
 * dappled one. Only open ground under the sky gets them (not floors indoors, and not walls), they fade as the Sun
 * nears the horizon and in rain, and there are none at night.</li>
 * <li><b>Caustics</b>: under open water up to {@link #CAUSTIC_DEPTH} deep, a rippling web of light plays over the bed,
 * brightest in the shallows, by day only.</li>
 * </ul>
 * A few columns are looked at again each tick, so a shadow follows the Sun and a changed block within a couple of
 * seconds, at next to no cost.
 */
public final class SunShade {
	/** How far round the camera the ground is shaded (blocks), and the band at its edge it fades out over. */
	static final int RANGE = 14;
	static final float EDGE = 4.0F;
	/** Squares across a block's top; {@link Column#shade} holds them as bits, so 8 at most. */
	static final int CELLS = 8;
	/** How far toward the Sun a square looks for something in the way (blocks along the ground). */
	static final int CAST = 20;
	/** Columns looked at each tick: ones not yet known first, then the rest in turn. */
	static final int COLUMNS_A_TICK = 26;
	/** How far below the top of a column its ground is looked for, and how far above or below the camera it may lie. */
	static final int GROUND_DOWN = 24, GROUND_FROM_EYE = 24;
	/** How dark a shadow is at most, and how much of a leaf canopy's shadow is shade. */
	static final float SHADE = 0.36F, LEAF_SHADE = 0.6F;
	/** The Sun's height (the sine of its altitude) below which there are no shadows, and from which they are full. */
	static final float SUN_FROM = 0.1F, SUN_FULL = 0.32F;
	/** How fast they come and go (per tick), and how far above the block they lie. */
	static final float EASE = 0.02F, LIFT = 1.0F / 128.0F;

	/** Caustics: the deepest water they reach through, how bright they are at most, and how much is left at that depth. */
	static final int CAUSTIC_DEPTH = 10;
	static final float CAUSTIC_LIGHT = 0.55F, CAUSTIC_DEEP = 0.25F;
	/** Their frames, and how long each shows (ticks); one fades into the next. */
	static final int FRAMES = 16, FRAME_TICKS = 3;

	/** How often a shader pack is looked for, and how often far columns are forgotten (ticks). */
	static final int SHADER_CHECK = 40, FORGET_EVERY = 20;

	private static final Identifier WHITE = JaymosKingdoms.id("textures/astronomy/white.png");
	private static final Identifier[] CAUSTICS = new Identifier[FRAMES];
	/** Every column in range, nearest first. */
	private static final int[] OFFSETS;
	private static final int FULL_BRIGHT = 15728880, NONE = Integer.MIN_VALUE;

	static {
		for (int i = 0; i < FRAMES; i++) {
			CAUSTICS[i] = JaymosKingdoms.id("textures/skies/caustics_" + i + ".png");
		}
		int side = RANGE * 2 + 1;
		Integer[] order = new Integer[side * side];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Integer.compare(far(a, side), far(b, side)));
		OFFSETS = new int[order.length];
		for (int i = 0; i < order.length; i++) {
			OFFSETS[i] = order[i];
		}
	}

	private static int far(int index, int side) {
		int x = index % side - RANGE, z = index / side - RANGE;
		return x * x + z * z;
	}

	/** What is known of one column of the world. */
	private static final class Column {
		/** The top of its ground (world y), or NaN where it has none to shade. */
		float top = Float.NaN;
		/** Its shaded squares: bit {@code row * CELLS + cell}, rows along z and cells along x. */
		long shade;
		/** The block under its water whose top the caustics play on, or {@link #NONE}; and how deep that water is. */
		int bed = NONE, deep;
	}

	private static final Long2ObjectOpenHashMap<Column> COLUMNS = new Long2ObjectOpenHashMap<>();
	/** Shadow quads (x0, z0, x1, z1, y each, from {@link #originX} and {@link #originZ}) and caustic quads (x, z, y, strength). */
	private static float[] shadeQuads = new float[0], causticQuads = new float[0];
	private static int shadeCount, causticCount, originX, originZ;
	/** How strongly the shadows and the caustics show, 0..1. */
	private static float strength;
	private static boolean shaders;
	private static int clock, cursor;
	private static final int[] ROOF = new int[CAST + 2];

	private SunShade() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(SunShade::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SunShade::render);
	}

	private static void clear() {
		COLUMNS.clear();
		shadeCount = causticCount = 0;
		strength = 0.0F;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (clock++ % SHADER_CHECK == 0) {
			shaders = DarkNights.shaderPackOn();
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		Vec3 sun = SunMoon.sun(level, eye);
		float rain = Math.max(level.getRainLevel(1.0F), Storms.clientRain);
		float want = shaders || level.dimension() != Level.OVERWORLD ? 0.0F
			: Mth.clamp(((float)sun.y - SUN_FROM) / (SUN_FULL - SUN_FROM), 0.0F, 1.0F) * (1.0F - Mth.clamp(rain, 0.0F, 1.0F));
		strength = strength < want ? Math.min(want, strength + EASE) : Math.max(want, strength - EASE);
		if (shaders || want <= 0.0F && strength <= 0.0F) {
			clear();
			return;
		}
		int cx = Mth.floor(eye.x), cz = Mth.floor(eye.z);
		if (clock % FORGET_EVERY == 0) {
			COLUMNS.long2ObjectEntrySet().removeIf(entry -> Math.abs(x(entry.getLongKey()) - cx) > RANGE || Math.abs(z(entry.getLongKey()) - cz) > RANGE);
		}
		int side = RANGE * 2 + 1, left = COLUMNS_A_TICK;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < OFFSETS.length && left > 0; i++) {
			int x = cx + OFFSETS[i] % side - RANGE, z = cz + OFFSETS[i] / side - RANGE;
			long key = key(x, z);
			if (!COLUMNS.containsKey(key)) {
				COLUMNS.put(key, look(level, at, x, z, eye, sun));
				left--;
			}
		}
		while (left-- > 0) {
			cursor = (cursor + 1) % OFFSETS.length;
			int x = cx + OFFSETS[cursor] % side - RANGE, z = cz + OFFSETS[cursor] / side - RANGE;
			COLUMNS.put(key(x, z), look(level, at, x, z, eye, sun));
		}
		build(cx, cz);
	}

	private static long key(int x, int z) {
		return (long)x << 32 | z & 0xFFFFFFFFL;
	}

	private static int x(long key) {
		return (int)(key >> 32);
	}

	private static int z(long key) {
		return (int)key;
	}

	// ---- looking at the world --------------------------------------------------------------------------------------------

	/** Finds a column's open ground (or its water's bed) and works out which of its squares are in shadow. */
	private static Column look(ClientLevel level, BlockPos.MutableBlockPos at, int x, int z, Vec3 eye, Vec3 sun) {
		Column column = new Column();
		int from = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		for (int y = from; y > from - GROUND_DOWN && y >= level.getMinY(); y--) {
			BlockState state = level.getBlockState(at.set(x, y, z));
			if (state.getFluidState().is(FluidTags.WATER)) {
				bed(level, at, x, y, z, eye, column);
				return column;
			}
			if (state.isAir() || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()) {
				continue;
			}
			// (a thin fall of snow has no collision, but it is the ground you see)
			VoxelShape shape = state.is(Blocks.SNOW) ? state.getShape(level, at) : state.getCollisionShape(level, at);
			if (shape.isEmpty()) {
				continue; // grass, flowers, torches: the ground is under them
			}
			float top = y + (float)shape.max(Direction.Axis.Y);
			if (Math.abs(top - eye.y) > GROUND_FROM_EYE) {
				return column;
			}
			column.top = top;
			column.shade = sun.y > 0.0 ? shade(level, at, x, top, z, sun) : 0L;
			return column;
		}
		return column;
	}

	/** Water from {@code y} down: the caustics lie on the first solid block under it, if it is near enough the surface. */
	private static void bed(ClientLevel level, BlockPos.MutableBlockPos at, int x, int y, int z, Vec3 eye, Column column) {
		for (int deep = 1; deep <= CAUSTIC_DEPTH; deep++) {
			BlockState below = level.getBlockState(at.set(x, y - deep, z));
			if (below.getFluidState().is(FluidTags.WATER)) {
				continue;
			}
			if (below.isSolidRender() && Math.abs(y - deep - eye.y) <= GROUND_FROM_EYE) {
				column.bed = y - deep;
				column.deep = deep;
			}
			return;
		}
	}

	/** The shaded squares of the block top at ({@code x}, {@code top}, {@code z}). The Sun rides east to west, so a square only looks along its own row. */
	private static long shade(ClientLevel level, BlockPos.MutableBlockPos at, int x, float top, int z, Vec3 sun) {
		int way = sun.x >= 0.0 ? 1 : -1;
		// the highest thing still ahead at each step toward the Sun: once a look is above it, it is in the open
		int roof = level.getMinY();
		for (int k = CAST + 1; k >= 0; k--) {
			roof = Math.max(roof, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + k * way, z));
			ROOF[k] = roof;
		}
		if (top + 0.01F >= ROOF[0]) {
			return 0L;
		}
		long shade = 0L;
		for (int row = 0; row < CELLS; row++) {
			double fz = (row + 0.5) / CELLS;
			for (int cell = 0; cell < CELLS; cell++) {
				// leaves let some of the light through, the same squares each time
				boolean dapple = Math.floorMod(Mth.murmurHash3Mixer((x * CELLS + cell) * 73428767 ^ (z * CELLS + row) * 912931), 100) < LEAF_SHADE * 100.0F;
				if (blocked(level, at, x, z, x + (cell + 0.5) / CELLS, top + 0.01, fz, sun.x, sun.y, way, dapple)) {
					shade |= 1L << row * CELLS + cell;
				}
			}
		}
		return shade;
	}

	/** Walks from ({@code ox}, {@code oy}) toward the Sun through the blocks of row {@code z}; true once something solid is in the way. */
	private static boolean blocked(ClientLevel level, BlockPos.MutableBlockPos at, int x0, int z, double ox, double oy, double fz, double dx, double dy, int way,
		boolean dapple) {
		int ix = Mth.floor(ox), iy = Mth.floor(oy);
		boolean level0 = Math.abs(dx) < 1.0E-6;
		double stepX = level0 ? Double.MAX_VALUE : 1.0 / Math.abs(dx), stepY = 1.0 / dy;
		double nextX = level0 ? Double.MAX_VALUE : (way > 0 ? ix + 1 - ox : ox - ix) * stepX, nextY = (iy + 1 - oy) * stepY;
		double t = 0.0;
		for (int n = 0; n < CAST * 4; n++) {
			int k = Math.abs(ix - x0);
			if (k > CAST || iy >= ROOF[k]) {
				return false;
			}
			double next = Math.min(nextX, nextY);
			BlockState state = level.getBlockState(at.set(ix, iy, z));
			if (!state.isAir()) {
				if (state.isSolidRender()) {
					return true;
				}
				if (state.is(BlockTags.LEAVES)) {
					if (dapple) {
						return true;
					}
				} else if (state.canOcclude()) {
					// slabs, stairs, fences, walls: by the box round their shape, where the look passes through the block
					VoxelShape shape = state.getCollisionShape(level, at);
					if (!shape.isEmpty()) {
						double mid = (t + next) * 0.5;
						AABB box = shape.bounds();
						double mx = ox + dx * mid - ix, my = oy + dy * mid - iy;
						if (mx >= box.minX && mx <= box.maxX && my >= box.minY && my <= box.maxY && fz >= box.minZ && fz <= box.maxZ) {
							return true;
						}
					}
				}
			}
			t = next;
			if (nextX < nextY) {
				ix += way;
				nextX += stepX;
			} else {
				iy++;
				nextY += stepY;
			}
		}
		return false;
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------------

	/** Turns what the columns hold into quads: each row's run of shaded squares is one quad, a wholly shaded top is one. */
	private static void build(int cx, int cz) {
		float[] shade = new float[Math.max(64, shadeCount * 5 + 320)];
		float[] caustic = new float[Math.max(64, causticCount * 4 + 160)];
		int shades = 0, caustics = 0;
		float cell = 1.0F / CELLS;
		for (var entry : COLUMNS.long2ObjectEntrySet()) {
			Column column = entry.getValue();
			int x = x(entry.getLongKey()) - cx, z = z(entry.getLongKey()) - cz;
			if (column.bed != NONE) {
				if (caustics * 4 + 4 > caustic.length) {
					caustic = Arrays.copyOf(caustic, caustic.length * 2);
				}
				int at = caustics++ * 4;
				caustic[at] = x;
				caustic[at + 1] = z;
				caustic[at + 2] = column.bed + 1.0F + LIFT;
				caustic[at + 3] = Mth.lerp((column.deep - 1) / (float)(CAUSTIC_DEPTH - 1), 1.0F, CAUSTIC_DEEP);
				continue;
			}
			long bits = column.shade;
			if (bits == 0L || Float.isNaN(column.top)) {
				continue;
			}
			if (shades * 5 + 5 * CELLS * CELLS / 2 > shade.length) {
				shade = Arrays.copyOf(shade, shade.length * 2 + 5 * CELLS * CELLS);
			}
			float y = column.top + LIFT;
			if (bits == -1L) {
				shades = quad(shade, shades, x, z, x + 1.0F, z + 1.0F, y);
				continue;
			}
			for (int row = 0; row < CELLS; row++) {
				int line = (int)(bits >>> row * CELLS) & (1 << CELLS) - 1;
				for (int from = 0; from < CELLS; from++) {
					if ((line >> from & 1) == 0) {
						continue;
					}
					int to = from;
					while (to + 1 < CELLS && (line >> to + 1 & 1) != 0) {
						to++;
					}
					shades = quad(shade, shades, x + from * cell, z + row * cell, x + (to + 1) * cell, z + (row + 1) * cell, y);
					from = to;
				}
			}
		}
		originX = cx;
		originZ = cz;
		shadeQuads = shade;
		causticQuads = caustic;
		shadeCount = shades;
		causticCount = caustics;
	}

	private static int quad(float[] into, int count, float x0, float z0, float x1, float z1, float y) {
		int at = count * 5;
		into[at] = x0;
		into[at + 1] = z0;
		into[at + 2] = x1;
		into[at + 3] = z1;
		into[at + 4] = y;
		return count + 1;
	}

	private static void render(LevelRenderContext context) {
		float show = strength;
		if (show <= 0.01F || shadeCount == 0 && causticCount == 0) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float bx = (float)(originX - camera.x), by = (float)-camera.y, bz = (float)(originZ - camera.z);
		PoseStack poseStack = context.poseStack();
		float[] shade = shadeQuads, caustic = causticQuads;
		int shades = shadeCount, caustics = causticCount;
		if (shades > 0) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityShadow(WHITE), (pose, buffer) -> {
				for (int i = 0; i < shades; i++) {
					int at = i * 5;
					float x0 = shade[at] + bx, z0 = shade[at + 1] + bz, x1 = shade[at + 2] + bx, z1 = shade[at + 3] + bz, y = shade[at + 4] + by;
					float alpha = SHADE * show * edge((x0 + x1) * 0.5F, (z0 + z1) * 0.5F);
					if (alpha > 0.004F) {
						flat(buffer, pose, x0, z0, x1, z1, y, ARGB.colorFromFloat(alpha, 0.0F, 0.0F, 0.0F), 0.0F, 0.0F, 1.0F, 1.0F);
					}
				}
			});
		}
		if (caustics > 0) {
			float time = (clock + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / FRAME_TICKS;
			int frame = Mth.floor(time);
			float blend = time - frame;
			caustics(context, poseStack, caustic, caustics, bx, by, bz, Math.floorMod(frame, FRAMES), CAUSTIC_LIGHT * show * (1.0F - blend));
			caustics(context, poseStack, caustic, caustics, bx, by, bz, Math.floorMod(frame + 1, FRAMES), CAUSTIC_LIGHT * show * blend);
		}
	}

	/** One frame of the web over every lit bed; it repeats every two blocks. */
	private static void caustics(LevelRenderContext context, PoseStack poseStack, float[] quads, int count, float bx, float by, float bz, int frame, float light) {
		if (light <= 0.004F) {
			return;
		}
		int ox = originX, oz = originZ;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityShadow(CAUSTICS[frame]), (pose, buffer) -> {
			for (int i = 0; i < count; i++) {
				int at = i * 4;
				float x0 = quads[at] + bx, z0 = quads[at + 1] + bz;
				float alpha = light * quads[at + 3] * edge(x0 + 0.5F, z0 + 0.5F);
				if (alpha <= 0.004F) {
					continue;
				}
				float u = (Mth.floor(quads[at]) + ox & 1) * 0.5F, v = (Mth.floor(quads[at + 1]) + oz & 1) * 0.5F;
				flat(buffer, pose, x0, z0, x0 + 1.0F, z0 + 1.0F, quads[at + 2] + by, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F), u, v, u + 0.5F, v + 0.5F);
			}
		});
	}

	/** How much of itself something this far from the camera (blocks, level) shows: it fades over the last {@link #EDGE} of the range. */
	private static float edge(float x, float z) {
		return Mth.clamp((RANGE - Mth.sqrt(x * x + z * z)) / EDGE, 0.0F, 1.0F);
	}

	/** A level quad facing up, wound as the game winds an entity's shadow. */
	private static void flat(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, int colour, float u0, float v0, float u1,
		float v1) {
		vertex(buffer, pose, x0, y, z0, colour, u0, v0);
		vertex(buffer, pose, x0, y, z1, colour, u0, v1);
		vertex(buffer, pose, x1, y, z1, colour, u1, v1);
		vertex(buffer, pose, x1, y, z0, colour, u1, v0);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int colour, float u, float v) {
		buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
