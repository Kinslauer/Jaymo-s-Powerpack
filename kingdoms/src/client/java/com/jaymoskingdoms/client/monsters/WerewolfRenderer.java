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
import net.minecraft.resources.Identifier;

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
	/** How long each of the fiery coat's skins shows (ticks). */
	private static final int FLICKER_TICKS = 4;

	private final EntityModel<LegendState> beast;
	private final HumanoidModel<LegendState> human;

	private WerewolfRenderer(EntityRendererProvider.Context context) {
		super(context, new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot()), 0.5F, 1.0F, state -> FACES[0], false);
		this.beast = this.model;
		this.human = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32).bakeRoot());
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
	}

	@Override
	public void submit(LegendState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.shut ? this.human : this.beast;
		super.submit(state, poseStack, collector, camera);
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
		return coat == Werewolf.FIRE_COAT ? FIRE[(int) (state.ageInTicks / FLICKER_TICKS) % FIRE.length] : COATS[coat];
	}

	static void install() {
		EntityRenderers.register(Monsters.WEREWOLF, WerewolfRenderer::new);
	}
}
