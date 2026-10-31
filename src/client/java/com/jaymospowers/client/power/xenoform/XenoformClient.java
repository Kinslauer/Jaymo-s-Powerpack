package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What everyone's client does with a transformed player: hides them, so {@link XenoformLayer} can draw the alien. */
public final class XenoformClient {
	/** The alien a player is, on their render state (null: themselves). */
	public static final RenderStateDataKey<String> FORM = RenderStateDataKey.create(() -> "jaymospowers:xenoform_form");
	/** True while the player's form is in its last seconds (the alien glows red). */
	public static final RenderStateDataKey<Boolean> ENDING = RenderStateDataKey.create(() -> "jaymospowers:xenoform_ending");
	/** (0.18.93) True while the alien is its Ultimate form (armoured, spiked, glowing seams). */
	public static final RenderStateDataKey<Boolean> ULTIMATE = RenderStateDataKey.create(() -> "jaymospowers:xenoform_ultimate");

	private XenoformClient() {
	}

	/** From the avatar renderer's extractRenderState, last. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Player player = entity instanceof Player p ? p : null;
		String form = player != null ? player.getAttached(XenoformState.FORM) : null;
		state.setData(FORM, form);
		state.setData(XenoformSlap.KEY, XenoformSlap.extract(entity, form, partialTicks));
		state.setData(XenoBand.KEY, form == null && player != null && XenoformPower.has(player));
		state.setData(ENDING, form != null && XenoformState.ending(player));
		state.setData(ULTIMATE, form != null && Boolean.TRUE.equals(player.getAttached(XenoformState.ULTIMATE)));
		XenoformFlightPose.extract(entity, state, partialTicks);
		if (form == null) {
			return;
		}
		hideBody(state);
	}

	/**
	 * (0.15.97) An afterimage of a transformed runner ({@code ChronoStreak}, for Velox's Velocity): the alien their frame
	 * wears, translucent in {@code tint}, laid out as the player's body would be. False (nothing drawn) for a player in
	 * their own shape.
	 */
	public static boolean submitGhost(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int tint) {
		XenoFormModels.FormModel model = XenoFormModels.of(state.getData(FORM));
		if (model == null) {
			return false;
		}
		poseStack.pushPose();
		if (model.renderScale != 1.0F) {
			// as XenoformLayer: drawn up from the feet
			poseStack.translate(0.0F, 1.501F, 0.0F);
			poseStack.scale(model.renderScale, model.renderScale, model.renderScale);
			poseStack.translate(0.0F, -1.501F, 0.0F);
		}
		collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(model.texture), light, OverlayTexture.NO_OVERLAY, tint, null,
			EntityRenderState.NO_OUTLINE);
		poseStack.popPose();
		return true;
	}

	/** The player's own body goes: no skin, no armour, no cape, nothing in the hands or on the head. */
	public static void hideBody(AvatarRenderState state) {
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.heldOnHead.clear();
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.leftHandItemStack = ItemStack.EMPTY;
		state.showCape = false;
		state.showExtraEars = false;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.arrowCount = 0;
		state.stingerCount = 0;
	}
}
