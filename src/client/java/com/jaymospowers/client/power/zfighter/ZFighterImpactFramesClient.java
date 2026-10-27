package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.zfighter.ZFighterSensesNet;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * (0.17.45, Andy's NOEA picks) Impact frames: when a Z Fighter finisher, a Spirit Bomb landing or a full ape smash goes
 * off within 24 blocks of you, the whole screen flashes white and then black, one game tick each ({@link #PHASE_TICKS}),
 * with a few white streaks across the black. A client setting, on by default, turns it off completely: nothing is
 * drawn and an incoming flash is dropped. The setting is the line {@code impact_frames=true} in
 * {@code config/jaymospowers-client.properties}, and the (unbound) Controls entry "Toggle impact frames" flips it in game.
 */
public final class ZFighterImpactFramesClient {
	/** Game ticks each of the two frames shows (a tick is 50 ms). */
	static final int PHASE_TICKS = 1;
	/** The black frame's white streaks. */
	static final int STREAKS = 14;
	static final String FILE = "jaymospowers-client.properties";
	static final String SETTING = "impact_frames";

	private static boolean enabled = true;
	private static boolean loaded;
	private static KeyMapping toggleKey;
	/** Client ticks counted since start (the flash runs on these, paused or not); and when the flash began (-1: none). */
	private static long ticks;
	private static long flashFrom = -1L;
	private static long seed;

	private ZFighterImpactFramesClient() {
	}

	static void init() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.impact_frames", InputConstants.UNKNOWN.getType(),
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSensesNet.ImpactFrame.TYPE, (payload, ctx) -> {
			if (enabled()) {
				flashFrom = ticks;
				seed = ticks * 31L + 17L;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterImpactFramesClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, JaymosPowers.id("zfighter_impact_frames"), ZFighterImpactFramesClient::render);
	}

	/** Whether impact frames are on (the setting, read once from the file). */
	public static boolean enabled() {
		if (!loaded) {
			loaded = true;
			Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
			if (Files.isRegularFile(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					Properties properties = new Properties();
					properties.load(in);
					enabled = !"false".equalsIgnoreCase(properties.getProperty(SETTING, "true").trim());
				} catch (IOException | RuntimeException e) {
					enabled = true;
				}
			}
		}
		return enabled;
	}

	private static void set(boolean value) {
		enabled = value;
		loaded = true;
		if (!value) {
			flashFrom = -1L;
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		Properties properties = new Properties();
		try {
			if (Files.isRegularFile(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					properties.load(in);
				}
			}
			properties.setProperty(SETTING, Boolean.toString(value));
			try (OutputStream out = Files.newOutputStream(file)) {
				properties.store(out, "Jaymo's Powers: client settings");
			}
		} catch (IOException | RuntimeException e) {
			// the setting still holds for this session
		}
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		while (toggleKey.consumeClick()) {
			set(!enabled());
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable(enabled() ? "message.jaymospowers.zfighter_impact_frames_on"
					: "message.jaymospowers.zfighter_impact_frames_off").withStyle(ChatFormatting.GRAY));
			}
		}
		if (flashFrom >= 0L && ticks - flashFrom >= 2L * PHASE_TICKS) {
			flashFrom = -1L;
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (flashFrom < 0L || !enabled()) {
			return;
		}
		long into = ticks - flashFrom;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (into < PHASE_TICKS) {
			graphics.fill(0, 0, width, height, 0xFFFFFFFF);
		} else if (into < 2L * PHASE_TICKS) {
			graphics.fill(0, 0, width, height, 0xFF000000);
			RandomSource random = RandomSource.create(seed);
			for (int i = 0; i < STREAKS; i++) {
				int y = random.nextInt(height);
				int x = random.nextInt(width);
				int length = width / 4 + random.nextInt(width / 2);
				graphics.fill(Math.max(0, x - length / 2), y, Math.min(width, x + length / 2), y + 1 + random.nextInt(2), 0xFFFFFFFF);
			}
		}
	}
}
