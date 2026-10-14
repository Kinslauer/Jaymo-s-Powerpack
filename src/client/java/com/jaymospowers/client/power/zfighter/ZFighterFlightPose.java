package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * How a flying Z Fighter holds himself, on everyone's screen (from the synced {@link ZFighterPower#FLIGHT}). Hovering he
 * stays upright, legs together; at speed Wind's cruise pose lays him out along the flight ({@code WindPose} asks
 * {@link #cruising}) while his arms keep the pose he picked with the scroll wheel:
 * <ol>
 * <li>rising: arms straight down at his sides, fists closed;</li>
 * <li>swept back: arms down and back;</li>
 * <li>one fist up: the leading fist overhead (out ahead at speed), the other at his side;</li>
 * <li>both fists up, either side of the head;</li>
 * <li>diving: both arms overhead, hands together.</li>
 * </ol>
 */
public final class ZFighterFlightPose {
	/** One player this frame: how far into flight (0-1), the arms (right x y z, left x y z, before the lead swap), the time. */
	public record Pose(float amount, float[] arms, float time) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_flight");

	/** Moving at least this fast (blocks a tick), he takes Wind's laid-out cruise pose. */
	static final double CRUISE_SPEED = 0.6;
	/** How fast flight and the arms ease from one pose to the next, per tick. */
	static final float EASE = 0.2F;
	static final float ARM_EASE = 0.25F;

	/**
	 * The arms per pose (index 1-5): lead arm x y z, other arm x y z (the lead is the main hand's; z outward for the
	 * right arm, mirrored for the left).
	 */
	private static final float[][] ARMS = {
		{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
		{0.05F, 0.0F, 0.12F, 0.05F, 0.0F, 0.12F},
		{0.6F, 0.0F, 0.3F, 0.6F, 0.0F, 0.3F},
		{-Mth.PI + 0.15F, 0.0F, -0.08F, 0.15F, 0.0F, 0.15F},
		{-Mth.PI + 0.35F, 0.0F, 0.35F, -Mth.PI + 0.35F, 0.0F, 0.35F},
		{-Mth.PI + 0.1F, 0.0F, -0.18F, -Mth.PI + 0.1F, 0.0F, -0.18F},
	};

	/** entity id → {amount, amount before, 6 arm angles now, 6 arm angles before}. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterFlightPose() {
	}

	/** A flying Z Fighter at speed: Wind's cruise pose lays him out. */
	public static boolean cruising(Player player) {
		if (ZFighterPower.flightPose(player) <= 0) {
			return false;
		}
		double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
		return dx * dx + dy * dy + dz * dz >= CRUISE_SPEED * CRUISE_SPEED;
	}

	/** While he flies, his arms are his own: Wind's cruise leaves them alone. */
	public static boolean ownsArms(AvatarRenderState state) {
		Pose pose = state.getData(KEY);
		return pose != null && pose.amount() > 0.001F;
	}

	/** Every client tick: ease everyone's flight and arms towards their pose. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATE.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			int pose = ZFighterPower.flightPose(player);
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (pose <= 0) {
					continue;
				}
				s = new float[14];
				System.arraycopy(ARMS[Mth.clamp(pose, 1, 5)], 0, s, 2, 6);
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			System.arraycopy(s, 2, s, 8, 6);
			s[0] = pose > 0 ? Math.min(1.0F, s[0] + EASE) : Math.max(0.0F, s[0] - EASE);
			if (pose > 0) {
				float[] target = ARMS[Mth.clamp(pose, 1, 5)];
				for (int i = 0; i < 6; i++) {
					s[2 + i] += (target[i] - s[2 + i]) * ARM_EASE;
				}
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		if (s == null) {
			state.setData(KEY, null);
			return;
		}
		float[] arms = new float[6];
		for (int i = 0; i < 6; i++) {
			arms[i] = Mth.lerp(partialTicks, s[8 + i], s[2 + i]);
		}
		state.setData(KEY, new Pose(Mth.lerp(partialTicks, s[1], s[0]), arms, entity.tickCount + partialTicks));
	}

	/** Right after a humanoid model animates: the arms in his pose, and (hovering) legs together, hanging. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null || pose.amount() <= 0.001F || avatar.deathTime > 0.0F) {
			return;
		}
		float a = smooth(pose.amount());
		float[] arms = pose.arms();
		float sway = Mth.sin(pose.time() * 0.12F) * 0.04F;
		ModelPart lead = avatar.mainArm == HumanoidArm.LEFT ? h.leftArm : h.rightArm;
		ModelPart other = lead == h.rightArm ? h.leftArm : h.rightArm;
		float leadOut = lead == h.rightArm ? 1.0F : -1.0F;
		pose(lead, arms[0] + sway, arms[1] * leadOut, arms[2] * leadOut, a);
		pose(other, arms[3] - sway, arms[4] * -leadOut, arms[5] * -leadOut, a);
		Float cruise = avatar.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
		float hover = a * (1.0F - (cruise == null ? 0.0F : cruise));
		if (hover > 0.001F) {
			// hovering: legs together and a little back, toes down, drifting
			pose(h.rightLeg, 0.12F + sway, 0.0F, 0.03F, hover);
			pose(h.leftLeg, 0.18F - sway, 0.0F, -0.03F, hover);
		}
	}

	private static void pose(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
