package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebRolls;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Combat Roll's charges, for Web's Spider-Sense counters (see {@link WebRolls}): they live in Combat Roll's
 * {@code RollManager} ({@code availableRolls} / {@code maxRolls}, reached through its {@code RollingEntity} by
 * reflection, as {@code DarktideRolls} does). While you're a web slinger the count goes to the server whenever it
 * changes; the server spends one per counter. Combat Roll's own HUD shows the charges. Without Combat Roll nothing is
 * sent and counters cost no charge.
 */
final class WebRollsClient {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("combat_roll");
	/** The count is sent again this often (ticks) even unchanged (the server forgets it on a relog). */
	private static final int RESEND = 40;
	private static @Nullable Method manager;
	private static @Nullable Field available, max;
	private static boolean broken;
	private static int sent = -1;

	private WebRollsClient() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(WebRollsClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(WebRolls.Charge.TYPE, (payload, context) -> change(payload.change()));
	}

	private static @Nullable Object rollManager(LocalPlayer player) {
		if (broken) {
			return null;
		}
		try {
			if (manager == null) {
				manager = Class.forName("net.combat_roll.internals.RollingEntity").getMethod("getRollManager");
				Class<?> type = Class.forName("net.combat_roll.internals.RollManager");
				available = type.getDeclaredField("availableRolls");
				max = type.getDeclaredField("maxRolls");
				available.setAccessible(true);
				max.setAccessible(true);
			}
			return manager.invoke(player);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			broken = true;
			JaymosPowers.LOGGER.warn("Combat Roll's roll charges weren't found; Web's counters cost no charge", e);
			return null;
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !WebPowerClient.isActive(player)) {
			sent = -1;
			return;
		}
		Object rolls = rollManager(player);
		if (rolls == null || available == null) {
			return;
		}
		try {
			int count = available.getInt(rolls);
			if ((count != sent || player.tickCount % RESEND == 0) && ClientPlayNetworking.canSend(WebRolls.Count.TYPE)) {
				sent = count;
				ClientPlayNetworking.send(new WebRolls.Count(count));
			}
		} catch (IllegalAccessException e) {
			broken = true;
		}
	}

	/** The server spent a charge on a counter (-1). */
	private static void change(int change) {
		LocalPlayer player = Minecraft.getInstance().player;
		Object rolls = player == null ? null : rollManager(player);
		if (rolls == null || available == null || max == null) {
			return;
		}
		try {
			int count = available.getInt(rolls) + change;
			available.setInt(rolls, Math.max(0, Math.min(max.getInt(rolls), count)));
		} catch (IllegalAccessException e) {
			broken = true;
		}
	}
}
