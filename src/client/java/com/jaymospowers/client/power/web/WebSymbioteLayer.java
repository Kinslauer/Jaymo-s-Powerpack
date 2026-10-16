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
 * The texture ({@code textures/entity/web_symbiote_suit.png}) is drawn by {@code tools/gen_web.py}: the clean black
 * suit. Its veins show only while it's feral (a bond under {@code VEIN_BOND}). The big body is its own hulking model
 * ({@link WebHulk}, veins in its texture) with a lolling tongue. While either is being put on (see WebSuitGrowth) a
 * layer of raw goo goes on under the suit first.
 */
final class WebSymbioteLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_suit.png");
	/** The raw goo laid on first, under the suit, as it's put on. */
	private static final Identifier GOO = JaymosPowers.id("textures/entity/web_symbiote_goo.png");
	/** Veins coursing over the feral suit ({@code tools/gen_web_veins.py}): frames cycled every {@code VEIN_TICKS}. */
	private static final Identifier[] VEINS = {
		JaymosPowers.id("textures/entity/web_symbiote_veins_0.png"), JaymosPowers.id("textures/entity/web_symbiote_veins_1.png"),
		JaymosPowers.id("textures/entity/web_symbiote_veins_2.png"), JaymosPowers.id("textures/entity/web_symbiote_veins_3.png")};
	private static final int VEIN_TICKS = 4;
	/** Under this bond the plain suit still shows its veins (it's feral). */
	private static final int VEIN_BOND = 40;
	/** The veins sit just over the suit. */
	private static final float VEIN_GAP = 0.04F;
	/** The veins over the plain suit (feral). */
	private final PlayerModel suitVeinsWide;
	private final PlayerModel suitVeinsSlim;

	/** The big body. */
	private final WebHulk.Model hulk;
	/** The plain suit sits just over the skin (not on it, where it would flicker against it); the goo just under it. */
	private static final float SKIN_GAP = 0.12F, GOO_GAP = 0.06F;
	private final PlayerModel suitWide;
	private final PlayerModel suitSlim;
	private final PlayerModel gooWide;
	private final PlayerModel gooSlim;

	WebSymbioteLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.hulk = new WebHulk.Model();
		this.suitWide = suitModel(SKIN_GAP, false);
		this.suitSlim = suitModel(SKIN_GAP, true);
		this.gooWide = suitModel(GOO_GAP, false);
		this.gooSlim = suitModel(GOO_GAP, true);
		this.suitVeinsWide = suitModel(SKIN_GAP + VEIN_GAP, false);
		this.suitVeinsSlim = suitModel(SKIN_GAP + VEIN_GAP, true);
		WebSuitGrowth.register(this.hulk, WebSuitGrowth.Role.HULK);
		for (PlayerModel model : new PlayerModel[] {this.suitWide, this.suitSlim, this.suitVeinsWide, this.suitVeinsSlim}) {
			WebSuitGrowth.register(model, WebSuitGrowth.Role.SUIT);
		}
		WebSuitGrowth.register(this.gooWide, WebSuitGrowth.Role.GOO);
		WebSuitGrowth.register(this.gooSlim, WebSuitGrowth.Role.GOO);
	}

	private static PlayerModel suitModel(float gap, boolean slim) {
		return new PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
			PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(gap), slim), 64, 64).bakeRoot(), slim);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		boolean slim = state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		WebSuitGrowth.Growth growth = state.getData(WebSuitGrowth.KEY);
		if (growth != null) {
			// it's being built over them (or taken apart): see WebSuitGrowth
			int high = Math.max(growth.from(), growth.to()), low = Math.min(growth.from(), growth.to());
			if (low == 0) {
				// bare skin: the goo goes on first, the suit over it
				coloredCutoutModelCopyLayerRender(slim ? this.gooSlim : this.gooWide, GOO, poseStack, collector, lightCoords, state, -1, 1);
			}
			coloredCutoutModelCopyLayerRender(slim ? this.suitSlim : this.suitWide, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			if (high == 2) {
				coloredCutoutModelCopyLayerRender(this.hulk, WebHulk.TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
				tongue(poseStack, collector, state, WebSuitGrowth.hulkBuilt(state));
			}
			return;
		}
		WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
		if (tendrils != null && tendrils.armor()) {
			if (state.getData(WebSuitGrowth.PULL) != null) {
				// fighting its hold: as the big body comes apart you show through it, tearing out of the suit beneath
				coloredCutoutModelCopyLayerRender(slim ? this.suitSlim : this.suitWide, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			}
			// the big body, whether or not the suit was showing
			coloredCutoutModelCopyLayerRender(this.hulk, WebHulk.TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			tongue(poseStack, collector, state, WebSuitGrowth.peeledHulk(state));
			return;
		}
		if (state.getData(WebSymbioteClient.SUIT) == null) {
			return;
		}
		PlayerModel suit = slim ? this.suitSlim : this.suitWide;
		coloredCutoutModelCopyLayerRender(suit, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
		Float feral = state.getData(WebSymbioteClient.FERAL);
		if (feral != null && feral > 1.0F - VEIN_BOND / (float)com.jaymospowers.power.web.WebSymbiote.BOND_MAX) {
			// still feral: its veins show through the suit
			coloredCutoutModelCopyLayerRender(slim ? this.suitVeinsSlim : this.suitVeinsWide, veins(state), poseStack, collector, lightCoords, state, -1, 1);
		}
	}

	/** The big body's tongue, lolling out of its jaws (it comes in with the head, the last thing built). */
	private void tongue(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, float built) {
		if (state.isInvisible || WebSymbioteClient.ownFirstPerson(state) || state.getData(WebSymbioteClient.EATING) != null
			|| state.getData(WebSymbioteClient.COUNTER_MAW) != null || state.getData(WebSymbioteClient.ROAR) != null) {
			// (the maw has the head)
			return;
		}
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		WebHulk.submitTongue(poseStack, collector, state, parent, net.minecraft.util.Mth.clamp((built - 0.85F) / 0.15F, 0.0F, 1.0F));
	}

	/** The veins' frame now: cycling, so they pulse and writhe. */
	private static Identifier veins(AvatarRenderState state) {
		return VEINS[(int)(state.ageInTicks / VEIN_TICKS) % VEINS.length];
	}
}
