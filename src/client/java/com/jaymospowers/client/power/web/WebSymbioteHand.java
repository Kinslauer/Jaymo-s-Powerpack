package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * The suit in first person: the hand you see is sleeved in it too (swollen with Living Armor), the same models as
 * {@link WebSymbioteLayer}, drawn over the arm the game just drew.
 */
public final class WebSymbioteHand {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_suit.png");
	private static PlayerModel suitWide, suitSlim, bulkWide, bulkSlim;

	private WebSymbioteHand() {
	}

	private static PlayerModel model(float inflate, boolean slim) {
		return new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflate), slim), 64, 64).bakeRoot(), slim);
	}

	/** After the first-person arm: if you wear the suit, its sleeve over it. */
	public static void render(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		// the suit put away stays away: the big body's powers work bare, without its look
		if (!WebSymbiote.suitShown(player)) {
			return;
		}
		boolean armor = WebSymbiote.bulked(player);
		if (suitWide == null) {
			suitWide = model(0.12F, false);
			suitSlim = model(0.12F, true);
			// thinner than the third-person bulk, or it fills the screen
			bulkWide = model(0.35F, false);
			bulkSlim = model(0.35F, true);
		}
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		PlayerModel suit = armor ? slim ? bulkSlim : bulkWide : slim ? suitSlim : suitWide;
		ModelPart sleeve = arm == source.rightArm ? suit.rightArm : suit.leftArm;
		sleeve.loadPose(arm.storePose());
		sleeve.visible = true;
		collector.submitModelPart(sleeve, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
	}
}
