package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * How much blood this player sees (a pick from Cruor's ideas): Off, Light, Normal or Heavy, a button in Video Settings
 * ({@code BloodVideoSettingsMixin}). It scales what the client draws: the drops a spray throws, how long pools and
 * lasting stains are drawn, and how many stains are drawn at once (the oldest go first). The server keeps its lasting
 * stains (tracking, blood magic) the same for everyone; only how this player sees them changes. The F3 screen
 * shows the setting and how much blood is out ({@code BloodDebugLineMixin}). Saved in {@code config/jaymospowers-blood.properties}.
 * <p>
 * Andy: "we can make a setting to turn all blood off". Off ({@link #off}) draws none of it for this player: no drops,
 * pools, stains, prints or wall marks, no blood on bodies, corpses or what's held, no drips or flies, and none of
 * Jaymo's Kitchen's chunks and insides of a body blown apart or eaten (it asks {@link #off} by name). The bodies
 * themselves, and everything the server does with blood, stay as they are.
 */
public final class BloodSettings {
	/** The choices: the share of drops drawn, and of how long a passing pool lies. */
	public enum Amount {
		OFF(0.0F, 0.0F, 0), LIGHT(0.5F, 0.5F, 1500), NORMAL(1.0F, 1.0F, 3000), HEAVY(1.6F, 1.5F, 6000);

		/** The share of drops drawn, the share of how long pools and stains are drawn, and the most stains drawn at once. */
		public final float drops, life;
		public final int stains;

		Amount(float drops, float life, int stains) {
			this.drops = drops;
			this.life = life;
			this.stains = stains;
		}

		public Component label() {
			return Component.translatable("options.jaymospowers.blood." + this.name().toLowerCase(Locale.ROOT));
		}
	}

	/** How much gore this player sees: the share of the extras beyond plain blood (ceiling drips, screen spatter, water clouds). */
	public enum Gore {
		LOW(0.5F), MEDIUM(1.0F), HIGH(1.5F);

		public final float share;

		Gore(float share) {
			this.share = share;
		}
	}

	/** The gore presets by the Blood button: a blood amount and a gore level each. */
	public enum Preset {
		LIGHT(Amount.LIGHT, Gore.LOW), CINEMATIC(Amount.NORMAL, Gore.MEDIUM), BRUTAL(Amount.HEAVY, Gore.HIGH);

		public final Amount amount;
		public final Gore gore;

		Preset(Amount amount, Gore gore) {
			this.amount = amount;
			this.gore = gore;
		}

		public Component label() {
			return Component.translatable("options.jaymospowers.gore." + this.name().toLowerCase(Locale.ROOT));
		}
	}

	/** The grey streamer safe draws blood in. */
	static final int SAFE_GREY = 0x8C8C8C;

	private static final String FILE = "jaymospowers-blood.properties", KEY = "amount", GORE_KEY = "gore", SAFE_KEY = "streamer_safe";
	private static Amount amount = Amount.NORMAL;
	private static Gore gore = Gore.MEDIUM;
	private static boolean streamerSafe;
	private static boolean loaded;

	private BloodSettings() {
	}

	public static Amount amount() {
		if (!loaded) {
			loaded = true;
			load();
		}
		return amount;
	}

	/** Whether this player has blood (and gore) turned off altogether. */
	public static boolean off() {
		return amount() == Amount.OFF;
	}

	/** How much gore this player sees. */
	public static Gore gore() {
		amount();
		return gore;
	}

	/**
	 * Streamer safe: blood draws as grey dust ({@link #tint}), severed parts and gibs aren't drawn, living bodies are drawn
	 * whole and wound marks are hidden. Drawers ask this by name.
	 */
	public static boolean streamerSafe() {
		amount();
		return streamerSafe;
	}

	/** {@code color} (RGB) as this player sees blood of it: grey dust when streamer safe. */
	public static int tint(int color) {
		return streamerSafe() ? SAFE_GREY : color;
	}

	public static void set(Amount to) {
		amount();
		amount = to;
		save();
	}

	public static void setStreamerSafe(boolean on) {
		amount();
		streamerSafe = on;
		save();
	}

	/** The preset this player's blood and gore match, or null if they differ from all of them. */
	public static @org.jspecify.annotations.Nullable Preset preset() {
		for (Preset preset : Preset.values()) {
			if (preset.amount == amount() && preset.gore == gore) {
				return preset;
			}
		}
		return null;
	}

	/** Picks a gore preset: its blood amount and gore level, and its hit reaction strength ({@link #setHitReactionStrength}). */
	public static void set(Preset preset) {
		amount();
		amount = preset.amount;
		gore = preset.gore;
		save();
		setHitReactionStrength(preset);
	}

	/** How hard a preset makes hits move bodies (flinch, shove), as {@link com.jaymospowers.client.MotionSettings}' scale. */
	static final float LIGHT_REACTIONS = 0.6F, CINEMATIC_REACTIONS = 1.0F, BRUTAL_REACTIONS = 1.4F;

	/** Sets how hard hits make bodies react for {@code preset}: Light gentle, Cinematic normal, Brutal strong. */
	public static void setHitReactionStrength(Preset preset) {
		com.jaymospowers.client.MotionSettings.setReactionScale(switch (preset) {
			case LIGHT -> LIGHT_REACTIONS;
			case CINEMATIC -> CINEMATIC_REACTIONS;
			case BRUTAL -> BRUTAL_REACTIONS;
		});
	}

	private static void save() {
		loaded = true;
		Properties props = new Properties();
		props.setProperty(KEY, amount.name());
		props.setProperty(GORE_KEY, gore.name());
		props.setProperty(SAFE_KEY, Boolean.toString(streamerSafe));
		try (OutputStream out = Files.newOutputStream(file())) {
			props.store(out, "Jaymo's Powers: how much blood you see (OFF, LIGHT, NORMAL, HEAVY), the gore level (LOW, MEDIUM, HIGH) and streamer safe");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't save the blood setting", e);
		}
	}

	private static void load() {
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(path)) {
			props.load(in);
			amount = Amount.valueOf(props.getProperty(KEY, Amount.NORMAL.name()).trim().toUpperCase(Locale.ROOT));
			gore = Gore.valueOf(props.getProperty(GORE_KEY, Gore.MEDIUM.name()).trim().toUpperCase(Locale.ROOT));
			streamerSafe = Boolean.parseBoolean(props.getProperty(SAFE_KEY, "false").trim());
		} catch (IOException | IllegalArgumentException e) {
			JaymosPowers.LOGGER.warn("Couldn't read the blood setting; using Normal", e);
			amount = Amount.NORMAL;
			gore = Gore.MEDIUM;
			streamerSafe = false;
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}
}
