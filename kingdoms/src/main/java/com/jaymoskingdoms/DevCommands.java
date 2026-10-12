package com.jaymoskingdoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * (0.5.7) For testing in a dev game only (the {@code jaymoskingdoms.devCommands} system property, set by the Gradle
 * run configs; never in the pack): once a second, the lines of {@code commands.txt} in the game folder are run as
 * server commands (as the server, so with every permission), then the file is deleted. Lets a test drive a
 * singleplayer world from outside (set up a scene, move the camera) without a second game or RCON.
 */
final class DevCommands {
	static final String FILE = "commands.txt";
	static final int EVERY = 20;

	private DevCommands() {
	}

	static void register() {
		if (!Boolean.getBoolean("jaymoskingdoms.devCommands")) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(DevCommands::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Path file = Path.of(FILE);
		if (!Files.exists(file)) {
			return;
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			Files.delete(file);
		} catch (IOException e) {
			return;
		}
		for (String line : lines) {
			String command = line.strip();
			if (command.isEmpty() || command.startsWith("#")) {
				continue;
			}
			JaymosKingdoms.LOGGER.info("[dev] {}", command);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		}
	}
}
