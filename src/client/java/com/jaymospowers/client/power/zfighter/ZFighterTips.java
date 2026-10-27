package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterFusion;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * (0.17.58, Andy's NOEA picks) First-time tips: a short toast the first time a player gets the Z Fighter power, first
 * opens the training screen, first holds Y (the technique wheel), first learns a form and first fuses. Each shows once
 * ever: the line {@code zfighter_tip.<id>=true} in {@code config/jaymospowers-client.properties}
 * ({@link ZFighterClientConfig}) marks it seen the moment it is shown. Several at once queue up as vanilla toasts do.
 * The client command {@code /zfighterhud} and the HUD editor are mentioned in the first tip.
 */
public final class ZFighterTips {
	/** How long a tip's toast stays up (ms). */
	private static final long TOAST_MS = 9000L;
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(TOAST_MS);
	private static final String PREFIX = "zfighter_tip.";

	/** The tips, each with the id its line in the file and its lang keys are named by. */
	public enum Tip {
		POWER("power"), TRAINING("training"), WHEEL("wheel"), FORM("form"), FUSION("fusion");

		public final String id;

		Tip(String id) {
			this.id = id;
		}
	}

	/** The forms open when the player was last looked at, or -1 before the first look (so a form already known isn't news). */
	private static int formsKnown = -1;

	private ZFighterTips() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterTips::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> formsKnown = -1);
	}

	/** Whether {@code tip} has been shown (to this player, on this computer). */
	public static boolean seen(Tip tip) {
		return "true".equals(ZFighterClientConfig.get(PREFIX + tip.id, "false"));
	}

	/** Shows {@code tip} as a toast, unless it has been shown before. */
	public static void show(Tip tip) {
		Minecraft minecraft = Minecraft.getInstance();
		if (seen(tip) || minecraft.player == null) {
			return;
		}
		ZFighterClientConfig.set(PREFIX + tip.id, "true");
		SystemToast.add(minecraft.gui.toastManager(), TOAST, Component.translatable("tip.jaymospowers.zfighter." + tip.id + ".title"),
			Component.translatable("tip.jaymospowers.zfighter." + tip.id));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || !Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		show(Tip.POWER);
		int forms = ZFighterTraining.formsOpen(player);
		if (formsKnown >= 0 && forms > formsKnown) {
			show(Tip.FORM);
		}
		formsKnown = forms;
		if (!seen(Tip.FUSION) && player.tickCount % 10 == 0) {
			// a fused body is drawn as both of them: the one carrying the fusion look with my id in it
			for (AbstractClientPlayer other : level.players()) {
				ZFighterFusion.Look look = other.getAttached(ZFighterFusion.LOOK);
				if (look != null && (look.first().equals(player.getUUID()) || look.second().equals(player.getUUID()))) {
					show(Tip.FUSION);
					break;
				}
			}
		}
	}
}
