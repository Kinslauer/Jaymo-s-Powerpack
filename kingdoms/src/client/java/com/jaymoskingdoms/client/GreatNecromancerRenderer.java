package com.jaymoskingdoms.client;

import com.jaymoskingdoms.GreatNecromancer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Necropolis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** The great necromancer ({@link GreatNecromancer}): the court necromancer's hood and black robe, taller than a man. */
final class GreatNecromancerRenderer extends HumanoidMobRenderer<GreatNecromancer, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/sorcery/necromancer_1.png");
	/** How much taller than a man he stands. */
	static final float SCALE = 1.25F;

	private GreatNecromancerRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot()), 0.5F * SCALE);
		this.addLayer(new ItemInHandLayer<>(this));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	static void register() {
		EntityRenderers.register(Necropolis.TYPE, GreatNecromancerRenderer::new);
	}
}
