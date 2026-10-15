package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Calamities;
import com.jaymoskingdoms.disasters.Calamity;
import com.jaymoskingdoms.disasters.Disasters;
import com.jaymoskingdoms.disasters.Storms;
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
	/** How fast they ease (per tick). */
	static final float EASE = 0.02F;
	/** Specks a tick at full strength (sand, snow, hail), and how far round the camera they start. */
	static final int[] SPECKS = {14, 16, 10};
	static final double SPECK_RANGE = 14.0;
	/** The fog at full strength: how far you can see in a sandstorm and a blizzard (blocks). */
	public static final float SAND_SIGHT = 20.0F, SNOW_SIGHT = 14.0F;
	/** The fog's colour (by day) in a sandstorm and a blizzard. */
	public static final float[] SAND_COLOUR = {0.80F, 0.62F, 0.40F}, SNOW_COLOUR = {0.86F, 0.89F, 0.93F};

	private static SpriteSet sprites;

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
			sand = snow = hail = 0.0F;
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
		if (sprites == null) {
			return;
		}
		RandomSource random = level.getRandom();
		float sandHere = Calamities.cover(level, Calamity.SANDSTORM, eye.x, eye.z);
		float snowHere = Calamities.cover(level, Calamity.BLIZZARD, eye.x, eye.z);
		specks(minecraft, level, eye, random, GustParticle.SAND, sandHere);
		specks(minecraft, level, eye, random, GustParticle.SNOW, snowHere);
		specks(minecraft, level, eye, random, GustParticle.HAIL, hail);
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

	/** The wind howling through a sandstorm or blizzard, and hailstones clattering on the ground. */
	private static void sounds(ClientLevel level, Vec3 eye, RandomSource random, float sandHere, float snowHere) {
		float howl = Math.max(sandHere, snowHere);
		if (howl > 0.2F && level.getGameTime() % 50 == 0) {
			level.playLocalSound(eye.x, eye.y, eye.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.25F * howl, 0.5F + random.nextFloat() * 0.15F,
				false);
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
