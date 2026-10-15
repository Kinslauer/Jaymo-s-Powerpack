package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Rig on the client. Keys: with no power, R hover, G palm blast, V shoulder launcher, B the Mind's report (the power
 * keys are free then). With a power those stay the power's, and the Rig key (J by default) uses the selected active;
 * Sneak + J picks the next. Hover is held here (the client moves the player): Jump rises, Sneak sinks, forward pushes
 * a little. The Helm's HUD is {@link RigHud}.
 */
public final class RigClient {
	/** Hover: rise / sink speed (b/t; slower than 0.5, so the descent is a controlled one), the push forward and its cap. */
	static final double HOVER_RISE = 0.22, HOVER_SINK = 0.2, HOVER_PUSH = 0.03, HOVER_MAX = 0.35;
	/** The gentle bob while holding height. */
	static final double HOVER_BOB = 0.015;

	private static KeyMapping rigKey;
	private static final boolean[] powerKeysDown = new boolean[PowerKey.values().length];
	/** The active J uses ({@link RigNet#HOVER}, {@link RigNet#PALM}, {@link RigNet#LAUNCHER}). */
	static int selected = RigNet.PALM;
	/** The server's last word. */
	static RigNet.Status status = new RigNet.Status(false, 0, 0, 0);
	private static int hoverTicks;

	private RigClient() {
	}

	public static void init() {
		rigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.rig", InputConstants.KEY_J, JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(RigNet.Status.TYPE, (payload, context) -> status = payload);
		ClientTickEvents.END_CLIENT_TICK.register(RigClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("rig_hud"), RigHud::render);
	}

	static KeyMapping rigKey() {
		return rigKey;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			status = new RigNet.Status(false, 0, 0, 0);
			java.util.Arrays.fill(powerKeysDown, false);
			return;
		}
		boolean wearing = Rig.wearsAny(player);
		boolean free = minecraft.gui.screen() == null && wearing && !player.isSpectator();
		boolean powerless = Powers.of(player) == null;
		// no power: the four power keys are the Rig's
		for (PowerKey key : PowerKey.values()) {
			boolean down = free && powerless && JaymosPowersClient.keyMapping(key).isDown();
			if (down && !powerKeysDown[key.ordinal()]) {
				ClientPlayNetworking.send(new RigNet.Use(key.ordinal()));
			}
			powerKeysDown[key.ordinal()] = down;
		}
		while (rigKey.consumeClick()) {
			if (!free) {
				continue;
			}
			if (player.isShiftKeyDown()) {
				selected = (selected + 1) % RigNet.ACTIVES;
			} else {
				ClientPlayNetworking.send(new RigNet.Use(selected));
			}
		}
		if (!wearing) {
			status = new RigNet.Status(false, 0, 0, 0);
		}
		if (status.hovering() && Rig.fullSet(player) && !minecraft.isPaused()) {
			hover(minecraft, player);
		} else {
			hoverTicks = 0;
		}
	}

	private static void hover(Minecraft minecraft, LocalPlayer player) {
		hoverTicks++;
		Vec3 motion = player.getDeltaMovement();
		double vy;
		if (minecraft.options.keyJump.isDown()) {
			vy = HOVER_RISE;
		} else if (minecraft.options.keyShift.isDown()) {
			vy = -HOVER_SINK;
		} else if (hoverTicks < 4 && player.onGround()) {
			// lift off
			vy = HOVER_RISE * 1.5;
		} else {
			vy = Math.sin(hoverTicks * 0.15) * HOVER_BOB;
		}
		double vx = motion.x, vz = motion.z;
		if (minecraft.options.keyUp.isDown()) {
			double yaw = Math.toRadians(player.getYRot());
			vx += -Math.sin(yaw) * HOVER_PUSH;
			vz += Math.cos(yaw) * HOVER_PUSH;
			double speed = Math.sqrt(vx * vx + vz * vz);
			if (speed > HOVER_MAX) {
				vx *= HOVER_MAX / speed;
				vz *= HOVER_MAX / speed;
			}
		}
		player.setDeltaMovement(vx, vy, vz);
		player.resetFallDistance();
	}
}
