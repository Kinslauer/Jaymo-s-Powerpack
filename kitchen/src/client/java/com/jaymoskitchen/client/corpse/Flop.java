package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.corpse.CorpseEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.4.39) A corpse's ragdoll physics, worked out on each client (the server only knows where the body is). Every
 * limb and the head hang on a damped spring around the pose the body would take at rest (sprawled, keeled over or
 * hanging limp), so anything that moves the body flings them about and they flop back: dying, landing from a fall
 * or a throw, a blow, being rummaged through, someone walking over it, an explosion shoving it, a cart jolting it.
 * A body's limbs slap down onto the ground and bounce rather than sinking into it.
 * <p>
 * The whole body moves the same way: whenever it changes how it lies (it dies, is picked up, dropped, lands), it
 * swings from where it was into its new place instead of jumping there, falling under gravity and bouncing when it
 * comes down. Lying still, it settles over the ground under it, tipped along slopes, steps and slabs, and bounces a
 * little when it hits the ground hard.
 */
public final class Flop {
	/** Set on the body's render state: the corpse's physics and this frame's partial tick (see {@link #apply}). */
	public static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:flop");

	public record Frame(Flop flop, float partialTicks) {
	}

	/** How the body is held: lying on something, carried across someone's arms, or falling from them. */
	public enum Layout {
		LYING, CARRIED, TUMBLING
	}

	/** The limbs that flop, wherever a model has them (on the root, or on its body). The head comes first. */
	private static final String[] LIMBS = {
		"head", "right_arm", "left_arm", "right_leg", "left_leg",
		"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg",
		"right_wing", "left_wing", "tail", "right_ear", "left_ear"
	};
	private static final int SLOTS = LIMBS.length * 2;

	/** Limbs: the spring pulling them back to rest, how much of their swing survives a tick, and the most they bend. */
	private static final float LIMB_STIFFNESS = 0.16F, LIMB_DAMPING = 0.8F, LIMB_LIMIT = 3.2F;
	/** A limb flung up off the ground falls back this much faster each tick, and bounces this much when it hits. */
	private static final float LIMB_GRAVITY = 0.05F, LIMB_BOUNCE = 0.35F;
	/** Dragging along the ground: how much of a lying limb's sideways swing survives a tick. */
	private static final float GROUND_FRICTION = 0.6F;
	/** The head flops this much as hard as the limbs. */
	private static final float HEAD_GIVE = 0.5F;

	/** Settling into a new place lying down: it falls into it, faster and faster, and bounces when it gets there. */
	private static final float FALL_START = 0.04F, FALL_PULL = 0.035F, FALL_BOUNCE = 0.3F, FALL_MIN_BOUNCE = 0.06F;
	/** Swinging into a new place held up (picked up, dropped): a quick damped spring. */
	private static final float SWING_STIFFNESS = 0.3F, SWING_DAMPING = 0.55F;

	/** The whole body bouncing up off the ground: gravity, and how much of the speed survives a bounce. */
	private static final float BOB_GRAVITY = 0.04F, BOB_BOUNCE = 0.3F, BOB_MAX = 0.18F;

	/** Lying on uneven ground: the steepest it tips (as a slope, 0.7 = 35 degrees), and how fast it settles. */
	private static final float SLOPE_MAX = 0.7F, SLOPE_FOLLOW = 0.3F;
	/** Ground no higher than this above the body counts as something it lies over; higher is a wall it lies against. */
	private static final double STEP_UP = 0.6;

	/** A body this fresh (ticks since it died) the first time it's seen falls into place from the dying mob's pose. */
	private static final long FRESH_TICKS = 4;
	/** Someone walking over a body kicks its limbs this often at most (ticks), and only moving at least this fast. */
	private static final int TREAD_EVERY = 5;
	private static final double TREAD_SPEED = 0.04;
	/** The body itself shoved this hard in one tick (a change of speed, blocks a tick) flings its limbs. */
	private static final double SHOVE_MIN = 0.04;

	private static final Map<CorpseEntity, Flop> ALL = new WeakHashMap<>();
	private static final Quaternionf IDENTITY = new Quaternionf();

	public static Flop of(CorpseEntity corpse) {
		return ALL.computeIfAbsent(corpse, Flop::new);
	}

	private final RandomSource random;
	private int tick;
	private Layout layout;
	private boolean fresh;
	/** The lying body is a sprawled humanoid (see Ragdoll): its limbs lie on the ground, which way up it lies. */
	private boolean onGround, faceUp;
	private float length = 1.0F;

