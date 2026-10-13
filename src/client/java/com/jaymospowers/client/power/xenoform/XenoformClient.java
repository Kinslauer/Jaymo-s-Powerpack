package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What everyone's client does with a transformed player: hides them, so {@link XenoformLayer} can draw the alien. */
public final class XenoformClient {
	/** The alien a player is, on their render state (null: themselves). */
	public static final RenderStateDataKey<String> FORM = RenderStateDataKey.create(() -> "jaymospowers:xenoform_form");
	/** True while the player's form is in its last seconds (the alien glows red). */
	public static final RenderStateDataKey<Boolean> ENDING = RenderStateDataKey.create(() -> "jaymospowers:xenoform_ending");

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
		if (form == null) {
			return;
		}
		// the player's own body goes: no skin, no armour, no cape, nothing in the hands or on the head
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
