package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebParkour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The parkour moves as everyone sees them ({@link WebParkour#MOVE}):
 * <ul>
 * <li><b>Vault:</b> the body tips sideways over the planted hand while the legs swing over together.</li>
 * <li><b>Cling:</b> leaning into the wall, both hands high on it, one knee up against it, the other leg hanging.</li>
 * <li><b>Wall jump:</b> arched back off the wall, arms flung up and out, legs driving off it, then tucking.</li>
 * <li><b>Slide:</b> laid back on one hip, the lead leg straight out ahead, the other folded under, a hand trailing on
 * the ground and the other arm up for balance.</li>
 * </ul>
 * Your own moves don't show in first person.
 */
public final class WebParkourPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** How far the body tips over the planted hand in a vault, and leans back in a slide (degrees). */
	static final float VAULT_TILT = 38.0F, SLIDE_LEAN = 62.0F;
	/** How far the body leans into the wall while clinging (degrees). */
	static final float CLING_LEAN = 10.0F;
	/** Ticks a held move (cling, slide) takes to settle in. */
	static final float EASE_IN = 3.0F;
	// ================================================================================================================

	/** {@code kind}: a {@link WebParkour} move; {@code p}: how far through it (0-1, timed moves); {@code in}: settled into it (0-1). */
	public record Pose(int kind, float p, float in, float time) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_parkour");

	/** Per entity id: the move it's in and the game time it began. */
	private static final java.util.Map<Integer, float[]> STARTED = new java.util.HashMap<>();

	private WebParkourPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer move = entity.getAttached(WebParkour.MOVE);
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		if (move == null || ownView || entity.isPassenger()) {
			if (move == null) {
				STARTED.remove(entity.getId());
			}
			state.setData(KEY, null);
			return;
		}
		int kind = WebParkour.kind(move);
		float time = entity.level().getGameTime() + partialTicks;
		float[] started = STARTED.get(entity.getId());
		if (started == null || started[0] != move) {
			started = new float[] {move, time};
			STARTED.put(entity.getId(), started);
		}
		float age = time - started[1];
		float length = kind == WebParkour.VAULT ? WebParkour.VAULT_TICKS : kind == WebParkour.WALL_JUMP ? WebParkour.WALL_JUMP_TICKS : 0.0F;
		float p = length > 0.0F ? Mth.clamp(age / length, 0.0F, 1.0F) : 0.0F;
		float in = length > 0.0F ? Mth.sin(Mth.PI * p) : smooth(Mth.clamp(age / EASE_IN, 0.0F, 1.0F));
		state.setData(KEY, new Pose(kind, p, Math.min(1.0F, in * (length > 0.0F ? 1.6F : 1.0F)), time));
	}

	/** After the body faces its way: tip over in a vault, lie back in a slide, lean into the wall in a cling. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || state.deathTime > 0.0F) {
			return;
		}
		// (here +X is the body's right, +Y up, +Z behind it)
		switch (pose.kind()) {
			case WebParkour.VAULT -> {
				// over the right hand: the hips swing up and to the left past it
				poseStack.translate(0.0F, 0.35F * pose.in(), 0.0F);
				poseStack.rotateDegrees(Axis.ZP, -VAULT_TILT * pose.in());
			}
			case WebParkour.SLIDE -> poseStack.rotateDegrees(Axis.XP, SLIDE_LEAN * pose.in());
			case WebParkour.CLING -> poseStack.rotateDegrees(Axis.XP, -CLING_LEAN * pose.in());
			case WebParkour.WALL_JUMP -> poseStack.rotateDegrees(Axis.XP, 18.0F * pose.in());
			default -> {
			}
		}
	}

	/** After the model animates: the limbs. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float e = pose.in();
		switch (pose.kind()) {
			case WebParkour.VAULT -> {
				// right hand planted straight down on the obstacle, the left flung out, legs together and swung over
				set(h.rightArm, -0.25F, 0.0F, 0.35F, e);
				set(h.leftArm, -1.2F, 0.0F, -1.3F, e);
				float swing = Mth.sin(Mth.PI * pose.p());
				set(h.rightLeg, -1.1F * swing, 0.0F, -0.5F, e);
				set(h.leftLeg, -1.1F * swing, 0.0F, -0.35F, e);
				h.head.zRot = Mth.lerp(e, h.head.zRot, VAULT_TILT * 0.5F * Mth.DEG_TO_RAD);
			}
			case WebParkour.CLING -> {
				// hands high on the wall, swaying a little as it hangs; a knee up on it, the other leg dangling
				float sway = Mth.sin(pose.time() * 0.12F) * 0.05F;
				set(h.rightArm, -2.75F + sway, -0.12F, 0.18F, e);
				set(h.leftArm, -2.75F - sway, 0.12F, -0.18F, e);
				set(h.rightLeg, -0.9F, 0.0F, 0.08F, e);
				set(h.leftLeg, -0.15F + sway, 0.0F, -0.06F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, Math.min(h.head.xRot, -0.25F));
			}
			case WebParkour.WALL_JUMP -> {
				// pushed off: arms up and out, legs driving down off the wall, tucking as it flies
				float tuck = pose.p();
				set(h.rightArm, -2.4F, 0.0F, 0.6F, e);
				set(h.leftArm, -2.4F, 0.0F, -0.6F, e);
				set(h.rightLeg, Mth.lerp(tuck, 0.5F, -1.0F), 0.0F, 0.1F, e);
				set(h.leftLeg, Mth.lerp(tuck, 0.3F, -0.7F), 0.0F, -0.1F, e);
			}
			case WebParkour.SLIDE -> {
				// lead leg straight out, the other folded under; the right hand trails on the ground, the left up
				set(h.rightLeg, -1.35F, 0.0F, 0.12F, e);
				set(h.leftLeg, -0.45F, 0.0F, -0.35F, e);
				set(h.rightArm, 0.55F, 0.0F, 0.55F, e);
				set(h.leftArm, -1.9F, 0.0F, -0.35F, e);
				// the head lifts to keep looking ahead
				h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - SLIDE_LEAN * 0.8F * Mth.DEG_TO_RAD);
			}
			default -> {
			}
		}
	}

	private static void set(ModelPart part, float x, float y, float z, float e) {
		part.xRot = Mth.lerp(e, part.xRot, x);
		part.yRot = Mth.lerp(e, part.yRot, y);
		part.zRot = Mth.lerp(e, part.zRot, z);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