	private final float[] offX = new float[SLOTS], offZ = new float[SLOTS], offXO = new float[SLOTS], offZO = new float[SLOTS];
	private final float[] velX = new float[SLOTS], velZ = new float[SLOTS];
	private final float[] lastX = new float[SLOTS], lastZ = new float[SLOTS], restX = new float[SLOTS];
	private final boolean[] seen = new boolean[SLOTS];
	/** The limbs swing from where they were drawn last (or, fresh, from standing straight) into the new pose. */
	private boolean resetAsked, resetting, fromStanding;

	private float blend = 1.0F, blendO = 1.0F, blendVel;
	private boolean bouncy, blendAsked;
	private final Quaternionf blendTurn = new Quaternionf();
	private final Vector3f blendMove = new Vector3f();
	/** Where the body was drawn last, relative to where the corpse was then (in the world). */
	private Matrix4f lastShown;
	private double shownX, shownY, shownZ;

	private float bob, bobO, bobVel;
	private float slopeX, slopeZ, slopeXO, slopeZO;

	private float joltSeen;
	private double lastDx, lastDy, lastDz;
	private float fallSpeed;
	private int treadCooldown;

	private Flop(CorpseEntity corpse) {
		this.random = RandomSource.create(corpse.getId() * 31L + corpse.poseSeed());
	}

	/**
	 * Client, each frame before drawing: catches the physics up to the corpse's tick. {@code humanoid} is set for a
	 * body lying sprawled (see Ragdoll), whose limbs lie along the ground; {@code length} is the body's length.
	 */
	public void update(CorpseEntity corpse, Layout layout, boolean humanoid, boolean faceUp, float length) {
		this.onGround = humanoid && layout == Layout.LYING;
		this.faceUp = faceUp;
		this.length = length;
		// only the first frame after a change of pose swings the limbs from where they were
		this.resetting = false;
		this.fromStanding = false;
		if (this.layout == null) {
			this.layout = layout;
			this.tick = corpse.tickCount;
			this.fresh = layout == Layout.LYING && corpse.sinceDeath() <= FRESH_TICKS;
			this.lastDx = corpse.getX() - corpse.xo;
			this.lastDy = corpse.getY() - corpse.yo;
			this.lastDz = corpse.getZ() - corpse.zo;
		} else if (layout != this.layout) {
			Layout was = this.layout;
			this.layout = layout;
			this.resetAsked = true;
			this.blendAsked = true;
			this.bouncy = layout == Layout.LYING;
			if (was == Layout.TUMBLING && layout == Layout.LYING) {
				// it hit the ground: the harder, the more it bounces and its limbs fly
				kick(Math.min(1.0F, 0.2F + fallSpeed * 1.5F));
				bobVel = Math.min(BOB_MAX, fallSpeed * 0.35F);
			}
		}
		if (this.resetAsked) {
			this.resetAsked = false;
			this.resetting = true;
		}
		int steps = corpse.tickCount - this.tick;
		if (steps > 20) {
			// not drawn for a while (out of sight): it has long since settled
			steps = 1;
			this.lastShown = null;
		}
		for (int i = 0; i < steps; i++) {
			step(corpse);
		}
		this.tick = corpse.tickCount;
	}

	/**
	 * A fresh body, the first time it's drawn: it falls from {@code dying} (where the dying mob was last drawn, keeled
	 * over) into place, limbs swinging out from straight as they were. True once.
	 */
	public boolean fallFrom(Matrix4f dying) {
		if (!this.fresh) {
			return false;
		}
		this.fresh = false;
		this.lastShown = new Matrix4f(dying);
		this.shownX = Double.NaN;
		this.blendAsked = true;
		this.bouncy = true;
		this.resetting = true;
		this.fromStanding = true;
		kick(0.5F);
		return true;
	}

