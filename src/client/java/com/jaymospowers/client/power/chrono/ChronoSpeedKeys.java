package com.jaymospowers.client.power.chrono;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.chrono.ChronoSlowTime;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The speedster's two extra keys (Overclock and Velocity both), rebindable under the powers' keys:
 * <ul>
 * <li><b>Slow time</b> ({@code .} by default), held at speed: the world crawls while you move normally, for as long as the
 * meter lasts ({@code ChronoSlowTime}).</li>
 * <li><b>Mark</b> ({@code /} by default): Sneak + mark lays a mark where you stand (a column of light shows it within
 * {@link #SHOW_DISTANCE} blocks); mark alone runs you there by yourself ({@code ChronoMarkRun}). One mark at a time, kept
 * until you leave the world.</li>
 * </ul>
 */
public final class ChronoSpeedKeys {
	/** How near (blocks) the mark's column of light is drawn, and ticks between its motes. */
	static final double SHOW_DISTANCE = 64.0;
	static final int SHOW_EVERY = 3;

	private static KeyMapping slowKey;
	private static KeyMapping markKey;
	private static boolean slowSent;
	private static @Nullable BlockPos mark;
	private static @Nullable Identifier markLevel;

	private ChronoSpeedKeys() {
	}

	public static void init() {
		slowKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.speed_slow", InputConstants.KEY_PERIOD, JaymosPowersClient.CATEGORY));
		markKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.speed_mark", InputConstants.KEY_SLASH, JaymosPowersClient.CATEGORY));
	}

	/** Left the world: no mark, no run, no slow. */
	public static void reset() {
		mark = null;
		markLevel = null;
		slowSent = false;
		ChronoMarkRun.stop();
	}

	public static void tick(LocalPlayer player, boolean overclocked) {
		if (slowKey == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean free = overclocked && minecraft.gui.screen() == null && !player.isSpectator() && player.isAlive();
		boolean slow = free && slowKey.isDown();
		if (slow != slowSent && ClientPlayNetworking.canSend(ChronoSlowTime.Hold.TYPE)) {
			slowSent = slow;
			ClientPlayNetworking.send(new ChronoSlowTime.Hold(slow));
		}
		while (markKey.consumeClick()) {
			if (!free) {
				continue;
			}
			Level level = player.level();
			if (player.isShiftKeyDown()) {
				mark = player.blockPosition();
				markLevel = level.dimension().identifier();
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.speed_marked"));
			} else if (ChronoMarkRun.running()) {
				ChronoMarkRun.stop();
			} else if (mark == null || !level.dimension().identifier().equals(markLevel)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.speed_no_mark"));
			} else if (!ChronoMarkRun.start(player, mark)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.speed_no_path"));
			}
		}
		if (!free && ChronoMarkRun.running()) {
			ChronoMarkRun.stop();
		}
		if (ChronoMarkRun.running()) {
			ChronoMarkRun.tick(player);
		}
		if (mark != null && player.tickCount % SHOW_EVERY == 0 && level(player).dimension().identifier().equals(markLevel)
			&& player.distanceToSqr(mark.getX() + 0.5, mark.getY(), mark.getZ() + 0.5) < SHOW_DISTANCE * SHOW_DISTANCE) {
			double y = mark.getY() + (player.tickCount % 24) / 24.0 * 3.0;
			player.level().addParticle(MagicFx.LIGHT, mark.getX() + 0.5, y, mark.getZ() + 0.5, 0.0, 0.05, 0.0);
		}
	}

	private static Level level(LocalPlayer player) {
		return player.level();
	}

	/** The mark's place, for the HUD, or null. */
	public static @Nullable BlockPos mark() {
		return mark;
	}

	public static boolean running() {
		return ChronoMarkRun.running();
	}
}
