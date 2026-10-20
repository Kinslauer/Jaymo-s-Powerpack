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
 * the server as the scan, the HUD ({@link XenoformHud}), the flash when you change shape, the slam of the band
 * ({@link XenoformSlap}), the band on the wrist ({@link XenoBand}), and the alien's body on everyone's screen
 * ({@link XenoformLayer}, {@link XenoformHand}).
 */
public final class XenoformPowerClient implements PowerClient {
	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String SKILL_KEY = "key.jaymospowers.web_symbiote";
	/** The flash on changing shape lasts this many ticks. */
	static final int FLASH_TICKS = 12;
	/** The burst of light at the band when the slam lands (first person) lasts this many ticks. */
	static final int BURST_TICKS = 8;

	private static @Nullable KeyMapping skillKey;
	private static boolean skillWasDown;
	private static @Nullable String formWas;
	/** Ticks left of the flash, and the colour of the shape it marked. */
	static int flash;
	static int flashColor;
	/** Ticks left of the slam's burst of light. */
	static int burst;

	private XenoformPowerClient() {
	}

	public static void init() {
		PowerClients.register(XenoformPower.ID, new XenoformPowerClient());
		ClientTickEvents.START_CLIENT_TICK.register(XenoformPowerClient::skillInput);
		// everyone's flight poses and Pyre's foot flames (other players may be aliens whatever the local power)
		ClientTickEvents.END_CLIENT_TICK.register(XenoformFlightPose::tick);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new XenoformLayer(avatar));
				helper.register(new XenoBand.Layer(avatar));
			}
		});
	}

	/**
	 * From the scroll mixin: while a band wearer holds Z as themselves, the wheel turns the dial (down: the next alien,
	 * like the hotbar) and the hotbar stays put. True if the scroll was taken.
	 */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (amount == 0.0 || player == null || minecraft.gui.screen() != null || player.isSpectator() || !XenoformPower.has(player)
			|| !com.jaymospowers.client.JaymosPowersClient.modeKey().isDown() || player.getAttached(XenoformState.FORM) != null) {
			return false;
		}
		ClientPlayNetworking.send(new XenoNet.Dial(amount < 0.0 ? 1 : -1));
		return true;
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
			XenoformSlap.changed(player.tickCount);
			var shape = XenoformState.form(player);
			flash = FLASH_TICKS;
			flashColor = shape != null ? shape.color : XenoformPower.COLOR;
		}
		if (flash > 0) {
			flash--;
		}
		if (burst > 0) {
			burst--;
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		XenoformHud.render(graphics, delta, player);
	}

	/** Z: the wrist comes up while it's held (the wheel turns the dial), the hand lands as the band slams on letting go. */
	@Override
	public void onMode(LocalPlayer player, boolean down) {
		// the same fractional clock the slap pose reads (tick + partial), so the release doesn't quantise to a tick
		float now = player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (down) {
			XenoformSlap.holdStart(now, player.getAttached(XenoformState.FORM) == null);
		} else {
			XenoformSlap.release(now);
		}
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		skillWasDown = false;
		formWas = active ? player.getAttached(XenoformState.FORM) : null;
		flash = 0;
		burst = 0;
		XenoformSlap.clear();
	}
}
