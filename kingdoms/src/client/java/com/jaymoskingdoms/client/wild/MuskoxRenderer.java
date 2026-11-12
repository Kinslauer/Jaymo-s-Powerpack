package com.jaymoskingdoms.client.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.wild.Muskox;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws the muskox ({@link Muskox}) by its own model and poses ({@link MuskoxModel}), in its brown or black coat, drawn
 * 1.1x (about 2.1 blocks long, 1.5 to the hump); a calf at half that.
 */
public final class MuskoxRenderer extends MobRenderer<Muskox, MuskoxRenderer.State, MuskoxModel> {
	static final float SCALE = 1.1F, SHADOW = 0.8F;
	private static final Identifier BROWN = JaymosKingdoms.id("textures/entity/wild/muskox_brown.png");
	private static final Identifier BLACK = JaymosKingdoms.id("textures/entity/wild/muskox_black.png");

	public static final class State extends LivingEntityRenderState {
		boolean black;
		/** How far its head is down to graze, and into a butt (0 to 1). */
		float graze, butt;
	}

	public MuskoxRenderer(EntityRendererProvider.Context context) {
		super(context, new MuskoxModel(MuskoxModel.createBodyLayer().bakeRoot()), SHADOW);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Muskox muskox, State state, float partialTicks) {
		super.extractRenderState(muskox, state, partialTicks);
		state.black = muskox.blackCoat();
		state.graze = muskox.grazeAmount(partialTicks);
		state.butt = muskox.buttAmount(partialTicks);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float scale = SCALE * state.ageScale;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.black ? BLACK : BROWN;
	}
}
