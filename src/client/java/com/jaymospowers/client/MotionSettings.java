package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reduced motion, for players who get sick or tired of a moving screen: a button in Video Settings
 * ({@code MotionVideoSettingsMixin}). On, it turns off every camera kick and rattle ({@link ScreenShake}), the flinch
 * shake of a blow you take, and the kill moment ({@link KillMomentClient}), and shrinks the bodies' hit reactions
 * ({@link HitReactionsClient}) to {@link #REDUCED_SHARE} of their size. {@link #reactionScale} is the player's own
 * share of the reaction (1 as designed, 0 none), for a gore preset to set. Saved in
 * {@code config/jaymospowers-motion.properties}.
 */
public final class MotionSettings {
	/** What share of a hit reaction is left with Reduced motion on. */
	public static final float REDUCED_SHARE = 0.35F;
	/** The most {@link #reactionScale} may be set to. */
	public static final float REACTION_MAX = 1.5F;

	private static final String FILE = "jaymospowers-motion.properties", KEY_REDUCED = "reduced", KEY_REACTION = "reaction";
	private static boolean reduced;
	private static float reaction = 1.0F;
	private static boolean loaded;

	private MotionSettings() {
	}

	/** Whether Reduced motion is on. */
	public static boolean reduced() {
		ensure();
		return reduced;
	}

	public static void setReduced(boolean on) {
		ensure();
		reduced = on;
		save();
	}

	/** The player's own share of hit reactions (0 none, 1 as designed, up to {@link #REACTION_MAX}). */
	public static float reactionScale() {
		ensure();
		return reaction;
	}

	public static void setReactionScale(float scale) {
		ensure();
		reaction = Math.clamp(scale, 0.0F, REACTION_MAX);
		save();
	}

	/** What the hit reactions are multiplied by right now: the player's share, cut down when Reduced motion is on. */
	public static float reactionNow() {
		return reactionScale() * (reduced() ? REDUCED_SHARE : 1.0F);
	}

	private static void ensure() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(path)) {
			props.load(in);
			reduced = Boolean.parseBoolean(props.getProperty(KEY_REDUCED, "false").trim());
			float read = Float.parseFloat(props.getProperty(KEY_REACTION, "1").trim());
			reaction = Float.isFinite(read) ? Math.clamp(read, 0.0F, REACTION_MAX) : 1.0F;
		} catch (IOException | IllegalArgumentException e) {
			JaymosPowers.LOGGER.warn("Couldn't read the motion setting; using the defaults", e);
			reduced = false;
			reaction = 1.0F;
		}
	}

	private static void save() {
		Properties props = new Properties();
		props.setProperty(KEY_REDUCED, Boolean.toString(reduced));
		props.setProperty(KEY_REACTION, Float.toString(reaction));
		try (OutputStream out = Files.newOutputStream(file())) {
			props.store(out, "Jaymo's Powers: reduced motion (true/false) and the share of hit reactions shown (0-1.5)");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't save the motion setting", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}
}
