package com.jaymospowers.client.power.chrono;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureOmen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * An Omen ({@link FutureOmen}) drawn by GeckoLib: "The Omen Red" or "The Omen Blue" (Blockbench Workshop, CC BY 4.0,
 * credits in art/the_omen-CREDITS.md and textures/entity/bb/CREDITS.txt), each its own geometry
 * ({@code geckolib/models/entity/omen_<build>.geo.json}) in its old skin ({@code textures/entity/bb/omen_<build>.png}),
 * both on one animation file ({@code omen.animation.json}; tools/gen_omen_geo.py writes them all). The entity picks and
 * triggers the animations; on top of them this turns its head where it looks, brings the cannon arms up on its target
 * while it fires, and rolls it with a heavy stride. Dying it goes down as it stands, not tipped over. The model stands
 * 4.5 blocks, its entity's own height.
 */
public final class OmenRenderer extends GeoEntityRenderer<FutureOmen, LivingEntityRenderState> {
	/** The cannon arms' angles held up on a target (the old renderer's, radians), and their tremor. */
	static final float AIM_LEFT = -1.35F, AIM_RIGHT = -1.45F, AIM_TREMOR = 0.02F;
	/** How much of the head's yaw the cannon arms follow, and how far the aim follows its pitch (degrees). */
	static final float CANNON_YAW = 0.5F, AIM_PITCH = 25.0F;
	/** How far the head turns (degrees): side to side, up and down. */
	static final float LOOK_YAW = 50.0F, LOOK_PITCH = 25.0F;
	/** The whole mech's roll in a heavy stride (degrees). */
	static final float WALK_ROLL = 1.8F;

	static final DataTicket<Boolean> AIMING = DataTicket.create("jaymospowers_omen_aiming", Boolean.class);
	static final DataTicket<Boolean> SMASHING = DataTicket.create("jaymospowers_omen_smashing", Boolean.class);

	public static OmenRenderer red(EntityRendererProvider.Context context) {
		return new OmenRenderer(context, "omen_red");
	}

	public static OmenRenderer blue(EntityRendererProvider.Context context) {
		return new OmenRenderer(context, "omen_blue");
	}

	private OmenRenderer(EntityRendererProvider.Context context, String name) {
		super(context, new DefaultedEntityGeoModel<FutureOmen>(JaymosPowers.id(name))
			.withAltAnimations(JaymosPowers.id("omen"))
			.withAltTexture(JaymosPowers.id("bb/" + name)));
		this.shadowRadius = 1.4F;
	}

	@Override
	public void addRenderData(FutureOmen omen, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(omen, related, state, partialTick);
		state.addGeckolibData(AIMING, omen.aiming());
		state.addGeckolibData(SMASHING, omen.getAttackAnimationTick() > 0);
	}

	/** It goes down as it stands, not tipped over. */
	@Override
	protected float getDeathMaxRotation(GeoRenderState state) {
		return 0.0F;
	}

	@Override
	protected void applyRotations(RenderPassInfo<LivingEntityRenderState> info, PoseStack poseStack, float bodyRot) {
		super.applyRotations(info, poseStack, bodyRot);
		LivingEntityRenderState state = info.renderState();
		if (state.walkAnimationSpeed >= 0.01F && state.deathTime <= 0.0F) {
			float wave = (Math.abs((state.walkAnimationPos + 6.0F) % 13.0F - 6.5F) - 3.25F) / 3.25F;
			poseStack.rotateDegrees(Axis.ZP, WALK_ROLL * wave);
		}
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		LivingEntityRenderState state = info.renderState();
		if (state.deathTime > 0.0F) {
			return;
		}
		// GeckoLib turns x and y the other way round from a ModelPart: these are the old model's angles, negated
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD;
		snapshots.ifPresent("head", b -> b.setRotY(b.getRotY() - yaw).setRotX(b.getRotX() - pitch));
		if (info.getOrDefaultGeckolibData(AIMING, false) && !info.getOrDefaultGeckolibData(SMASHING, false)) {
			// level on the target: held up, following its pitch and half its yaw, with the old tremor
			float age = (float) state.ageInTicks;
			float aimPitch = Mth.clamp(state.xRot, -AIM_PITCH, AIM_PITCH) * Mth.DEG_TO_RAD;
			float left = AIM_LEFT + aimPitch + Mth.sin(age * 0.9F) * AIM_TREMOR;
			float right = AIM_RIGHT + aimPitch + Mth.sin(age * 0.9F + 1.0F) * AIM_TREMOR;
			snapshots.ifPresent("left_arm_joint", b -> b.setRotX(-left).setRotY(-yaw * CANNON_YAW).setRotZ(0.0F));
			snapshots.ifPresent("right_arm_joint", b -> b.setRotX(-right).setRotY(-yaw * CANNON_YAW).setRotZ(0.0F));
		}
	}
}
