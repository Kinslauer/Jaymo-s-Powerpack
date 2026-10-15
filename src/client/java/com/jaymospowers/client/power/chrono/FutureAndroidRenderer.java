package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureAndroid;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/** The future's androids on the humanoid model, in one of three shells (tools/gen_future_machines.py), eyes aglow; a gun is held up to aim. */
public class FutureAndroidRenderer extends HumanoidMobRenderer<FutureAndroid, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	static final String[] SHELLS = {"synthetic", "security", "torn"};
	static final Identifier[] SKINS = new Identifier[SHELLS.length];
	static final RenderType[] EYES = new RenderType[SHELLS.length];
	static final RenderStateDataKey<Integer> SHELL = RenderStateDataKey.create(() -> "jaymospowers:android_shell");

	static {
		for (int i = 0; i < SHELLS.length; i++) {
			SKINS[i] = JaymosPowers.id("textures/entity/future/android_" + SHELLS[i] + ".png");
			EYES[i] = RenderTypes.eyes(JaymosPowers.id("textures/entity/future/android_" + SHELLS[i] + "_eyes.png"));
		}
	}

	public FutureAndroidRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES[0];
			}

			@Override
			public void submit(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
				int light, HumanoidRenderState state, float yRot, float xRot) {
				Integer shell = state.getData(SHELL);
				collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES[shell == null ? 0 : shell], light,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.outlineColor);
			}
		});
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(FutureAndroid entity, HumanoidRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.setData(SHELL, Math.floorMod(entity.getUUID().hashCode(), SHELLS.length));
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(FutureAndroid mob, HumanoidArm arm) {
		// a gun is shouldered, both hands on it
		return mob.getMainHandItem().isEmpty() ? super.getArmPose(mob, arm) : HumanoidModel.ArmPose.CROSSBOW_HOLD;
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		Integer shell = state.getData(SHELL);
		return SKINS[shell == null ? 0 : shell];
	}
}
