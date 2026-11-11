package com.jaymospowers.client.combat;

import com.jaymospowers.combat.ClashFx;
import com.jaymospowers.power.chrono.future.MuzzleFlash;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClashFx} on this side: one {@link ClashFx.Burst} from the server becomes its whole shower here.
 * <ul>
 * <li>Sparks ({@link Spark}): full bright, thrown mostly across the line of the blow and a little up, pulled down hard,
 * bouncing once off the ground; white-hot, then orange, then a dull red as they shrink away. A small flash (the muzzle
 * burst's star, small) marks the point the steel met.</li>
 * <li>Shards ({@link Shard}): bits of a wooden shield, lit by the world, tumbling as they fly, lying where they land
 * for a moment and fading.</li>
 * </ul>
 * {@link #sparks} and {@link #splinters} make the same showers with no packet, for anything that plays on the client
 * alone (a film's staged fight).
 */
public final class ClashFxClient {
	/** A spark's speed (blocks/tick, between these), its lift, and how much of its speed along the blow's line is kept. */
	static final double SPARK_SPEED_MIN = 0.12, SPARK_SPEED_MAX = 0.42, SPARK_LIFT = 0.12, SPARK_ALONG = 0.35;
	/** A spark's life (ticks, between these), size (half a quad, for scale 1), weight and how much of a fall it bounces back with. */
	static final int SPARK_LIFE_MIN = 9, SPARK_LIFE_MAX = 20;
	static final float SPARK_SIZE = 0.06F, SPARK_GRAVITY = 1.1F, SPARK_BOUNCE = 0.4F;
	/** The flash where the steel met: the muzzle burst's star at this scale per spark scale. */
	static final float FLASH_SCALE = 0.32F;
	/** A shard's speed, lift, life, size and tumble (radians a tick). */
	static final double SHARD_SPEED_MIN = 0.08, SHARD_SPEED_MAX = 0.3, SHARD_LIFT = 0.16, SHARD_ALONG = 0.14;
	static final int SHARD_LIFE_MIN = 26, SHARD_LIFE_MAX = 48;
	static final float SHARD_SIZE = 0.085F, SHARD_GRAVITY = 1.0F, SHARD_TUMBLE = 0.55F;

	private ClashFxClient() {
	}

	public static void init() {
		ParticleProviderRegistry.getInstance().register(ClashFx.SPARKS, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
			sparks(level, sprites, x, y, z, new Vec3(options.dx(), options.dy(), options.dz()), options.count(), options.scale()));
		ParticleProviderRegistry.getInstance().register(ClashFx.SPLINTERS, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
			splinters(level, sprites, x, y, z, new Vec3(options.dx(), options.dy(), options.dz()), options.count(), options.color()));
	}

	/** A shower of sparks at {@code at}, made here (no packet): see {@link ClashFx#sparks}. */
	public static void sparks(ClientLevel level, Vec3 at, @Nullable Vec3 line, int count, float scale) {
		Vec3 l = line == null || line.lengthSqr() < 1.0E-6 ? Vec3.ZERO : line.normalize();
		level.addParticle(new ClashFx.Burst((float)l.x, (float)l.y, (float)l.z, Mth.clamp(count, 1, ClashFx.MAX_BURST), scale, 0xFFFFFF, false),
			true, false, at.x, at.y, at.z, 0.0, 0.0, 0.0);
	}

	/** Shards of wood at {@code at}, made here (no packet): see {@link ClashFx#splinters}. */
	public static void splinters(ClientLevel level, Vec3 at, @Nullable Vec3 line, int count, int color) {
		Vec3 l = line == null || line.lengthSqr() < 1.0E-6 ? Vec3.ZERO : line.normalize();
		level.addParticle(new ClashFx.Burst((float)l.x, (float)l.y, (float)l.z, Mth.clamp(count, 1, ClashFx.MAX_BURST), 1.0F, color, true),
			true, false, at.x, at.y, at.z, 0.0, 0.0, 0.0);
	}

	/** The whole shower: every spark but the last goes straight to the particle engine, the last is the provider's own. */
	private static Particle sparks(ClientLevel level, SpriteSet sprites, double x, double y, double z, Vec3 line, int count, float scale) {
		Minecraft minecraft = Minecraft.getInstance();
		RandomSource random = level.getRandom();
		minecraft.particleEngine.createParticle(new MuzzleFlash.Flash(FLASH_SCALE * scale * (0.85F + random.nextFloat() * 0.3F)), x, y, z, 0.0, 0.0, 0.0);
		Spark last = null;
		for (int i = 0; i < Math.max(1, count); i++) {
			if (last != null) {
				minecraft.particleEngine.add(last);
			}
			Vec3 v = thrown(random, line, SPARK_ALONG, Mth.lerp(random.nextDouble() * random.nextDouble(), SPARK_SPEED_MIN, SPARK_SPEED_MAX), SPARK_LIFT);
			last = new Spark(level, x, y, z, v, scale, sprites.get(random));
		}
		return last;
	}

	private static Particle splinters(ClientLevel level, SpriteSet sprites, double x, double y, double z, Vec3 line, int count, int color) {
		Minecraft minecraft = Minecraft.getInstance();
		RandomSource random = level.getRandom();
		Shard last = null;
		for (int i = 0; i < Math.max(1, count); i++) {
			if (last != null) {
				minecraft.particleEngine.add(last);
			}
			Vec3 v = thrown(random, line, 1.0, Mth.lerp(random.nextDouble(), SHARD_SPEED_MIN, SHARD_SPEED_MAX), SHARD_LIFT).add(line.scale(SHARD_ALONG));
			last = new Shard(level, x + (random.nextDouble() - 0.5) * 0.3, y + (random.nextDouble() - 0.5) * 0.4, z + (random.nextDouble() - 0.5) * 0.3, v,
				color, sprites.get(random));
		}
		return last;
	}

	/**
	 * A piece's first speed: any way at all, with only {@code along} of what runs along {@code line} kept (so a shower
	 * off two meeting blades spreads out to their sides), at {@code speed}, plus {@code lift} up.
	 */
	private static Vec3 thrown(RandomSource random, Vec3 line, double along, double speed, double lift) {
		Vec3 any = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
		if (any.lengthSqr() < 1.0E-6) {
			any = new Vec3(0.0, 1.0, 0.0);
		}
		any = any.normalize();
		if (line.lengthSqr() > 1.0E-6) {
			any = any.subtract(line.scale(any.dot(line) * (1.0 - along)));
			if (any.lengthSqr() < 1.0E-6) {
				any = new Vec3(0.0, 1.0, 0.0);
			}
			any = any.normalize();
		}
		return any.scale(speed).add(0.0, lift * random.nextDouble(), 0.0);
	}

	/** One hot spark. */
	static final class Spark extends SingleQuadParticle {
		private final float fullSize;
		private boolean bounced;

		Spark(ClientLevel level, double x, double y, double z, Vec3 v, float scale, TextureAtlasSprite sprite) {
			super(level, x, y, z, sprite);
			this.xd = v.x;
			this.yd = v.y;
			this.zd = v.z;
			this.gravity = SPARK_GRAVITY;
			this.friction = 0.96F;
			this.hasPhysics = true;
			this.fullSize = SPARK_SIZE * scale * (0.7F + this.random.nextFloat() * 0.7F);
			this.quadSize = this.fullSize;
			this.lifetime = SPARK_LIFE_MIN + this.random.nextInt(SPARK_LIFE_MAX - SPARK_LIFE_MIN + 1);
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			this.heat(0.0F);
		}

		@Override
		public void tick() {
			double falling = this.yd;
			super.tick();
			if (this.onGround && !this.bounced && falling < -0.08) {
				this.bounced = true;
				this.onGround = false;
				this.yd = -falling * SPARK_BOUNCE;
			}
			this.heat(this.age / (float)this.lifetime);
		}

		/** White-hot, then orange, then a dull red, going out over the last third. */
		private void heat(float t) {
			if (t < 0.3F) {
				float k = t / 0.3F;
				this.setColor(1.0F, Mth.lerp(k, 0.98F, 0.78F), Mth.lerp(k, 0.82F, 0.28F));
			} else {
				float k = Mth.clamp((t - 0.3F) / 0.7F, 0.0F, 1.0F);
				this.setColor(Mth.lerp(k, 1.0F, 0.78F), Mth.lerp(k, 0.78F, 0.2F), Mth.lerp(k, 0.28F, 0.06F));
			}
			this.alpha = t < 0.66F ? 1.0F : Mth.clamp(1.0F - (t - 0.66F) / 0.34F, 0.0F, 1.0F);
		}

		@Override
		public float getQuadSize(float partial) {
			float t = Mth.clamp((this.age + partial) / this.lifetime, 0.0F, 1.0F);
			return this.fullSize * (1.0F - 0.6F * t);
		}

		@Override
		protected int getLightCoords(float partial) {
			return 0xF000F0;
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}

	/** One shard of a wooden shield. */
	static final class Shard extends SingleQuadParticle {
		private final float spin;

		Shard(ClientLevel level, double x, double y, double z, Vec3 v, int color, TextureAtlasSprite sprite) {
			super(level, x, y, z, sprite);
			this.xd = v.x;
			this.yd = v.y;
			this.zd = v.z;
			this.gravity = SHARD_GRAVITY;
			this.friction = 0.98F;
			this.hasPhysics = true;
			this.quadSize = SHARD_SIZE * (0.7F + this.random.nextFloat() * 0.7F);
			this.lifetime = SHARD_LIFE_MIN + this.random.nextInt(SHARD_LIFE_MAX - SHARD_LIFE_MIN + 1);
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			this.spin = (this.random.nextFloat() - 0.5F) * 2.0F * SHARD_TUMBLE;
			float shade = 0.85F + this.random.nextFloat() * 0.15F;
			this.setColor((color >> 16 & 0xFF) / 255.0F * shade, (color >> 8 & 0xFF) / 255.0F * shade, (color & 0xFF) / 255.0F * shade);
			this.alpha = 1.0F;
		}

		@Override
		public void tick() {
			super.tick();
			this.oRoll = this.roll;
			if (!this.onGround) {
				this.roll += this.spin;
			}
			int fadeFrom = this.lifetime - 8;
			this.alpha = this.age < fadeFrom ? 1.0F : Mth.clamp(1.0F - (this.age - fadeFrom) / 8.0F, 0.0F, 1.0F);
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}
}
