package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoDouble;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** Draws one of Duplex's doubles as Duplex itself (its own model and skin), tinted red when it has gone rogue. */
public class XenoDoubleRenderer extends MobRenderer<XenoDouble, XenoDoubleRenderer.State, HumanoidModel<AvatarRenderState>> {
	/** A rogue double's tint. */
	private static final int ROGUE_TINT = 0xFFFF7A6A;
	/** Drawn at Duplex's size (as players are drawn, then the form's 0.9). */
	private static final float SCALE = 0.9375F * 0.9F;

	public static final class State extends AvatarRenderState {
		boolean rogue;
	}

	private final XenoFormModels.FormModel form;

	public XenoDoubleRenderer(EntityRendererProvider.Context context) {
		super(context, XenoFormModels.of("duplex"), 0.35F);
		this.form = XenoFormModels.of("duplex");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(XenoDouble entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.rogue = entity.rogue();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.form.texture;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected int getModelTint(State state) {
		return state.rogue ? ROGUE_TINT : super.getModelTint(state);
	}
}
