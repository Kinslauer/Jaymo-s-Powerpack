package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RisenOrc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * A risen orc ({@link RisenOrc}) drawn as a dead orc of the old legion: an orc grunt's shape and hide ({@link OrcSkins},
 * as broad, with the tusks, brow and ears), tinted the grey of something long in the ground.
 */
final class RisenOrcRenderer extends HumanoidMobRenderer<RisenOrc, RisenOrcRenderer.State, HumanoidModel<RisenOrcRenderer.State>> {
	/** The grave-grey the hide is tinted (ARGB). */
	static final int GRAVE_TINT = 0xFFA7B09C;

	static final class State extends HumanoidRenderState {
		int face;
	}

	private static final Identifier[] FACES = new Identifier[OrcSkins.GRUNT_FACES];

	static {
		for (int i = 0; i < FACES.length; i++) {
			FACES[i] = JaymosKingdoms.id("textures/entity/orc/grunt_" + i + ".png");
		}
	}

	private RisenOrcRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot()), 0.6F);
		this.addLayer(new Features(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RisenOrc orc, State state, float partialTicks) {
		super.extractRenderState(orc, state, partialTicks);
		state.face = Math.floorMod(orc.getUUID().hashCode(), FACES.length);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return FACES[state.face];
	}

	@Override
	protected int getModelTint(State state) {
		return GRAVE_TINT;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);
		poseStack.scale(OrcSkins.BROAD, 1.0F, OrcSkins.BROAD);
	}

	/** The tusks, brow and ears, in the same grey. */
	private static final class Features extends RenderLayer<State, HumanoidModel<State>> {
		private final HumanoidModel<State> model = OrcSkins.features();

		Features(RenderLayerParent<State, HumanoidModel<State>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			coloredCutoutModelCopyLayerRender(this.model, FACES[state.face], poseStack, collector, lightCoords, state, GRAVE_TINT, 1);
		}
	}

	static void register() {
		EntityRenderers.register(RisenOrc.TYPE, RisenOrcRenderer::new);
	}
}
