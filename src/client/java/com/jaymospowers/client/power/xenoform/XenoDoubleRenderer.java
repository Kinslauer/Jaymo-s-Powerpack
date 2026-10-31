package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoDouble;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * Fills a double's render state the way a transformed player's is filled. The game draws every avatar render state
 * with its player renderer (EntityRenderDispatcher.getRenderer), never this one, so the double is marked as Duplex
 * with its body hidden and {@link XenoformLayer} draws it exactly as it draws its owner: same model, skin and size
 * (its {@code Attributes.SCALE} is Duplex's). (0.18.113) A false copy of Mirage's is drawn as its {@link XenoDouble#look}.
 */
public class XenoDoubleRenderer extends MobRenderer<XenoDouble, AvatarRenderState, HumanoidModel<AvatarRenderState>> {
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
		// (0.18.113) Mirage's false copies are drawn as Mirage
		state.setData(XenoformClient.FORM, entity.look());
		state.setData(XenoformClient.ENDING, false);
		XenoformClient.hideBody(state);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return this.form.texture;
	}
}
