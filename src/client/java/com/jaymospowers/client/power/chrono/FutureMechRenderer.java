package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureMech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The war mech: the iron golem's frame in our own gunmetal plating (tools/gen_future_machines.py), half as big again,
 * visor and reactor aglow. When it fires, both arm cannons come up level at its target.
 */
public class FutureMechRenderer extends MobRenderer<FutureMech, IronGolemRenderState, FutureMechRenderer.Model> {
	static final Identifier SKIN = JaymosPowers.id("textures/entity/future/war_mech.png");
	static final RenderType EYES = RenderTypes.eyes(JaymosPowers.id("textures/entity/future/war_mech_eyes.png"));
	static final RenderStateDataKey<Boolean> AIMING = RenderStateDataKey.create(() -> "jaymospowers:mech_aiming");
	/** How much bigger than an iron golem. */
	static final float SCALE = 1.5F;

	public FutureMechRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.7F * SCALE);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES;
			}
		});
	}

	@Override
	public IronGolemRenderState createRenderState() {
		return new IronGolemRenderState();
	}

	@Override
	public void extractRenderState(FutureMech entity, IronGolemRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.attackTicksRemaining = entity.getAttackAnimationTick() > 0 ? entity.getAttackAnimationTick() - partialTicks : 0.0F;
		state.setData(AIMING, entity.aiming() ? Boolean.TRUE : null);
	}

	@Override
	public Identifier getTextureLocation(IronGolemRenderState state) {
		return SKIN;
	}

	@Override
	protected void scale(IronGolemRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected void setupRotations(IronGolemRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.walkAnimationSpeed >= 0.01F) {
			// a heavy, swaying stride
			float wave = (Math.abs((state.walkAnimationPos + 6.0F) % 13.0F - 6.5F) - 3.25F) / 3.25F;
			poseStack.rotateDegrees(Axis.ZP, 4.0F * wave);
		}
	}

	/** The golem's frame, arms raised level while it fires. */
	public static class Model extends IronGolemModel {
		private final ModelPart rightArm;
		private final ModelPart leftArm;

		public Model(ModelPart root) {
			super(root);
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
		}

		@Override
		public void setupAnim(IronGolemRenderState state) {
			super.setupAnim(state);
			if (state.attackTicksRemaining <= 0.0F && Boolean.TRUE.equals(state.getData(AIMING))) {
				float aim = -1.45F + state.xRot * (float) (Math.PI / 180.0);
				this.rightArm.xRot = aim;
				this.leftArm.xRot = aim;
			}
		}
	}
}
