package com.jaymoskingdoms.client.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.wild.Grizzly;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** (0.7.610) Draws the grizzly ({@link Grizzly}) by its own model and poses ({@link GrizzlyModel}), drawn 1.15x (about 2.3 blocks long, 1.7 to the hump). */
public final class GrizzlyRenderer extends MobRenderer<Grizzly, GrizzlyRenderer.State, GrizzlyModel> {
	static final float SCALE = 1.15F;
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/wild/grizzly.png");

	public static final class State extends LivingEntityRenderState {
		@Nullable String action;
		float actionAge;
	}

	public GrizzlyRenderer(EntityRendererProvider.Context context) {
		super(context, new GrizzlyModel(GrizzlyModel.createBodyLayer().bakeRoot()), 1.1F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Grizzly grizzly, State state, float partialTicks) {
		super.extractRenderState(grizzly, state, partialTicks);
		state.action = grizzly.action();
		state.actionAge = grizzly.actionAge() + partialTicks;
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
