package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.56) Low-end mode, the pack's answer to Luxium (a 1.20.1-only shader replacement that can't run beside Iris):
 * shaders off and vanilla's Fast graphics preset (render distance 8, simulation 6, fast clouds, fewer particles, no
 * entity shadows or smooth lighting). Complementary's own settings are never touched (its licence counts that as
 * changing it), only whether Iris draws a shader pack at all.
 *
 * <p>Once per install, when the game has started and the graphics card is known, a weak PC gets it switched on: an
 * integrated, software or virtual graphics card, too little memory given to Java, or too few cores. The first world
 * joined after that says so in chat. {@code /lowend on|off} switches it any time; off puts back the graphics preset,
 * render and simulation distance and shaders as they were before it went on (a Custom preset comes back as Fancy, since
 * the preset alone can't say what was customised).
 */
public final class LowEndMode {
	/** Java given less than this much memory (MB) counts as a weak PC. */
	static final long MIN_MEMORY_MB = 3500;
	/** This many cores or fewer counts as a weak PC. */
	static final int MIN_CORES = 2;

	/**
	 * AMD's built-in Radeons, which the game's own guess (it knows Intel's) calls OTHER under OpenGL: "AMD Radeon(TM)
	 * Graphics", "Radeon Vega 8 Graphics", "Radeon 780M".
	 */
	static final Pattern AMD_BUILT_IN = Pattern.compile("radeon(\\(tm\\))? (vega \\d+ )?graphics|radeon \\d{3}m\\b");

	private static final String FILE = "jaymospowers-low-end.properties";
	/** Bump to check every install again (for a new rule in a later pack version). */
	private static final String CHECKED = "checked-v1";

	private static boolean on;
	/** Why it switched itself on this launch, said once in chat on joining a world. */
	private static @Nullable String announce;

	private LowEndMode() {
	}

	/** True while low-end mode is on, for our own effects that want to do less. */
	public static boolean on() {
		return on;
	}

	public static void init() {
		ClientLifecycleEvents.CLIENT_STARTED.register(LowEndMode::checkOnce);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			String why = announce;
			if (why != null) {
				announce = null;
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(Component.translatable("message.jaymospowers.low_end.auto", why));
					}
				});
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("lowend")
				.executes(c -> report(c.getSource()))
				.then(ClientCommands.literal("on").executes(c -> toggle(c.getSource(), true)))
				.then(ClientCommands.literal("off").executes(c -> toggle(c.getSource(), false)))));
	}

	private static int report(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable(on ? "message.jaymospowers.low_end.is_on" : "message.jaymospowers.low_end.is_off"));
		return Command.SINGLE_SUCCESS;
	}

	private static int toggle(FabricClientCommandSource source, boolean wanted) {
		if (wanted != on) {
			Properties state = load();
			if (wanted) {
				switchOn(source.getClient(), state);
			} else {
				switchOff(source.getClient(), state);
			}
			save(state);
		}
		source.sendFeedback(Component.translatable(wanted ? "message.jaymospowers.low_end.on" : "message.jaymospowers.low_end.off"));
		return Command.SINGLE_SUCCESS;
	}

	private static void checkOnce(Minecraft client) {
		Properties state = load();
		on = Boolean.parseBoolean(state.getProperty("on", "false"));
		if (state.containsKey(CHECKED)) {
			return;
		}
		String why = weakness();
		state.setProperty(CHECKED, why == null ? "fine" : why);
		if (why != null && !on) {
			switchOn(client, state);
			announce = why;
			JaymosPowers.LOGGER.info("Low-end mode switched on: {}", why);
		}
		save(state);
	}

	/** What makes this PC weak, or null if nothing does. */
	private static @Nullable String weakness() {
		try {
			DeviceInfo device = RenderSystem.getDevice().getDeviceInfo();
			DeviceType type = device.type();
			if (type == DeviceType.INTEGRATED || type == DeviceType.CPU || type == DeviceType.VIRTUAL
				|| (type == DeviceType.OTHER && AMD_BUILT_IN.matcher(device.name().toLowerCase(Locale.ROOT)).find())) {
				return "built-in graphics (" + device.name() + ")";
			}
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't read the graphics card", e);
		}
		long memory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
		if (memory < MIN_MEMORY_MB) {
			return memory + " MB of memory for the game";
		}
		int cores = Runtime.getRuntime().availableProcessors();
		if (cores <= MIN_CORES) {
			return cores + " processor cores";
		}
		return null;
	}

	private static void switchOn(Minecraft client, Properties state) {
		Options options = client.options;
		state.setProperty("before.preset", options.graphicsPreset().get().getSerializedName());
		state.setProperty("before.renderDistance", String.valueOf(options.renderDistance().get()));
		state.setProperty("before.simulationDistance", String.valueOf(options.simulationDistance().get()));
		Boolean shaders = Iris.shadersOn();
		if (shaders != null) {
			state.setProperty("before.shaders", String.valueOf(shaders));
		}
		// setting the preset applies it (Options.applyGraphicsPreset)
		options.graphicsPreset().set(GraphicsPreset.FAST);
		options.save();
		Iris.setShaders(false);
		state.setProperty("on", "true");
		on = true;
	}

	private static void switchOff(Minecraft client, Properties state) {
		Options options = client.options;
		GraphicsPreset preset = GraphicsPreset.FANCY;
		for (GraphicsPreset each : GraphicsPreset.values()) {
			if (each != GraphicsPreset.CUSTOM && each.getSerializedName().equals(state.getProperty("before.preset"))) {
				preset = each;
			}
		}
		options.graphicsPreset().set(preset);
		options.renderDistance().set(number(state, "before.renderDistance", options.renderDistance().get()));
		options.simulationDistance().set(number(state, "before.simulationDistance", options.simulationDistance().get()));
		options.save();
		if (Boolean.parseBoolean(state.getProperty("before.shaders", "false"))) {
			Iris.setShaders(true);
		}
		state.setProperty("on", "false");
		on = false;
	}

	private static int number(Properties state, String key, int fallback) {
		try {
			return Integer.parseInt(state.getProperty(key, ""));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static Properties load() {
		Properties state = new Properties();
		if (Files.exists(path())) {
			try (InputStream in = Files.newInputStream(path())) {
				state.load(in);
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't read {}", path(), e);
			}
		}
		return state;
	}

	private static void save(Properties state) {
		try (OutputStream out = Files.newOutputStream(path())) {
			state.store(out, "Jaymo's Powers low-end mode (/lowend on|off). checked-v1 says what the first launch found.");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't write {}", path(), e);
		}
	}

	/** Iris's public API (net.irisshaders.iris.api.v0), looked up by name so the game runs without Iris. */
	private static final class Iris {
		private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
		private static final String CONFIG = "net.irisshaders.iris.api.v0.IrisApiConfig";

		private static @Nullable Object config() {
			if (!FabricLoader.getInstance().isModLoaded("iris")) {
				return null;
			}
			try {
				Class<?> api = Class.forName(API);
				return api.getMethod("getConfig").invoke(api.getMethod("getInstance").invoke(null));
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Couldn't reach Iris", e);
				return null;
			}
		}

		static @Nullable Boolean shadersOn() {
			Object config = config();
			try {
				return config == null ? null : (Boolean) Class.forName(CONFIG).getMethod("areShadersEnabled").invoke(config);
			} catch (ReflectiveOperationException e) {
				JaymosPowers.LOGGER.warn("Couldn't ask Iris about shaders", e);
				return null;
			}
		}

		static void setShaders(boolean enabled) {
			Object config = config();
			try {
				if (config != null) {
					Class.forName(CONFIG).getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
				}
			} catch (ReflectiveOperationException e) {
				JaymosPowers.LOGGER.warn("Couldn't switch Iris shaders {}", enabled ? "on" : "off", e);
			}
		}
	}
}
