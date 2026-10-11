package com.jaymospowers.client.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.darktide.DarktideMove;
import com.jaymospowers.power.darktide.DarktideNet;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.darktide.DarktideState;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.darktide.DarktideWeapons;
import com.jaymospowers.power.web.WebSymbiote;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Darktide's client half: the guard (right click held, sent to the server), the Power Skill key X for Gedan (taken
 * before the symbiote's menu sees it, the way Wind takes it for the Tempest Kick), C at night before ascending (Drown:
 * the core only forwards C for ascended powers), and the HUD. Effects and poses everyone sees are in {@link DarktideFx}
 * and {@link DarktidePose}.
 */
public final class DarktidePowerClient implements PowerClient {
	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String SKILL_KEY = "key.jaymospowers.web_symbiote";

	private static @Nullable KeyMapping skillKey;
	private static boolean skillWasDown;
	private static boolean guardWasUp;
	private static boolean nightWasDown;

	private DarktidePowerClient() {
	}

	public static void init() {
		PowerClients.register(DarktidePower.ID, new DarktidePowerClient());
		ClientTickEvents.START_CLIENT_TICK.register(DarktidePowerClient::skillInput);
		ClientPlayNetworking.registerGlobalReceiver(DarktideNet.Fx.TYPE, (payload, context) -> DarktideFx.receive(payload));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("darktide_fx"), DarktideFx::renderOverlay);
		ClientTickEvents.END_CLIENT_TICK.register(DarktideFx::tick);
	}

	static boolean isActive(LocalPlayer player) {
		return DarktidePower.has(player);
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

	/** X for a Darktide user: Gedan's hold and release. Its presses are taken here so the symbiote's menu never opens. */
	private static void skillInput(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		KeyMapping mapping = skillKey(minecraft);
		if (mapping == null || player == null || !isActive(player) || WebSymbiote.isBonded(player)) {
			skillWasDown = false;
			return;
		}
		while (mapping.consumeClick()) {
			// taken
		}
		boolean down = mapping.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (down != skillWasDown) {
			skillWasDown = down;
			ClientPlayNetworking.send(new DarktideNet.Skill(down));
		}
	}

	/** Whether the local player is rooted (stunned, or holding the Gedan stance). */
	public static boolean inputLocked() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		if (DarktideStatus.stunned(player)) {
			return true;
		}
		DarktideState.Move move = DarktideMove.current(player);
		return move != null && (move.kind() == DarktideMove.STANCE || move.kind() == DarktideMove.STILLWATER || move.kind() == DarktideMove.FLURRY);
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		// the guard: right click held, with hands that can guard
		boolean guard = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& DarktideWeapons.canGuard(player) && !(player.isShiftKeyDown() && DarktideWeapons.isKnife(player.getMainHandItem()));
		if (guard != guardWasUp) {
			guardWasUp = guard;
			ClientPlayNetworking.send(new DarktideNet.Guard(guard));
		}
		// C in the Drowned Dark before ascending: Drown
		boolean night = !Fate.isAscended(player) && DarktidePower.drowned(player) && minecraft.gui.screen() == null
			&& JaymosPowersClient.ultimateKey().isDown();
		if (night != nightWasDown) {
			nightWasDown = night;
			ClientPlayNetworking.send(new DarktideNet.NightKey(night));
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		DarktideHud.render(graphics, delta, player);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		if (guardWasUp) {
			guardWasUp = false;
			ClientPlayNetworking.send(new DarktideNet.Guard(false));
		}
		skillWasDown = false;
		nightWasDown = false;
	}
}
