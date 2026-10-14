package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Riding the earthboard (rock flight), drawn for everyone: a low surfer's crouch on the slab, leaning far forward
 * (about 30 degrees, from the hips) with both arms reaching out in front and spread, front foot forward, back foot
 * behind, eyes up ahead. It carves: the whole body leans into a turn or a slide, pivoting on the feet, and the inside
 * hand drops while the outside one rises. Climbing, the board's nose comes up and the rider sinks deeper; sinking,
 * it dips and they rise; at speed it noses down a touch. Rider and board bob together. Every lean and tilt is a
 * damped spring, so it swings past and settles rather than snapping. Worked out from the actual motion (how fast the
 * rider is turning, sliding, climbing and going), so other players see it too; the board is drawn to match
 * ({@code BlockClusterRenderer}).
 */
public final class GravityBoardPose {
	/** Lean (degrees) per degree/tick of turning, and per block/tick of sideways slide; never more than {@code MAX_LEAN}. */
	static final float TURN_LEAN = 1.8F, SLIDE_LEAN = 45.0F, MAX_LEAN = 30.0F;
	/** The forward lean from the hips (degrees), and the little extra at full speed ({@code TOP_SPEED} blocks/tick). */
	static final float PITCH = 30.0F, SPEED_PITCH = 5.0F, TOP_SPEED = 0.95F;
	/** The hips (blocks over the feet), and how far the crouch sinks the body. */
	static final float HIP = 0.75F, CROUCH = 0.12F;
	/** Both arms reach out in front (radians from hanging, + forward) and spread apart; the lean swings them. */
	static final float ARM_FORWARD = 0.95F, ARM_SPREAD = 0.6F, ARM_LEAN = 0.5F;
	/** The stance: front leg forward, back leg behind (radians, before standing them up against the lean). */
	static final float FRONT_LEG = 0.7F, BACK_LEG = 0.6F;

	/** The board tips nose-up this far (degrees) climbing flat out, and nose-down this far at top speed. */
	static final float CLIMB_TILT = 10.0F, SPEED_DIP = 4.0F;
	/** Climbing or sinking at this speed (blocks/tick) is "flat out" for the tilt. */
	static final float CLIMB_FULL = 0.18F;
	/** The rider sinks deeper into the crouch climbing (blocks), and rises a little sinking. */
	static final float CLIMB_CROUCH = 0.06F;
	/** The slow bob of the board and its rider together (blocks, and radians per tick). */
	static final float BOB = 0.035F, BOB_RATE = 0.11F;
	/**
	 * Every lean and tilt is a damped spring chasing its target (like Wind's hover sway): stiffness and damping per
	 * tick. It follows quickly and settles after one soft overshoot, so a sharp turn swings the rider past and back.
	 */
	static final float SPRING = 0.16F, DAMPING = 0.5F;

	/**
	 * {@code lean}: degrees, + is to the rider's left; {@code pitch}: degrees forward from the hips; {@code tilt}: the
	 * board's nose-up (degrees, the rider tips with it); {@code climb}: -1 sinking to 1 climbing; {@code bob}: blocks.
	 */
	public record Ride(float lean, float pitch, float time, float tilt, float climb, float bob) {
	}

	static final RenderStateDataKey<Ride> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_board");

	/** Each rider's springs: lean, its speed, tilt, its speed, climb, its speed, forward pitch, and when last worked out. */
	private static final Int2ObjectOpenHashMap<float[]> SPRINGS = new Int2ObjectOpenHashMap<>();
	private static final int LEAN = 0, TILT = 2, CLIMB = 4, PITCH_EXTRA = 6, TIME = 8;

