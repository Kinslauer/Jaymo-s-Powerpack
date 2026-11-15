package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Calamities;
import com.jaymoskingdoms.disasters.Calamity;
import com.jaymoskingdoms.disasters.Disasters;
import com.jaymoskingdoms.disasters.Magnitude;
import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.client.WorldLoop;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.31) The client's side of stage 2's weather ({@link Calamities}): keeps its copy of the sandstorms, blizzards
 * and hail showers moving, eases how thick each is where the camera is (out in the open; under a roof the fog lifts),
 * which the fog ({@code CalamityFogMixin}) reads, and blows sand, snow and hail past the camera, with their sounds. All
 * of it only round the camera, a few dozen specks a tick at most.
 */
public final class CalamitiesClient {
	/** How thick the sand, snow and hail are at the camera, 0..1 (eased). The fog mixin reads the first two. */
	public static float sand, snow, hail;
	/** How thick a volcano's ash is at the camera, 0..1 (eased; the fog mixin reads it), and how strong the signs are. */
	public static float ash, sign;
	/** (Stage 3) How hard the ground shakes at the camera, in degrees of rattle (eased); {@code CalamityShakeMixin}. */
	public static float quake;
	/** A quake's rattle per magnitude (degrees), and how fast it rattles. */
	static final float QUAKE_DEGREES = 0.9F, QUAKE_SPEED = 2.2F;
	private static int clock;
	/** The storm's howl where you stand (eased, sheltered or not), a gust swelling it now and then, and the next gust. */
	private static float around, gust;
	private static int nextGust, nextThump;
	/** The howl's loudness in the open and sheltered, its pitch, and a gust's swell; a quake's rumble bed. */
	static final float HOWL_OPEN = 0.3F, HOWL_SHELTERED = 0.12F, HOWL_PITCH = 0.5F, GUST_SWELL = 0.6F, RUMBLE = 0.35F;
	/** How fast they ease (per tick). */
	static final float EASE = 0.02F;
	/** Specks a tick at full strength (sand, snow, hail), and how far round the camera they start. */
	static final int[] SPECKS = {14, 16, 10};
	static final double SPECK_RANGE = 14.0;
	/** The fog at full strength: how far you can see in a sandstorm and a blizzard (blocks). */
	public static final float SAND_SIGHT = 20.0F, SNOW_SIGHT = 14.0F, ASH_SIGHT = 26.0F;
	/** The signs' tremble at full strength (degrees), and how often a far-off rumble comes in them (the odds a tick). */
	static final float SIGN_DEGREES = 0.35F, SIGN_RUMBLE = 0.01F;
	/** Ash specks a tick at full strength. */
	static final int ASH_SPECKS = 12;
	/** The fog's colour (by day) in a sandstorm and a blizzard. */
	public static final float[] SAND_COLOUR = {0.80F, 0.62F, 0.40F}, SNOW_COLOUR = {0.86F, 0.89F, 0.93F}, ASH_COLOUR = {0.36F, 0.34F, 0.33F};

	static SpriteSet sprites;

