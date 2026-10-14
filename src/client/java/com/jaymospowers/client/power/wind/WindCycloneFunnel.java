package com.jaymospowers.client.power.wind;

import com.jaymospowers.core.Fate;
import com.jaymospowers.power.wind.WindCyclone;
import com.jaymospowers.power.wind.WindCycloneElement;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Cyclone's funnel as everyone sees it: drawn like a Jaymo's Kingdoms tornado (soft puffs whirling up a funnel,
 * fastest near the middle, with dust churned round the foot), but white, and tinted by what it has drawn up (see
 * {@link WindCycloneElement}). Each wisp follows its player's Cyclone and fades if it ends.
 */
final class WindCycloneFunnel {
	/** Funnels are drawn for Cyclones this near (blocks). */
	static final double VIEW = 160.0;
	/** Wisps and foot debris per tick, at full size. */
	static final int WISPS = 14, DEBRIS = 3;
	/** A wisp's size (blocks, grown toward the top) and life (ticks). */
	static final float WISP_SIZE = 1.3F;
	static final int WISP_LIFE = 30;
	/** How much of an element's tint the wisps take (the rest stays white). */
	static final float TINT = 0.55F;

	private static @Nullable SpriteSet sprites;

	private WindCycloneFunnel() {
	}

	static void register() {
		ParticleProviderRegistry.getInstance().register(WindCyclone.FUNNEL_WISP, set -> {
			sprites = set;
			// (spawned only from here, never by type)
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});
	}

	/** Every client tick: wisps for each Cyclone in view. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || sprites == null || minecraft.isPaused()) {
			return;
		}
		if (minecraft.player == null) {
			return;
		}
		Vec3 eye = minecraft.player.position();
		RandomSource random = level.getRandom();
		for (AbstractClientPlayer player : level.players()) {
			WindCyclone.Stance stance = player.getAttached(WindCyclone.CYCLONE);
			if (stance == null || player.position().distanceToSqr(eye) > VIEW * VIEW) {
				continue;
			}
			float grown = stance.grown(level.getGameTime());
			float[] tint = tint(player, WindCycloneElement.byId(stance.element()));
			int wisps = Math.max(2, Math.round(WISPS * (0.4F + 0.6F * grown)));
			for (int i = 0; i < wisps; i++) {
				float h = random.nextFloat() * stance.height() * 0.9F * Math.max(0.2F, grown);
				float shade = 0.9F + random.nextFloat() * 0.1F;
				float mix = random.nextFloat() < 0.6F ? TINT : 0.0F;
				float r = Mth.lerp(mix, shade, tint[0]), g = Mth.lerp(mix, shade, tint[1]), b = Mth.lerp(mix, shade, tint[2]);
				float size = WISP_SIZE * (0.7F + 0.6F * random.nextFloat()) * (0.6F + 0.8F * h / stance.height());
				minecraft.particleEngine.add(new Wisp(level, player, sprites.get(random), false, size, WISP_LIFE + random.nextInt(15), r, g, b, 0.8F)
					.at(h, random.nextFloat() * Mth.TWO_PI, 0.75F + random.nextFloat() * 0.35F, 0.12F + random.nextFloat() * 0.2F));
			}
			for (int i = 0; i < DEBRIS; i++) {
				float tone = random.nextFloat() * 0.12F;
				minecraft.particleEngine.add(new Wisp(level, player, sprites.get(random), true, WISP_SIZE * (0.4F + random.nextFloat() * 0.4F),
					20 + random.nextInt(20), 0.6F + tone, 0.55F + tone, 0.45F + tone, 0.55F)
					.at(random.nextFloat() * 1.2F, random.nextFloat() * Mth.TWO_PI, 1.3F + random.nextFloat() * 0.6F, 0.03F + random.nextFloat() * 0.06F));
			}
		}
	}

	/** The colour an element (or a Stormbound user's corruption) lends the funnel. */
	private static float[] tint(Player player, WindCycloneElement element) {
		return switch (element) {
			case FIRE -> new float[] {1.0F, 0.55F, 0.3F};
			case WATER -> new float[] {0.65F, 0.8F, 1.0F};
			case SNOW -> new float[] {0.92F, 0.97F, 1.0F};
			case SAND -> new float[] {0.92F, 0.8F, 0.58F};
			case STORM -> new float[] {0.62F, 0.62F, 0.75F};
			case GALE -> new float[] {0.85F, 0.95F, 1.0F};
			default -> Fate.isCorrupted(player) ? new float[] {0.75F, 0.6F, 0.95F} : new float[] {1.0F, 1.0F, 1.0F};
		};
	}

