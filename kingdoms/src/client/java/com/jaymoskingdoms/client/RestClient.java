package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BedRest;
import java.lang.ref.WeakReference;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.4) The client's side of bed rest ({@link BedRest}) and the timelapse night ({@link com.jaymoskingdoms.Timelapse}):
 * how dark the screen is in bed. Falling asleep fades to black as in vanilla, but once the night runs by as a timelapse
 * the screen clears to {@link #TIMELAPSE_DIM} so you can watch it, and resting only dims it to {@link #REST_DIM}. The
 * Sleep button over Leave Bed shows while you rest at night.
 */
public final class RestClient {
	/** How dark the screen is (0-1, vanilla's full sleep is 1) while resting, and while the night runs by. */
	static final float REST_DIM = 0.3F, TIMELAPSE_DIM = 0.12F;
	/** How fast the dimness changes (per tick) on its way to those. */
	static final float FADE_STEP = 0.05F;

	static boolean resting;
	private static float shown;
	private static @Nullable WeakReference<Button> sleepButton;

	private RestClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BedRest.State.TYPE, (payload, context) -> resting = payload.resting());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			resting = false;
			shown = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(RestClient::tick);
	}

	/** The Sleep button the bed screen just made. */
	public static void sleepButton(Button button) {
		sleepButton = new WeakReference<>(button);
		button.visible = canSleepNow(Minecraft.getInstance());
	}

	public static void pressSleep() {
		if (ClientPlayNetworking.canSend(BedRest.Sleep.TYPE)) {
			ClientPlayNetworking.send(new BedRest.Sleep());
		}
	}

	private static boolean canSleepNow(Minecraft minecraft) {
		return resting && minecraft.level != null && minecraft.level.isDarkOutside();
	}

	private static boolean timelapse(@Nullable ClientLevel level) {
		return level != null && level.dimensionType().defaultClock().map(c -> level.clockManager().getInstance(c).rate() > 1.0F).orElse(false);
	}

	/** Vanilla's sleep darkness (0-1) for this sleep timer. */
	private static float vanilla(int timer) {
		float amount = timer / 100.0F;
		return amount > 1.0F ? 1.0F - (timer - 100.0F) / 10.0F : amount;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		Button button = sleepButton == null ? null : sleepButton.get();
		if (button != null) {
			button.visible = canSleepNow(minecraft);
		}
		if (player == null || player.getSleepTimer() <= 0) {
			shown = 0.0F;
			return;
		}
		float vanilla = vanilla(player.getSleepTimer());
		if (!player.isSleeping()) {
			shown = vanilla;
			return;
		}
		float target = resting ? Math.min(vanilla, REST_DIM) : timelapse(minecraft.level) ? TIMELAPSE_DIM : vanilla;
		shown = shown < target ? Math.min(target, shown + FADE_STEP) : Math.max(target, shown - FADE_STEP);
	}

	/** How dark to draw the sleep overlay now (0-1). */
	public static float overlay() {
		return shown;
	}
}
