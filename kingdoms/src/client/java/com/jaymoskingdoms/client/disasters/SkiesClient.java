package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.disasters.Storms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The everyday sky and weather, on this side only (Andy's ImmersiveVFX picks, built as our own after its test ended:
 * nothing of that mod is used). All of it is particles and fog, so it shows with a shader pack on or off:
 * <ul>
 * <li><b>Downpours</b>: at the heart of a storm cell ({@link Storms#downpour}) the rain falls in thick streaks, splashes
 * everywhere, roars, and the view closes in to grey ({@code SkiesFogMixin}). The server's side is {@code Downpours}.</li>
 * <li><b>Morning mist</b>: from before dawn to mid-morning, banks of pale fog lie on low ground and over water, thickest
 * in swamps and by rivers, more of it in autumn and less in summer; none in dry lands or in the rain.</li>
 * <li><b>Sand winds</b>: on a windy day streams of sand run low over open sand, well short of a sandstorm.</li>
 * <li><b>Auroras</b>: two clear nights in three, green and violet curtains hang in the northern sky over cold lands.</li>
 * <li><b>Livelier fire</b>: fire near you licks taller and throws sparks, and a lit campfire spits one now and then.</li>
 * </ul>
 */
public final class SkiesClient {
	/** How hard the downpour is and how thick the mist is at the camera, 0..1 (eased). {@code SkiesFogMixin} reads both. */
	public static float downpour, mist;
	/** How far you can see in a full downpour and in full mist (blocks), their colours by day, and how much of the mist the fog takes. */
	public static final float DOWNPOUR_SIGHT = 44.0F, MIST_SIGHT = 72.0F, MIST_FOG = 0.6F;
	public static final float[] DOWNPOUR_COLOUR = {0.52F, 0.56F, 0.61F}, MIST_COLOUR = {0.84F, 0.86F, 0.88F};

	/** How fast the downpour, the mist and the aurora ease in and out (per tick). */
	static final float EASE = 0.01F, MIST_EASE = 0.002F, AURORA_EASE = 0.002F;
	/** A downpour: streaks and splashes a tick at its hardest, how far round the camera, and how often it roars (ticks). */
	static final int STREAKS = 26, SPLASHES = 14, ROAR_TICKS = 18;
	static final double STREAK_RANGE = 12.0, SPLASH_RANGE = 10.0;
	/** Indoors a downpour's fog is this much of itself. */
	static final float INDOORS = 0.35F;
	/** Mist: from when it gathers to when it is full, and from when it thins to when it is gone (time of day, ticks; it wraps past 24000). */
	static final int MIST_FROM = 22300, MIST_FULL = 23300, MIST_THINS = 1000, MIST_GONE = 2600;
	/** Mist by place: swamps, by water, wooded country, anywhere else that rains or snows; and how much of it is left high up. */
	static final float MIST_SWAMP = 1.0F, MIST_WATER = 0.8F, MIST_WOODS = 0.5F, MIST_OPEN = 0.35F, MIST_HIGH = 0.2F;
	/** Ground counts as low this far above the sea, and the camera as high up this far above it. */
	static final int MIST_LOW = 16, MIST_HIGH_FROM = 40;
	/** Mist by season. */
	static final float MIST_AUTUMN = 1.3F, MIST_SUMMER = 0.7F;
	/** Fog banks a tick at full mist, how near and far they form, their size, and how long they last. */
	static final float BANKS = 0.5F, BANK_MIN = 8.0F, BANK_MAX = 14.0F;
	static final double BANK_NEAR = 10.0, BANK_FAR = 56.0;
	static final int BANK_LIFE = 220;
	/** Sand winds: the wind has to blow this hard (0..1); streams a tick at its hardest, and how far round the camera. */
	static final float SAND_WIND_FROM = 0.45F;
	static final int SAND_STREAMS = 5;
	static final double SAND_RANGE = 12.0;
	/** Auroras: the night's hours they show in (time of day), how many nights in {@link #AURORA_OF} have one. */
	static final int AURORA_FROM = 13800, AURORA_TO = 22200, AURORA_RAMP = 1200, AURORA_NIGHTS = 2, AURORA_OF = 3;
	/** Where the curtain hangs from the camera: this far north, this wide each way, its foot this high and its height. */
	static final double AURORA_AWAY = 300.0, AURORA_SPAN = 360.0, AURORA_Y = 205.0, AURORA_TALL = 75.0;
	/** Its patches a tick, their size, how long they last and how strongly they show. */
	static final float AURORA_PATCHES = 1.6F, AURORA_MIN = 30.0F, AURORA_MAX = 52.0F, AURORA_ALPHA = 0.17F;
	static final int AURORA_LIFE = 180;
	/** Fire: blocks looked at each tick, how far round the camera, and the odds a fire found licks up and throws a spark. */
	static final int FIRE_SAMPLES = 48, FIRE_RANGE = 12;
	static final float FIRE_LICK = 0.35F, FIRE_SPARK = 0.12F, CAMPFIRE_SPARK = 0.08F;

	private static float aurora, bankCarry, auroraCarry;
	private static int clock;

	private SkiesClient() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> downpour = mist = aurora = 0.0F);
		ClientTickEvents.END_CLIENT_TICK.register(SkiesClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		clock++;
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = level.getRandom();
		fire(level, eye, random);
		if (level.dimension() != Level.OVERWORLD) {
			downpour = ease(downpour, 0.0F, EASE * 4.0F);
			mist = ease(mist, 0.0F, MIST_EASE * 4.0F);
			aurora = 0.0F;
			return;
		}
		BlockPos at = BlockPos.containing(eye);
		boolean open = level.canSeeSky(at);
		Holder<Biome> biome = level.getBiome(at);
		Biome.Precipitation falls = biome.value().getPrecipitationAt(at, level.getSeaLevel());
		int time = (int)Math.floorMod(level.getOverworldClockTime(), 24000L);

		float pouring = falls == Biome.Precipitation.RAIN ? Storms.downpour(level, eye.x, eye.z) : 0.0F;
		downpour = ease(downpour, pouring * (open ? 1.0F : INDOORS), EASE);
		if (pouring > 0.05F) {
			pour(minecraft, level, eye, random, pouring, open);
		}

		float rain = Math.max(level.getRainLevel(1.0F), Storms.clientRain);
		mist = ease(mist, mistAt(level, biome, falls, eye, time) * (1.0F - rain), MIST_EASE);
		if (mist > 0.05F) {
			banks(level, eye, random);
		}

		if (open && CalamitiesClient.sand < 0.05F && falls == Biome.Precipitation.NONE) {
			sandWind(minecraft, level, eye, random);
		}

		boolean tonight = Math.floorMod((level.getOverworldClockTime() / 24000L) * 7L + 3L, (long)AURORA_OF) < AURORA_NIGHTS;
		float night = Math.min(Mth.clamp((time - AURORA_FROM) / (float)AURORA_RAMP, 0.0F, 1.0F), Mth.clamp((AURORA_TO - time) / (float)AURORA_RAMP, 0.0F, 1.0F));
		boolean cold = biome.value().coldEnoughToSnow(at, level.getSeaLevel());
		aurora = ease(aurora, tonight && cold && rain < 0.1F ? night : 0.0F, AURORA_EASE);
		if (aurora > 0.02F && StormsClient.cloudSprites != null) {
			curtain(minecraft, level, eye, random);
		}
	}

	private static float ease(float now, float target, float step) {
		return now < target ? Math.min(target, now + step) : Math.max(target, now - step);
	}

	// ---- downpours ---------------------------------------------------------------------------------------------------

	private static void pour(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, float hard, boolean open) {
		if (CalamitiesClient.sprites != null) {
			int streaks = Math.round(STREAKS * hard);
			double wx = Math.cos(Storms.clientWindAngle) * 0.12, wz = Math.sin(Storms.clientWindAngle) * 0.12;
			for (int i = 0; i < streaks; i++) {
				double x = eye.x + (random.nextDouble() - 0.5) * 2.0 * STREAK_RANGE, z = eye.z + (random.nextDouble() - 0.5) * 2.0 * STREAK_RANGE;
				double y = eye.y + 5.0 + random.nextDouble() * 7.0;
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) {
					continue;
				}
				float tone = random.nextFloat() * 0.08F;
				minecraft.particleEngine.add(new GustParticle(level, x, y, z, wx, -1.1, wz, CalamitiesClient.sprites.get(random), GustParticle.HAIL,
					0.035F + random.nextFloat() * 0.03F, 30, 0.6F + tone, 0.68F + tone, 0.8F + tone, 0.7F));
			}
		}
		int splashes = Math.round(SPLASHES * hard);
		for (int i = 0; i < splashes; i++) {
			int x = Mth.floor(eye.x + (random.nextDouble() - 0.5) * 2.0 * SPLASH_RANGE), z = Mth.floor(eye.z + (random.nextDouble() - 0.5) * 2.0 * SPLASH_RANGE);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (Math.abs(y - eye.y) < 12.0) {
				level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SPLASH : ParticleTypes.RAIN, x + random.nextDouble(), y + 0.05, z + random.nextDouble(),
					0.0, 0.0, 0.0);
			}
		}
		if (clock % ROAR_TICKS == 0) {
			level.playLocalSound(eye.x, eye.y + (open ? 0.0 : 6.0), eye.z, open ? SoundEvents.WEATHER_RAIN : SoundEvents.WEATHER_RAIN_ABOVE,
				SoundSource.WEATHER, 0.45F * hard, 0.65F + random.nextFloat() * 0.1F, false);
		}
	}

	// ---- morning mist ------------------------------------------------------------------------------------------------

	/** How thick the mist would lie where the camera is, 0..1, before the rain's say. */
	private static float mistAt(ClientLevel level, Holder<Biome> biome, Biome.Precipitation falls, Vec3 eye, int time) {
		float hour;
		if (time >= MIST_FROM) {
			hour = Mth.clamp((time - MIST_FROM) / (float)(MIST_FULL - MIST_FROM), 0.0F, 1.0F);
		} else {
			hour = 1.0F - Mth.clamp((time - MIST_THINS) / (float)(MIST_GONE - MIST_THINS), 0.0F, 1.0F);
		}
		if (hour <= 0.0F || falls == Biome.Precipitation.NONE) {
			return 0.0F;
		}
		float place = biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? MIST_SWAMP
			: biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) ? MIST_WATER
			: biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE) ? MIST_WOODS : MIST_OPEN;
		if (eye.y > level.getSeaLevel() + MIST_HIGH_FROM) {
			place *= MIST_HIGH;
		}
		String season = KitchenCompat.season(level);
		float year = "autumn".equals(season) ? MIST_AUTUMN : "summer".equals(season) ? MIST_SUMMER : 1.0F;
		return Mth.clamp(hour * place * year, 0.0F, 1.0F);
	}

	/** Banks of fog on the low ground and the water round the camera, drifting a little with the wind. */
	private static void banks(ClientLevel level, Vec3 eye, RandomSource random) {
		bankCarry += BANKS * mist;
		int n = (int)bankCarry;
		bankCarry -= n;
		double drift = 0.004 + Storms.clientWindSpeed * 0.1;
		double dx = Math.cos(Storms.clientWindAngle) * drift, dz = Math.sin(Storms.clientWindAngle) * drift;
		for (int i = 0; i < n; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, r = BANK_NEAR + random.nextDouble() * (BANK_FAR - BANK_NEAR);
			int x = Mth.floor(eye.x + Math.cos(a) * r), z = Mth.floor(eye.z + Math.sin(a) * r);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			boolean water = level.getFluidState(new BlockPos(x, y - 1, z)).is(FluidTags.WATER);
			if (!water && y > level.getSeaLevel() + MIST_LOW || y > eye.y + 8.0) {
				continue;
			}
			StormsClient.fog(level, x + 0.5, y + 0.8 + random.nextDouble() * 1.4, z + 0.5, dx, dz, Mth.lerp(random.nextFloat(), BANK_MIN, BANK_MAX),
				BANK_LIFE + random.nextInt(120), 0.86F, 0.3F * mist);
		}
	}

	// ---- sand winds ------------------------------------------------------------------------------------------------

	private static void sandWind(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random) {
		float blow = (Storms.clientWindStrength() - SAND_WIND_FROM) / (1.0F - SAND_WIND_FROM);
		if (blow <= 0.0F || CalamitiesClient.sprites == null) {
			return;
		}
		int n = Math.round(SAND_STREAMS * blow + random.nextFloat());
		double wind = 0.25 + Storms.clientWindSpeed * 4.0;
		double wx = Math.cos(Storms.clientWindAngle) * wind, wz = Math.sin(Storms.clientWindAngle) * wind;
		for (int i = 0; i < n; i++) {
			int x = Mth.floor(eye.x + (random.nextDouble() - 0.5) * 2.0 * SAND_RANGE - wx * 8.0), z = Mth.floor(eye.z + (random.nextDouble() - 0.5) * 2.0 * SAND_RANGE - wz * 8.0);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockState ground = level.getBlockState(new BlockPos(x, y - 1, z));
			if (!ground.is(BlockTags.SAND) || Math.abs(y - eye.y) > 10.0) {
				continue;
			}
			boolean red = ground.is(Blocks.RED_SAND);
			float tone = random.nextFloat() * 0.08F;
			// a short run of specks one behind another reads as a stream
			for (int k = 0; k < 3; k++) {
				minecraft.particleEngine.add(new GustParticle(level, x + random.nextDouble() - wx * k * 0.6, y + 0.08 + random.nextDouble() * 0.45,
					z + random.nextDouble() - wz * k * 0.6, wx, 0.0, wz, CalamitiesClient.sprites.get(random), GustParticle.SAND,
					0.05F + random.nextFloat() * 0.07F, 26 + random.nextInt(16), (red ? 0.78F : 0.84F) + tone, (red ? 0.44F : 0.72F) + tone,
					(red ? 0.24F : 0.48F) + tone, 0.6F));
			}
		}
	}

	// ---- auroras ---------------------------------------------------------------------------------------------------

	/** Patches of the curtain: a wavering line across the north, green at its foot and violet at its top. */
	private static void curtain(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random) {
		auroraCarry += AURORA_PATCHES * aurora;
		int n = (int)auroraCarry;
		auroraCarry -= n;
		for (int i = 0; i < n; i++) {
			double x = eye.x + (random.nextDouble() - 0.5) * 2.0 * AURORA_SPAN;
			// the line it hangs along is fixed to the world, and shifts slowly through the night
			double z = eye.z - AURORA_AWAY + Math.sin(x * 0.013 + clock * 0.0006) * 55.0 + Math.sin(x * 0.041) * 14.0;
			float up = random.nextFloat();
			double y = AURORA_Y + up * AURORA_TALL;
			float r = Mth.lerp(up, 0.22F, 0.62F), g = Mth.lerp(up, 1.0F, 0.34F), b = Mth.lerp(up, 0.5F, 1.0F);
			minecraft.particleEngine.add(new AuroraParticle(level, x, y, z, StormsClient.cloudSprites.get(random), Mth.lerp(random.nextFloat(), AURORA_MIN, AURORA_MAX),
				AURORA_LIFE + random.nextInt(100), r, g, b, AURORA_ALPHA * aurora * (1.0F - 0.5F * up)));
		}
	}

	// ---- fire --------------------------------------------------------------------------------------------------------

	/** Fire round the camera licks taller and throws sparks; a lit campfire spits one now and then. In every world. */
	private static void fire(ClientLevel level, Vec3 eye, RandomSource random) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < FIRE_SAMPLES; i++) {
			at.set(Mth.floor(eye.x) + random.nextInt(FIRE_RANGE * 2 + 1) - FIRE_RANGE, Mth.floor(eye.y) + random.nextInt(FIRE_RANGE + 1) - FIRE_RANGE / 2,
				Mth.floor(eye.z) + random.nextInt(FIRE_RANGE * 2 + 1) - FIRE_RANGE);
			BlockState state = level.getBlockState(at);
			if (state.is(BlockTags.FIRE)) {
				if (random.nextFloat() < FIRE_LICK) {
					level.addParticle(ParticleTypes.FLAME, at.getX() + 0.2 + random.nextDouble() * 0.6, at.getY() + 0.7 + random.nextDouble() * 0.5,
						at.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03 + random.nextDouble() * 0.04, 0.0);
				}
				if (random.nextFloat() < FIRE_SPARK) {
					spark(level, at, random, 1.0);
				}
			} else if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)
				&& random.nextFloat() < CAMPFIRE_SPARK) {
				spark(level, at, random, 0.5);
			}
		}
	}

	private static void spark(ClientLevel level, BlockPos at, RandomSource random, double from) {
		level.addParticle(ParticleTypes.SMALL_FLAME, at.getX() + 0.3 + random.nextDouble() * 0.4, at.getY() + from, at.getZ() + 0.3 + random.nextDouble() * 0.4,
			(random.nextDouble() - 0.5) * 0.06, 0.08 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.06);
	}
}
