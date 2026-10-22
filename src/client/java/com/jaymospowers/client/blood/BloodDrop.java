package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3fc;

/**
 * (0.15.91) One drop of a {@link BloodParticles.Spray} (Iron's Simple Blood's {@code BloodParticle} and
 * {@code SpriteSheetBloodParticle}, ported): tossed up, falls hard, and where it lands leaves a {@link BloodPuddle}.
 * A sprite sheet ({@code frames} over 1: a spatter, spurt or swipe) plays its frames side by side; a swipe or spurt
 * ({@code directional}) turns to face the way it flies. Under water it drifts, slows, spreads and fades instead.
 */
final class BloodDrop extends SingleQuadParticle {
	/** Ticks a sheet's frame shows for. */
	static final int TICKS_PER_FRAME = 3;
	/** How hard it falls, and how long it can fly (ticks: at least this, up to this many more). */
	static final float GRAVITY = 1.5F;
	static final int LIFE = 100, LIFE_SPREAD = 40;
	/** A drop starts this many times a vanilla particle's size (times the spray's scale). */
	static final float SIZE = 2.5F;
	/** Under water: its speed and fall are cut to this, and it fades this much a frame (and grows a little). */
	static final float UNDERWATER_SLOW = 0.5F, UNDERWATER_FALL = 0.1F, UNDERWATER_FADE = 0.005F;

	private final int color;
	private final int frames;
	private final boolean directional;
	private final float growIn;
	private final boolean underwater;
	private boolean mirrored;

	BloodDrop(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, int frames,
		int textureSize, boolean directional, int color, float scale) {
		super(level, x, y, z, xd, yd, zd, sprites.first());
		this.color = color;
		this.frames = frames;
		this.directional = directional;
		this.xd = xd;
		this.yd = yd * 1.5 + 0.15;
		this.zd = zd;
		this.quadSize *= 1.0F + this.random.nextFloat();
		this.scale(scale * SIZE);
		this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD);
		this.gravity = GRAVITY;
		this.setSprite(frames > 1 ? sprites.get(0, 1) : sprites.get(this.random));
		if (frames > 1) {
			this.quadSize *= textureSize / 16.0F;
		}
		this.setColor(BloodPuddle.red(color), BloodPuddle.green(color), BloodPuddle.blue(color));
		this.growIn = 1.0F + this.random.nextFloat();
		this.mirrored = this.random.nextBoolean();
		this.underwater = !level.getFluidState(BlockPos.containing(x, y, z)).isEmpty();
		if (this.underwater) {
			this.xd *= UNDERWATER_SLOW;
			this.yd *= UNDERWATER_SLOW;
			this.zd *= UNDERWATER_SLOW;
			this.gravity *= UNDERWATER_FALL;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.underwater) {
			this.gravity *= 0.99F;
		}
		if (this.onGround) {
			if (this.alpha > 0.5F) {
				// (a drop that has faded under water leaves nothing)
				Vec3 at = new Vec3(this.x, this.y, this.z);
				Vec3 ground = this.level.clip(new ClipContext(at.add(0.0, 0.6, 0.0), at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
					CollisionContext.empty())).getLocation();
				this.level.addParticle(new BloodParticles.Puddle(this.color, this.getQuadSize(0.0F)), true, false, ground.x, ground.y, ground.z,
					0.0, 0.0, 0.0);
			}
			this.remove();
		}
	}

	@Override
	public float getQuadSize(float partialTick) {
		float age = this.age + partialTick;
		return super.getQuadSize(partialTick) * (age > this.growIn ? 1.0F : age / (this.growIn * 2.0F) + 0.5F);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return this.underwater ? SingleQuadParticle.Layer.TRANSLUCENT : SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		if (this.directional) {
			Vector3fc left = camera.leftVector();
			double length = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
			if (length > 1.0E-6) {
				double dot = (left.x() * this.xd + left.z() * this.zd) / length;
				if (Math.abs(dot) > 0.1) {
					// the sheet is drawn flying right: mirror it when it flies left on screen
					this.mirrored = dot >= 0.0;
				}
			}
		}
		if (this.underwater) {
			this.alpha -= UNDERWATER_FADE;
			this.scale(1.005F);
			if (this.alpha < 0.1F) {
				this.remove();
				return;
			}
		}
		super.extract(state, camera, partialTick);
	}

	@Override
	protected float getU0() {
		return this.mirrored ? this.frameU(1) : this.frameU(0);
	}

	@Override
	protected float getU1() {
		return this.mirrored ? this.frameU(0) : this.frameU(1);
	}

	/** The left ({@code 0}) or right ({@code 1}) edge of the frame showing now. */
	private float frameU(int edge) {
		float u0 = super.getU0(), u1 = super.getU1();
		if (this.frames <= 1) {
			return edge == 0 ? u0 : u1;
		}
		int frame = Math.min(this.age / TICKS_PER_FRAME, this.frames - 1);
		return Mth.lerp((frame + edge) / (float) this.frames, u0, u1);
	}
}
