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
 * (0.15.93) One drop of a {@link BloodParticles.Spray} (Iron's Simple Blood's {@code BloodParticle} and
 * {@code SpriteSheetBloodParticle}, ported): tossed up, falls hard, and where it lands leaves a {@link BloodPuddle}.
 * A sprite sheet ({@code frames} over 1: a spatter, spurt or swipe) plays its frames side by side; a swipe or spurt
 * ({@code directional}) turns to face the way it flies. Under water it drifts, slows, spreads and fades instead.
 * <p>(0.20.41) One that flies into a wall splats on it ({@link BloodWallMark#splat}). Magma blood glows as it flies.
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
	/** (0.17.182) Whether it leaves its own splash where it lands (a cut's drip doesn't: the server lays its stain). */
	private final boolean stains;
	private boolean mirrored;
	/** (0.20.86) A drip: drawn on its sheet's first frame only. */
	private boolean still;

	BloodDrop(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, int frames,
		int textureSize, boolean directional, int color, float scale, boolean stains) {
		super(level, x, y, z, xd, yd, zd, sprites.first());
		this.color = color;
		this.stains = stains;
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

	/** (0.20.41) A drop off the end of a run on a wall: no toss, it just falls. */
	BloodDrop drip() {
		// (0.20.86) it stays a drop the whole way down (its sheet's first frame), never opening into the star
		this.still = true;
		this.xd = 0.0;
		this.zd = 0.0;
		this.yd = -0.02;
		return this;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		// (0.20.41) magma glows
		return this.color == BloodParticles.MAGMA ? 0xF000F0 : super.getLightCoords(partialTick);
	}

	@Override
	public void tick() {
		double px = this.x, py = this.y, pz = this.z, pxd = this.xd, pyd = this.yd, pzd = this.zd;
		super.tick();
		if (this.stains && !this.underwater && !this.removed && !this.onGround && this.alpha > 0.5F
			&& (pxd != 0.0 && this.xd == 0.0 || pzd != 0.0 && this.zd == 0.0)) {
			// (0.20.41) it hit a wall: find the face it hit and splat there
			Vec3 from = new Vec3(px, py, pz);
			Vec3 to = from.add(pxd * 2.0 + Math.signum(pxd) * 0.1, pyd, pzd * 2.0 + Math.signum(pzd) * 0.1);
			var hit = this.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getDirection().getAxis() != net.minecraft.core.Direction.Axis.Y) {
				BloodWallMark.splat(this.level, hit.getBlockPos(), hit.getLocation(), hit.getDirection(), this.color, this.getQuadSize(0.0F));
				this.remove();
				return;
			}
		}
		if (this.underwater) {
			this.gravity *= 0.99F;
		}
		if (this.onGround) {
			if (this.stains && this.alpha > 0.5F) {
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

	/**
	 * (0.20.66) A drop this close (blocks, sideways) under a first-person camera isn't drawn: it is the viewer's own
	 * bleeding, falling past their eyes, and drawn there it looked like the stains ahead splattering by themselves (Andy:
	 * "the stains on the ground were splattering when it should have been static"). Its stain still lands.
	 */
	static final double OWN_DRIP_REACH = 0.45;

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		if (ownDrip(camera, partialTick)) {
			return;
		}
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

	/** (0.20.66) Whether it falls right past a first-person viewer's eyes ({@link #OWN_DRIP_REACH}). */
	private boolean ownDrip(Camera camera, float partialTick) {
		if (camera.isDetached()) {
			return false;
		}
		Vec3 eye = camera.position();
		double x = Mth.lerp(partialTick, this.xo, this.x), y = Mth.lerp(partialTick, this.yo, this.y), z = Mth.lerp(partialTick, this.zo, this.z);
		double dx = x - eye.x, dz = z - eye.z;
		return y < eye.y + 0.1 && dx * dx + dz * dz < OWN_DRIP_REACH * OWN_DRIP_REACH;
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
		int frame = this.still ? 0 : Math.min(this.age / TICKS_PER_FRAME, this.frames - 1);
		return Mth.lerp((frame + edge) / (float) this.frames, u0, u1);
	}
}