	private CalamitiesClient() {
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(Disasters.GUST, set -> {
			sprites = set;
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});
		ClientPlayNetworking.registerGlobalReceiver(Calamities.Sync.TYPE, (payload, context) -> {
			Calamities.CLIENT = new ArrayList<>(payload.calamities());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Calamities.CLIENT = List.of();
			sand = snow = hail = quake = ash = sign = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(CalamitiesClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		if (level.dimension() != Level.OVERWORLD || Calamities.CLIENT.isEmpty()) {
			sand = ease(sand, 0.0F);
			snow = ease(snow, 0.0F);
			hail = ease(hail, 0.0F);
			ash = ease(ash, 0.0F);
			sign = ease(sign, 0.0F);
			quake = Math.max(LastBellClient.shake, quake - 0.1F);
			around = ease(around, 0.0F);
			gust = Math.max(0.0F, gust - 0.02F);
			if (Calamities.CLIENT.isEmpty() || level.dimension() != Level.OVERWORLD) {
				return;
			}
		}
		for (Calamity c : Calamities.CLIENT) {
			c.move();
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		float open = level.canSeeSky(BlockPos.containing(eye)) ? 1.0F : 0.0F;
		sand = ease(sand, Calamities.cover(level, Calamity.SANDSTORM, eye.x, eye.z) * open);
		snow = ease(snow, Calamities.cover(level, Calamity.BLIZZARD, eye.x, eye.z) * open);
		hail = ease(hail, Calamities.cover(level, Calamity.HAIL, eye.x, eye.z));
		ash = ease(ash, Calamities.cover(level, Calamity.ASH, eye.x, eye.z) * open);
		sign = ease(sign, Calamities.cover(level, Calamity.SIGN, eye.x, eye.z));
		clock++;
		float shaking = LastBellClient.shake;
		int hailMagnitude = Magnitude.MODERATE;
		for (Calamity c : Calamities.CLIENT) {
			if (c.kind == Calamity.QUAKE) {
				shaking = Math.max(shaking, c.over(eye.x, eye.z) * (0.5F + 0.5F * c.magnitude) * QUAKE_DEGREES);
			} else if (c.kind == Calamity.HAIL && c.over(eye.x, eye.z) > 0.05F) {
				hailMagnitude = Math.max(hailMagnitude, c.magnitude);
			}
		}
		// the ground trembles in the signs before a great one
		shaking = Math.max(shaking, sign * SIGN_DEGREES);
		quake += (shaking - quake) * 0.2F;
		if (sign > 0.2F && level.getRandom().nextFloat() < SIGN_RUMBLE * sign) {
			RandomSource r = level.getRandom();
			level.playLocalSound(eye.x + r.nextGaussian() * 40.0, eye.y - 10.0, eye.z + r.nextGaussian() * 40.0, SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.AMBIENT, 0.25F * sign, 0.15F + r.nextFloat() * 0.1F, false);
		}
		if (quake > 0.1F) {
			rumble(level, eye, level.getRandom());
		}
		if (sprites == null) {
			return;
		}
		RandomSource random = level.getRandom();
		float sandHere = Calamities.cover(level, Calamity.SANDSTORM, eye.x, eye.z);
		float snowHere = Calamities.cover(level, Calamity.BLIZZARD, eye.x, eye.z);
		specks(minecraft, level, eye, random, GustParticle.SAND, sandHere);
		specks(minecraft, level, eye, random, GustParticle.SNOW, snowHere);
		specks(minecraft, level, eye, random, GustParticle.HAIL, hail);
		if (hailMagnitude >= Magnitude.GREAT) {
			// stones the size of a fist among the hail
			giantHail(minecraft, level, eye, random, hail);
		}
		ashFall(minecraft, level, eye, random, Calamities.cover(level, Calamity.ASH, eye.x, eye.z));
		sounds(level, eye, random, sandHere, snowHere);
	}

	private static float ease(float now, float target) {
		return now < target ? Math.min(target, now + EASE) : Math.max(target, now - EASE);
	}

	/** Specks of {@code kind} round the camera, in the open air only, blown along the wind (hail falling). */
	private static void specks(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, int kind, float strength) {
		if (strength <= 0.02F) {
			return;
		}
		int n = Math.round(SPECKS[kind] * strength);
		float angle = Storms.clientWindAngle;
		double wind = 0.35 + Storms.clientWindSpeed * 6.0;
		double wx = Math.cos(angle) * wind, wz = Math.sin(angle) * wind;
		for (int i = 0; i < n; i++) {
			// start them upwind of the camera so they blow past it
			double x = eye.x + (random.nextDouble() - 0.5) * 2.0 * SPECK_RANGE - wx * 6.0;
			double z = eye.z + (random.nextDouble() - 0.5) * 2.0 * SPECK_RANGE - wz * 6.0;
			double y = kind == GustParticle.HAIL ? eye.y + 8.0 + random.nextDouble() * 6.0 : eye.y + (random.nextDouble() - 0.4) * 8.0;
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) {
				continue;
			}
			float tone = random.nextFloat() * 0.08F;
			GustParticle p;
			if (kind == GustParticle.SAND) {
				p = new GustParticle(level, x, y, z, wx * (0.8 + random.nextDouble() * 0.5), -0.01, wz * (0.8 + random.nextDouble() * 0.5),
					sprites.get(random), kind, 0.08F + random.nextFloat() * 0.12F, 30 + random.nextInt(20), 0.82F + tone, 0.66F + tone,
					0.42F + tone, 0.75F);
			} else if (kind == GustParticle.SNOW) {
				p = new GustParticle(level, x, y, z, wx * (0.6 + random.nextDouble() * 0.4), -0.06, wz * (0.6 + random.nextDouble() * 0.4),
					sprites.get(random), kind, 0.06F + random.nextFloat() * 0.08F, 40 + random.nextInt(20), 0.95F, 0.96F + tone * 0.5F, 1.0F,
					0.9F);
			} else {
				p = new GustParticle(level, x, y, z, wx * 0.15, -0.9, wz * 0.15, sprites.get(random), kind, 0.05F + random.nextFloat() * 0.04F,
					40, 0.88F + tone, 0.92F + tone, 0.98F, 0.95F);
			}
			minecraft.particleEngine.add(p);
		}
	}

	/** A great hailstorm's fist-sized stones, a few, falling hard. */
	private static void giantHail(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, float strength) {
		if (strength <= 0.1F || random.nextFloat() > strength * 0.5F) {
			return;
		}
		double x = eye.x + (random.nextDouble() - 0.5) * 2.0 * SPECK_RANGE, z = eye.z + (random.nextDouble() - 0.5) * 2.0 * SPECK_RANGE;
		double y = eye.y + 10.0 + random.nextDouble() * 6.0;
		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) {
			return;
		}
		minecraft.particleEngine.add(new GustParticle(level, x, y, z, 0.0, -1.3, 0.0, sprites.get(random), GustParticle.HAIL,
			0.18F + random.nextFloat() * 0.1F, 30, 0.84F, 0.9F, 0.97F, 1.0F));
	}

	/** A volcano's ash, drifting down grey round the camera. */
	private static void ashFall(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random, float strength) {
		if (strength <= 0.02F) {
			return;
		}
		int n = Math.round(ASH_SPECKS * strength);
		for (int i = 0; i < n; i++) {
			double x = eye.x + (random.nextDouble() - 0.5) * 2.0 * SPECK_RANGE, z = eye.z + (random.nextDouble() - 0.5) * 2.0 * SPECK_RANGE;
			double y = eye.y + (random.nextDouble() - 0.2) * 10.0;
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) {
				continue;
			}
			float tone = random.nextFloat() * 0.1F;
			minecraft.particleEngine.add(new GustParticle(level, x, y, z, (random.nextDouble() - 0.5) * 0.05, -0.04, (random.nextDouble() - 0.5) * 0.05,
				sprites.get(random), GustParticle.SNOW, 0.05F + random.nextFloat() * 0.07F, 50 + random.nextInt(30), 0.30F + tone, 0.29F + tone,
				0.28F + tone, 0.85F));
		}
	}

	/** (Stage 3) The deep rumble of a quake, and stones and timbers creaking. */
	private static void rumble(ClientLevel level, Vec3 eye, RandomSource random) {
		// a low bed under the whole quake, and deep thumps at uneven spacing
		WorldLoop.keep("quake", SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), SoundSource.AMBIENT, () -> RUMBLE * Math.min(1.0F, quake / 2.0F),
			() -> 0.5F + 0.1F * Math.min(1.0F, quake / 2.7F));
		if (--nextThump <= 0) {
			nextThump = 12 + random.nextInt(18);
			level.playLocalSound(eye.x + random.nextGaussian() * 3.0, eye.y - 4.0, eye.z + random.nextGaussian() * 3.0, SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.AMBIENT, (0.18F + random.nextFloat() * 0.14F) * quake, 0.2F + random.nextFloat() * 0.1F, false);
		}
		if (random.nextInt(8) == 0) {
			level.playLocalSound(eye.x + random.nextGaussian() * 6.0, eye.y - 2.0, eye.z + random.nextGaussian() * 6.0,
				random.nextBoolean() ? SoundEvents.DEEPSLATE_BREAK : SoundEvents.WOOD_BREAK, SoundSource.AMBIENT, 0.35F * quake, 0.5F, false);
		}
	}

