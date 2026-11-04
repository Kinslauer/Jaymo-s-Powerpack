package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.116) Players' own designs: something you built, scanned with the {@link Surveyor}'s design mode (two corners)
 * and saved by name ({@code /kingdom design save <name>}), that a faction's builders can then put up on a plot
 * ({@code /kingdom plot build design.<name>}) like any building in its catalogue: block by block, bottom up, from the
 * raw materials in its storage. A design is a structure file ({@code <world>/jaymoskingdoms_designs/<name>.nbt}, the
 * same format a structure block saves), so a structure block's own files can be dropped in there too. Only blocks are
 * kept: containers come empty and no entities come along. Designs are the world's, shared by everyone on it.
 */
public final class Designs {
	/** A design's building id in a plot or project: this and its name. */
	public static final String PREFIX = "design.";
	/** The folder in the world's save that holds them. */
	static final String FOLDER = "jaymoskingdoms_designs";
	/** The largest a design a settlement's builders put up may be: across (either way), high, and blocks in all. */
	static final int MAX_SIDE = Plots.MAX_SIDE, MAX_HEIGHT = 48, MAX_BLOCKS = 16000;
	/**
	 * (0.7.488) The largest any design may be (a {@link MasonTable} builds these): across, high, blocks. Bigger files are
	 * refused when uploaded and left unread in the folder.
	 */
	static final int PLAN_SIDE = 96, PLAN_HEIGHT = 128, PLAN_BLOCKS = 120000;
	/** The smallest: across. */
	static final int MIN_SIDE = 2;
	/** How many designs one player may keep (creative players any number). */
	static final int PER_PLAYER = 24;
	/** A name: lower-case letters, digits, _ and -. */
	static final String NAME_PATTERN = "[a-z0-9_\\-]{1,32}";

	/** Read designs by name (an empty one: none, or it can't be read). Cleared when one is saved or deleted. */
	private static final Map<String, Optional<Blueprint>> CACHE = new HashMap<>();
	/** Who made each design: the Mason's Table screen asks for every design on each refresh, so the files aren't reread. */
	private static final Map<String, String> AUTHORS = new HashMap<>();

	/** The server running (designs are read from its world's save). */
	private static @Nullable MinecraftServer server;

