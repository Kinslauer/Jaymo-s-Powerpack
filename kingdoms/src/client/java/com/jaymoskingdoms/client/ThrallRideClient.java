package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ThrallRide;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The client's half of riding a thrall by day ({@link ThrallRide}): the camera is the thrall's eyes while the server says so
 * ({@link ThrallRide.Seeing}); the mouse turns the thrall's view, not the sleeper's ({@code RideTurnMixin}, the view mixins); the
 * keys that walk, sprint, jump, sneak and drop go to the server each tick ({@link ThrallRide.Drive}); and one key wakes (with sneak,
 * rides the next thrall). The bed screen stays shut while riding ({@code GuiRideMixin}).
 */
public final class ThrallRideClient {
	/** How long (ticks) a thrall missing from the client's world is waited for before the camera comes home. */
	private static final int PATIENCE = 40;
	/** Degrees the mouse turns the view per pixel. */
	private static final double MOUSE = 0.15;

	private static KeyMapping wake;
	private static int seeing = -1, missing;
	private static float yaw, pitch;
	private static boolean started;

	private ThrallRideClient() {
	}

	static void register() {
		wake = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.thrall_wake", InputConstants.KEY_DELETE, ClassKeys.category()));
		ClientPlayNetworking.registerGlobalReceiver(ThrallRide.Seeing.TYPE, (payload, context) -> context.client().execute(() -> {
			seeing = payload.entity();
			missing = 0;
			started = false;
			if (seeing < 0) {
				home(context.client());
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			seeing = -1;
			started = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ThrallRideClient::tick);
	}

	/** Whether the player is looking out through a thrall's eyes. */
	public static boolean riding() {
		return seeing >= 0;
	}

	/** Whether the camera is on {@code e}, the thrall being ridden. */
	public static boolean looksThrough(Entity e) {
		return seeing >= 0 && e.getId() == seeing;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** The mouse turns the thrall's view. */
	public static void turn(double dx, double dy) {
		yaw = Mth.wrapDegrees(yaw + (float) (dx * MOUSE));
		pitch = Mth.clamp((float) (pitch + dy * MOUSE), -90.0F, 90.0F);
	}

	private static void home(Minecraft mc) {
		if (mc.player != null && mc.getCameraEntity() != mc.player) {
			mc.setCameraEntity(mc.player);
		}
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			seeing = -1;
			return;
		}
		if (seeing < 0) {
			return;
		}
		Entity e = mc.level.getEntity(seeing);
		if (e == null || !e.isAlive()) {
			if (++missing > PATIENCE) {
				seeing = -1;
				home(mc);
			}
		} else {
			missing = 0;
			if (!started) {
				started = true;
				yaw = e.getYHeadRot();
				pitch = e.getXRot();
			}
			if (mc.getCameraEntity() != e) {
				mc.setCameraEntity(e);
			}
		}
		int keys = 0;
		int act = ThrallRide.NONE;
		if (mc.gui.screen() == null) {
			keys |= mc.options.keyUp.isDown() ? ThrallRide.FORWARD : 0;
			keys |= mc.options.keyDown.isDown() ? ThrallRide.BACK : 0;
			keys |= mc.options.keyLeft.isDown() ? ThrallRide.LEFT : 0;
			keys |= mc.options.keyRight.isDown() ? ThrallRide.RIGHT : 0;
			keys |= mc.options.keyJump.isDown() ? ThrallRide.JUMPING : 0;
			keys |= mc.options.keyShift.isDown() ? ThrallRide.SNEAKING : 0;
			keys |= mc.options.keySprint.isDown() ? ThrallRide.SPRINTING : 0;
			while (mc.options.keyDrop.consumeClick()) {
				keys |= ThrallRide.DROP;
			}
			while (wake.consumeClick()) {
				act = mc.options.keyShift.isDown() ? ThrallRide.NEXT : ThrallRide.WAKE;
			}
		}
		if (ClientPlayNetworking.canSend(ThrallRide.Drive.TYPE)) {
			ClientPlayNetworking.send(new ThrallRide.Drive(yaw, pitch, keys, act));
		}
	}
}
