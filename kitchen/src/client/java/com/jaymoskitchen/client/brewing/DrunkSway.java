package com.jaymoskitchen.client.brewing;

import com.jaymoskitchen.brewing.Drunk;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Drunkenness ({@link Drunk}) on your own screen: the view sways gently side to side and up and down, more the drunker
 * you are. Only the local player, and not in a menu.
 */
public final class DrunkSway {
	/** Degrees of yaw swing, and of pitch swing, per point of drunkenness. */
	private static final float YAW_PER_POINT = 0.35F, PITCH_PER_POINT = 0.12F;
	/** The most the view swings (degrees of yaw). */
	private static final float MAX_YAW = 4.0F;
	/** Radians per tick of the side to side, and of the up and down, swing. */
	private static final float YAW_SPEED = 0.06F, PITCH_SPEED = 0.043F;

	/** Client ticks drunk, for the swing's phase. */
	private static float time;
	/** What was added to the view last tick, so each tick adds only the change. */
	private static float lastYaw, lastPitch;

	private DrunkSway() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DrunkSway::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		int level = player == null ? 0 : Drunk.level(player);
		if (player == null || level <= 0 || !player.isAlive()) {
			time = 0.0F;
			lastYaw = 0.0F;
			lastPitch = 0.0F;
			return;
		}
		if (minecraft.gui.screen() != null) {
			return;
		}
		time += 1.0F;
		float yaw = Math.min(MAX_YAW, level * YAW_PER_POINT) * Mth.sin(time * YAW_SPEED);
		float pitch = Math.min(MAX_YAW / 2.0F, level * PITCH_PER_POINT) * Mth.sin(time * PITCH_SPEED + 1.0F);
		player.setYRot(player.getYRot() + (yaw - lastYaw));
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - lastPitch), -90.0F, 90.0F));
		lastYaw = yaw;
		lastPitch = pitch;
	}
}
