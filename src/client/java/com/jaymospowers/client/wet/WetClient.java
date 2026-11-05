package com.jaymospowers.client.wet;

import com.jaymospowers.client.blood.BloodPuddle;
import com.jaymospowers.core.wet.ArmourGrime;
import com.jaymospowers.core.wet.WetParticles;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.63) Wet weather on the client (Andy: "pooling water on the ground near trees, during rain, and under wet characters
 * like the blood trail", and "subsurface scattering rays underwater"). All of it is only for show, so each client works
 * it out for itself:
 * <ul>
 * <li><b>Drips:</b> anyone who's been in water or rain stays wet for a while after; out of it they drip, and the drops
 * gather into little puddles under them that dry up, leaving a trail of wet spots behind whoever walks off.</li>
 * <li><b>Rain puddles:</b> while it rains, water pools on the ground under trees (where the leaves drip) and beside their
 * trunks, and less often in hollows in the open. A puddle grows while the rain keeps falling on it and dries up after.
 * (0.20.54) Andy: "rain doesn't seem to be pooling water". In the open only two-sided hollows ever got one, a fifth of the
 * time, and a puddle grew only when a random try happened to land on its block again, so out in a field there were next
 * to none and those few stayed small. Now flat open ground pools too (never a ledge water would run off, nor sand), hollows
 * more surely, and every puddle the rain still falls on is fed each {@link #RAIN_FEED_EVERY} ticks, so it grows full.</li>
 * <li><b>Light shafts:</b> under water by day, beams of sunlight hang down from the surface overhead
 * ({@link LightShaft}).</li>
 * </ul>
 * Also the tooltip of dirty armour ({@link ArmourGrime}).
 */
public final class WetClient {
	/** How long a body stays wet after leaving water, and after leaving the rain (ticks). */
	static final int WET_FROM_WATER = 600, WET_FROM_RAIN = 300;
	/** How far from the camera bodies drip (blocks). */
	static final double DRIP_RANGE = 32.0;
	/** The chance a soaked body drips each tick (falling off as it dries), and a drip's puddle: how big, how long it lies. */
	static final float DRIP_CHANCE = 0.25F, DRIP_SCALE = 0.22F, DRIP_GROW = 0.04F, DRIP_MAX = 0.6F;
	static final int DRIP_LIFE = 500;
	/** Drops falling this close (blocks across) to a drip puddle join it. */
	static final double DRIP_MERGE = 0.45;
	/** Rain puddles: how often a spot is tried (ticks), how many, how far out (blocks), how big they start and how long they lie. */
	static final int RAIN_EVERY = 5, RAIN_TRIES = 4, RAIN_RANGE = 20, RAIN_LIFE = 1600;
	static final float RAIN_SCALE = 0.45F, RAIN_GROW = 0.12F;
	/** The most rain puddles kept; how far from a trunk counts as beside it; the chance an open hollow gets one. */
	static final int MAX_RAIN_PUDDLES = 160, TRUNK_REACH = 3;
	static final float HOLLOW_CHANCE = 0.6F;
	/** (0.20.54) The chance a flat open spot gets one, and how often every rained-on puddle is fed (ticks). */
	static final float FLAT_CHANCE = 0.1F;
	static final int RAIN_FEED_EVERY = 100;
	/** Light shafts: how often one is tried (ticks), how far out (blocks), how many at once, how long they last (ticks). */
	static final int SHAFT_EVERY = 3, SHAFT_RANGE = 14, MAX_SHAFTS = 36, SHAFT_LIFE = 90, SHAFT_LIFE_SPREAD = 80;
	/** How deep (blocks) the surface may be above the camera, and how bright a shaft is at noon. */
	static final int SHAFT_DEPTH = 28;
	static final float SHAFT_BRIGHTNESS = 0.32F;

	private static @Nullable SpriteSet puddleSprites, shaftSprites;
	/** How long each body near the camera stays wet. */
	private static final Map<LivingEntity, Integer> WET = new WeakHashMap<>();
	/** The drip puddles lying now, and the rain puddles by block. */
	private static final List<BloodPuddle> DRIPS = new ArrayList<>();
	private static final Long2ObjectOpenHashMap<BloodPuddle> RAIN = new Long2ObjectOpenHashMap<>();
	private static final List<LightShaft> SHAFTS = new ArrayList<>();
	private static @Nullable ClientLevel puddlesIn;

	private WetClient() {
	}

	public static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(WetParticles.PUDDLE, sprites -> {
			puddleSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> BloodPuddle.water(level, x, y, z, sprites, RAIN_SCALE, RAIN_LIFE);
		});
		registry.register(WetParticles.LIGHT_SHAFT, sprites -> {
			shaftSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> new LightShaft(level, x, y, z, sprites, 8.0, 1.0F, 0.0F, 0.0F,
				SHAFT_BRIGHTNESS, SHAFT_LIFE);
		});
		ClientTickEvents.END_CLIENT_TICK.register(WetClient::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			ArmourGrime.Grime grime = ArmourGrime.grime(stack);
			if (grime != null && grime.amount() > 0) {
				String how = grime.level() > 0.66F ? "filthy" : grime.level() > 0.33F ? "dirty" : "grubby";
				String what = grime.muddiness() > 0.5F ? "mud" : "dust";
				lines.add(Component.translatable("tooltip.jaymospowers.grime." + how + "." + what).withStyle(ChatFormatting.GOLD));
			}
		});
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		if (level != puddlesIn) {
			DRIPS.clear();
			RAIN.clear();
			SHAFTS.clear();
			WET.clear();
			puddlesIn = level;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		DRIPS.removeIf(puddle -> !puddle.isAlive());
		SHAFTS.removeIf(shaft -> !shaft.isAlive());
		if (level.getGameTime() % 40 == 0) {
			RAIN.values().removeIf(puddle -> !puddle.isAlive());
		}
		if (puddleSprites != null) {
			drips(minecraft, level, eye);
			if (level.getGameTime() % RAIN_EVERY == 0 && level.getRainLevel(1.0F) > 0.2F) {
				rain(minecraft, level, eye);
			}
			if (level.getGameTime() % RAIN_FEED_EVERY == 0 && level.getRainLevel(1.0F) > 0.2F) {
				feedRain(level);
			}
		}
		if (shaftSprites != null && level.getGameTime() % SHAFT_EVERY == 0 && camera.getFluidInCamera() == FogType.WATER) {
			shafts(minecraft, level, eye);
		}
	}

	// ---- drips ------------------------------------------------------------------------------------------------------------

	private static void drips(Minecraft minecraft, ClientLevel level, Vec3 eye) {
		RandomSource random = level.getRandom();
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(DRIP_RANGE), LivingEntity::isAlive)) {
			if (body.isInWater()) {
				WET.put(body, WET_FROM_WATER);
				continue;
			}
			int wet = WET.getOrDefault(body, 0);
			if (body.isInWaterOrRain()) {
				// in the rain: soaking, and the rain itself makes the puddles
				WET.put(body, Math.max(wet, WET_FROM_RAIN));
				continue;
			}
			if (wet <= 0) {
				continue;
			}
			WET.put(body, wet - 1);
			if (wet == 1) {
				WET.remove(body);
			}
			if (random.nextFloat() >= DRIP_CHANCE * wet / (float) WET_FROM_WATER) {
				continue;
			}
			AABB box = body.getBoundingBox();
			double x = Mth.lerp(random.nextDouble(), box.minX, box.maxX);
			double z = Mth.lerp(random.nextDouble(), box.minZ, box.maxZ);
			double y = Mth.lerp(0.2 + random.nextDouble() * 0.6, box.minY, box.maxY);
			level.addParticle(ParticleTypes.FALLING_WATER, x, y, z, 0.0, 0.0, 0.0);
			if (body.onGround()) {
				drip(minecraft, level, x, box.minY + 0.05, z);
			}
		}
	}

	/** A drop off a wet body landing at (x, y, z): into the drip puddle there, or a new one. */
	private static void drip(Minecraft minecraft, ClientLevel level, double x, double y, double z) {
		if (!level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) {
			return;
		}
		for (BloodPuddle puddle : DRIPS) {
			Vec3 at = puddle.position();
			if (Math.abs(at.x - x) < DRIP_MERGE && Math.abs(at.z - z) < DRIP_MERGE && Math.abs(at.y - y) < 1.0) {
				if (puddle.size() < DRIP_MAX * 1.5F) {
					puddle.feed(DRIP_LIFE, DRIP_GROW);
				}
				return;
			}
		}
		float scale = DRIP_SCALE * (0.8F + level.getRandom().nextFloat() * 0.4F);
		BloodPuddle puddle = BloodPuddle.water(level, x, y, z, puddleSprites, scale, DRIP_LIFE);
		DRIPS.add(puddle);
		minecraft.particleEngine.add(puddle);
	}

	// ---- rain -------------------------------------------------------------------------------------------------------------

	private static void rain(Minecraft minecraft, ClientLevel level, Vec3 eye) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < RAIN_TRIES; i++) {
			int x = Mth.floor(eye.x) + random.nextInt(RAIN_RANGE * 2 + 1) - RAIN_RANGE;
			int z = Mth.floor(eye.z) + random.nextInt(RAIN_RANGE * 2 + 1) - RAIN_RANGE;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos at = new BlockPos(x, ground, z);
			BlockPos below = at.below();
			BlockState floor = level.getBlockState(below);
			if (!level.getFluidState(below).isEmpty() || !level.getFluidState(at).isEmpty() || !floor.isFaceSturdy(level, below, Direction.UP)
				|| floor.is(BlockTags.LEAVES) || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				continue;
			}
			// raining here (not snowing), whether or not leaves are in the way
			BlockPos sky = new BlockPos(x, Math.max(ground, canopy), z);
			if (level.getBiome(sky).value().getPrecipitationAt(sky, level.getSeaLevel()) != Biome.Precipitation.RAIN) {
				continue;
			}
			boolean underLeaves = canopy > ground && level.getBlockState(new BlockPos(x, canopy - 1, z)).is(BlockTags.LEAVES);
			if (!underLeaves && !besideTrunk(level, at) && !open(level, floor, x, ground, z, random)) {
				continue;
			}
			long key = at.asLong();
			BloodPuddle known = RAIN.get(key);
			if (known != null && known.isAlive()) {
				known.feed(RAIN_LIFE, RAIN_GROW);
				continue;
			}
			if (RAIN.size() >= MAX_RAIN_PUDDLES) {
				continue;
			}
			double px = x + 0.2 + random.nextDouble() * 0.6, pz = z + 0.2 + random.nextDouble() * 0.6;
			float scale = RAIN_SCALE * (0.7F + random.nextFloat() * 0.6F);
			BloodPuddle puddle = BloodPuddle.water(level, px, ground + 0.05, pz, puddleSprites, scale, RAIN_LIFE);
			RAIN.put(key, puddle);
			minecraft.particleEngine.add(puddle);
		}
	}

	/** Whether a tree trunk stands within {@link #TRUNK_REACH} of {@code at}. */
	private static boolean besideTrunk(ClientLevel level, BlockPos at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -TRUNK_REACH; dx <= TRUNK_REACH; dx++) {
			for (int dz = -TRUNK_REACH; dz <= TRUNK_REACH; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					if (level.getBlockState(pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz)).is(BlockTags.LOGS)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * (0.20.54) Whether rain pools on this open spot: never on sand (it soaks in) or a ledge (any side lower: it runs off);
	 * a hollow by {@link #HOLLOW_CHANCE}, flat ground by {@link #FLAT_CHANCE}.
	 */
	private static boolean open(ClientLevel level, BlockState floor, int x, int ground, int z, RandomSource random) {
		if (floor.is(BlockTags.SAND)) {
			return false;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + side.getStepX(), z + side.getStepZ()) < ground) {
				return false;
			}
		}
		return random.nextFloat() < (hollow(level, x, ground, z) ? HOLLOW_CHANCE : FLAT_CHANCE);
	}

	/** (0.20.54) Every rain puddle the rain still falls on (or drips on, under the leaves) grows and keeps. */
	private static void feedRain(ClientLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Long2ObjectMap.Entry<BloodPuddle> entry : RAIN.long2ObjectEntrySet()) {
			BloodPuddle puddle = entry.getValue();
			pos.set(entry.getLongKey());
			int ground = pos.getY(), canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
			if (!puddle.isAlive() || canopy > ground && !level.getBlockState(pos.setY(canopy - 1)).is(BlockTags.LEAVES)) {
				// (roofed over since: it dries)
				continue;
			}
			pos.setY(Math.max(ground, canopy));
			if (level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN) {
				puddle.feed(RAIN_LIFE, RAIN_GROW);
			}
		}
	}

	/** Whether the ground here is lower than at least two of its four neighbours (water runs into it). */
	private static boolean hollow(ClientLevel level, int x, int ground, int z) {
		int higher = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + side.getStepX(), z + side.getStepZ()) > ground) {
				higher++;
			}
		}
		return higher >= 2;
	}

	// ---- light shafts -----------------------------------------------------------------------------------------------------

	private static void shafts(Minecraft minecraft, ClientLevel level, Vec3 eye) {
		if (SHAFTS.size() >= MAX_SHAFTS || !level.dimensionType().hasSkyLight()) {
			return;
		}
		float daylight = 1.0F - level.getSkyDarken() / 11.0F;
		if (daylight < 0.2F) {
			return;
		}
		RandomSource random = level.getRandom();
		double x = eye.x + (random.nextDouble() * 2.0 - 1.0) * SHAFT_RANGE;
		double z = eye.z + (random.nextDouble() * 2.0 - 1.0) * SHAFT_RANGE;
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, eye.y, z).mutable();
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			return;
		}
		int up = 0;
		while (up < SHAFT_DEPTH && level.getFluidState(pos.move(Direction.UP)).is(FluidTags.WATER)) {
			up++;
		}
		if (up >= SHAFT_DEPTH || !level.canSeeSky(pos)) {
			return;
		}
		double surface = pos.getY() - 0.1;
		// down to somewhere below the camera, not through the sea floor
		int down = 0;
		BlockPos.MutableBlockPos floor = BlockPos.containing(x, eye.y, z).mutable();
		while (down < 12 && level.getFluidState(floor.move(Direction.DOWN)).is(FluidTags.WATER)) {
			down++;
		}
		double length = Math.min(surface - floor.getY() - 1.0, (surface - eye.y) + 4.0 + random.nextDouble() * 8.0);
		if (length < 2.0) {
			return;
		}
		// the beams lean away from the sun: east in the morning, west in the evening
		float day = (level.getDefaultClockTime() % 24000L) / 24000.0F;
		float lean = Mth.sin(day * Mth.TWO_PI) * 0.35F;
		float width = 0.5F + random.nextFloat() * 1.2F;
		int life = SHAFT_LIFE + random.nextInt(SHAFT_LIFE_SPREAD);
		LightShaft shaft = new LightShaft(level, x, surface, z, shaftSprites, length, width, (float) (-lean * length), 0.0F,
			SHAFT_BRIGHTNESS * daylight * (0.6F + random.nextFloat() * 0.4F), life);
		SHAFTS.add(shaft);
		minecraft.particleEngine.add(shaft);
	}
}