	private GravityBoardPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || player.getAttached(GravityPower.ROCK_FLIGHT) == null || player.isPassenger()) {
			SPRINGS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		Ride ride = ride(player, partialTicks);
		state.setData(KEY, ride);
	}

	/** The rider's lean and tilt right now (springs stepped to this frame); the board itself is drawn to match. */
	public static Ride ride(Player player, float partialTicks) {
		float yaw = player.getYRot();
		double lookX = -Mth.sin(yaw * Mth.DEG_TO_RAD), lookZ = Mth.cos(yaw * Mth.DEG_TO_RAD);
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo, dy = player.getY() - player.yo;
		double forward = dx * lookX + dz * lookZ;
		// the rider's right is (-lookZ, lookX): sliding left is negative
		double side = dx * -lookZ + dz * lookX;
		float turning = Mth.wrapDegrees(yaw - player.yRotO);
		float speed = (float)Math.sqrt(dx * dx + dz * dz);
		// a turn standing still barely leans; at speed it carves
		float carve = Mth.clamp(0.3F + speed / 0.6F, 0.3F, 1.0F);
		float lean = Mth.clamp(-turning * TURN_LEAN * carve - (float)side * SLIDE_LEAN, -MAX_LEAN, MAX_LEAN);
		float ahead = Mth.clamp((float)forward / TOP_SPEED, 0.0F, 1.0F);
		float climb = Mth.clamp((float)dy / CLIMB_FULL, -1.0F, 1.0F);
		float tilt = climb * CLIMB_TILT - ahead * SPEED_DIP;

		float time = player.tickCount + partialTicks;
		float[] s = SPRINGS.computeIfAbsent(player.getId(), id -> new float[] {0, 0, 0, 0, 0, 0, 0, 0, time});
		float dt = Mth.clamp(time - s[TIME], 0.0F, 2.0F);
		s[TIME] = time;
		spring(s, LEAN, lean, dt);
		spring(s, TILT, tilt, dt);
		spring(s, CLIMB, climb, dt);
		spring(s, PITCH_EXTRA, SPEED_PITCH * ahead, dt);
		float bob = Mth.sin(time * BOB_RATE) * BOB * (1.0F - 0.5F * ahead);
		return new Ride(s[LEAN], PITCH + s[PITCH_EXTRA], time, s[TILT], s[CLIMB], bob);
	}

	/** One damped spring (value at {@code i}, its speed at {@code i + 1}) stepped {@code dt} ticks toward {@code target}. */
	private static void spring(float[] s, int i, float target, float dt) {
		// small steps, so a long frame doesn't overshoot wildly
		for (float left = dt; left > 0.0F; left -= 0.5F) {
			float step = Math.min(0.5F, left);
			s[i + 1] += ((target - s[i]) * SPRING - s[i + 1] * DAMPING) * step;
			s[i] += s[i + 1] * step;
		}
	}

	/**
	 * After the body faces its way: up and down with the board's bob, tipped with its nose, leaning into the turn
	 * about the feet, then bent forward at the hips, crouched (deeper climbing).
	 */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Ride ride = state.getData(KEY);
		if (ride == null || state.deathTime > 0.0F) {
			return;
		}
		poseStack.translate(0.0F, ride.bob(), 0.0F);
		// (a positive turn about Z tips the top to the left; a negative one about X tips it forward; the model faces -Z)
		poseStack.rotateDegrees(Axis.XP, ride.tilt());
		poseStack.rotateDegrees(Axis.ZP, ride.lean());
		float crouch = CROUCH + CLIMB_CROUCH * ride.climb();
		poseStack.translate(0.0F, HIP - crouch, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -ride.pitch());
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** After the model animates: the stance, which holds whatever else the arms and legs were doing. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Ride ride = living.getData(KEY);
		if (ride == null) {
			return;
		}
		float lean = ride.lean() * Mth.DEG_TO_RAD;
		float sway = Mth.sin(ride.time() * 0.12F) * 0.06F;
		float pitch = ride.pitch() * Mth.DEG_TO_RAD;
		// both arms out in front and spread; the inside hand drops in a lean, the outside one rises
		set(h.leftArm, -ARM_FORWARD + sway + lean * ARM_LEAN * 0.4F, 0.0F, -ARM_SPREAD);
		set(h.rightArm, -ARM_FORWARD - sway - lean * ARM_LEAN * 0.4F, 0.0F, ARM_SPREAD);
		h.leftArm.zRot += lean * ARM_LEAN;
		h.rightArm.zRot += lean * ARM_LEAN;
		// the legs stand back up against the forward bend: right foot forward, left foot behind, a little wide
		set(h.rightLeg, -pitch - FRONT_LEG, 0.15F, 0.08F);
		set(h.leftLeg, -pitch + BACK_LEG, -0.1F, -0.08F);
		h.body.xRot = 0.0F;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
		// eyes up and ahead, past the forward bend
		h.head.xRot -= pitch * 0.85F;
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