	/** How loud the howl is: full in the open, muffled under a roof, swelling in a gust. */
	private static double howlVolume() {
		float open = Math.max(sand, snow);
		float base = open * HOWL_OPEN + Math.max(0.0F, around - open) * HOWL_SHELTERED;
		return around < 0.05F ? 0.0 : base * (1.0F + GUST_SWELL * gust);
	}

	/** (Stage 3) Rattles the view by the quake (with the hurt bob, so your aim never moves). */
	public static void shake(com.mojang.blaze3d.vertex.PoseStack pose, float partialTicks) {
		if (quake <= 0.02F) {
			return;
		}
		float p = (clock + partialTicks) * QUAKE_SPEED;
		float pitch = quake * (0.6F * Mth.sin(p * 1.13F) + 0.4F * Mth.sin(p * 2.31F + 1.7F));
		float roll = quake * 0.8F * (0.6F * Mth.sin(p * 0.97F + 3.1F) + 0.4F * Mth.sin(p * 1.87F + 0.6F));
		float yaw = quake * 0.4F * Mth.sin(p * 0.71F + 2.0F);
		pose.rotateDegrees(com.mojang.math.Axis.XP, pitch);
		pose.rotateDegrees(com.mojang.math.Axis.YP, yaw);
		pose.rotateDegrees(com.mojang.math.Axis.ZP, roll);
	}

