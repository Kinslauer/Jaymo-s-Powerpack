package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoNet;
import com.jaymospowers.power.xenoform.XenoVoices;
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
	/** (0.18.91) Each beep of a form's last seconds flashes the screen's edge this colour for this long (the flash fades over FLASH_TICKS). */
	static final int BEEP_FLASH_COLOR = 0xFF3030, BEEP_FLASH_TICKS = 8;
	/** The form's ticks left as this client counts them through its end, and whether it was ending last tick. */
	private static int endingTicks;
	private static boolean endingWas;

	private XenoformPowerClient() {
	}

	public static void init() {
		PowerClients.register(XenoformPower.ID, new XenoformPowerClient());
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.power.xenoform.XenoDouble.TYPE, XenoDoubleRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.power.xenoform.XenoStalker.TYPE, XenoStalkerRenderer::new);
		// (0.18.125, patch 8) the band hunter, changed
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.power.xenoform.RivalAlien.TYPE, RivalAlienRenderer::new);
		// (0.18.122) the star wrecks' probes
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.power.xenoform.XenoFinds.PROBE, StarProbeRenderer::new);
		ClientTickEvents.START_CLIENT_TICK.register(XenoformPowerClient::skillInput);
		NadirRulesScreen.register();
		// (0.18.123, patch 7) the band screen
		BandScreen.register();
		// (0.18.126, patch 9) the band's looks, painted from the skins
		XenoLooks.init();
		XenoEyes.init();
		BandFlashHud.init();
		// everyone's flight poses and Pyre's foot flames (other players may be aliens whatever the local power)
		ClientTickEvents.END_CLIENT_TICK.register(XenoformFlightPose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SkitterCeiling::tick);
		PyreComet.init();
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new XenoformLayer(avatar));
				helper.register(new XenoBand.Layer(avatar));
			}
		});
	}

	/**
	 * From the scroll mixin: while a band wearer holds Z, the wheel turns the dial (down: the next alien, like the hotbar)
	 * and the hotbar stays put; as an alien, letting Z go then quick-changes (0.18.92). True if the scroll was taken.
	 */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (amount == 0.0 || player == null || minecraft.gui.screen() != null || player.isSpectator() || !XenoformPower.has(player)
			|| !com.jaymospowers.client.JaymosPowersClient.modeKey().isDown()) {
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
		// Velox's Velocity: Overclock's wall run (and, through ChronoSpeed.overclocked, its water and lava run)
		com.jaymospowers.client.power.chrono.ChronoSpeedClient.tick(player, com.jaymospowers.power.chrono.ChronoSpeed.velocity(player));
		String form = player.getAttached(XenoformState.FORM);
		if (!java.util.Objects.equals(form, formWas)) {
			formWas = form;
			XenoformSlap.changed(player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
			var shape = XenoformState.form(player);
			flash = FLASH_TICKS;
			flashColor = shape != null ? shape.color : XenoformPower.COLOR;
		}
		// (0.18.91) the last 5 seconds: the screen's edge flashes red on each beep, the server's clock kept from the moment the end began
		boolean ending = XenoformState.ending(player);
		if (ending && !endingWas) {
			endingTicks = XenoformPower.BEEP_TICKS;
		}
		endingWas = ending;
		if (ending && endingTicks > 0) {
			if (XenoformPower.beepsAt(endingTicks)) {
				flash = BEEP_FLASH_TICKS;
				flashColor = BEEP_FLASH_COLOR;
			}
			endingTicks--;
		}
		if (flash > 0) {
			flash--;
		}
		if (burst > 0) {
			burst--;
		}
		PyreFlightClient.tick(player);
	}

	/** G as Pyre: the Flame Jet, flown here ({@link PyreFlightClient}); the server toggles it on the same press. */
	@Override
	public void onKey(LocalPlayer player, com.jaymospowers.api.PowerKey key, boolean down) {
		if (down && key == com.jaymospowers.api.PowerKey.ABILITY_2 && "pyre".equals(player.getAttached(XenoformState.FORM))) {
			PyreFlightClient.toggle(player);
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
		} else if (player.getAttached(XenoformState.FORM) == null && refused(player)) {
			// a band still recharging, or Nadir dialled before the voices are content, refuses the change: the arms drop away, no strike, kick or burst for nothing
			XenoformSlap.drop(now);
		} else {
			XenoformSlap.release(now);
		}
	}

	/** Whether slamming the band now would be refused (the server's checks: the dialled hidden alien not yet earned, the recharge). */
	static boolean refused(LocalPlayer player) {
		XenoForm dialled = XenoForms.get(XenoformState.band(player).dialled());
		return dialled != null && dialled.lent() && !XenoVoices.content(player) || XenoformState.hud(player).recharge() > 0
			|| com.jaymospowers.power.xenoform.XenoJam.jammed(player);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		skillWasDown = false;
		formWas = active ? player.getAttached(XenoformState.FORM) : null;
		flash = 0;
		burst = 0;
		XenoformSlap.clear();
		PyreFlightClient.reset();
		com.jaymospowers.client.power.chrono.ChronoSpeedClient.reset();
	}
}
