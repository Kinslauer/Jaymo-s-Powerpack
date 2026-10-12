package com.jaymospowers.client;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.web.WebPower;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Over-the-shoulder third person, for the aimed powers (Web and Gravity): F5 only flips between first person and a
 * view from just behind and to the right of the shoulder ({@link #DISTANCE} back, {@link #SIDE} right, {@link #UP}
 * up), with the crosshair kept on screen so abilities can be aimed from it. The front-facing view is skipped. Anyone
 * else keeps vanilla's three views. See the Camera, CameraType and Hud mixins.
 */
public final class ShoulderCamera {
	/** How far behind the eyes, to the right and up (blocks), before walls pull it in. */
	public static final float DISTANCE = 2.6F, SIDE = 0.7F, UP = 0.15F;

	private ShoulderCamera() {
	}

	/** The local player has a power that uses the shoulder view. */
	public static boolean active() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && (Powers.has(player, WebPower.INSTANCE) || Powers.has(player, GravityPower.INSTANCE));
	}

	/** In the shoulder view right now (third person, from behind, with the power). */
	public static boolean inView() {
		return Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK && active();
	}
}
