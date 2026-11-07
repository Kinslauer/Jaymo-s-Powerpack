package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoStalker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * (0.18.96) The stalker, drawn as Lurk (as {@link XenoDoubleRenderer} draws a double: the state goes through the player
 * renderer, so it's marked as the form with its body hidden and {@link XenoformLayer} draws it). Its shed skin kneels,
 * a pale see-through husk ({@link XenoformClient#SHED}).
 */
public class XenoStalkerRenderer extends MobRenderer<XenoStalker, AvatarRenderState, HumanoidModel<AvatarRenderState>> {
	private final XenoFormModels.FormModel form;

	public XenoStalkerRenderer(EntityRendererProvider.Context context) {
		super(context, XenoFormModels.of("lurk"), 0.4F);
		this.form = XenoFormModels.of("lurk");
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(XenoStalker entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.setData(XenoformClient.FORM, "lurk");
		state.setData(XenoformClient.ENDING, false);
		state.setData(XenoformClient.SHED, entity.shed());
		if (entity.shed()) {
			state.isCrouching = true;
		}
		XenoformClient.hideBody(state);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return this.form.texture;
	}
}
