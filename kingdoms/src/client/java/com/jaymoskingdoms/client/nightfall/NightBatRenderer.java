package com.jaymoskingdoms.client.nightfall;

import com.jaymoskingdoms.nightfall.Blightwing;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ambient.Bat;

/** The plague's bats on vanilla's bat model: the vampire bat a little bigger, the Blightwing far bigger, each its own skin. */
final class NightBatRenderer<T extends Bat> extends MobRenderer<T, NightBatRenderer.State, BatModel> {
	private final float scale;
	private final Function<State, Identifier> texture;

	NightBatRenderer(EntityRendererProvider.Context context, BatModel model, float shadow, float scale, Function<State, Identifier> texture) {
		super(context, model, shadow);
		this.scale = scale;
		this.texture = texture;
	}

	static final class State extends BatRenderState {
		int variant;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.isResting = entity.isResting();
		state.flyAnimationState.copyFrom(entity.flyAnimationState);
		state.restAnimationState.copyFrom(entity.restAnimationState);
		state.variant = entity instanceof Blightwing wing ? wing.variant() : 0;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(this.scale, this.scale, this.scale);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.texture.apply(state);
	}
}
