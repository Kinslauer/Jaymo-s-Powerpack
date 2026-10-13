package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Disasters;
import com.jaymoskingdoms.disasters.Storm;
import com.jaymoskingdoms.disasters.StormNet;
import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.disasters.Tornado;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.6.9) The client's side of the storms: keeps its copy of the cells and tornadoes ({@link StormNet.Sync}) moving
 * between updates, eases the rain and thunder where the camera is (which {@code LevelWeatherMixin} hands the sky and
 * the rain), and draws the storms as particles: dark clouds over each cell, seen from far off, and each tornado's
 * whirling funnel and the debris at its foot.
 */
public final class StormsClient {
	/** Clouds are drawn for cells this near; funnels for tornadoes this near. */
	static final double CLOUD_VIEW = 640.0, FUNNEL_VIEW = 420.0;
	/** The cloud deck's height and depth; cloud puffs' size range; how long they last; how many a tick per cell. */
	static final float CLOUD_Y = 165.0F, CLOUD_DEPTH = 18.0F, CLOUD_MIN = 26.0F, CLOUD_MAX = 46.0F;
	static final int CLOUD_LIFE = 420;
	static final float CLOUDS_PER_TICK = 1.2F;
	/** Funnel wisps per tick (by tornado size) and their size; debris per tick. */
	static final int[] WISPS = {5, 18, 32}, DEBRIS = {3, 6, 10};
	static final float[] WISP_SIZE = {1.6F, 5.0F, 8.5F};
	/** How fast the rain and thunder ease toward what's overhead (per tick). */
	static final float EASE = 0.008F;

	private static SpriteSet cloudSprites, funnelSprites;
	private static float cloudCarry;

