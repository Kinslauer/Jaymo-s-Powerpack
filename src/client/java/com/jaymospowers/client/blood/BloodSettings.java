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
 * How much blood this player sees (a pick from Cruor's ideas): Light, Normal or Heavy, a button in Video Settings
 * ({@code BloodVideoSettingsMixin}). It scales what the client draws: the drops a spray throws, how long pools and
 * lasting stains are drawn, and how many stains are drawn at once (the oldest go first). The server keeps its lasting
 * stains (tracking, blood magic) the same for everyone; only how this player sees them changes. The F3 screen
 * shows the setting and how much blood is out ({@code BloodDebugLineMixin}). Saved in {@code config/jaymospowers-blood.properties}.
 */
public final class BloodSettings {
	/** The choices: the share of drops drawn, and of how long a passing pool lies. */
	public enum Amount {
		LIGHT(0.5F, 0.5F, 1500), NORMAL(1.0F, 1.0F, 3000), HEAVY(1.6F, 1.5F, 6000);

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

	private static final String FILE = "jaymospowers-blood.properties", KEY = "amount";
	private static Amount amount = Amount.NORMAL;
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

	public static void set(Amount to) {
		amount = to;
		loaded = true;
		Properties props = new Properties();
		props.setProperty(KEY, to.name());
		try (OutputStream out = Files.newOutputStream(file())) {
			props.store(out, "Jaymo's Powers: how much blood you see (LIGHT, NORMAL, HEAVY)");
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
		} catch (IOException | IllegalArgumentException e) {
			JaymosPowers.LOGGER.warn("Couldn't read the blood setting; using Normal", e);
			amount = Amount.NORMAL;
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}
}
