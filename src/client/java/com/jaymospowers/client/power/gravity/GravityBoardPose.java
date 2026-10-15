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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Riding the earthboard (rock flight), drawn for everyone: side-on like a snowboarder or surfer. The lead shoulder
 * points down the board's nose (left foot forward; right foot forward, "goofy", for left-handed players), feet wide
 * apart along the board, hips sunk, a slight hinge toward the toes, and the head turned to look where they're going.
 * The lead arm reaches out over the nose and the back arm hangs out over the tail for balance. It carves: the whole
 * body leans into a turn or a slide about the feet, which for a side-on rider is onto the heels or the toes, and the
 * arms swing forward to balance a heel-side lean and back on a toe-side one. Climbing, the board's nose comes up and
 * the rider sinks deeper; sinking, it dips and they rise; at speed they tuck a little lower over the toes. Rider and
 * board bob together. Every lean and tilt is a damped spring, so it swings past and settles rather than snapping.
 * Worked out from the actual motion (how fast the rider is turning, sliding, climbing and going), so other players see
 * it too; the board is drawn to match ({@code BlockClusterRenderer}).
 */
public final class GravityBoardPose {
	/** Lean (degrees) per degree/tick of turning, and per block/tick of sideways slide; never more than {@code MAX_LEAN}. */
	static final float TURN_LEAN = 1.8F, SLIDE_LEAN = 45.0F, MAX_LEAN = 30.0F;
	/** How far the body turns side-on to the way it's going (degrees; 90 would be square across the board). */
	static final float STANCE = 78.0F;
	/** The hinge toward the toes from the hips (degrees), and the extra tuck at full speed ({@code TOP_SPEED} blocks/tick). */
	static final float PITCH = 14.0F, SPEED_PITCH = 8.0F, TOP_SPEED = 0.95F;
	/** The hips (blocks over the feet), and how far the body sinks (the wide stance, as knees we don't have). */
	static final float HIP = 0.75F, CROUCH = 0.05F;
	/** Each leg out to its side, so the feet stand wide apart along the board (radians). */
	static final float LEG_SPREAD = 0.3F;
	/** The lead arm out over the nose and the back arm out over the tail (radians out from the side, and forward). */
	static final float LEAD_OUT = 1.05F, LEAD_FORWARD = 0.3F, TRAIL_OUT = 0.75F, TRAIL_FORWARD = 0.15F;
	/** The arms swing forward this far (radians per radian of lean) leaning onto the heels, back onto the toes. */
	static final float ARM_LEAN = 0.6F;
	/** How much of the toe-side hinge the head takes back, so the eyes stay up. */
	static final float HEAD_LEVEL = 0.85F;

	/** The board tips nose-up this far (degrees) climbing flat out, and nose-down this far at top speed. */
	static final float CLIMB_TILT = 10.0F, SPEED_DIP = 4.0F;
	/** Climbing or sinking at this speed (blocks/tick) is "flat out" for the tilt. */
	static final float CLIMB_FULL = 0.18F;
	/** The rider sinks deeper into the crouch climbing (blocks), and rises a little sinking. */
	static final float CLIMB_CROUCH = 0.04F;
	/** The slow bob of the board and its rider together (blocks, and radians per tick). */
	static final float BOB = 0.035F, BOB_RATE = 0.11F;
	/**
	 * Every lean and tilt is a damped spring chasing its target (like Wind's hover sway): stiffness and damping per
	 * tick. It follows quickly and settles after one soft overshoot, so a sharp turn swings the rider past and back.
	 */
	static final float SPRING = 0.16F, DAMPING = 0.5F;

	/**
	 * {@code lean}: degrees, + is to the rider's left; {@code pitch}: degrees forward from the hips; {@code tilt}: the
	 * board's nose-up (degrees, the rider tips with it); {@code climb}: -1 sinking to 1 climbing; {@code bob}: blocks;
	 * {@code goofy}: right foot forward (left-handed players).
	 */
	public record Ride(float lean, float pitch, float time, float tilt, float climb, float bob, boolean goofy) {
	}

	public static final RenderStateDataKey<Ride> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_board");

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
		return new Ride(s[LEAN], PITCH + s[PITCH_EXTRA], time, s[TILT], s[CLIMB], bob, player.getMainArm() == HumanoidArm.LEFT);
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
	 * about the feet, turned side-on, then hinged toward the toes at the hips, sunk into the stance (deeper climbing).
	 */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Ride ride = state.getData(KEY);
		if (ride == null || state.deathTime > 0.0F) {
			return;
		}
		poseStack.translate(0.0F, ride.bob(), 0.0F);
		// (a positive turn about Z tips the top to the left; a negative one about X tips it forward; the model faces -Z;
		// a negative turn about Y brings the left shoulder round to the front)
		poseStack.rotateDegrees(Axis.XP, ride.tilt());
		poseStack.rotateDegrees(Axis.ZP, ride.lean());
		poseStack.rotateDegrees(Axis.YP, ride.goofy() ? STANCE : -STANCE);
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
		// + left foot forward, - right foot forward
		float side = ride.goofy() ? -1.0F : 1.0F;
		// leaning to the left of the way it's going is onto the heels, riding left foot forward (the chest faces right)
		float heel = ride.lean() * Mth.DEG_TO_RAD * side;
		float sway = Mth.sin(ride.time() * 0.12F) * 0.06F;
		float pitch = ride.pitch() * Mth.DEG_TO_RAD;
		float swing = -heel * ARM_LEAN;
		// the lead arm out over the nose, the back arm out over the tail; a heel-side lean swings both forward
		ModelPart lead = ride.goofy() ? h.rightArm : h.leftArm, trail = ride.goofy() ? h.leftArm : h.rightArm;
		set(lead, -LEAD_FORWARD + swing + sway, 0.0F, -LEAD_OUT * side + sway * 0.5F);
		set(trail, -TRAIL_FORWARD + swing - sway, 0.0F, TRAIL_OUT * side - sway * 0.5F);
		// the legs stand back up against the hinge, wide apart along the board
		set(h.rightLeg, -pitch, 0.0F, LEG_SPREAD);
		set(h.leftLeg, -pitch, 0.0F, -LEG_SPREAD);
		h.body.xRot = 0.0F;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
		// the head turns back round to look where they're going (and where the player's looking), eyes up past the hinge
		Quaternionf look = new Quaternionf().rotationX(-pitch * HEAD_LEVEL).rotateY(-STANCE * Mth.DEG_TO_RAD * side)
			.rotateY(h.head.yRot).rotateX(h.head.xRot);
		Vector3f euler = look.getEulerAnglesZYX(new Vector3f());
		set(h.head, euler.x, euler.y, euler.z);
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
