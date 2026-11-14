package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Harness;
import com.jaymoskingdoms.HeraldryArmour;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Visors;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The harness on the client: its screen and inventory tab, the visor key (Home; it flips a visored helm's visor,
 * {@link Visors}), an unbound key for the harness screen, and the view from inside a shut helm: with the visor down
 * (or in a frogmouth, whose face never lifts) the world is seen through the sight, a dark band above and below it.
 * Raising the visor takes the bands away. The field of view itself never changes.
 */
final class HeraldryClient {
	/** The dark of the inside of a helm, and how far (gui pixels) it fades into the sight at each edge. */
	static final int DARK = 0xFF07070A, EDGE = 0x0007070A, FADE = 6;

	private static KeyMapping visor, harness;

	private HeraldryClient() {
	}

	static void register() {
		MenuScreens.register(Harness.MENU, HarnessScreen::new);
		HarnessTab.init();
		visor = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.visor", InputConstants.KEY_HOME, ClassKeys.category()));
		harness = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.harness", InputConstants.UNKNOWN.getValue(), ClassKeys.category()));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (visor.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(Visors.Flip.TYPE)) {
					ClientPlayNetworking.send(new Visors.Flip());
				}
			}
			while (harness.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(Harness.Open.TYPE)) {
					ClientPlayNetworking.send(new Harness.Open());
				}
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("visor_sight"), HeraldryClient::sight);
	}

	/** The inside of a shut helm, in first person: dark above and below the sight. */
	private static void sight(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || self.isSpectator() || !minecraft.options.getCameraType().isFirstPerson()
			|| HeraldryArmour.face(self) == HeraldryArmour.Face.OPEN) {
			return;
		}
		HeraldryArmour.Piece helm = HeraldryArmour.of(self.getItemBySlot(EquipmentSlot.HEAD));
		if (helm == null) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int open = Math.round(h * helm.sight());
		int top = (h - open) / 2, bottom = top + open;
		graphics.fill(0, 0, w, top, DARK);
		graphics.fillGradient(0, top, w, top + FADE, DARK, EDGE);
		graphics.fillGradient(0, bottom - FADE, w, bottom, EDGE, DARK);
		graphics.fill(0, bottom, w, h, DARK);
	}
}
