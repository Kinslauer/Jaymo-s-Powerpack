package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityMeteorBurst;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor's burst, as seen ({@link GravityMeteorBurst}): a storm of rock and earth flung out of the crater (real
 * chunks of the ground it hit and of the rock, flying fast and far, bouncing where they land and lying a while), a
 * blast of smoke and dust billowing up and rolling out along the ground, and then a cloud that hangs over the crater,
 * thinning slowly, before it clears. Vanilla's block particles can't be thrown fast (their speed is reset when made),
 * so the debris is made here, where it can be.
 */
public final class GravityMeteorFx {
	/** How many chunks of debris (a tap's, a full charge's), how fast they're flung (blocks/tick), how long they last (ticks). */
	static final int CHUNKS_MIN = 260, CHUNKS_MAX = 900;
	static final double FLING_MIN = 0.8, FLING_MAX = 2.2;
	static final int CHUNK_LIFE_MIN = 50, CHUNK_LIFE_MAX = 140;
	/** How big a chunk is drawn (times a block particle). */
	static final float CHUNK_SIZE_MIN = 1.3F, CHUNK_SIZE_MAX = 3.6F;
	/** The blast of smoke and dust at the burst (a tap's, a full charge's), and how fast it rolls out (blocks/tick). */
	static final int BILLOW_MIN = 120, BILLOW_MAX = 420;
	static final double BILLOW_SPEED = 0.22;
	/** How long the cloud hangs over the crater before it has cleared (ticks; a tap's, a full charge's). */
	static final int CLEAR_MIN = 400, CLEAR_MAX = 900;
	/** Puffs a tick at first (a tap's, a full charge's); fewer and fewer as it clears. */
	static final float PUFFS_MIN = 4.0F, PUFFS_MAX = 14.0F;
	/** The cloud's width (a share of the blast's reach) and height (blocks, a tap's to a full charge's). */
	static final double CLOUD_WIDTH = 0.45, CLOUD_HIGH_MIN = 4.0, CLOUD_HIGH_MAX = 16.0;
	/** Dust (pale brown-grey) and smoke (dark grey) tints for the smoke sprites. */
	static final float[] DUST = {0.62F, 0.56F, 0.49F}, SMOKE = {0.22F, 0.21F, 0.21F};
	/** In low-end mode, this share of the debris, smoke and hanging dust (on top of the particle setting). */
	static final float LOW_END_SHARE = 0.5F;
	private static final DustParticleOptions HAZE = new DustParticleOptions(0x8C8273, 4.0F);

	private static final class Cloud {
		final Vec3 at;
		final float charge;
		final double width, high;
		final int life;
		final List<BlockState> states;
		int age;
		float owed;

		Cloud(Vec3 at, float charge, double radius, List<BlockState> states) {
			this.at = at;
			this.charge = charge;
			this.width = Math.max(4.0, radius * CLOUD_WIDTH);
			this.high = Mth.lerp(charge, CLOUD_HIGH_MIN, CLOUD_HIGH_MAX);
			this.life = Math.round(Mth.lerp(charge, CLEAR_MIN, CLEAR_MAX));
			this.states = states;
		}
	}

	private static final List<Cloud> CLOUDS = new ArrayList<>();