	private void step(CorpseEntity corpse) {
		Level level = corpse.level();
		double dx = corpse.getX() - corpse.xo, dy = corpse.getY() - corpse.yo, dz = corpse.getZ() - corpse.zo;
		if (layout == Layout.TUMBLING) {
			fallSpeed = (float) Math.max(0.0, -dy);
		} else if (layout == Layout.LYING) {
			// shoved (an explosion, a gravity power, a cart lurching): the limbs lag behind the body and fly
			double ax = dx - lastDx, ay = dy - lastDy, az = dz - lastDz;
			double shove = Math.sqrt(ax * ax + ay * ay + az * az);
			if (shove > SHOVE_MIN) {
				kick((float) Math.min(0.7, shove * 4.0));
			}
			tread(corpse, level);
		}
		lastDx = dx;
		lastDy = dy;
		lastDz = dz;

		// a blow or rummaging: a fresh jolt flings the limbs and bounces the body a little
		float jolt = corpse.jolt(0.0F);
		if (jolt > joltSeen + 0.05F) {
			kick(jolt * 0.55F);
			bobVel = Math.max(bobVel, jolt * 0.06F);
		}
		joltSeen = jolt;

		stepLimbs();
		stepBlend();

		bobO = bob;
		if (bob > 0.0F || bobVel != 0.0F) {
			bobVel -= BOB_GRAVITY;
			bob += bobVel;
			if (bob <= 0.0F) {
				bob = 0.0F;
				bobVel = bobVel < -0.03F ? -bobVel * BOB_BOUNCE : 0.0F;
			}
		}

		slopeXO = slopeX;
		slopeZO = slopeZ;
		float wantX = 0.0F, wantZ = 0.0F;
		if (layout == Layout.LYING && !corpse.isPassenger() && !corpse.isNoGravity()) {
			double r = Mth.clamp(length / 2.0F, 0.3F, 1.0F);
			double y = corpse.getY();
			double east = groundAt(level, corpse.getX() + r, y, corpse.getZ()), west = groundAt(level, corpse.getX() - r, y, corpse.getZ());
			double south = groundAt(level, corpse.getX(), y, corpse.getZ() + r), north = groundAt(level, corpse.getX(), y, corpse.getZ() - r);
			wantX = (float) Mth.clamp((east - west) / (2.0 * r), -SLOPE_MAX, SLOPE_MAX);
			wantZ = (float) Mth.clamp((south - north) / (2.0 * r), -SLOPE_MAX, SLOPE_MAX);
		}
		slopeX += (wantX - slopeX) * SLOPE_FOLLOW;
		slopeZ += (wantZ - slopeZ) * SLOPE_FOLLOW;
	}

	/** Someone walking (or running) through the body kicks its limbs about. */
	private void tread(CorpseEntity corpse, Level level) {
		if (treadCooldown > 0) {
			treadCooldown--;
			return;
		}
		double fastest = 0.0;
		for (Entity walker : level.getEntities(corpse, corpse.getBoundingBox().inflate(0.15),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			double mx = walker.getX() - walker.xo, mz = walker.getZ() - walker.zo;
			fastest = Math.max(fastest, Math.sqrt(mx * mx + mz * mz));
		}
		if (fastest > TREAD_SPEED) {
			kick((float) Math.min(0.5, fastest * 2.5));
			bobVel = Math.max(bobVel, 0.02F);
			treadCooldown = TREAD_EVERY;
		}
	}

	/** The top of whatever the body would lie on at this spot: from a little above it to a block and a half below. */
	private static double groundAt(Level level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(y + 0.5);
		for (int by = top; by >= top - 2; by--) {
			pos.set(Mth.floor(x), by, Mth.floor(z));
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double surface = by + shape.max(Direction.Axis.Y);
				// a wall it lies against rather than over: it doesn't climb it
				return surface <= y + STEP_UP ? surface : y;
			}
		}
		return y - 1.5;
	}

	/** Flings every limb, each its own way: lying on the ground, up off it (they can't go into it). */
	private void kick(float strength) {
		float down = faceUp ? 1.0F : -1.0F;
		for (int i = 0; i < SLOTS; i++) {
			float give = i % LIMBS.length == 0 ? HEAD_GIVE : 1.0F;
			float up = onGround ? -down * (0.5F + random.nextFloat() * 0.5F) : (random.nextFloat() * 2.0F - 1.0F);
			velX[i] += up * strength * give;
			velZ[i] += (random.nextFloat() - 0.5F) * 1.2F * strength * give;
		}
	}

	private void stepLimbs() {
		float down = faceUp ? 1.0F : -1.0F;
		for (int i = 0; i < SLOTS; i++) {
			offXO[i] = offX[i];
			offZO[i] = offZ[i];
			if (!seen[i]) {
				continue;
			}
			boolean head = i % LIMBS.length == 0;
			boolean lies = onGround && !head;
			velX[i] = (velX[i] - offX[i] * LIMB_STIFFNESS) * LIMB_DAMPING;
			velZ[i] = (velZ[i] - offZ[i] * LIMB_STIFFNESS) * (lies ? LIMB_DAMPING * GROUND_FRICTION : LIMB_DAMPING);
			if (lies && offX[i] * down < 0.0F) {
				// up off the ground: it falls back
				velX[i] += LIMB_GRAVITY * down;
			}
			offX[i] = Mth.clamp(offX[i] + velX[i], -LIMB_LIMIT, LIMB_LIMIT);
			offZ[i] = Mth.clamp(offZ[i] + velZ[i], -LIMB_LIMIT, LIMB_LIMIT);
			if (lies && (restX[i] + offX[i]) * down > 0.0F && offX[i] * down > 0.0F) {
				// slapped down onto the ground: it stops there and bounces
				offX[i] = Math.max(-restX[i] * down, 0.0F) * down;
				if (velX[i] * down > 0.0F) {
					velX[i] = -velX[i] * LIMB_BOUNCE;
				}
			}
		}
	}