	private Designs() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			CACHE.clear();
			AUTHORS.clear();
			server = null;
		});
	}

	static Path folder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).normalize().resolve(FOLDER);
	}

	/** Design {@code name}'s file: whichever of the formats it's in ({@link SchematicFiles#EXTENSIONS}), else where a new one goes. */
	static Path file(MinecraftServer server, String name) {
		for (String ext : SchematicFiles.EXTENSIONS) {
			Path p = folder(server).resolve(name + ext);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		return folder(server).resolve(name + ".nbt");
	}

	/** (0.7.488) A design small enough for a settlement's builders (else only a {@link MasonTable} puts it up). */
	static boolean forCrews(Blueprint bp) {
		return bp.width() <= MAX_SIDE && bp.depth() <= MAX_SIDE && bp.height() <= MAX_HEIGHT && bp.blocks().size() <= MAX_BLOCKS;
	}

	/** {@code id} is a design's building id. */
	public static boolean isDesign(String id) {
		return id.startsWith(PREFIX);
	}

	/** The catalogue entry for design building {@code id} ({@code design.<name>}), or null if there's no such design. */
	static Blueprint.@Nullable Entry entry(String id) {
		MinecraftServer server = Designs.server;
		if (server == null || !isDesign(id)) {
			return null;
		}
		String name = id.substring(PREFIX.length());
		Blueprint bp = blueprint(server, name);
		if (bp == null || !forCrews(bp)) {
			return null;
		}
		// (never in a growth order: only put up where a lord asks for it)
		return new Blueprint.Entry(id, (level, faction, variant) -> {
			Blueprint now = blueprint(level.getServer(), name);
			return now != null ? now : new Blueprint(id, 1, 1, 1, List.of());
		}, 0, Integer.MAX_VALUE);
	}

	/** Design {@code name} as a blueprint (its id {@code design.<name>}), or null. */
	static @Nullable Blueprint blueprint(MinecraftServer server, String name) {
		return CACHE.computeIfAbsent(name, n -> Optional.ofNullable(read(server, n))).orElse(null);
	}

	private static @Nullable Blueprint read(MinecraftServer server, String name) {
		Path file = file(server, name);
		if (!name.matches(NAME_PATTERN) || !Files.isRegularFile(file)) {
			return null;
		}
		try {
			// (0.7.488) .schem and .litematic files too, brought up to date
			CompoundTag template = SchematicFiles.toTemplate(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), server.getFixerUpper());
			Blueprint bp = template == null ? null : Blueprint.fromTemplate(PREFIX + name, template);
			if (bp == null || !fits(bp)) {
				JaymosKingdoms.LOGGER.warn("Design {} is empty, not a schematic we read, or too big", name);
				return null;
			}
			return bp;
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Design {} can't be read: {}", name, e.toString());
			return null;
		}
	}

	/** Every design's name, sorted. */
	static List<String> names(MinecraftServer server) {
		Path dir = folder(server);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).filter(n -> SchematicFiles.extension(n) != null)
				.map(n -> n.substring(0, n.length() - SchematicFiles.extension(n).length())).filter(n -> n.matches(NAME_PATTERN)).distinct().sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** Who saved design {@code name} (the template's author), or "". */
	static String author(MinecraftServer server, String name) {
		return AUTHORS.computeIfAbsent(name, n -> readAuthor(server, n));
	}

	private static String readAuthor(MinecraftServer server, String name) {
		try {
			CompoundTag tag = NbtIo.readCompressed(file(server, name), NbtAccounter.unlimitedHeap());
			String by = tag.getStringOr("author", "");
			// (a .schem or .litematic names its author in its metadata)
			return by.isEmpty() ? tag.getCompoundOrEmpty("Metadata").getStringOr("Author", "") : by;
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	/** Within the largest a design may be at all. */
	static boolean fits(Blueprint bp) {
		return bp.width() <= PLAN_SIDE && bp.depth() <= PLAN_SIDE && bp.height() <= PLAN_HEIGHT && bp.blocks().size() <= PLAN_BLOCKS;
	}

	/** A project or plot of any faction is using design {@code name} (so it may not be changed under them). */
	static boolean inUse(MinecraftServer server, String name) {
		String id = PREFIX + name;
		if (Commissions.wanted(server, id)) {
			return true;
		}
		for (Works.Site site : Works.all(server).values()) {
			if (site.projects().stream().anyMatch(p -> p.entry().equals(id))
				|| site.plots().stream().anyMatch(p -> !p.done() && p.building().filter(id::equals).isPresent())) {
				return true;
			}
		}
		return false;
	}

	// ---- saving ---------------------------------------------------------------------------------------------------

	/** Saves what stands between {@code a} and {@code b} (inclusive) as design {@code name}; tells {@code player} how it went. */
	static boolean save(ServerPlayer player, BlockPos a, BlockPos b, String name) {
		MinecraftServer server = player.level().getServer();
		BlockPos min = Surveyor.min(a, b), max = Surveyor.max(a, b);
		Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
		if (!name.matches(NAME_PATTERN)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.bad_name").withStyle(ChatFormatting.RED));
			return false;
		}
		if (size.getX() > PLAN_SIDE || size.getZ() > PLAN_SIDE || size.getY() > PLAN_HEIGHT || size.getX() < MIN_SIDE || size.getZ() < MIN_SIDE) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.size", size.getX(), size.getZ(), size.getY(), PLAN_SIDE, PLAN_HEIGHT)
				.withStyle(ChatFormatting.RED));
			return false;
		}
		boolean creative = player.isCreative();
		boolean exists = Files.isRegularFile(file(server, name));
		if (exists && !creative && !author(server, name).equals(player.getGameProfile().name())) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.taken", name).withStyle(ChatFormatting.RED));
			return false;
		}
		if (exists && inUse(server, name)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.in_use", name).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!exists && !creative && names(server).stream().filter(n -> author(server, n).equals(player.getGameProfile().name())).count() >= PER_PLAYER) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.too_many", PER_PLAYER).withStyle(ChatFormatting.RED));
			return false;
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(player.level(), min, size, false, List.of());
		CompoundTag tag = template.save(new CompoundTag());
		tag.putString("author", player.getGameProfile().name());
		Blueprint bp = Blueprint.fromTemplate(PREFIX + name, tag);
		if (bp == null || bp.blocks().size() > PLAN_BLOCKS) {
			MasonTable.tell(player, Component.translatable(bp == null ? "message.jaymoskingdoms.design.empty" : "message.jaymoskingdoms.design.too_many_blocks",
				PLAN_BLOCKS).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!store(server, name, tag)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.failed", name).withStyle(ChatFormatting.RED));
			return false;
		}
		MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.saved", name, bp.width(), bp.depth(), bp.height(), bp.blocks().size(),
			PREFIX + name).withStyle(ChatFormatting.GREEN));
		if (!forCrews(bp)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.table_only", MAX_SIDE, MAX_HEIGHT, MAX_BLOCKS)
				.withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** Writes design {@code name} as a structure file (any older file of it in another format goes). */
	static boolean store(MinecraftServer server, String name, CompoundTag tag) {
		try {
			Files.createDirectories(folder(server));
			for (String ext : SchematicFiles.EXTENSIONS) {
				Files.deleteIfExists(folder(server).resolve(name + ext));
			}
			NbtIo.writeCompressed(tag, folder(server).resolve(name + ".nbt"));
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Design {} couldn't be saved: {}", name, e.toString());
			return false;
		}
		CACHE.remove(name);
		AUTHORS.remove(name);
		return true;
	}

	/**
	 * (0.7.488) A schematic file {@code player} sent from their own computer ({@link DesignUploads}): read, checked like a
	 * design saved in the world, and kept as design {@code name}, theirs (or the next free name). The name it's kept as, or null.
	 */
	static @Nullable String saveUploaded(ServerPlayer player, String name, CompoundTag root) {
		MinecraftServer server = player.level().getServer();
		if (!name.matches(NAME_PATTERN)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.bad_name").withStyle(ChatFormatting.RED));
			return null;
		}
		boolean creative = player.isCreative();
		// (0.7.488) a name someone else has: the next free one (house_2, house_3...)
		String base = name;
		for (int n = 2; Files.isRegularFile(file(server, name)) && !author(server, name).equals(player.getGameProfile().name()) && n < 100; n++) {
			String suffix = "_" + n;
			name = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()) : base) + suffix;
		}
		boolean exists = Files.isRegularFile(file(server, name));
		if (exists && !creative && !author(server, name).equals(player.getGameProfile().name())) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.taken", name).withStyle(ChatFormatting.RED));
			return null;
		}
		if (exists && inUse(server, name)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.in_use", name).withStyle(ChatFormatting.RED));
			return null;
		}
		if (!exists && !creative && names(server).stream().filter(n -> author(server, n).equals(player.getGameProfile().name())).count() >= PER_PLAYER) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.too_many", PER_PLAYER).withStyle(ChatFormatting.RED));
			return null;
		}
		CompoundTag tag = SchematicFiles.toTemplate(root, server.getFixerUpper());
		Blueprint bp = tag == null ? null : Blueprint.fromTemplate(PREFIX + name, tag);
		if (tag == null || bp == null) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.unreadable").withStyle(ChatFormatting.RED));
			return null;
		}
		if (!fits(bp)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.too_big", bp.width(), bp.depth(), bp.height(), bp.blocks().size(),
				PLAN_SIDE, PLAN_HEIGHT, PLAN_BLOCKS).withStyle(ChatFormatting.RED));
			return null;
		}
		// (whoever drew it first, the design on this world is the uploader's)
		tag.putString("author", player.getGameProfile().name());
		if (!store(server, name, tag)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.failed", name).withStyle(ChatFormatting.RED));
			return null;
		}
		MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.uploaded", name, bp.width(), bp.depth(), bp.height(), bp.blocks().size(), name)
			.withStyle(ChatFormatting.GREEN));
		if (!forCrews(bp)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.table_only", MAX_SIDE, MAX_HEIGHT, MAX_BLOCKS)
				.withStyle(ChatFormatting.GRAY));
		}
		return name;
	}

	static boolean delete(ServerPlayer player, String name) {
		MinecraftServer server = player.level().getServer();
		boolean creative = player.isCreative();
		if (!name.matches(NAME_PATTERN) || !Files.isRegularFile(file(server, name))) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.unknown", name).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!creative && !author(server, name).equals(player.getGameProfile().name())) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.not_yours", name).withStyle(ChatFormatting.RED));
			return false;
		}
		if (inUse(server, name)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.in_use", name).withStyle(ChatFormatting.RED));
			return false;
		}
		try {
			Files.deleteIfExists(file(server, name));
		} catch (IOException e) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.failed", name).withStyle(ChatFormatting.RED));
			return false;
		}
		CACHE.remove(name);
		AUTHORS.remove(name);
		MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.deleted", name).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	// ---- commands -------------------------------------------------------------------------------------------------

	private static final SuggestionProvider<CommandSourceStack> NAMES = (c, builder) ->
		SharedSuggestionProvider.suggest(names(c.getSource().getServer()), builder);

	/** Design building ids ({@code design.<name>}), for the plot command's suggestions. */
	static List<String> ids(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		for (String n : names(server)) {
			out.add(PREFIX + n);
		}
		return out;
	}

	/** {@code /kingdom design save <name> | list | delete <name>}. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("design")
			.then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES).executes(Designs::saveCommand)))
			.then(Commands.literal("list").executes(Designs::list))

			.then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
				.executes(c -> delete(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")) ? 1 : 0)));
	}

	private static int saveCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Surveyor.Selection sel = Surveyor.selection(player);
		if (sel == null || sel.a() == null || sel.b() == null) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.no_selection").withStyle(ChatFormatting.RED));
			return 0;
		}
		return save(player, sel.a(), sel.b(), StringArgumentType.getString(c, "name").toLowerCase(java.util.Locale.ROOT)) ? 1 : 0;
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		List<String> names = names(server);
		if (names.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.design.none"), false);
			return 0;
		}
		for (String n : names) {
			Blueprint bp = blueprint(server, n);
			String by = author(server, n);
			c.getSource().sendSuccess(() -> bp == null
				? Component.translatable("message.jaymoskingdoms.design.line_bad", n).withStyle(ChatFormatting.DARK_GRAY)
				: !forCrews(bp) ? Component.translatable("message.jaymoskingdoms.design.line_table", n, bp.width(), bp.depth(), bp.height(), bp.blocks().size(),
					by.isEmpty() ? "?" : by).withStyle(ChatFormatting.GRAY)
				: Component.translatable("message.jaymoskingdoms.design.line", PREFIX + n, bp.width(), bp.depth(), bp.height(), bp.blocks().size(),
					by.isEmpty() ? "?" : by).withStyle(ChatFormatting.GRAY), false);
		}
		return names.size();
	}
}
