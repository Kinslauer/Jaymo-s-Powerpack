package com.jaymoskingdoms.client.whaling;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.whaling.GreatWhale;
import com.jaymoskingdoms.whaling.Whaling;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/** (0.7.206) Draws the great whales ({@link GreatWhale}) with our own model, {@link #SCALE}x, in their two looks. */
public final class GreatWhaleRenderer extends MobRenderer<GreatWhale, GreatWhaleRenderer.State, GreatWhaleModel> {
	/** How much bigger than the model it's drawn (about fourteen blocks long). */
	static final float SCALE = 3.5F;
	static final Identifier[] SKINS = {JaymosKingdoms.id("textures/entity/whale/rorqual.png"), JaymosKingdoms.id("textures/entity/whale/sperm.png"),
		JaymosKingdoms.id("textures/entity/whale/white.png")};

	public static final class State extends LivingEntityRenderState {
		int variant;
		/** Drawn as a carcass (still, belly up: {@link WhaleCarcassRenderer}). */
		boolean carcass;
	}

	private GreatWhaleRenderer(EntityRendererProvider.Context context) {
		super(context, new GreatWhaleModel(GreatWhaleModel.createBodyLayer().bakeRoot()), 2.5F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GreatWhale whale, State state, float partialTicks) {
		super.extractRenderState(whale, state, partialTicks);
		state.variant = whale.variant();
		state.carcass = false;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return SKINS[Math.floorMod(state.variant, SKINS.length)];
	}

	/** Its head and flukes reach far past its body's box. */
	@Override
	protected AABB getBoundingBoxForCulling(GreatWhale whale, float partialTicks) {
		return super.getBoundingBoxForCulling(whale, partialTicks).inflate(7.0, 1.0, 7.0);
	}

	public static void register() {
		EntityRenderers.register(Whaling.GREAT_WHALE, GreatWhaleRenderer::new);
		EntityRenderers.register(Whaling.WHALE_CARCASS, WhaleCarcassRenderer::new);
		EntityRenderers.register(Whaling.HARPOON, HarpoonRenderer::new);
		EntityRenderers.register(Whaling.FIREBOMB, context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 0.8F, false));
	}
}