	private StormsClient() {
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(Disasters.STORM_CLOUD, sprites -> {
			cloudSprites = sprites;
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});
		ParticleProviderRegistry.getInstance().register(Disasters.FUNNEL, sprites -> {
			funnelSprites = sprites;
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});
		ClientPlayNetworking.registerGlobalReceiver(StormNet.Sync.TYPE, (payload, context) -> {
			Storms.CLIENT = new ArrayList<>(payload.storms());
			Storms.CLIENT_TORNADOES = new ArrayList<>(payload.tornadoes());
			Storms.clientWindAngle = payload.windAngle();
			Storms.clientWindSpeed = payload.windSpeed();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Storms.CLIENT = List.of();
			Storms.CLIENT_TORNADOES = List.of();
			Storms.clientRain = 0.0F;
			Storms.clientThunder = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(StormsClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		if (level.dimension() != Level.OVERWORLD) {
			Storms.clientRain = Math.max(0.0F, Storms.clientRain - EASE * 4.0F);
			Storms.clientThunder = Math.max(0.0F, Storms.clientThunder - EASE * 4.0F);
			return;
		}
		for (Storm s : Storms.CLIENT) {
			s.move();
		}
		for (Tornado t : Storms.CLIENT_TORNADOES) {
			t.x += t.vx;
			t.z += t.vz;
			t.age++;
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		Storms.clientRain = ease(Storms.clientRain, Storms.cover(level, eye.x, eye.z));
		Storms.clientThunder = ease(Storms.clientThunder, Storms.thunder(level, eye.x, eye.z));
		RandomSource random = level.getRandom();
		if (cloudSprites != null) {
			clouds(minecraft, level, eye, random);
		}
		if (funnelSprites != null) {
			funnels(minecraft, level, eye, random);
		}
	}

	private static float ease(float now, float target) {
		return now < target ? Math.min(target, now + EASE) : Math.max(target, now - EASE);
	}

	/** A puff or two of dark cloud over each cell in view, wherever under it. */
	private static void clouds(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random) {
		cloudCarry += CLOUDS_PER_TICK;
		int n = (int)cloudCarry;
		cloudCarry -= n;
		for (Storm s : Storms.CLIENT) {
			double dx = s.x - eye.x, dz = s.z - eye.z;
			if (dx * dx + dz * dz > (CLOUD_VIEW + s.radius) * (CLOUD_VIEW + s.radius)) {
				continue;
			}
			// bigger cells, more puffs
			int puffs = n * Math.max(1, Math.round(s.radius / 90.0F));
			for (int i = 0; i < puffs; i++) {
				if (random.nextFloat() > s.strength() + 0.1F) {
					continue;
				}
				double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * s.radius;
				double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
				float middle = 1.0F - (float)(r / s.radius);
				// darker toward the middle; a supercell's core has a sickly green cast, and hangs lower
				float grey = 0.36F - 0.14F * middle - (s.kind == Storm.RAIN ? -0.12F : 0.0F);
				float green = s.kind == Storm.SUPERCELL ? 0.05F * middle : 0.0F;
				float y = CLOUD_Y + random.nextFloat() * CLOUD_DEPTH - (s.kind == Storm.SUPERCELL ? 25.0F * middle : 0.0F);
				float size = Mth.lerp(random.nextFloat(), CLOUD_MIN, CLOUD_MAX);
				StormParticle p = new StormParticle(level, s.x + ox, y, s.z + oz, cloudSprites.get(random), StormParticle.CLOUD, s.id, size,
					CLOUD_LIFE + random.nextInt(120), grey, grey + green, grey, 0.75F).cloud(ox, oz);
				minecraft.particleEngine.add(p);
			}
		}
	}

	/** Each tornado in view: wisps whirling up its funnel, and dust and debris round its foot. */
	private static void funnels(Minecraft minecraft, ClientLevel level, Vec3 eye, RandomSource random) {
		for (Tornado t : Storms.CLIENT_TORNADOES) {
			double dx = t.x - eye.x, dz = t.z - eye.z;
			if (dx * dx + dz * dz > FUNNEL_VIEW * FUNNEL_VIEW) {
				continue;
			}
			float formed = t.formed();
			boolean devil = t.size == Tornado.DUST_DEVIL;
			int wisps = Math.max(1, Math.round(WISPS[t.size] * formed));
			for (int i = 0; i < wisps; i++) {
				float h = random.nextFloat() * t.height() * 0.85F;
				float angle = random.nextFloat() * Mth.TWO_PI;
				float tone = random.nextFloat() * 0.1F;
				// the funnel's dark grey (a dust devil's sandy), browner near the ground where it picks up dirt
				float low = 1.0F - Math.min(1.0F, h / 20.0F);
				float r = devil ? 0.72F + tone : 0.5F + tone + 0.08F * low;
				float g = devil ? 0.62F + tone : 0.5F + tone + 0.02F * low;
				float b = devil ? 0.46F + tone : 0.52F + tone - 0.08F * low;
				float size = WISP_SIZE[t.size] * (0.7F + 0.6F * random.nextFloat()) * (0.6F + 0.8F * h / t.height());
				StormParticle p = new StormParticle(level, t.x, t.y + h, t.z, funnelSprites.get(random), StormParticle.FUNNEL, t.id, size,
					50 + random.nextInt(40), r, g, b, devil ? 0.55F : 0.9F).funnel(h, angle, 0.75F + random.nextFloat() * 0.35F,
					0.25F + random.nextFloat() * 0.35F + t.size * 0.15F);
				minecraft.particleEngine.add(p);
			}
			int debris = Math.round(DEBRIS[t.size] * formed);
			for (int i = 0; i < debris; i++) {
				float angle = random.nextFloat() * Mth.TWO_PI;
				float tone = random.nextFloat() * 0.12F;
				StormParticle p = new StormParticle(level, t.x, t.y, t.z, funnelSprites.get(random), StormParticle.DEBRIS, t.id,
					WISP_SIZE[t.size] * (0.5F + random.nextFloat() * 0.5F), 30 + random.nextInt(30),
					0.42F + tone, 0.35F + tone, 0.26F + tone, devil ? 0.5F : 0.75F).funnel(random.nextFloat() * 2.0F, angle,
					1.6F + random.nextFloat() * 0.8F, 0.05F + random.nextFloat() * 0.1F);
				minecraft.particleEngine.add(p);
			}
		}
	}
}
