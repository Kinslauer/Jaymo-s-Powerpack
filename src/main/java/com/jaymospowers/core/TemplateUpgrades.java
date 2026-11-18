package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Structure templates the game has already brought up to date, kept on disk between sessions. Nearly every structure
 * in the pack (9,700 of 13,600 templates, Dungeons and Taverns, Towns and Towers, the Epic and Moog's structures, our own
 * Kingdoms buildings) was saved by an older game, so the game upgrades each one through its data fixers the first time a
 * session uses it. Profiled on a new world (2026-11-17), that upgrading was 30% of all the work the game did while it
 * found a spawn and made the first player's land, and it came back in every session. So the upgraded template is kept,
 * named by a hash of the template as the mod ships it and the game's data version; the next session reads it back instead
 * of upgrading again. A change to the template or to the game makes a new name; anything unreadable is just upgraded again.
 */
public final class TemplateUpgrades {
	private static final int VERSION = SharedConstants.WORLD_VERSION;
	private static final String PREFIX = "templates-v";
	private static volatile Path dir;
	private static volatile boolean broken;

	private TemplateUpgrades() {
	}

	/** {@code tag} (saved at {@code version}) brought up to date: from the disk if this was done before, else by {@code upgrade}, then kept. */
	public static CompoundTag upgraded(CompoundTag tag, int version, Supplier<CompoundTag> upgrade) {
		if (version >= VERSION || broken) {
			return upgrade.get();
		}
		Path file;
		try {
			file = dir().resolve(hash(tag, version) + ".nbt");
		} catch (IOException | RuntimeException e) {
			give(e);
			return upgrade.get();
		}
		if (Files.isRegularFile(file)) {
			try {
				return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			} catch (IOException | RuntimeException e) {
				JaymosPowers.LOGGER.debug("Couldn't read the kept template {}; upgrading it again", file, e);
			}
		}
		CompoundTag done = upgrade.get();
		try {
			Path part = Files.createTempFile(file.getParent(), "part", ".tmp");
			NbtIo.writeCompressed(done, part);
			Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException e) {
			give(e);
		}
		return done;
	}

	private static void give(Exception e) {
		if (!broken) {
			broken = true;
			JaymosPowers.LOGGER.warn("Can't keep upgraded structure templates on disk; they'll be upgraded every session", e);
		}
	}

	/** The folder for this game version (made on first use, older versions' folders cleared away). */
	private static Path dir() throws IOException {
		Path d = dir;
		if (d == null) {
			synchronized (TemplateUpgrades.class) {
				d = dir;
				if (d == null) {
					Path root = FabricLoader.getInstance().getGameDir().resolve(".cache").resolve(JaymosPowers.MOD_ID);
					d = root.resolve(PREFIX + VERSION);
					Files.createDirectories(d);
					try (Stream<Path> old = Files.list(root)) {
						for (Path p : old.filter(p -> p.getFileName().toString().startsWith(PREFIX) && !p.getFileName().toString().equals(PREFIX + VERSION)).toList()) {
							clear(p);
						}
					}
					dir = d;
				}
			}
		}
		return d;
	}

	private static void clear(Path folder) {
		try (Stream<Path> files = Files.list(folder)) {
			for (Path f : files.toList()) {
				Files.deleteIfExists(f);
			}
			Files.deleteIfExists(folder);
		} catch (IOException e) {
			JaymosPowers.LOGGER.debug("Couldn't clear old kept templates in {}", folder, e);
		}
	}

	private static String hash(CompoundTag tag, int version) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			out.writeInt(version);
			NbtIo.write(tag, out);
		}
		return HexFormat.of().formatHex(digest.digest(), 0, 20);
	}
}
