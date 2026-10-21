package com.jaymoskingdoms.client.nightfall;

import com.jaymoskingdoms.nightfall.NightBleeder;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

/** The Night Bleeder, in its three looks; unseen through a spyglass. */
final class NightBleederRenderer extends IllagerRenderer<NightBleeder, NightBleederRenderer.State> {
	private final Identifier[] textures;

	NightBleederRenderer(EntityRendererProvider.Context context, IllagerModel<State> model, Identifier[] textures) {
		super(context, model, 0.5F);
		this.textures = textures;
	}

	static final class State extends IllagerRenderState {
		int variant;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(NightBleeder entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.variant = entity.variant();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.textures[Math.floorMod(state.variant, this.textures.length)];
	}

	@Override
	public boolean shouldRender(NightBleeder entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		return !NightfallClient.scoping() && super.shouldRender(entity, culler, camX, camY, camZ, partialTicks);
	}
}
