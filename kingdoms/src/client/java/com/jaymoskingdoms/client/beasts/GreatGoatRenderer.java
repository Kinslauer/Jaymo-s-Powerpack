package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.GreatGoat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws the great goat ({@link GreatGoat}) by its own model ({@link GreatGoatModel}) in its shaggy mountain coat; a kid at half size. */
public final class GreatGoatRenderer extends MobRenderer<GreatGoat, GreatGoatRenderer.State, GreatGoatModel> {
	static final float SCALE = 1.0F, SHADOW = 0.7F;
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/great_goat.png");

	public static final class State extends LivingEntityRenderState {
		boolean saddled;
		/** How far its head is down to crop grass (0 to 1). */
		float graze;
	}

	public GreatGoatRenderer(EntityRendererProvider.Context context) {
		super(context, new GreatGoatModel(GreatGoatModel.createBodyLayer().bakeRoot()), SHADOW);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GreatGoat goat, State state, float partialTicks) {
		super.extractRenderState(goat, state, partialTicks);
		state.saddled = goat.isSaddled();
		state.graze = goat.getEatAnim(partialTicks);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float scale = SCALE * state.ageScale;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
