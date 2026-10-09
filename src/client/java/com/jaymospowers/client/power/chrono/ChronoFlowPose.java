package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Time Flow's blows, drawn: a jab, a hook, an uppercut, a lunging punch, a flying kick, the counter (guard up, then
 * the strike) and Chrono Punch's quick jabs. Everything is set outright from the blow's progress, so applying it twice
 * changes nothing.
 */
public final class ChronoFlowPose {
	/** The blow on this player and how far through it they are (0-1). */
	record Pose(ChronoFlow.Move move, float p) {
	}

	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:chrono_flow_pose");

	private ChronoFlowPose() {
	}

	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		ChronoFlow.Move move = entity.getAttached(ChronoFlow.MOVE);
		ChronoFlow.Move mine = ChronoFlowClient.local;
		if (entity == Minecraft.getInstance().player && mine != null && (move == null || mine.start() >= move.start())) {
			move = mine;
		}
		if (move == null) {
			state.setData(KEY, null);
			return;
		}
		float p = (entity.level().getGameTime() - move.start() + partialTicks) / ChronoFlow.DURATION[move.style()];
		state.setData(KEY, p >= 0.0F && p < 1.0F ? new Pose(move, p) : null);
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		float p = pose.p();
		// out fast, back slower
		float e = p < 0.35F ? p / 0.35F : 1.0F - (p - 0.35F) / 0.65F;
		e = e * e * (3.0F - 2.0F * e);
		int side = pose.move().side();
		ModelPart lead = side > 0 ? h.rightArm : h.leftArm;
		ModelPart guard = side > 0 ? h.leftArm : h.rightArm;
		ModelPart frontLeg = side > 0 ? h.leftLeg : h.rightLeg;
		ModelPart backLeg = side > 0 ? h.rightLeg : h.leftLeg;
		switch (pose.move().style()) {
			case ChronoFlow.JAB, ChronoFlow.RAPID -> {
				set(lead, -Mth.HALF_PI, 0.0F, 0.0F, e);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, Math.max(e, 0.6F));
				h.body.yRot = side * -0.35F * e;
			}
			case ChronoFlow.HOOK -> {
				// swung round from the side
				set(lead, -Mth.HALF_PI, Mth.lerp(p, side * 1.0F, side * -0.5F), 0.0F, e);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, 0.7F);
				h.body.yRot = Mth.lerp(p, side * 0.3F, side * -0.5F) * e;
			}
			case ChronoFlow.UPPERCUT -> {
				// from low, driven up
				set(lead, Mth.lerp(Math.min(1.0F, p * 2.0F), 0.4F, -2.7F), 0.0F, side * -0.15F, e);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, 0.7F);
				h.body.xRot = -0.15F * e;
			}
			case ChronoFlow.LUNGE -> {
				set(lead, -Mth.HALF_PI - 0.1F, 0.0F, 0.0F, e);
				set(guard, 0.6F, 0.0F, side * 0.2F, e);
				set(frontLeg, -0.7F, 0.0F, 0.0F, e);
				set(backLeg, 0.6F, 0.0F, 0.0F, e);
				h.body.xRot = 0.3F * e;
				h.body.yRot = side * -0.4F * e;
			}
			case ChronoFlow.KICK -> {
				// the kicking leg straight out ahead, the body thrown back, arms flung out
				set(h.rightLeg, -Mth.HALF_PI - 0.2F, 0.0F, 0.0F, e);
				set(h.leftLeg, 0.45F, 0.0F, 0.0F, e);
				set(h.rightArm, -0.4F, 0.0F, 1.2F, e);
				set(h.leftArm, -0.4F, 0.0F, -1.2F, e);
				h.body.xRot = -0.3F * e;
			}
			case ChronoFlow.COUNTER -> {
				if (p < 0.4F) {
					// guard up: the forearm across the face, catching the blow
					float g = p / 0.4F;
					set(lead, -2.0F, side * -0.7F, 0.0F, Math.min(1.0F, g * 2.0F));
					set(guard, -1.4F, side * 0.4F, 0.0F, Math.min(1.0F, g * 2.0F));
				} else {
					// and the answer: a palm strike straight back
					float s = 1.0F - (p - 0.4F) / 0.6F;
					set(lead, -Mth.HALF_PI, 0.0F, 0.0F, s);
					set(guard, -1.25F, side * 0.3F, side * 0.25F, s);
					h.body.yRot = side * -0.45F * s;
				}
			}
			default -> {
			}
		}
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
