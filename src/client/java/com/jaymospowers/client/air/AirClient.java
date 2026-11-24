package com.jaymospowers.client.air;

import com.jaymospowers.client.LowEndMode;
import com.jaymospowers.core.Seasons;
import com.jaymospowers.core.air.AirParticles;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Sky, light and air (Reference Mods patch 14, after ImmersiveVFX's ideas; our own code and sprites, the leaves drawn
 * with the game's own leaf sprites), on this side only, round the camera:
 * <ul>
 * <li><b>Scorch</b> (X3): a fire that has burned a few seconds leaves soot on what it touches, the floor under it and
 * any wall beside it, that stays a good while after it's out ({@link #SCORCH_LIFE}).</li>
 * <li><b>Lava crust</b> (X9): still lava against rock cools to a dark, cracked, glowing crust along that edge
 * ({@link LavaCrust}).</li>
 * <li><b>Leaves on water</b> (X2): leaves fall from the trees over a pond or river and float, carried by the current,
 * until they rot; in autumn many more fall, in autumn's colours, they last longer, and they pile up along the banks
 * ({@link FloatingLeaf}).</li>
 * </ul>
 * Light in its block's colour (X10) is {@link LightTint}; blocks settling into place (X11) {@link BlockSettle}.
 * Fire's embers and its smoke lit from below (X3) are Kingdoms' ({@code SkiesClient}), with its wind.
 */
public final class AirClient {
	/** Scorch: blocks looked at a tick and how far round the camera; how long a fire must burn before it scorches (ticks). */
	static final int FIRE_SAMPLES = 64, FIRE_RANGE = 16, SCORCH_AFTER = 60;
	/** How long scorch stays (ticks, at least, up to this many more) and fades, and how dark and thick it is. */
	static final int SCORCH_LIFE = 9600, SCORCH_SPREAD = 2400, SCORCH_FADE = 1200;
	static final int SOOT = 0x16110E;
	static final float SOOT_ALPHA = 0.8F;
	/** The most scorched places kept (the oldest forgotten first); a fire seen once and not again is forgotten after this long. */
	static final int SCORCHED_MOST = 600, FIRE_FORGET = 600;
	/** Lava crust: blocks looked at a tick, how far round the camera (each way, and up and down), and the most at once. */
	static final int LAVA_SAMPLES = 96, LAVA_RANGE = 20, LAVA_HEIGHT = 10, CRUSTS_MOST = 300;
	/** Leaves: columns looked at a tick, how far round the camera, how high over the water a tree's leaves may be. */
	static final int LEAF_SAMPLES = 8, LEAF_RANGE = 16, CANOPY_ABOVE = 10;
	/** The odds a column under leaves drops one (and in autumn), how many float at once (and in autumn). */
	static final float LEAF_CHANCE = 0.05F, AUTUMN_LEAF_CHANCE = 0.3F;
	static final int LEAVES_MOST = 120, AUTUMN_LEAVES_MOST = 320;
	/** How long a leaf floats (ticks, plus up to the spread), and in autumn; its size (blocks); how much autumn turns its colour. */
	static final int LEAF_LIFE = 1800, AUTUMN_LEAF_LIFE = 4800, LEAF_LIFE_SPREAD = 1800;
	static final float LEAF_SIZE_MIN = 0.28F, LEAF_SIZE_MAX = 0.4F, AUTUMN_TURN = 0.75F;
	static final int[] AUTUMN = {0xD9822B, 0xB8432A, 0xD8B13A, 0x8A5A2B, 0xC9622A};

	private static @Nullable SpriteSet scorchSprites, crustSprites, leafSprites;
	/** Fires seen and when they were first seen; places already scorched, oldest first. */
	private static final Long2LongOpenHashMap FIRES = new Long2LongOpenHashMap();
	private static final LongOpenHashSet SCORCHED = new LongOpenHashSet();
	private static final LongArrayFIFOQueue SCORCHED_ORDER = new LongArrayFIFOQueue();
	/** Crusts by lava block and bank side ({@code pos * 4 + side}). */
	private static final Long2ObjectOpenHashMap<LavaCrust> CRUSTS = new Long2ObjectOpenHashMap<>();
	private static final List<FloatingLeaf> LEAVES = new ArrayList<>();
	private static @Nullable ClientLevel in;

	private AirClient() {
	}

	public static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		// sprites only: a bare /particle of any of these lays nothing
		registry.register(AirParticles.SCORCH, sprites -> {
			scorchSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> null;
		});
		registry.register(AirParticles.LAVA_CRUST, sprites -> {
			crustSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> null;
		});
		registry.register(AirParticles.WATER_LEAF, sprites -> {
			leafSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> null;
		});
		ClientTickEvents.END_CLIENT_TICK.register(AirClient::tick);
		LightTint.init();
		BlockSettle.init();
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != in) {
			FIRES.clear();
			SCORCHED.clear();
			SCORCHED_ORDER.clear();
			CRUSTS.clear();
			LEAVES.clear();
			in = level;
		}
		if (level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = level.getRandom();
		boolean low = LowEndMode.on();
		scorch(minecraft, level, eye, random, low ? FIRE_SAMPLES / 3 : FIRE_SAMPLES);
		crust(minecraft, level, eye, random, low ? LAVA_SAMPLES / 3 : LAVA_SAMPLES);
		leaves(minecraft, level, eye, random, low ? LEAF_SAMPLES / 2 : LEAF_SAMPLES);
	}

	// ---- scorch -------------------------------------------------------------------------------------------------------

	private static void scorch(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, int samples) {
		if (scorchSprites == null) {
			return;
		}
		long now = level.getGameTime();
		if (now % 100L == 0L) {
			FIRES.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > FIRE_FORGET);
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples; i++) {
			at.set(Mth.floor(eye.x) + random.nextInt(FIRE_RANGE * 2 + 1) - FIRE_RANGE, Mth.floor(eye.y) + random.nextInt(FIRE_RANGE + 1) - FIRE_RANGE / 2,
				Mth.floor(eye.z) + random.nextInt(FIRE_RANGE * 2 + 1) - FIRE_RANGE);
			if (!level.getBlockState(at).is(BlockTags.FIRE)) {
				continue;
			}
			long key = at.asLong();
			if (SCORCHED.contains(key)) {
				continue;
			}
			long first = FIRES.getOrDefault(key, -1L);
			if (first < 0L) {
				FIRES.put(key, now);
			} else if (now - first >= SCORCH_AFTER) {
				FIRES.remove(key);
				scorched(key);
				lay(minecraft, level, at.immutable(), random);
			}
		}
	}

	private static void scorched(long key) {
		SCORCHED.add(key);
		SCORCHED_ORDER.enqueue(key);
		while (SCORCHED_ORDER.size() > SCORCHED_MOST) {
			SCORCHED.remove(SCORCHED_ORDER.dequeueLong());
		}
	}

	/** Soot from the fire at {@code fire}: on the floor under it and the walls beside it. */
	private static void lay(Minecraft minecraft, ClientLevel level, BlockPos fire, RandomSource random) {
		int life = SCORCH_LIFE + random.nextInt(SCORCH_SPREAD);
		BlockPos below = fire.below();
		BlockState floor = level.getBlockState(below);
		VoxelShape shape = floor.getCollisionShape(level, below);
		if (!shape.isEmpty() && floor.getFluidState().isEmpty()) {
			double top = below.getY() + shape.max(Direction.Axis.Y);
			// a quarter turn of the sprite at random, so no two look alike
			float yaw = random.nextInt(4) * Mth.HALF_PI;
			Vec3 u = new Vec3(Mth.cos(yaw) * 0.5, 0.0, Mth.sin(yaw) * 0.5), v = new Vec3(-Mth.sin(yaw) * 0.5, 0.0, Mth.cos(yaw) * 0.5);
			minecraft.particleEngine.add(new AirDecal(level, fire.getX() + 0.5, top + AirDecal.LIFT, fire.getZ() + 0.5, u, v,
				scorchSprites.get(random), SOOT, SOOT_ALPHA, life, 40, SCORCH_FADE, false));
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos wall = fire.relative(side);
			if (!level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite()) || !level.getFluidState(wall).isEmpty()) {
				continue;
			}
			Vec3 u = new Vec3(Math.abs(side.getStepZ()) * 0.5, 0.0, Math.abs(side.getStepX()) * 0.5), v = new Vec3(0.0, 0.5, 0.0);
			minecraft.particleEngine.add(new AirDecal(level, fire.getX() + 0.5 + side.getStepX() * (0.5 - AirDecal.LIFT), fire.getY() + 0.5,
				fire.getZ() + 0.5 + side.getStepZ() * (0.5 - AirDecal.LIFT), u, v, scorchSprites.get(random), SOOT, SOOT_ALPHA * 0.85F, life, 40,
				SCORCH_FADE, false));
		}
	}

	// ---- lava crust ---------------------------------------------------------------------------------------------------

	private static void crust(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, int samples) {
		if (crustSprites == null) {
			return;
		}
		CRUSTS.values().removeIf(c -> !c.isAlive());
		if (CRUSTS.size() >= CRUSTS_MOST) {
			return;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples; i++) {
			at.set(Mth.floor(eye.x) + random.nextInt(LAVA_RANGE * 2 + 1) - LAVA_RANGE, Mth.floor(eye.y) + random.nextInt(LAVA_HEIGHT * 2 + 1) - LAVA_HEIGHT,
				Mth.floor(eye.z) + random.nextInt(LAVA_RANGE * 2 + 1) - LAVA_RANGE);
			FluidState fluid = level.getFluidState(at);
			if (!fluid.is(FluidTags.LAVA)) {
				continue;
			}
			for (Direction bank : Direction.Plane.HORIZONTAL) {
				long key = at.asLong() * 4L + bank.get2DDataValue();
				if (CRUSTS.containsKey(key) || !LavaCrust.holds(level, at, bank)) {
					continue;
				}
				LavaCrust crust = new LavaCrust(level, at, bank, at.getY() + fluid.getHeight(level, at), crustSprites.get(random),
					Mth.lerp(random.nextFloat(), LavaCrust.DEPTH_MIN, LavaCrust.DEPTH_MAX));
				minecraft.particleEngine.add(crust);
				CRUSTS.put(key, crust);
			}
		}
	}

	// ---- leaves on water ----------------------------------------------------------------------------------------------

	private static void leaves(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, int samples) {
		if (leafSprites == null) {
			return;
		}
		LEAVES.removeIf(l -> !l.isAlive());
		boolean autumn = Seasons.of(level) == Seasons.Season.AUTUMN;
		if (LEAVES.size() >= (autumn ? AUTUMN_LEAVES_MOST : LEAVES_MOST)) {
			return;
		}
		float chance = autumn ? AUTUMN_LEAF_CHANCE : LEAF_CHANCE;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples; i++) {
			if (random.nextFloat() >= chance) {
				continue;
			}
			int x = Mth.floor(eye.x) + random.nextInt(LEAF_RANGE * 2 + 1) - LEAF_RANGE, z = Mth.floor(eye.z) + random.nextInt(LEAF_RANGE * 2 + 1) - LEAF_RANGE;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			if (Math.abs(y - eye.y) > 24.0) {
				continue;
			}
			at.set(x, y, z);
			FluidState water = level.getFluidState(at);
			if (!water.is(FluidTags.WATER) || !level.getBlockState(at.above()).isAir()) {
				continue;
			}
			BlockPos canopy = null;
			for (int up = 2; up <= CANOPY_ABOVE; up++) {
				BlockPos p = at.above(up);
				if (level.getBlockState(p).is(BlockTags.LEAVES)) {
					canopy = p;
					break;
				}
			}
			if (canopy == null) {
				continue;
			}
			int rgb = leafColour(minecraft, level, canopy, autumn, random);
			int life = (autumn ? AUTUMN_LEAF_LIFE : LEAF_LIFE) + random.nextInt(LEAF_LIFE_SPREAD);
			FloatingLeaf leaf = new FloatingLeaf(level, x + random.nextDouble(), y + water.getHeight(level, at) + AirDecal.LIFT, z + random.nextDouble(),
				leafSprites.get(random), rgb, Mth.lerp(random.nextFloat(), LEAF_SIZE_MIN, LEAF_SIZE_MAX), life);
			minecraft.particleEngine.add(leaf);
			LEAVES.add(leaf);
		}
	}

	/** The colour of a leaf off {@code canopy}: its leaves' own (tinted by the land), turned in autumn. */
	private static int leafColour(Minecraft minecraft, ClientLevel level, BlockPos canopy, boolean autumn, RandomSource random) {
		BlockState leaves = level.getBlockState(canopy);
		int rgb;
		BlockTintSource tint = minecraft.getBlockColors().getTintSource(leaves, 0);
		if (tint != null) {
			rgb = tint.colorInWorld(leaves, level, canopy) & 0xFFFFFF;
		} else if (leaves.is(Blocks.CHERRY_LEAVES)) {
			rgb = 0xF2B6C9;
		} else if (leaves.is(Blocks.PALE_OAK_LEAVES)) {
			rgb = 0xA6A69A;
		} else {
			rgb = 0x6F9A3E;
		}
		if (autumn) {
			rgb = ARGB.srgbLerp(AUTUMN_TURN * (0.7F + random.nextFloat() * 0.3F), rgb, AUTUMN[random.nextInt(AUTUMN.length)]) & 0xFFFFFF;
		}
		// a fallen leaf is a little darker than one on the tree
		return ARGB.scaleRGB(rgb, 0.85F) & 0xFFFFFF;
	}
}
