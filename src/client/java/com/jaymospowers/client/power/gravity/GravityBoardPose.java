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
 * hand drops while the outside one rises. Worked out from the actual motion (how fast the rider is turning, sliding and going), so other
 * players see it too.
 */
public final class GravityBoardPose {
	/** Lean (degrees) per degree/tick of turning, and per block/tick of sideways slide; never more than {@code MAX_LEAN}. */
	static final float TURN_LEAN = 1.8F, SLIDE_LEAN = 45.0F, MAX_LEAN = 30.0F;
	/** How quickly the lean follows (per tick, 0-1). */
	static final float LEAN_EASE = 0.22F;
	/** The forward lean from the hips (degrees), and the little extra at full speed ({@code TOP_SPEED} blocks/tick). */
	static final float PITCH = 30.0F, SPEED_PITCH = 5.0F, TOP_SPEED = 0.95F;
	/** The hips (blocks over the feet), and how far the crouch sinks the body. */
	static final float HIP = 0.75F, CROUCH = 0.12F;
	/** Both arms reach out in front (radians from hanging, + forward) and spread apart; the lean swings them. */
	static final float ARM_FORWARD = 0.95F, ARM_SPREAD = 0.6F, ARM_LEAN = 0.5F;
	/** The stance: front leg forward, back leg behind (radians, before standing them up against the lean). */
	static final float FRONT_LEG = 0.7F, BACK_LEG = 0.6F;

	/** {@code lean}: degrees, + is to the rider's left; {@code pitch}: degrees forward. */
	record Ride(float lean, float pitch, float time) {
	}

	static final RenderStateDataKey<Ride> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_board");

	/** Each rider's lean so far (eased toward the target), and when it was last worked out. */
	private static final Int2ObjectOpenHashMap<float[]> LEANS = new Int2ObjectOpenHashMap<>();

	private GravityBoardPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || player.getAttached(GravityPower.ROCK_FLIGHT) == null || player.isPassenger()) {
			LEANS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		float yaw = player.getYRot();
		double lookX = -Mth.sin(yaw * Mth.DEG_TO_RAD), lookZ = Mth.cos(yaw * Mth.DEG_TO_RAD);
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		double forward = dx * lookX + dz * lookZ;
		// the rider's right is (-lookZ, lookX): sliding left is negative
		double side = dx * -lookZ + dz * lookX;
		float turning = Mth.wrapDegrees(yaw - player.yRotO);
		float speed = (float)Math.sqrt(dx * dx + dz * dz);
		// a turn standing still barely leans; at speed it carves
		float carve = Mth.clamp(0.3F + speed / 0.6F, 0.3F, 1.0F);
		float target = Mth.clamp(-turning * TURN_LEAN * carve - (float)side * SLIDE_LEAN, -MAX_LEAN, MAX_LEAN);

		float time = player.tickCount + partialTicks;
		float[] lean = LEANS.computeIfAbsent(player.getId(), id -> new float[] {0.0F, time});
		float dt = Mth.clamp(time - lean[1], 0.0F, 2.0F);
		lean[0] += (target - lean[0]) * (1.0F - (float)Math.pow(1.0F - LEAN_EASE, dt));
		lean[1] = time;
		float pitch = PITCH + SPEED_PITCH * Mth.clamp((float)forward / TOP_SPEED, 0.0F, 1.0F);
		state.setData(KEY, new Ride(lean[0], pitch, time));
	}

	/** After the body faces its way: leaning into the turn about the feet, then bent forward at the hips, crouched. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Ride ride = state.getData(KEY);
		if (ride == null || state.deathTime > 0.0F) {
			return;
		}
		// (a positive turn about Z tips the top to the left; a negative one about X tips it forward)
		poseStack.rotateDegrees(Axis.ZP, ride.lean());
		poseStack.translate(0.0F, HIP - CROUCH, 0.0F);
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
