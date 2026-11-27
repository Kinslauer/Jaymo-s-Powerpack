package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.ComboClashCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Our side of Combo Clash (shipped as its unmodified jar, "Combo Clash" by HanHoYoung; see {@link ComboClashCompat}). While
 * its combat mode is on (its own key, set to the grave key by {@code KeyConflicts}), its 4-hit strings, dash and evasive
 * are the fight, and ours step aside so the two never double up:
 * <ul>
 * <li>Better Combat's swings and our region swings: Better Combat sees no weapon in the hand ({@code ToolSwingGateMixin}),
 * so the click is Combo Clash's M1.</li>
 * <li>Our Alt dodge (dash, roll, hold to prone, dive, vault, flip): Combo Clash's dash sits on Left Alt too, so in combat
 * mode Alt is its dash and evasive ({@code DodgeClient}). Rolling back up after a leg blow still works.</li>
 * <li>Our arm and body poses over a player (the armed stance, the parry, the hit flinch and everything else that asks
 * {@code PoseTakeover.posedOtherwise}) give way while one of its motions is playing on that player ({@link #moving}).</li>
 * </ul>
 * Combat mode is switched off, as its own key would (camera put back, the server told), for anyone who can't fight just
 * now ({@link ComboClashCompat#cantFight}): downed, carried, fainting, tied hand and foot or crippled.
 * Everything is reached by reflection ({@code com.comboclash.client.ClientCombat} and {@code CombatAnim}), so nothing
 * breaks when Combo Clash isn't installed.
 */
public final class ComboClashClient {
	private static Field combatMode;
	private static KeyMapping toggle;
	private static Field clickCount;
	private static Method playing;
	/** Ticks left before we press its key again (the toggle lands on its next tick). */
	private static int pressedFor;

	private ComboClashClient() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(ComboClashCompat.MOD_ID)) {
			return;
		}
		try {
			Class<?> combat = Class.forName("com.comboclash.client.ClientCombat");
			combatMode = combat.getField("combatMode");
			toggle = (KeyMapping) combat.getField("TOGGLE").get(null);
			clickCount = KeyMapping.class.getDeclaredField("clickCount");
			clickCount.setAccessible(true);
			playing = Class.forName("com.comboclash.client.CombatAnim").getMethod("playing", AbstractClientPlayer.class);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't reach Combo Clash's client (a newer Combo Clash?); our combat won't step aside for it", e);
			combatMode = null;
			playing = null;
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(ComboClashClient::tick);
	}

	/** Whether Combo Clash's combat mode is on for us. */
	public static boolean active() {
		if (combatMode == null) {
			return false;
		}
		try {
			return combatMode.getBoolean(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Whether one of Combo Clash's motions is playing on the player drawn as {@code state}. */
	public static boolean moving(AvatarRenderState state) {
		if (playing == null) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.level.getEntity(state.id) instanceof AbstractClientPlayer player)) {
			return false;
		}
		try {
			return playing.invoke(null, player) != null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static void tick(Minecraft minecraft) {
		if (pressedFor > 0) {
			pressedFor--;
			return;
		}
		if (minecraft.player == null || !active() || !ComboClashCompat.cantFight(minecraft.player)) {
			return;
		}
		// one press of its own key: it puts the camera back and tells the server, as a player's press would
		try {
			clickCount.setInt(toggle, clickCount.getInt(toggle) + 1);
			pressedFor = 5;
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't switch Combo Clash's combat mode off", e);
			combatMode = null;
		}
	}
}