	/** A puff of the funnel (or the dust at its foot), whirling round and up its player's Cyclone. */
	private static final class Wisp extends SingleQuadParticle {
		private static final int FADE = 8;
		private final int owner;
		private final boolean debris;
		private final float baseAlpha;
		private float h, angle, out, climb;

		Wisp(ClientLevel level, Entity owner, TextureAtlasSprite sprite, boolean debris, float size, int life, float r, float g, float b, float alpha) {
			super(level, owner.getX(), owner.getY(), owner.getZ(), sprite);
			this.owner = owner.getId();
			this.debris = debris;
			this.quadSize = size;
			this.lifetime = life;
			this.hasPhysics = false;
			this.gravity = 0.0F;
			this.friction = 1.0F;
			this.baseAlpha = alpha;
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			this.setColor(r, g, b);
			this.setAlpha(0.0F);
		}

		/** Starts {@code h} up, at {@code angle}, {@code out} of the radius there, climbing {@code climb} a tick. */
		Wisp at(float h, float angle, float out, float climb) {
			this.h = h;
			this.angle = angle;
			this.out = out;
			this.climb = climb;
			this.place(this.level.getEntity(this.owner));
			this.xo = this.x;
			this.yo = this.y;
			this.zo = this.z;
			return this;
		}

		@Override
		public void tick() {
			this.xo = this.x;
			this.yo = this.y;
			this.zo = this.z;
			this.oRoll = this.roll;
			if (this.age++ >= this.lifetime) {
				this.remove();
				return;
			}
			Entity owner = this.level.getEntity(this.owner);
			WindCyclone.Stance stance = owner instanceof Player p ? p.getAttached(WindCyclone.CYCLONE) : null;
			if (stance == null) {
				// the Cyclone let go: drift up and fade
				this.y += 0.15;
				this.age = Math.max(this.age, this.lifetime - FADE);
			} else {
				float r = Math.max(0.4F, stance.radiusAt(this.h) * stance.grown(this.level.getGameTime()) * this.out);
				// faster nearer the middle
				this.angle += (this.debris ? 0.8F : 1.4F) / Math.max(1.0F, r);
				this.h += this.climb;
				if (this.debris) {
					this.out += 0.02F;
				}
				this.place(owner);
				if (this.h > stance.height()) {
					this.age = Math.max(this.age, this.lifetime - FADE);
				}
			}
			this.roll += 0.06F;
			float fade = Math.min(1.0F, Math.min(this.age / (float)FADE, (this.lifetime - this.age) / (float)FADE));
			this.alpha = this.baseAlpha * Mth.clamp(fade, 0.0F, 1.0F);
		}

		private void place(@Nullable Entity owner) {
			if (!(owner instanceof Player p) || p.getAttached(WindCyclone.CYCLONE) == null) {
				return;
			}
			WindCyclone.Stance stance = p.getAttached(WindCyclone.CYCLONE);
			float r = Math.max(0.4F, stance.radiusAt(this.h) * stance.grown(this.level.getGameTime()) * this.out);
			this.x = owner.getX() + Mth.cos(this.angle) * r;
			this.z = owner.getZ() + Mth.sin(this.angle) * r;
			this.y = owner.getY() + this.h;
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}
}
