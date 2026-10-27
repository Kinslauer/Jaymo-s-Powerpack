package com.jaymospowers.client.power.zfighter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.17.60, Andy's NOEA picks) The Z Fighter client settings kept in {@code config/jaymospowers-client.properties}, the
 * file impact frames already use ({@link ZFighterImpactFramesClient}): the HUD layout ({@link ZFighterHudLayout}) and which
 * first-time tips have been shown ({@link ZFighterTips}). Read once, then kept in memory; a write reads the file again
 * first and changes only its own key, so another setting written meanwhile survives. Anything unreadable just means
 * the defaults for this session.
 */
public final class ZFighterClientConfig {
	static final String FILE = "jaymospowers-client.properties";

	private static final Properties CACHE = new Properties();
	private static boolean loaded;

	private ZFighterClientConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static Properties read() {
		Properties properties = new Properties();
		Path file = file();
		if (Files.isRegularFile(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				properties.load(in);
			} catch (IOException | RuntimeException e) {
				// the defaults hold
			}
		}
		return properties;
	}

	/** The setting {@code key}, or {@code fallback} when it isn't there. */
	public static String get(String key, String fallback) {
		if (!loaded) {
			loaded = true;
			CACHE.putAll(read());
		}
		return CACHE.getProperty(key, fallback);
	}

	/** Sets {@code key} (and, with a null value, removes it), in memory and in the file. */
	public static void set(String key, String value) {
		get(key, "");
		if (value == null) {
			CACHE.remove(key);
		} else {
			CACHE.setProperty(key, value);
		}
		Properties properties = read();
		if (value == null) {
			properties.remove(key);
		} else {
			properties.setProperty(key, value);
		}
		try (OutputStream out = Files.newOutputStream(file())) {
			properties.store(out, "Jaymo's Powers: client settings");
		} catch (IOException | RuntimeException e) {
			// the setting still holds for this session
		}
	}
}
