package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.SandWorm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Draws the sand worm ({@link SandWorm}) by its own model and poses ({@link SandWormModel}), {@link #SCALE} times over: about eight blocks out of the sand. */
public final class SandWormRenderer extends MobRenderer<SandWorm, SandWormRenderer.State, SandWormModel> {
	static final float SCALE = 1.6F;
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/beasts/sand_worm.png");

	public static final class State extends LivingEntityRenderState {
		@Nullable String action;
		float actionAge;
	}

	public SandWormRenderer(EntityRendererProvider.Context context) {
		super(context, new SandWormModel(SandWormModel.createBodyLayer().bakeRoot()), 0.0F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SandWorm worm, State state, float partialTicks) {
		super.extractRenderState(worm, state, partialTicks);
		state.action = worm.action();
		state.actionAge = worm.actionAge() + partialTicks;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
