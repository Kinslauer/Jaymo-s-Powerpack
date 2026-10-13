package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.xenoform.XenoNet;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Xenoform's client half: the Power Skill key X (taken before the symbiote's menu sees it, the way Darktide does) sent to
 * the server as the scan, the HUD ({@link XenoformHud}), the flash when you change shape, and the alien's body on
 * everyone's screen ({@link XenoformLayer}, {@link XenoformHand}).
 */
public final class XenoformPowerClient implements PowerClient {
	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String SKILL_KEY = "key.jaymospowers.web_symbiote";
	/** The flash on changing shape lasts this many ticks. */
	static final int FLASH_TICKS = 12;

	private static @Nullable KeyMapping skillKey;
	private static boolean skillWasDown;
	private static @Nullable String formWas;
	/** Ticks left of the flash, and the colour of the shape it marked. */
	static int flash;
	static int flashColor;

	private XenoformPowerClient() {
	}

	public static void init() {
		PowerClients.register(XenoformPower.ID, new XenoformPowerClient());
		ClientTickEvents.START_CLIENT_TICK.register(XenoformPowerClient::skillInput);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new XenoformLayer(avatar));
			}
		});
	}

	static @Nullable KeyMapping skillKey(Minecraft minecraft) {
		if (skillKey == null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (SKILL_KEY.equals(mapping.getName())) {
					skillKey = mapping;
					break;
				}
			}
		}
		return skillKey;
	}

	static String skillKeyLabel(Minecraft minecraft) {
		KeyMapping mapping = skillKey(minecraft);
		return mapping == null ? "X" : mapping.getTranslatedKeyMessage().getString();
	}

	/** X for a band wearer: the scan, held. Its presses are taken here so nothing else answers them. */
	private static void skillInput(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		KeyMapping mapping = skillKey(minecraft);
		if (mapping == null || player == null || !XenoformPower.has(player)) {
			skillWasDown = false;
			return;
		}
		while (mapping.consumeClick()) {
			// taken
		}
		boolean down = mapping.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (down != skillWasDown) {
			skillWasDown = down;
			ClientPlayNetworking.send(new XenoNet.Skill(down));
		}
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		String form = player.getAttached(XenoformState.FORM);
		if (!java.util.Objects.equals(form, formWas)) {
			formWas = form;
			var shape = XenoformState.form(player);
			flash = FLASH_TICKS;
			flashColor = shape != null ? shape.color : XenoformPower.COLOR;
		}
		if (flash > 0) {
			flash--;
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		XenoformHud.render(graphics, delta, player);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		skillWasDown = false;
		formWas = active ? player.getAttached(XenoformState.FORM) : null;
		flash = 0;
	}
}
