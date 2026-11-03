package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DesignUploads;
import com.jaymoskingdoms.SchematicFiles;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * (0.7.484) {@code /schematics}: the schematic files on this computer, and sending one to the server as a design
 * ({@link DesignUploads}). Files are read from the game folder's {@code schematics} folder (where Litematica keeps
 * its own, so files downloaded for it are already there): {@code .nbt}, {@code .schem} and {@code .litematic}.
 * <ul>
 * <li>{@code /schematics list}: the files there.</li>
 * <li>{@code /schematics upload <file> [name]}: sends one; its design takes the file's name unless given another.</li>
 * <li>{@code /schematics folder}: where the folder is.</li>
 * </ul>
 */
public final class DesignUploadClient {
	/** The folder in the game directory. */
	static final String FOLDER = "schematics";

	private DesignUploadClient() {
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("schematics")
			.then(ClientCommands.literal("list").executes(DesignUploadClient::list))
			.then(ClientCommands.literal("folder").executes(c -> {
				c.getSource().sendFeedback(Component.translatable("message.jaymoskingdoms.schematics.folder", folder().toString()).withStyle(ChatFormatting.GRAY));
				return 1;
			}))
			.then(ClientCommands.literal("upload").then(ClientCommands.argument("file", StringArgumentType.string())
				.suggests((c, builder) -> SharedSuggestionProvider.suggest(files().stream().map(DesignUploadClient::quoted), builder))
				.executes(c -> upload(c, null))
				.then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> upload(c, StringArgumentType.getString(c, "name"))))))));
	}

	static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
	}

	/** The schematic files in the folder (and its subfolders, as {@code sub/file.schem}), sorted. */
	static List<String> files() {
		Path dir = folder();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(dir, 3)) {
			return walk.filter(Files::isRegularFile).filter(p -> SchematicFiles.extension(p.getFileName().toString()) != null)
				.map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static String quoted(String file) {
		return file.matches("[A-Za-z0-9_.+-]+") ? file : "\"" + file.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static int list(CommandContext<FabricClientCommandSource> c) {
		List<String> files = files();
		try {
			Files.createDirectories(folder());
		} catch (IOException ignored) {
			// (only so the folder is there to drop files in)
		}
		if (files.isEmpty()) {
			c.getSource().sendFeedback(Component.translatable("message.jaymoskingdoms.schematics.none", folder().toString()).withStyle(ChatFormatting.GRAY));
			return 0;
		}
		for (String f : files) {
			c.getSource().sendFeedback(Component.literal(" " + f).withStyle(ChatFormatting.GRAY));
		}
		return files.size();
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

	private static int upload(CommandContext<FabricClientCommandSource> c, String name) {
		String file = StringArgumentType.getString(c, "file");
		Path dir = folder().normalize();
		Path path = dir.resolve(file).normalize();
		if (!path.startsWith(dir) || !Files.isRegularFile(path) || SchematicFiles.extension(path.getFileName().toString()) == null) {
			c.getSource().sendError(Component.translatable("message.jaymoskingdoms.schematics.no_file", file));
			return 0;
		}
		if (!ClientPlayNetworking.canSend(DesignUploads.Piece.TYPE)) {
			c.getSource().sendError(Component.translatable("message.jaymoskingdoms.schematics.no_server"));
			return 0;
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(path);
		} catch (IOException e) {
			c.getSource().sendError(Component.translatable("message.jaymoskingdoms.schematics.no_file", file));
			return 0;
		}
		// every one of the three formats is gzipped NBT
		if (bytes.length < 2 || (bytes[0] & 0xFF) != 0x1F || (bytes[1] & 0xFF) != 0x8B) {
			c.getSource().sendError(Component.translatable("message.jaymoskingdoms.design.unreadable"));
			return 0;
		}
		int total = (bytes.length + DesignUploads.PIECE - 1) / DesignUploads.PIECE;
		if (total > DesignUploads.MAX_PIECES) {
			c.getSource().sendError(Component.translatable("message.jaymoskingdoms.design.upload_big", DesignUploads.PIECE * DesignUploads.MAX_PIECES / 1024));
			return 0;
		}
		String design = name != null ? name.toLowerCase(Locale.ROOT) : nameFor(file);
		for (int i = 0; i < total; i++) {
			byte[] part = Arrays.copyOfRange(bytes, i * DesignUploads.PIECE, Math.min(bytes.length, (i + 1) * DesignUploads.PIECE));
			ClientPlayNetworking.send(new DesignUploads.Piece(design, i, total, part));
		}
		c.getSource().sendFeedback(Component.translatable("message.jaymoskingdoms.schematics.sent", file, design).withStyle(ChatFormatting.GRAY));
		return 1;
	}
}
