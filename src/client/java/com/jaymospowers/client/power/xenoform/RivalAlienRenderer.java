package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.RivalAlien;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * (0.18.125, Xeno-Band patch 8) The band hunter changed: drawn as {@link XenoDoubleRenderer} draws a double (through the
 * player renderer and {@link XenoformLayer}), as its {@link RivalAlien#look}, darkened ({@link XenoformClient#DARK}).
 */
public class RivalAlienRenderer extends MobRenderer<RivalAlien, AvatarRenderState, HumanoidModel<AvatarRenderState>> {
	private final XenoFormModels.FormModel form;

	public RivalAlienRenderer(EntityRendererProvider.Context context) {
		super(context, XenoFormModels.of("gyre"), 0.5F);
		this.form = XenoFormModels.of("gyre");
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(RivalAlien entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.setData(XenoformClient.FORM, entity.look());
		state.setData(XenoformClient.ENDING, false);
		state.setData(XenoformClient.DARK, true);
		XenoformClient.hideBody(state);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return this.form.texture;
	}
}
