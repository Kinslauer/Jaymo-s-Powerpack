package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BioBud;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.310, Andy: "they should look like smaller versions of the user, not zombies") A Bio-built's bud drawn as its maker.
 * (0.7.313) Its maker buds only in the Perfect form, so it is that form small ({@link BioForms}: the skin, glow and
 * parts in the maker's palette; a maker this client doesn't see, the seamed green).
 */
final class BioBudRenderer extends HumanoidMobRenderer<BioBud, BioBudRenderer.State, HumanoidModel<BioBudRenderer.State>> {
	/** The form buds take: the third. */
	static final int FORM = 2;

	static final class State extends HumanoidRenderState {
		int palette;
	}

	private BioBudRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot()), 0.5F);
		this.addLayer(new Form(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BioBud bud, State state, float partialTicks) {
		super.extractRenderState(bud, state, partialTicks);
		UUID maker = BioBud.maker(bud);
		Player player = maker == null || bud.level() == null ? null : bud.level().getPlayerByUUID(maker);
		state.palette = BioForms.palette(player);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return BioForms.texture(state.palette, FORM + "_skin");
	}

	/** The form's glow (nodes, beads, eyes) and its parts (horns, wings, stinger). */
	private static final class Form extends RenderLayer<State, HumanoidModel<State>> {
		Form(RenderLayerParent<State, HumanoidModel<State>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			HumanoidModel<State> model = this.getParentModel();
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(BioForms.texture(state.palette, FORM + "_glow")), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			BioForms.parts(poseStack, collector, lightCoords, model, FORM, state.palette, false, false);
		}
	}

	static void register() {
		EntityRenderers.register(BioBud.TYPE, BioBudRenderer::new);
	}
}
