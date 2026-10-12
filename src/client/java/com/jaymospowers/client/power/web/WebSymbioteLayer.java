package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * The symbiote's black suit, drawn over a bonded web slinger's whole body (and their skin's outer layer) with the
 * player model's own pose. Everyone sees it; it hides when the wearer is invisible or has the suit turned off.
 * The texture ({@code textures/entity/web_symbiote_suit.png}) is drawn by {@code tools/gen_web.py}.
 */
final class WebSymbioteLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_suit.png");
	/** Veins coursing over the big body ({@code tools/gen_web_veins.py}): frames cycled every {@code VEIN_TICKS}. */
	private static final Identifier[] VEINS = {
		JaymosPowers.id("textures/entity/web_symbiote_veins_0.png"), JaymosPowers.id("textures/entity/web_symbiote_veins_1.png"),
		JaymosPowers.id("textures/entity/web_symbiote_veins_2.png"), JaymosPowers.id("textures/entity/web_symbiote_veins_3.png")};
	private static final int VEIN_TICKS = 4;
	/** The veins sit just over the bulk. */
	private static final float VEIN_GAP = 0.04F;
	private final PlayerModel veinsWide;
	private final PlayerModel veinsSlim;

	/** The suit swollen by Living Armor: the player model inflated, thick and muscular (wide and slim arms). */
	private static final float BULK = 0.7F;
	private final PlayerModel bulkWide;
	private final PlayerModel bulkSlim;
	/** The plain suit sits just over the skin (not on it, where it would flicker against it). */
	private static final float SKIN_GAP = 0.12F;
	private final PlayerModel suitWide;
	private final PlayerModel suitSlim;

	WebSymbioteLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.bulkWide = new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(BULK), false), 64, 64).bakeRoot(), false);
		this.bulkSlim = new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(BULK), true), 64, 64).bakeRoot(), true);
		this.suitWide = new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(SKIN_GAP), false), 64, 64).bakeRoot(), false);
		this.suitSlim = new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(SKIN_GAP), true), 64, 64).bakeRoot(), true);
		this.veinsWide = new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(BULK + VEIN_GAP), false), 64, 64).bakeRoot(), false);
		this.veinsSlim = new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(BULK + VEIN_GAP), true), 64, 64).bakeRoot(), true);
		WebSuitGrowth.register(this.bulkWide, true);
		WebSuitGrowth.register(this.bulkSlim, true);
		WebSuitGrowth.register(this.suitWide, false);
		WebSuitGrowth.register(this.suitSlim, false);
		WebSuitGrowth.register(this.veinsWide, false);
		WebSuitGrowth.register(this.veinsSlim, false);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		boolean slim = state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		WebSuitGrowth.Growth growth = state.getData(WebSuitGrowth.KEY);
		if (growth != null) {
			// it's growing over them (or drawing back): see WebSuitGrowth
			int high = Math.max(growth.from(), growth.to()), low = Math.min(growth.from(), growth.to());
			if (high == 2 && low == 1 || high == 1) {
				coloredCutoutModelCopyLayerRender(slim ? this.suitSlim : this.suitWide, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			}
			if (high == 2) {
				coloredCutoutModelCopyLayerRender(slim ? this.bulkSlim : this.bulkWide, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			}
			return;
		}
		WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
		if (tendrils != null && tendrils.armor()) {
			// Living Armor: the suit swells over the whole body, whether or not it was showing
			PlayerModel bulk = state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM ? this.bulkSlim : this.bulkWide;
			coloredCutoutModelCopyLayerRender(bulk, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			// veins coursing over it, writhing as the frames cycle
			PlayerModel veins = slim ? this.veinsSlim : this.veinsWide;
			Identifier frame = VEINS[(int)(state.ageInTicks / VEIN_TICKS) % VEINS.length];
			coloredCutoutModelCopyLayerRender(veins, frame, poseStack, collector, lightCoords, state, -1, 1);
			return;
		}
		if (state.getData(WebSymbioteClient.SUIT) == null) {
			return;
		}
		PlayerModel suit = state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM ? this.suitSlim : this.suitWide;
		coloredCutoutModelCopyLayerRender(suit, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
	}
}