	private void stepBlend() {
		blendO = blend;
		if (blend >= 1.0F && blendVel == 0.0F) {
			return;
		}
		if (bouncy) {
			blendVel += FALL_PULL;
			blend += blendVel;
			if (blend >= 1.0F) {
				blend = 1.0F;
				blendVel = blendVel > FALL_MIN_BOUNCE ? -blendVel * FALL_BOUNCE : 0.0F;
			}
		} else {
			blendVel = (blendVel + (1.0F - blend) * SWING_STIFFNESS) * SWING_DAMPING;
			blend += blendVel;
			if (Math.abs(1.0F - blend) < 0.002F && Math.abs(blendVel) < 0.002F) {
				blend = 1.0F;
				blendVel = 0.0F;
			}
		}
	}

	/**
	 * Client, each frame: {@code placement} is where the body is drawn now, relative to the corpse's own spot (at x, y,
	 * z in the world), as the renderer places it. Fills in this frame's tilt, bounce and the swing still to go from
	 * where it was drawn before.
	 */
	public void placed(Matrix4f placement, double x, double y, double z, float partialTicks, Quaternionf tilt, Vector3f move,
		Quaternionf turn, float[] lift) {
		if (blendAsked) {
			blendAsked = false;
			if (lastShown != null) {
				// where it was, as seen from where the corpse is now (picked up, it jumps up into someone's arms)
				Matrix4f from = Double.isNaN(shownX) ? new Matrix4f()
					: new Matrix4f().translation((float) (shownX - x), (float) (shownY - y), (float) (shownZ - z));
				from.mul(lastShown).mul(new Matrix4f(placement).invert());
				from.getNormalizedRotation(blendTurn);
				from.getTranslation(blendMove);
				float angle = 2.0F * (float) Math.acos(Math.min(1.0F, Math.abs(blendTurn.w)));
				if (angle > 0.05F || blendMove.lengthSquared() > 0.0025F) {
					blend = blendO = 0.0F;
					blendVel = bouncy ? FALL_START : 0.0F;
				}
			}
		}
		float p = Mth.lerp(partialTicks, blendO, blend);
		turn.set(blendTurn).slerp(IDENTITY, p);
		move.set(blendMove).mul(1.0F - p);
		if (p >= 1.0F) {
			turn.identity();
			move.zero();
		}
		float sx = Mth.lerp(partialTicks, slopeXO, slopeX), sz = Mth.lerp(partialTicks, slopeZO, slopeZ);
		tilt.rotationTo(0.0F, 1.0F, 0.0F, -sx, 1.0F, -sz);
		lift[0] = Mth.lerp(partialTicks, bobO, bob);
		this.lastShown = new Matrix4f().translation(move).rotate(turn).mul(placement);
		this.shownX = x;
		this.shownY = y;
		this.shownZ = z;
	}

	/** Right after a corpse's model has been posed (see Limp#apply): its limbs moved by their physics. */
	public static void apply(Model<?> model, Object state) {
		if (state instanceof EntityRenderState renderState && renderState.getData(KEY) instanceof Frame frame) {
			frame.flop.pose(model.root(), frame.partialTicks);
		}
	}

	private void pose(ModelPart root, float partialTicks) {
		pose(root, 0, partialTicks);
		if (root.hasChild("body")) {
			pose(root.getChild("body"), LIMBS.length, partialTicks);
		}
	}

	private void pose(ModelPart parent, int slot, float partialTicks) {
		for (int i = 0; i < LIMBS.length; i++) {
			if (!parent.hasChild(LIMBS[i])) {
				continue;
			}
			int k = slot + i;
			ModelPart limb = parent.getChild(LIMBS[i]);
			if (!seen[k]) {
				seen[k] = true;
				lastX[k] = limb.xRot;
				lastZ[k] = limb.zRot;
			}
			restX[k] = limb.xRot;
			if (resetting) {
				// from where it was drawn last (or straight, as it stood) into this pose, keeping any swing it had
				float fromX = fromStanding ? 0.0F : lastX[k], fromZ = fromStanding ? 0.0F : lastZ[k];
				offX[k] = offXO[k] = Mth.clamp(fromX - limb.xRot, -LIMB_LIMIT, LIMB_LIMIT);
				offZ[k] = offZO[k] = Mth.clamp(fromZ - limb.zRot, -LIMB_LIMIT, LIMB_LIMIT);
			}
			limb.xRot += Mth.lerp(partialTicks, offXO[k], offX[k]);
			limb.zRot += Mth.lerp(partialTicks, offZO[k], offZ[k]);
			lastX[k] = limb.xRot;
			lastZ[k] = limb.zRot;
		}
	}
}
