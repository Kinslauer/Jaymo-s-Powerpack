package com.jaymoskingdoms.client.monsters;

import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.Werewolf;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The werewolf (after Mo' Creatures'): by day a plain biped in one of four faces, by night the beast ({@link WerewolfModel})
 * in one of four coats (the fiery one flickering through its three skins). It shakes while it changes.
 */
final class WerewolfRenderer extends LegendRenderer<Werewolf> {
	private static final Identifier[] FACES = {LegendRenderer.texture("werewolf_dude"), LegendRenderer.texture("werewolf_human"),
		LegendRenderer.texture("werewolf_oldie"), LegendRenderer.texture("werewolf_woman")};
	private static final Identifier[] COATS = {LegendRenderer.texture("werewolf_black"), LegendRenderer.texture("werewolf_brown"),
		LegendRenderer.texture("werewolf_timber")};
	private static final Identifier[] FIRE = {LegendRenderer.texture("werewolf_fire_1"), LegendRenderer.texture("werewolf_fire_2"),
		LegendRenderer.texture("werewolf_fire_3")};
	/** (0.7.1) The cursed ones' coats (coat, then wear: mangy, scarred, patchy) and their rags ({@code tools/gen_cursed_werewolf.py}). */
	private static final Identifier[][] CURSED = new Identifier[COATS.length][Werewolf.WEARS];
	private static final Identifier[] RAGS = new Identifier[Werewolf.RAG_SETS];

	static {
		String[] coats = {"black", "brown", "timber"}, wears = {"mangy", "scarred", "patchy"};
		for (int c = 0; c < COATS.length; c++) {
			for (int w = 0; w < Werewolf.WEARS; w++) {
				CURSED[c][w] = LegendRenderer.texture("werewolf_cursed_" + coats[c] + "_" + wears[w]);
			}
		}
		for (int r = 0; r < RAGS.length; r++) {
			RAGS[r] = LegendRenderer.texture("werewolf_rags_" + r);
		}
	}

	/** How long each of the fiery coat's skins shows (ticks). */
	private static final int FLICKER_TICKS = 4;

	private final EntityModel<LegendState> beast;
	private final HumanoidModel<LegendState> human;

	private WerewolfRenderer(EntityRendererProvider.Context context) {
		super(context, new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot()), 0.5F, 1.0F, state -> FACES[0], false);
		this.beast = this.model;
		this.human = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32).bakeRoot());
		this.addLayer(new RagsLayer(this));
	}

	@Override
	public void extractRenderState(Werewolf werewolf, LegendState state, float partialTicks) {
		super.extractRenderState(werewolf, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(werewolf, state, partialTicks, this.itemModelResolver);
		state.variant = werewolf.variant();
		state.shut = werewolf.isHuman();
		state.changing = werewolf.changing() || werewolf.change() != 0;
		state.hunched = !werewolf.isHuman() && werewolf.isAggressive() || werewolf.change() != 0;
		state.beast = werewolf.beastness(partialTicks);
		state.build = werewolf.build();
		state.look = werewolf.look();
		state.rags = werewolf.rags();
	}

	@Override
	public void submit(LegendState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.shut ? this.human : this.beast;
		super.submit(state, poseStack, collector, camera);
	}

	/** (0.7.0) A werewolf player doesn't see their own beast from inside it (the camera's in its chest). */
	@Override
	public boolean shouldRender(Werewolf werewolf, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, float partialTicks) {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (werewolf.isNoAi() && minecraft.player != null && minecraft.options.getCameraType().isFirstPerson()
			&& werewolf.distanceToSqr(minecraft.player) < 2.0) {
			return false;
		}
		return super.shouldRender(werewolf, frustum, x, y, z, partialTicks);
	}

	/** (0.7.0) A cursed one's build, and its body swelling as the beast comes out (or shrinking as it goes). */
	@Override
	protected void scale(LegendState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		float grow = 0.65F + 0.35F * state.beast;
		float wide = state.build == 0 ? 0.85F : state.build == 2 ? 1.2F : 1.0F;
		if (grow != 1.0F || wide != 1.0F) {
			poseStack.scale(grow * wide, grow, grow * wide);
		}
	}

	@Override
	public Identifier getTextureLocation(LegendState state) {
		if (state.shut) {
			return FACES[Math.floorMod(state.variant / Werewolf.COATS, FACES.length)];
		}
		int coat = Math.floorMod(state.variant, Werewolf.COATS);
		if (coat == Werewolf.FIRE_COAT) {
			return FIRE[(int) (state.ageInTicks / FLICKER_TICKS) % FIRE.length];
		}
		return state.look > 0 ? CURSED[coat][(state.look - 1) / Werewolf.RAG_SETS % Werewolf.WEARS] : COATS[coat];
	}

	/** (0.7.1) What's left of a cursed one's clothes, dyed the colour they were, torn on over its coat. */
	private static final class RagsLayer extends RenderLayer<LegendState, EntityModel<LegendState>> {
		RagsLayer(RenderLayerParent<LegendState, EntityModel<LegendState>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, LegendState state, float yRot, float xRot) {
			if (state.look <= 0 || state.shut || state.isInvisible) {
				return;
			}
			Identifier rags = RAGS[(state.look - 1) % Werewolf.RAG_SETS];
			renderColoredCutoutModel(this.getParentModel(), rags, poseStack, collector, lightCoords, state, ARGB.opaque(state.rags), 1);
		}
	}

	static void install() {
		EntityRenderers.register(Monsters.WEREWOLF, WerewolfRenderer::new);
	}
}
