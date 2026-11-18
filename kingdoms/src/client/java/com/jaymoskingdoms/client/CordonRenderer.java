package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Cordon;
import com.jaymoskingdoms.CordonFolk;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * (Awakening pick) The Cordon ({@link CordonFolk}) on the humanoid model, one skin a rank (tools/gen_awakening_assets.py): the
 * sect's ash-grey robes and the black band of the blindfolded over the eyes; the stalker hooded, the enforcer in mail with
 * the binding cord, the Overseer sashed in red, the Arbiter in black under an ash-iron mask, and a head taller.
 */
final class CordonRenderer extends HumanoidMobRenderer<CordonFolk, CordonRenderer.State, HumanoidModel<CordonRenderer.State>> {
	/** How much bigger the Arbiter is drawn (its box is as much bigger). */
	static final float ARBITER_SCALE = 1.25F;

	static final class State extends HumanoidRenderState {
		Cordon.Rank rank = Cordon.Rank.SENTINEL;
	}

	private static final Map<Cordon.Rank, Identifier> SKINS = new EnumMap<>(Cordon.Rank.class);

	static {
		for (Cordon.Rank rank : Cordon.Rank.values()) {
			SKINS.put(rank, JaymosKingdoms.id("textures/entity/cordon/" + rank.key() + ".png"));
		}
	}

	private CordonRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot()), 0.5F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CordonFolk folk, State state, float partialTicks) {
		super.extractRenderState(folk, state, partialTicks);
		state.rank = folk.rank();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return SKINS.get(state.rank);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);
		if (state.rank == Cordon.Rank.ARBITER) {
			poseStack.scale(ARBITER_SCALE, ARBITER_SCALE, ARBITER_SCALE);
		}
	}

	static void register() {
		for (Cordon.Rank rank : Cordon.Rank.values()) {
			EntityRenderers.register(Cordon.TYPES.get(rank), CordonRenderer::new);
		}
	}
}