	/** The wind howling through a sandstorm or blizzard, and hailstones clattering on the ground. */
	private static void sounds(ClientLevel level, Vec3 eye, RandomSource random, float sandHere, float snowHere) {
		// the howl is a held bed that swells with the storm and with gusts, louder out in the open than under a roof
		around = ease(around, Math.max(sandHere, snowHere));
		gust = Math.max(0.0F, gust - 0.02F);
		if (around > 0.15F && --nextGust <= 0) {
			nextGust = 40 + random.nextInt(80);
			gust = (0.4F + random.nextFloat() * 0.6F) * around;
		}
		WorldLoop.keep("howl", SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, CalamitiesClient::howlVolume, () -> HOWL_PITCH + 0.15F * around + 0.2F * gust);
		if (snowHere > 0.2F && random.nextInt(14) == 0) {
			level.playLocalSound(eye.x + random.nextGaussian() * 4.0, eye.y, eye.z + random.nextGaussian() * 4.0, SoundEvents.POWDER_SNOW_STEP,
				SoundSource.WEATHER, 0.25F * snowHere, 0.6F + random.nextFloat() * 0.3F, false);
		}
		if (sandHere > 0.2F && random.nextInt(10) == 0) {
			level.playLocalSound(eye.x + random.nextGaussian() * 4.0, eye.y, eye.z + random.nextGaussian() * 4.0, SoundEvents.SAND_IDLE,
				SoundSource.WEATHER, 0.4F * sandHere, 0.8F, false);
		}
		if (hail > 0.1F) {
			int clatter = random.nextFloat() < hail ? 2 : 0;
			for (int i = 0; i < clatter; i++) {
				int x = Mth.floor(eye.x + random.nextGaussian() * 6.0), z = Mth.floor(eye.z + random.nextGaussian() * 6.0);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				level.playLocalSound(x + 0.5, y, z + 0.5, SoundEvents.CALCITE_HIT, SoundSource.WEATHER, 0.25F * hail,
					1.4F + random.nextFloat() * 0.4F, false);
			}
		}
	}
}
