package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DesignUploads;
import com.jaymoskingdoms.SchematicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) The schematic files on this computer, and sending one to the server as a design ({@link DesignUploads}).
 * Files are read from the game folder's {@code schematics} folder (where Litematica keeps its own, so files downloaded
 * for it are already there): {@code .nbt}, {@code .schem} and {@code .litematic}. (0.7.488) Used by the mason's table's
 * screen ({@link MasonScreen}); no commands.
 */
public final class DesignUploadClient {
	/** The folder in the game directory. */
	static final String FOLDER = "schematics";

	private DesignUploadClient() {
	}

	static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
	}

	/** Makes the folder if it isn't there yet (so it can be opened and filled). */
	static Path ensureFolder() {
		Path dir = folder();
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
			// (opening it then just fails)
		}
		return dir;
	}

	/** The schematic files in the folder (and its subfolders, as {@code sub/file.schem}), sorted. */
	static List<String> files() {
		Path dir = folder();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(dir, 3)) {
			return walk.filter(Files::isRegularFile).filter(p -> SchematicFiles.extension(p.getFileName().toString()) != null)
				.map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted(String.CASE_INSENSITIVE_ORDER).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** A design name from a file's name: lower case, anything else made _, at most 32. */
	static String nameFor(String file) {
		String base = file.substring(file.lastIndexOf('/') + 1);
		String ext = SchematicFiles.extension(base);
		if (ext != null) {
			base = base.substring(0, base.length() - ext.length());
		}
		String name = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_").replaceAll("_+", "_");
		name = name.replaceAll("^_|_$", "");
		if (name.length() > 32) {
			name = name.substring(0, 32);
		}
		return name.isEmpty() ? "design" : name;
	}

	/**
	 * Sends {@code file} (from the folder) up as design {@link #nameFor its name}, from the screen of the table at
	 * {@code table}. Null if it went; else what's wrong.
	 */
	static @Nullable Component upload(String file, BlockPos table) {
		Path dir = folder().normalize();
		Path path = dir.resolve(file).normalize();
		if (!path.startsWith(dir) || !Files.isRegularFile(path) || SchematicFiles.extension(path.getFileName().toString()) == null) {
			return Component.translatable("message.jaymoskingdoms.schematics.no_file", file);
		}
		if (!ClientPlayNetworking.canSend(DesignUploads.Piece.TYPE)) {
			return Component.translatable("message.jaymoskingdoms.schematics.no_server");
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(path);
		} catch (IOException e) {
			return Component.translatable("message.jaymoskingdoms.schematics.no_file", file);
		}
		// every one of the three formats is gzipped NBT
		if (bytes.length < 2 || (bytes[0] & 0xFF) != 0x1F || (bytes[1] & 0xFF) != 0x8B) {
			return Component.translatable("message.jaymoskingdoms.design.unreadable");
		}
		int total = (bytes.length + DesignUploads.PIECE - 1) / DesignUploads.PIECE;
		if (total > DesignUploads.MAX_PIECES) {
			return Component.translatable("message.jaymoskingdoms.design.upload_big", DesignUploads.PIECE * DesignUploads.MAX_PIECES / 1024);
		}
		String design = nameFor(file);
		for (int i = 0; i < total; i++) {
			byte[] part = Arrays.copyOfRange(bytes, i * DesignUploads.PIECE, Math.min(bytes.length, (i + 1) * DesignUploads.PIECE));
			ClientPlayNetworking.send(new DesignUploads.Piece(design, i, total, part, table));
		}
		return null;
	}
}
