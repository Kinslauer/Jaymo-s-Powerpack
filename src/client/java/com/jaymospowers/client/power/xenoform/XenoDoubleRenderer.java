package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoDouble;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** Draws one of Duplex's doubles as Duplex itself: its own model and skin. */
public class XenoDoubleRenderer extends MobRenderer<XenoDouble, AvatarRenderState, HumanoidModel<AvatarRenderState>> {
	/** Drawn at Duplex's size (as players are drawn, then the form's 0.9). */
	private static final float SCALE = 0.9375F * 0.9F;

	private final XenoFormModels.FormModel form;

	public XenoDoubleRenderer(EntityRendererProvider.Context context) {
		super(context, XenoFormModels.of("duplex"), 0.35F);
		this.form = XenoFormModels.of("duplex");
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(XenoDouble entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return this.form.texture;
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}
}
