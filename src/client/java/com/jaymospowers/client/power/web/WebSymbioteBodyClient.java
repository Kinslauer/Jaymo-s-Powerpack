package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteBody;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.150) The client side of the symbiote's body ({@link WebSymbioteBody}): the Arm's keys (K stows what's in your
 * hand into it, or draws the picked thing out; Sneak + K picks the next slot; hold U to have the tendril use the picked
 * thing), the Mantle key (N), a row of the arm's three slots beside the hotbar, the mantle's catch of the fall (player
 * motion is the client's), the layer that draws the cape and the stored things, and the red strain's sense.
 */
final class WebSymbioteBodyClient {
	private static KeyMapping armKey;
	private static KeyMapping useKey;
	private static KeyMapping mantleKey;
	/** The last Arm Use state sent, so only changes go over the wire. */
	private static boolean useSent;
	/** HUD chips: each slot's square (pixels) and the gap between them, and how far left of the hotbar they start. */
	private static final int CHIP = 22, CHIP_GAP = 2, HOTBAR_HALF = 91, HOTBAR_MARGIN = 6, BOTTOM = 23;
	private static final int CHIP_FILL = 0x90100818, CHIP_RIM = 0xFF5A2A80, CHIP_PICK = 0xFFD86CFF, CHIP_USING = 0xFFFFFFFF;

	private WebSymbioteBodyClient() {
	}

	static void init() {
		armKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_arm", InputConstants.KEY_K, JaymosPowersClient.CATEGORY));
		useKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_arm_use", InputConstants.KEY_U, JaymosPowersClient.CATEGORY));
		mantleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_mantle", InputConstants.KEY_N, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(WebSymbioteBodyClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			useSent = false;
			WebRedSense.reset();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("web_symbiote_arm"), WebSymbioteBodyClient::hud);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new WebBodyLayer(avatar));
			}
		});
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		boolean bonded = WebSymbiote.isBonded(player);
		boolean free = bonded && minecraft.gui.screen() == null && !WebSymbioteClient.inputLocked() && !player.isSpectator() && !minecraft.isPaused();
		while (armKey.consumeClick()) {
			if (free && ClientPlayNetworking.canSend(WebSymbioteBody.ArmKey.TYPE)) {
				ClientPlayNetworking.send(new WebSymbioteBody.ArmKey(player.isShiftKeyDown() ? WebSymbioteBody.CYCLE : WebSymbioteBody.STOW));
			}
		}
		while (mantleKey.consumeClick()) {
			if (free && ClientPlayNetworking.canSend(WebSymbioteBody.MantleKey.TYPE)) {
				ClientPlayNetworking.send(new WebSymbioteBody.MantleKey());
			}
		}
		boolean down = free && useKey.isDown();
		if (down != useSent && ClientPlayNetworking.canSend(WebSymbioteBody.ArmUse.TYPE)) {
			useSent = down;
			ClientPlayNetworking.send(new WebSymbioteBody.ArmUse(down));
		}
		// the mantle catches a long fall: a cape slows you to a controlled descent (the server forgives that fall; motion is ours)
		if (bonded && WebSymbioteBody.mantleCatches(player) && player.fallDistance >= WebSymbioteBody.FALL_MIN) {
			Vec3 motion = player.getDeltaMovement();
			if (motion.y < -WebSymbioteBody.FALL_CAP) {
				player.setDeltaMovement(motion.x, -WebSymbioteBody.FALL_CAP, motion.z);
			}
		}
	}

	/** The arm's slots in a column of chips to the left of the hotbar, only while it holds something or is in use. */
	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || !WebSymbiote.isBonded(player) || !WebSymbioteBody.armUnlocked(player)) {
			return;
		}
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		int total = WebSymbioteBody.ARM_SLOTS * CHIP + (WebSymbioteBody.ARM_SLOTS - 1) * CHIP_GAP;
		int x0 = width / 2 - HOTBAR_HALF - HOTBAR_MARGIN - total;
		int y = height - BOTTOM + 1;
		for (int slot = 0; slot < WebSymbioteBody.ARM_SLOTS; slot++) {
			int x = x0 + slot * (CHIP + CHIP_GAP);
			ItemStack stack = arm.get(slot);
			boolean picked = slot == arm.selected();
			graphics.fill(x, y, x + CHIP, y + CHIP, CHIP_FILL);
			graphics.outline(x, y, CHIP, CHIP, picked ? (arm.using() ? CHIP_USING : CHIP_PICK) : CHIP_RIM);
			if (!stack.isEmpty()) {
				graphics.item(stack, x + 3, y + 3);
				graphics.itemDecorations(minecraft.font, stack, x + 3, y + 3);
			}
		}
		if (!arm.picked().isEmpty() && minecraft.gui.screen() == null) {
			Component name = arm.picked().getHoverName();
			graphics.text(minecraft.font, name, x0, y - 10, CHIP_PICK, true);
		}
	}
}
