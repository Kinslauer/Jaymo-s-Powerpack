package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.equestrian.FreeRein;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.110) Free rein in the saddle ({@link FreeRein}), in third person: the mouse turns the camera
 * ({@code MouseHandlerMixin}, {@code FreeReinViewMixin}) and the movement keys turn the rider toward where the camera
 * looks, a little each tick, and the horse after them. Aiming a bow or the like turns you to the camera at once. Back in
 * first person, or out of the saddle, you look where the camera looked. A key (unbound at first) switches it off and on.
 */
public final class FreeReinClient {
	/** How much of the way to the wanted heading the rider turns each tick (Better Mount Steering's mountTurnSpeed). */
	static final float TURN_SPEED = 0.25F;
	/** Mouse to degrees, as {@code Entity.turn}. */
	static final float MOUSE_SCALE = 0.15F;

	private static boolean enabled = true;
	private static boolean on;
	private static float camYaw, camPitch;
	private static @Nullable KeyMapping toggleKey;

	private FreeReinClient() {
	}

	static void register(KeyMapping.Category category) {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.free_rein", InputConstants.UNKNOWN.getValue(), category));
		FreeRein.active = rider -> on && rider == Minecraft.getInstance().player;
		ClientTickEvents.END_CLIENT_TICK.register(FreeReinClient::tick);
	}

	/** Whether free rein holds the camera now. */
	public static boolean on() {
		return on;
	}

	public static float yaw() {
		return camYaw;
	}

	public static float pitch() {
		return camPitch;
	}

	/** (From {@code MouseHandlerMixin}) the mouse moved: under free rein it turns the camera only. True if it did. */
	public static boolean turn(double dx, double dy) {
		if (!on) {
			return false;
		}
		camYaw += (float)dx * MOUSE_SCALE;
		camPitch = Mth.clamp(camPitch + (float)dy * MOUSE_SCALE, -90.0F, 90.0F);
		return true;
	}

	private static boolean wanted(Minecraft minecraft, LocalPlayer player) {
		return enabled && !minecraft.options.getCameraType().isFirstPerson() && player.getVehicle() instanceof AbstractHorse horse
			&& horse.getControllingPassenger() == player;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		while (toggleKey != null && toggleKey.consumeClick()) {
			enabled = !enabled;
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(enabled ? "message.jaymoskingdoms.free_rein.on" : "message.jaymoskingdoms.free_rein.off"));
			}
		}
		boolean want = player != null && wanted(minecraft, player);
		if (want && !on) {
			camYaw = player.getYRot();
			camPitch = player.getXRot();
		} else if (!want && on && player != null) {
			// (you look where the camera looked)
			player.setYRot(camYaw);
			player.setXRot(camPitch);
			player.yRotO = camYaw;
			player.xRotO = camPitch;
		}
		on = want;
		if (!on) {
			return;
		}
		float heading = player.getYRot();
		if (player.isUsingItem()) {
			// aiming: face the camera at once
			heading = camYaw;
		} else {
			// (0.7.137) on a flying dragon S brakes (DragonsClient) instead of turning it round
			boolean braking = player.getVehicle() instanceof com.jaymoskingdoms.dragons.WindDragon dragon && dragon.airborne();
			float ahead = (minecraft.options.keyUp.isDown() ? 1.0F : 0.0F) - (minecraft.options.keyDown.isDown() && !braking ? 1.0F : 0.0F);
			float right = (minecraft.options.keyRight.isDown() ? 1.0F : 0.0F) - (minecraft.options.keyLeft.isDown() ? 1.0F : 0.0F);
			if (ahead != 0.0F || right != 0.0F) {
				float wanted = camYaw + (float)Math.toDegrees(Math.atan2(right, ahead));
				heading += Mth.wrapDegrees(wanted - heading) * TURN_SPEED;
			}
		}
		player.setYRot(heading);
		player.setYHeadRot(heading);
		// (a flying mount climbs where you look)
		player.setXRot(camPitch);
	}
}
