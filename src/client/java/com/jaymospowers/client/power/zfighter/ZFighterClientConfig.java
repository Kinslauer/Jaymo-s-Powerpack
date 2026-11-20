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

	// ---- front camera settings ----
	/** Default distance of the front camera from the player (blocks). */
	public static final double DEFAULT_FRONT_CAM_DISTANCE = 3.4;
	/** Default height offset of the front camera (blocks). */
	public static final double DEFAULT_FRONT_CAM_HEIGHT = 0.1;
	/** Default side offset of the front camera (blocks). */
	public static final double DEFAULT_FRONT_CAM_SIDE = -0.9;
	/** Default smoothing factor for front camera movement (0 = instant, 1 = no movement). */
	public static final double DEFAULT_FRONT_CAM_SMOOTHING = 0.15;

	/** Gets the front camera distance setting. */
	public static double getFrontCamDistance() {
		return Double.parseDouble(get("zfighter_front_cam_distance", String.valueOf(DEFAULT_FRONT_CAM_DISTANCE)));
	}

	/** Sets the front camera distance setting. */
	public static void setFrontCamDistance(double value) {
		set("zfighter_front_cam_distance", String.format(java.util.Locale.ROOT, "%.2f", value));
	}

	/** Gets the front camera height setting. */
	public static double getFrontCamHeight() {
		return Double.parseDouble(get("zfighter_front_cam_height", String.valueOf(DEFAULT_FRONT_CAM_HEIGHT)));
	}

	/** Sets the front camera height setting. */
	public static void setFrontCamHeight(double value) {
		set("zfighter_front_cam_height", String.format(java.util.Locale.ROOT, "%.2f", value));
	}

	/** Gets the front camera side offset setting. */
	public static double getFrontCamSide() {
		return Double.parseDouble(get("zfighter_front_cam_side", String.valueOf(DEFAULT_FRONT_CAM_SIDE)));
	}

	/** Sets the front camera side offset setting. */
	public static void setFrontCamSide(double value) {
		set("zfighter_front_cam_side", String.format(java.util.Locale.ROOT, "%.2f", value));
	}

	/** Gets the front camera smoothing setting. */
	public static double getFrontCamSmoothing() {
		return Double.parseDouble(get("zfighter_front_cam_smoothing", String.valueOf(DEFAULT_FRONT_CAM_SMOOTHING)));
	}

	/** Sets the front camera smoothing setting. */
	public static void setFrontCamSmoothing(double value) {
		set("zfighter_front_cam_smoothing", String.format(java.util.Locale.ROOT, "%.2f", value));
	}

	// ---- HUD options ----
	/** Whether to hide numbers on the HUD (ki, combo, etc.). */
	public static boolean getHudHideNumbers() {
		return Boolean.parseBoolean(get("zfighter_hud_hide_numbers", "false"));
	}

	/** Sets whether to hide numbers on the HUD. */
	public static void setHudHideNumbers(boolean value) {
		set("zfighter_hud_hide_numbers", String.valueOf(value));
	}

	/** Whether HUD values are always visible (not just when the power is active). */
	public static boolean getHudAlwaysVisible() {
		return Boolean.parseBoolean(get("zfighter_hud_always_visible", "false"));
	}

	/** Sets whether HUD values are always visible. */
	public static void setHudAlwaysVisible(boolean value) {
		set("zfighter_hud_always_visible", String.valueOf(value));
	}

	/** The HUD menu scale multiplier. */
	public static double getHudMenuScale() {
		return Double.parseDouble(get("zfighter_hud_menu_scale", "1.0"));
	}

	/** Sets the HUD menu scale multiplier. */
	public static void setHudMenuScale(double value) {
		set("zfighter_hud_menu_scale", String.format(java.util.Locale.ROOT, "%.2f", value));
	}

	/** Whether to show markers over party members. */
	public static boolean getHudPartyMarkers() {
		return Boolean.parseBoolean(get("zfighter_hud_party_markers", "true"));
	}

	/** Sets whether to show markers over party members. */
	public static void setHudPartyMarkers(boolean value) {
		set("zfighter_hud_party_markers", String.valueOf(value));
	}

	// ---- ki sense damage tally ----
	/** Whether to show damage tally on ki sense. */
	public static boolean getKiSenseDamageTally() {
		return Boolean.parseBoolean(get("zfighter_ki_sense_damage_tally", "true"));
	}

	/** Sets whether to show damage tally on ki sense. */
	public static void setKiSenseDamageTally(boolean value) {
		set("zfighter_ki_sense_damage_tally", String.valueOf(value));
	}

	// ---- flight mode ----
	/** Whether flight is in travel mode (fast) or combat mode (nimble). */
	public static boolean getFlightTravelMode() {
		return Boolean.parseBoolean(get("zfighter_flight_travel_mode", "false"));
	}

	/** Sets whether flight is in travel mode. */
	public static void setFlightTravelMode(boolean value) {
		set("zfighter_flight_travel_mode", String.valueOf(value));
	}

	/** Whether the flight mode is locked (prevents accidental switching). */
	public static boolean getFlightModeLocked() {
		return Boolean.parseBoolean(get("zfighter_flight_mode_locked", "false"));
	}

	/** Sets whether the flight mode is locked. */
	public static void setFlightModeLocked(boolean value) {
		set("zfighter_flight_mode_locked", String.valueOf(value));
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
