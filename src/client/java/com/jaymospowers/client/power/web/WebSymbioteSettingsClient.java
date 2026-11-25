package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbioteSettings;
import com.jaymospowers.power.web.WebSymbioteSettings.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The symbiote's Settings (WebSymbioteSettingsScreen) as this player has them: kept in {@code config/jaymospowers-symbiote.properties}
 * (one {@code <setting>.off=true} line per setting switched off), and sent to the server on joining and on every change
 * ({@link WebSymbioteSettings.SetOff}), where fidgets, jealousy, requests and hints are asked for. The edge membrane is
 * only ever read here ({@link #edgeOff}).
 */
final class WebSymbioteSettingsClient {
	private static final String FILE = "jaymospowers-symbiote.properties";
	/** The switched-off settings as a bit mask ({@link Setting#bit()}). */
	private static int off;
	private static boolean loaded;

	private WebSymbioteSettingsClient() {
	}

	static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> send());
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = path();
		if (!Files.isRegularFile(file)) {
			return;
		}
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			properties.load(in);
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't read the symbiote settings", e);
			return;
		}
		for (Setting setting : Setting.values()) {
			if (Boolean.parseBoolean(properties.getProperty(key(setting), "false"))) {
				off |= setting.bit();
			}
		}
	}

	private static String key(Setting setting) {
		return setting.name().toLowerCase(Locale.ROOT) + ".off";
	}

	private static void save() {
		Properties properties = new Properties();
		for (Setting setting : Setting.values()) {
			properties.setProperty(key(setting), Boolean.toString(isOff(setting)));
		}
		try (OutputStream out = Files.newOutputStream(path())) {
			properties.store(out, "Jaymo's Powers: what the symbiote's Settings screen has switched off");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't save the symbiote settings", e);
		}
	}

	static boolean isOff(Setting setting) {
		load();
		return (off & setting.bit()) != 0;
	}

	/** The edge membrane (the dread vignette) is switched off. */
	static boolean edgeOff() {
		return isOff(Setting.EDGE);
	}

	/** Flips a setting, keeps it and tells the server. */
	static void toggle(Setting setting) {
		load();
		off ^= setting.bit();
		save();
		send();
	}

	/** Sends the whole mask to the server, if it has the mod. */
	static void send() {
		load();
		if (ClientPlayNetworking.canSend(WebSymbioteSettings.SetOff.TYPE)) {
			ClientPlayNetworking.send(new WebSymbioteSettings.SetOff(off));
		}
	}
}