	private GravityMeteorFx() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GravityMeteorBurst.TYPE, (burst, context) -> burst(context.client(), burst));
		ClientTickEvents.END_CLIENT_TICK.register(GravityMeteorFx::tick);
	}

	/** Fewer of everything on decreased particles, far fewer on minimal; (0.17.200) and half again in low-end mode. */
	private static float share(Minecraft minecraft) {
		ParticleStatus status = minecraft.options.particles().get();
		float share = status == ParticleStatus.MINIMAL ? 0.25F : status == ParticleStatus.DECREASED ? 0.5F : 1.0F;
		return com.jaymospowers.client.LowEndMode.on() ? share * LOW_END_SHARE : share;
	}

	private static void burst(Minecraft minecraft, GravityMeteorBurst burst) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Vec3 at = new Vec3(burst.x(), burst.y(), burst.z());
		float charge = burst.charge(), share = share(minecraft);
		List<BlockState> states = new ArrayList<>();
		for (int id : burst.states()) {
			BlockState state = Block.stateById(id);
			if (!state.isAir() && state.shouldSpawnTerrainParticles()) {
				states.add(state);
			}
		}
		if (states.isEmpty()) {
			states.add(Blocks.STONE.defaultBlockState());
		}
		// the debris: flung up and out, the most of it high, falling back all over and round the crater
		int chunks = Math.round(Mth.lerp(charge, CHUNKS_MIN, CHUNKS_MAX) * share);
		double fling = Mth.lerp(charge, FLING_MIN, FLING_MAX), spread = 1.0 + charge * 4.0;
		for (int i = 0; i < chunks; i++) {
			BlockState state = states.get(random.nextInt(states.size()));
			Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 1.4 + 0.3, random.nextGaussian()).normalize();
			double speed = fling * (0.3 + random.nextDouble() * 0.7);
			double x = at.x + random.nextGaussian() * spread * 0.4, y = at.y + 0.5 + random.nextDouble() * spread * 0.5,
				z = at.z + random.nextGaussian() * spread * 0.4;
			TerrainParticle chunk = new TerrainParticle(level, x, y, z, 0.0, 0.0, 0.0, state);
			chunk.setParticleSpeed(dir.x * speed, dir.y * speed, dir.z * speed);
			chunk.scale(Mth.lerp(random.nextFloat(), CHUNK_SIZE_MIN, CHUNK_SIZE_MAX));
			chunk.setLifetime(CHUNK_LIFE_MIN + random.nextInt(CHUNK_LIFE_MAX - CHUNK_LIFE_MIN + 1));
			minecraft.particleEngine.add(chunk);
		}
		// burning bits among it
		int embers = Math.round((40 + charge * 120) * share);
		for (int i = 0; i < embers; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) + 0.4, random.nextGaussian()).normalize();
			double speed = fling * (0.2 + random.nextDouble() * 0.5);
			add(minecraft, random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, at.x, at.y + 1.0, at.z, dir.scale(speed), null);
		}
		// the blast of smoke and dust: billowing up from the middle, and a wall of it rolling out along the ground
		int billow = Math.round(Mth.lerp(charge, BILLOW_MIN, BILLOW_MAX) * share);
		double roll = BILLOW_SPEED * (1.0 + charge * 1.5);
		for (int i = 0; i < billow; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			if (i % 3 == 0) {
				Vec3 v = new Vec3(Math.cos(a) * roll * 0.4 * random.nextDouble(), 0.05 + random.nextDouble() * 0.12 * (1.0 + charge), Math.sin(a) * roll * 0.4 * random.nextDouble());
				add(minecraft, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x + Math.cos(a) * random.nextDouble() * spread, at.y + 0.5 + random.nextDouble() * spread,
					at.z + Math.sin(a) * random.nextDouble() * spread, v, SMOKE);
			} else {
				double out = roll * (0.4 + random.nextDouble() * 0.6);
				Vec3 v = new Vec3(Math.cos(a) * out, 0.005 + random.nextDouble() * 0.02, Math.sin(a) * out);
				add(minecraft, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + Math.cos(a) * spread, at.y + 0.4 + random.nextDouble() * 1.5, at.z + Math.sin(a) * spread, v,
					DUST);
			}
		}
		CLOUDS.add(new Cloud(at, charge, burst.radius(), states));
	}

	/** The clouds hanging over craters, thinning as they clear. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			CLOUDS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		float share = share(minecraft);
		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
			Cloud cloud = it.next();
			if (++cloud.age >= cloud.life) {
				it.remove();
				continue;
			}
			float left = 1.0F - cloud.age / (float)cloud.life;
			// it spreads out over the first few seconds, then just thins
			double width = cloud.width * (0.5 + 0.5 * Math.min(1.0, cloud.age / 80.0));
			cloud.owed += Mth.lerp(cloud.charge, PUFFS_MIN, PUFFS_MAX) * left * left * share;
			while (cloud.owed >= 1.0F) {
				cloud.owed -= 1.0F;
				double a = random.nextDouble() * Math.PI * 2.0, d = width * Math.sqrt(random.nextDouble());
				double x = cloud.at.x + Math.cos(a) * d, z = cloud.at.z + Math.sin(a) * d;
				double ground = Math.max(cloud.at.y - 8.0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)));
				double y = ground + random.nextDouble() * cloud.high * (0.3 + 0.7 * (1.0 - d / width));
				Vec3 drift = new Vec3(random.nextGaussian() * 0.01, 0.004 + random.nextDouble() * 0.01, random.nextGaussian() * 0.01);
				int kind = random.nextInt(10);
				if (kind < 5) {
					add(minecraft, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, drift, DUST);
				} else if (kind < 7 && d < width * 0.5) {
					// the darker smoke still rising out of the middle
					add(minecraft, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, drift.add(0.0, 0.02, 0.0), SMOKE);
				} else if (kind < 9) {
					add(minecraft, HAZE, x, y, z, drift, null);
				} else {
					// grit settling out of it
					BlockState state = cloud.states.get(random.nextInt(cloud.states.size()));
					add(minecraft, new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, y + 2.0, z, Vec3.ZERO, null);
				}
			}
		}
	}

	private static void add(Minecraft minecraft, ParticleOptions options, double x, double y, double z, Vec3 velocity, float[] tint) {
		Particle particle = minecraft.particleEngine.createParticle(options, x, y, z, velocity.x, velocity.y, velocity.z);
		if (particle == null) {
			return;
		}
		particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
		if (tint != null && particle instanceof SingleQuadParticle quad) {
			float shade = 0.9F + minecraft.level.getRandom().nextFloat() * 0.2F;
			quad.setColor(tint[0] * shade, tint[1] * shade, tint[2] * shade);
		}
	}
}
