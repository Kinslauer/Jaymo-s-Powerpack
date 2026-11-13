package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.WreckThing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Draws the thing under the wreck ({@link WreckThing}) by its own model and poses ({@link WreckThingModel}), {@link #SCALE} times over: a body three blocks across, its arms seven blocks up. */
public final class WreckThingRenderer extends MobRenderer<WreckThing, WreckThingRenderer.State, WreckThingModel> {
	static final float SCALE = 2.0F;
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/beasts/wreck_thing.png");

	public static final class State extends LivingEntityRenderState {
		@Nullable String action;
		float actionAge;
	}

	public WreckThingRenderer(EntityRendererProvider.Context context) {
		super(context, new WreckThingModel(WreckThingModel.createBodyLayer().bakeRoot()), 0.0F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WreckThing thing, State state, float partialTicks) {
		super.extractRenderState(thing, state, partialTicks);
		state.action = thing.action();
		state.actionAge = thing.actionAge() + partialTicks;
	}

	/** Its arms stand far over its body: drawn while any of them could be in view. */
	@Override
	protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(WreckThing thing, float partialTicks) {
		return thing.getBoundingBox().inflate(4.0, 0.0, 4.0).expandTowards(0.0, 8.0, 0.0);
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
