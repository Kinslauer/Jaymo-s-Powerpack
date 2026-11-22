package com.jaymoskingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The masons' pattern books (docs/BuildingLibrary-Plan.md). Andy (2026-11-19) picked 88 rows of medieval buildings for
 * the mason's table, "builders should draw from this library as well whenever their kingdom needs a structure". Every
 * book is one ready-made building, from one of three homes:
 * <ul>
 * <li><b>in the pack</b>: Towns and Towers' and Moog's Voyager Structures' own files, read from the installed mod in the
 * game (as {@link CtovHalls} reads CTOV's), never copied;</li>
 * <li><b>shipped</b>: the open-licence mods' buildings, copied into our resources with their credits by
 * {@code tools/library_import.py} ({@code data/jaymoskingdoms/structure/library/}, {@code LIBRARY-CREDITS.md});</li>
 * <li><b>the world's own</b>: files the server's owner drops into {@code <world>/jaymoskingdoms_library/} (mc-mod.net,
 * Planet Minecraft: no licence to hand them on), sorted by their file names or by {@code /kingdom library tag}.</li>
 * </ul>
 * The index is {@code jaymoskingdoms/library.json} (written by the tool). The mason's table lists every book ("Pattern
 * books"); a settlement's builders draw one for a building it may stand in for (its kinds), in their land and, where a
 * style keeps to one, their biome: a birch-wood town gets the birch village's houses. A variant staked from now on
 * carries {@link #MARK} on a third of the tries (always, for the buildings only the books have: the chapel, the windmill
 * and the barn), so older projects and standing buildings keep drawing as they were. Abandoned houses go up whole, and
 * an outpost's tower flies the realm's banner, not the ominous one. Library castles stand only as the seat of a kingdom
 * raised from nothing ({@link GrandSeats}); any other comes from the mason's table (Andy, 2026-11-19).
 */
final class Library {
	/** The top 16 bits of a variant that is drawn from the pattern books. */
	static final long MARK = 0x11B5L << 48;
	/** On a building that's ours, CTOV's or D&T's too: one try in this many is a pattern book's. */
	static final int SHARE = 3;
	/** Buildings only the pattern books have, and when a settlement raises one: kingdom and fiefdom, village (population). */
	static final List<String> OWN = List.of("chapel", "windmill", "barn");
	static final int CHAPEL_FROM = 30, CHAPEL_VILLAGE_FROM = 20, WINDMILL_FROM = 26, WINDMILL_VILLAGE_FROM = 18, BARN_FROM = 22, BARN_VILLAGE_FROM = 14;
	/** The world's own folder, and the file in it that keeps what {@code /kingdom library tag} said. */
	static final String FOLDER = "jaymoskingdoms_library", TAGS = "library-tags.json";
	/** The index in our resources. */
	static final String INDEX = "/jaymoskingdoms/library.json";

	/** One pattern book. {@code source}: a template id ({@code ns:path}) or, for the world's own, its file name. */
	record Book(String id, String name, String source, List<String> kinds, List<Palette> lands, List<String> biomes, String author, String licence,
		boolean world, boolean ruin, boolean castle, boolean outpost) {
		boolean suits(Palette land) {
			for (Palette p : this.lands) {
				if (p == land) {
					return true;
				}
			}
			return false;
		}
	}

	/** A world file's name and the book it makes: the words its name holds, and what it is. */
	private record WorldName(String match, String id, String name, List<String> kinds, List<Palette> lands, boolean castle) {
	}

	private static final Map<String, Palette> LANDS = Map.of("PLAINS", Palette.PLAINS, "TAIGA", Palette.TAIGA, "SNOWY", Palette.SNOWY, "SAVANNA",
		Palette.SAVANNA, "DESERT", Palette.DESERT, "BADLANDS", Palette.BADLANDS, "JUNGLE", Palette.JUNGLE, "SWAMP", Palette.SWAMP, "CHERRY", Palette.CHERRY);

	/** The books in our resources (in the pack and shipped), and the world-file names, read once. */
	private static @Nullable List<Book> shipped;
	private static List<WorldName> worldNames = List.of();
	/** Every kind a book (or a world file's name) may stand in for. */
	private static Set<String> kinds = Set.of();
	/** The world's own books, read when the server starts and on {@code /kingdom library reload}. */
	private static List<Book> world = List.of();
	/** Each book's file, as a structure template (jigsaws as they are), by id; empty if it can't be read. */
	private static final Map<String, Optional<CompoundTag>> TEMPLATES = new HashMap<>();
	/** Each book as a crew builds it, by id and building (empty: too big, or not readable). */
	private static final Map<String, Optional<Blueprint>> CREWS = new HashMap<>();
	/** Each book as the mason's table draws it (and as a castle stands), by id. */
	private static final Map<String, Optional<Blueprint>> WHOLE = new HashMap<>();

	private Library() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Library::reload);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			world = List.of();
			TEMPLATES.clear();
			CREWS.clear();
			WHOLE.clear();
		});
	}

	// ---- the index --------------------------------------------------------------------------------------------------

	static List<Book> shipped() {
		if (shipped == null) {
			load();
		}
		return shipped;
	}

	private static void load() {
		List<Book> books = new ArrayList<>();
		List<WorldName> names = new ArrayList<>();
		Set<String> all = new HashSet<>(OWN);
		try (InputStream in = Library.class.getResourceAsStream(INDEX)) {
			if (in == null) {
				JaymosKingdoms.LOGGER.warn("The building library's index {} is missing", INDEX);
			} else {
				JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement e : root.getAsJsonArray("books")) {
					JsonObject o = e.getAsJsonObject();
					books.add(new Book(o.get("id").getAsString(), o.get("name").getAsString(), o.get("source").getAsString(), strings(o, "kinds"),
						lands(strings(o, "lands")), strings(o, "biomes"), text(o, "author"), text(o, "licence"), false, flag(o, "ruin"), flag(o, "castle"),
						flag(o, "outpost")));
				}
				for (JsonElement e : root.getAsJsonArray("world")) {
					JsonObject o = e.getAsJsonObject();
					names.add(new WorldName(o.get("match").getAsString(), o.get("id").getAsString(), o.get("name").getAsString(), strings(o, "kinds"),
						lands(strings(o, "lands")), flag(o, "castle")));
				}
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("The building library's index can't be read: {}", e.toString());
		}
		for (Book b : books) {
			all.addAll(b.kinds());
		}
		for (WorldName n : names) {
			all.addAll(n.kinds());
		}
		shipped = List.copyOf(books);
		worldNames = List.copyOf(names);
		kinds = Set.copyOf(all);
	}

	/** A land's name in the index ({@code PLAINS}...). */
	static String landName(Palette p) {
		for (Map.Entry<String, Palette> e : LANDS.entrySet()) {
			if (e.getValue() == p) {
				return e.getKey();
			}
		}
		return "";
	}

	private static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		JsonArray a = o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
		for (JsonElement e : a) {
			out.add(e.getAsString());
		}
		return List.copyOf(out);
	}

	private static List<Palette> lands(List<String> names) {
		List<Palette> out = new ArrayList<>();
		for (String n : names) {
			Palette p = LANDS.get(n.toUpperCase(Locale.ROOT));
			if (p != null) {
				out.add(p);
			}
		}
		return List.copyOf(out);
	}

	private static String text(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsString() : "";
	}

	private static boolean flag(JsonObject o, String key) {
		return o.has(key) && o.get(key).getAsBoolean();
	}

	/** Every book: ours, then the world's own. */
	static List<Book> all() {
		List<Book> out = new ArrayList<>(shipped());
		out.addAll(world);
		return out;
	}

	static @Nullable Book book(String id) {
		for (Book b : all()) {
			if (b.id().equals(id)) {
				return b;
			}
		}
		return null;
	}

	// ---- the world's own folder ---------------------------------------------------------------------------------------

	static Path folder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).normalize().resolve(FOLDER);
	}

	/** Reads the world's folder again: every file there is a book, sorted by its name or by what was tagged. */
	static int reload(MinecraftServer server) {
		shipped();
		Path dir = folder(server);
		List<Book> found = new ArrayList<>();
		Map<String, JsonObject> tags = readTags(server);
		List<String> bad = new ArrayList<>();
		if (Files.isDirectory(dir)) {
			List<Path> files;
			try (Stream<Path> list = Files.list(dir)) {
				files = list.filter(Files::isRegularFile).sorted().toList();
			} catch (IOException e) {
				files = List.of();
			}
			Set<String> ids = new HashSet<>();
			for (Path p : files) {
				String file = p.getFileName().toString();
				if (file.equals(TAGS)) {
					continue;
				}
				if (SchematicFiles.extension(file) == null) {
					// (an old .schematic or a Bedrock .mcstructure: named in the log, so the owner knows)
					bad.add(file);
					continue;
				}
				Book b = sort(file, tags.get(file));
				if (ids.add(b.id())) {
					found.add(b);
				}
			}
		}
		world = List.copyOf(found);
		TEMPLATES.keySet().removeIf(k -> k.startsWith("world_"));
		CREWS.keySet().removeIf(k -> k.startsWith("world_"));
		WHOLE.keySet().removeIf(k -> k.startsWith("world_"));
		if (!bad.isEmpty()) {
			JaymosKingdoms.LOGGER.warn("Building library: these files are not a kind we read (.nbt, .schem, .litematic): {}", bad);
		}
		JaymosKingdoms.LOGGER.info("Building library: {} pattern books of ours, {} of this world's", shipped().size(), world.size());
		return world.size();
	}

	/** The book a world file makes: by its tag, else by its name, else unsorted (the mason's table's only). */
	private static Book sort(String file, @Nullable JsonObject tag) {
		String base = file.substring(0, file.length() - SchematicFiles.extension(file).length());
		String id = "world_" + base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
		String lower = file.toLowerCase(Locale.ROOT);
		WorldName named = null;
		for (WorldName n : worldNames) {
			if (lower.contains(n.match())) {
				named = n;
				break;
			}
		}
		String name = named != null ? named.name() : base;
		List<String> kinds = named != null ? named.kinds() : List.of();
		List<Palette> lands = named != null ? named.lands() : List.of();
		boolean castle = named != null && named.castle();
		if (tag != null) {
			kinds = tag.has("kind") ? List.of(tag.get("kind").getAsString()) : kinds;
			if (tag.has("land")) {
				lands = lands(List.of(tag.get("land").getAsString()));
			} else if (lands.isEmpty()) {
				lands = List.of(Palette.PLAINS);
			}
		}
		return new Book(id, name, file, kinds, lands, List.of(), "", "", true, false, castle, false);
	}

	private static Map<String, JsonObject> readTags(MinecraftServer server) {
		Map<String, JsonObject> out = new LinkedHashMap<>();
		Path file = folder(server).resolve(TAGS);
		if (!Files.isRegularFile(file)) {
			return out;
		}
		try (Reader in = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
			for (String k : root.keySet()) {
				out.put(k, root.getAsJsonObject(k));
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Building library: {} can't be read: {}", file, e.toString());
		}
		return out;
	}

	// ---- reading a book ---------------------------------------------------------------------------------------------

	/** Book {@code b}'s file as a structure template, or null. */
	static @Nullable CompoundTag template(MinecraftServer server, Book b) {
		return TEMPLATES.computeIfAbsent(b.id(), k -> Optional.ofNullable(readTemplate(server, b))).map(CompoundTag::copy).orElse(null);
	}

	private static @Nullable CompoundTag readTemplate(MinecraftServer server, Book b) {
		try {
			if (b.world()) {
				Path file = folder(server).resolve(b.source());
				CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(DesignUploads.NBT_QUOTA));
				CompoundTag tag = SchematicFiles.toTemplate(root, server.getFixerUpper());
				if (tag != null && !tag.contains("author")) {
					// (a .schem or .litematic names its author in its metadata)
					String by = root.getCompoundOrEmpty("Metadata").getStringOr("Author", "");
					if (!by.isEmpty()) {
						tag.putString("author", by);
					}
				}
				return tag;
			}
			Identifier id = Identifier.tryParse(b.source());
			if (id == null) {
				return null;
			}
			Optional<StructureTemplate> template = server.getStructureTemplateManager().get(id);
			return template.map(t -> t.save(new CompoundTag())).orElse(null);
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Pattern book {} ({}) can't be read: {}", b.id(), b.source(), e.toString());
			return null;
		}
	}

	/** Who drew book {@code b}: the index's author, else the file's own. */
	static String author(MinecraftServer server, Book b) {
		if (!b.author().isEmpty()) {
			return b.author();
		}
		CompoundTag tag = template(server, b);
		return tag == null ? "" : tag.getStringOr("author", "");
	}

	/** Book {@code b} as a settlement's crew puts it up for building {@code entry}: within a crew's size, or null. */
	static @Nullable Blueprint crew(MinecraftServer server, Book b, String entry) {
		return CREWS.computeIfAbsent(b.id() + "|" + entry, k -> {
			CompoundTag tag = template(server, b);
			Blueprint bp = tag == null ? null : CtovHalls.convert(entry, tag, Plots.MAX_SIDE, CtovHalls.MAX_HEIGHT, Designs.MAX_BLOCKS, false);
			return Optional.ofNullable(bp == null ? null : finish(server, b, bp));
		}).orElse(null);
	}

	/** Book {@code b} whole (up to the mason's table's size), its entrance north: for a plan or a castle; or null. */
	static @Nullable Blueprint whole(MinecraftServer server, Book b) {
		return WHOLE.computeIfAbsent(b.id(), k -> {
			CompoundTag tag = template(server, b);
			Blueprint bp = tag == null ? null : CtovHalls.convert(b.id(), tag, Designs.PLAN_SIDE, Designs.PLAN_HEIGHT, Designs.PLAN_BLOCKS, true);
			return Optional.ofNullable(bp == null ? null : finish(server, b, bp));
		}).orElse(null);
	}

	/** An abandoned house goes up whole; an outpost's banners come down (the realm's go up when it's built, {@link #banners}). */
	private static Blueprint finish(MinecraftServer server, Book b, Blueprint bp) {
		return b.ruin() ? DntBuildings.tidy(bp) : bp;
	}

	/** {@code bp} with every banner in {@code f}'s own colour (an outpost's ominous banner is a white banner with patterns). */
	static Blueprint banners(MinecraftServer server, Faction f, Blueprint bp) {
		String colour = f.styleColor(server).getName();
		Optional<Block> standing = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(colour + "_banner"));
		Optional<Block> wall = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(colour + "_wall_banner"));
		if (standing.isEmpty() || wall.isEmpty()) {
			return bp;
		}
		List<Blueprint.Placement> out = new ArrayList<>(bp.blocks().size());
		for (Blueprint.Placement pl : bp.blocks()) {
			BlockState s = pl.state();
			if (s.is(net.minecraft.tags.BlockTags.BANNERS)) {
				s = s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
					? wall.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, s.getValue(BlockStateProperties.HORIZONTAL_FACING))
					: s.hasProperty(BlockStateProperties.ROTATION_16)
						? standing.get().defaultBlockState().setValue(BlockStateProperties.ROTATION_16, s.getValue(BlockStateProperties.ROTATION_16))
						: s;
			}
			out.add(new Blueprint.Placement(pl.offset(), s));
		}
		return new Blueprint(bp.id(), bp.width(), bp.depth(), bp.height(), List.copyOf(out));
	}

	/**
	 * Book {@code b}'s file with its jigsaws turned to the blocks they become: a structure file a design can be saved
	 * from ({@link Designs#keepCaptured}); null if it can't be read.
	 */
	static @Nullable CompoundTag resolved(MinecraftServer server, Book b) {
		CompoundTag tag = template(server, b);
		if (tag == null) {
			return null;
		}
		ListTag palette = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
		ListTag blocks = tag.getListOrEmpty("blocks");
		Map<String, Integer> added = new HashMap<>();
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag block = blocks.getCompoundOrEmpty(i);
			int s = block.getIntOr("state", -1);
			if (s < 0 || s >= palette.size() || !palette.getCompoundOrEmpty(s).getStringOr("Name", "").equals("minecraft:jigsaw")) {
				continue;
			}
			String fin = block.getCompoundOrEmpty("nbt").getStringOr("final_state", "minecraft:air");
			BlockState state = CtovHalls.finalState(fin);
			int index = added.computeIfAbsent(state.toString(), k -> {
				palette.add(NbtUtils.writeBlockState(state));
				return palette.size() - 1;
			});
			block.putInt("state", index);
			block.remove("nbt");
		}
		if (b.world() && !tag.getStringOr("author", "").isEmpty()) {
			return tag;
		}
		tag.putString("author", author(server, b));
		return tag;
	}

	// ---- the builders -----------------------------------------------------------------------------------------------

	/** {@code entry} wrapped so a variant marked {@link #MARK} draws a pattern book for it ({@link Blueprint#register}). */
	static Blueprint.Entry wrap(Blueprint.Entry entry) {
		shipped();
		if (!kinds.contains(entry.id())) {
			return entry;
		}
		return new Blueprint.Entry(entry.id(), (level, faction, variant) -> {
			if (marked(variant)) {
				Blueprint bp = pick(level, faction, variant, entry.id());
				if (bp != null) {
					return bp;
				}
			}
			return entry.maker().make(level, faction, variant);
		}, entry.max(), entry.minPopulation());
	}

	/** A variant just picked for {@code entry} ({@link Planner#variant}), marked for the pattern books when it may be. */
	static long tag(String entry, long variant, int attempt) {
		shipped();
		if (!kinds.contains(entry)) {
			return variant;
		}
		long mix = variant ^ variant >>> 23 ^ (long)attempt * 0x9E3779B97F4A7C15L;
		if (OWN.contains(entry) || Math.floorMod(mix ^ mix >>> 31, SHARE) == 0) {
			return variant & ~CtovHalls.MARK_BITS | MARK;
		}
		return variant;
	}

	static boolean marked(long variant) {
		return (variant & CtovHalls.MARK_BITS) == MARK;
	}

	/** The books for {@code kind} in {@code f}'s land: those kept to its biome first, if any; else the land's others. */
	static List<Book> choices(ServerLevel level, Faction f, String kind) {
		MinecraftServer server = level.getServer();
		Palette land = Palette.at(level, f.seat());
		if (land == Palette.CHERRY || !Havens.of(server, f).isEmpty()) {
			// (the cherry groves and the havens build their own way)
			return List.of();
		}
		String biome = level.getBiome(f.seat()).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		List<Book> kept = new ArrayList<>(), open = new ArrayList<>();
		for (Book b : all()) {
			if (!b.kinds().contains(kind) || !b.suits(land)) {
				continue;
			}
			if (b.biomes().isEmpty()) {
				open.add(b);
			} else if (b.biomes().stream().anyMatch(biome::contains)) {
				kept.add(b);
			}
		}
		return kept.isEmpty() ? open : kept;
	}

	/** A pattern book for {@code kind} in {@code f}'s land, picked by {@code variant} (the next that fits a crew), or null. */
	static @Nullable Blueprint pick(ServerLevel level, Faction f, long variant, String kind) {
		List<Book> books = choices(level, f, kind);
		if (books.isEmpty()) {
			return null;
		}
		int first = (int)Math.floorMod(variant >>> 13, (long)books.size());
		for (int i = 0; i < books.size(); i++) {
			Book b = books.get((first + i) % books.size());
			Blueprint bp = crew(level.getServer(), b, kind);
			if (bp != null) {
				return b.outpost() ? banners(level.getServer(), f, bp) : bp;
			}
		}
		return null;
	}

	/** {@code id} is a building only the pattern books have, and none suits {@code f}'s land: it isn't built there. */
	static boolean unavailable(MinecraftServer server, Faction f, String id) {
		if (!OWN.contains(id)) {
			return false;
		}
		ServerLevel level = Factions.level(server);
		for (Book b : choices(level, f, id)) {
			if (crew(server, b, id) != null) {
				return false;
			}
		}
		return true;
	}

	/** The buildings only the pattern books have, in the growth orders ({@link Buildings#register}). */
	static void registerOwn() {
		for (FactionType type : new FactionType[] {FactionType.KINGDOM, FactionType.FIEFDOM}) {
			own(type, "barn", BARN_FROM);
			own(type, "windmill", WINDMILL_FROM);
			own(type, "chapel", CHAPEL_FROM);
		}
		own(FactionType.VILLAGE, "barn", BARN_VILLAGE_FROM);
		own(FactionType.VILLAGE, "windmill", WINDMILL_VILLAGE_FROM);
		own(FactionType.VILLAGE, "chapel", CHAPEL_VILLAGE_FROM);
	}

	/** One of {@link #OWN}: a fire pit if no book can be read (it isn't planned where none suits: {@link #unavailable}). */
	private static void own(FactionType type, String id, int minSize) {
		Blueprint.register(type, new Blueprint.Entry(id, (level, faction, variant) -> Buildings.firePit(Buildings.Kit.of(level, faction, variant, variant)),
			1, minSize));
	}

	// ---- castles ------------------------------------------------------------------------------------------------------

	/** The library castles for {@code land} (a plains castle where the land has none). */
	static List<Book> castles(MinecraftServer server, Palette land) {
		List<Book> out = new ArrayList<>();
		for (Book b : all()) {
			if (b.castle() && b.suits(land)) {
				out.add(b);
			}
		}
		if (out.isEmpty() && land != Palette.PLAINS) {
			return castles(server, Palette.PLAINS);
		}
		return out;
	}

	/**
	 * Raises library castle {@code b} as {@code kingdom}'s seat beside the village at {@code bell} (a kingdom raised from
	 * nothing: {@link GrandSeats#build}), its gate toward the village. Returns its keep, or null if it can't be read.
	 */
	static Castle.@Nullable Keep raise(ServerLevel level, Faction kingdom, BlockPos bell, Book b) {
		MinecraftServer server = level.getServer();
		Blueprint bp = whole(server, b);
		if (bp == null || bp.width() > GrandSeats.MAX_SIDE + 16 || bp.depth() > GrandSeats.MAX_SIDE + 16) {
			return null;
		}
		bp = banners(server, kingdom, bp);
		int half = Math.max(bp.width(), bp.depth()) / 2 + GrandSeats.SITE_MARGIN;
		Castle.Site site = Castle.site(level, bell, half, half + GrandSeats.SITE_CLEARANCE, half + GrandSeats.SITE_CLEARANCE + GrandSeats.SITE_REACH, 0.0);
		// (its entrance is north in the blueprint: turned to face the way the site's gate does)
		Rotation turn = switch (site.facing()) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		boolean across = turn == Rotation.CLOCKWISE_90 || turn == Rotation.COUNTERCLOCKWISE_90;
		int w = across ? bp.depth() : bp.width(), d = across ? bp.width() : bp.depth();
		BlockPos corner = new BlockPos(site.center().getX() - w / 2, site.floor(), site.center().getZ() - d / 2);
		BoundingBox box = new BoundingBox(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + w - 1, corner.getY() + bp.height() - 1,
			corner.getZ() + d - 1);
		GrandSeats.clear(level, List.of(box), site.floor(), box.maxY());
		for (Blueprint.Placement pl : bp.blocks()) {
			BlockPos at = corner.offset(CtovHalls.turn(pl.offset(), bp.width(), bp.depth(), turn));
			level.setBlock(at, pl.state().rotate(turn), Block.UPDATE_CLIENTS);
		}
		GrandSeats.footing(level, box, site.floor());
		Holds.guard(server, kingdom.id(), box);
		BlockPos hall = Construction.standable(level, box.getCenter().atY(site.floor() + 1), 12);
		BlockPos gate = Construction.standable(level, site.center().relative(site.facing(), Math.max(w, d) / 2 + 2).atY(site.floor()), 8);
		JaymosKingdoms.LOGGER.info("{} takes the pattern books' {} for its seat at {} ({} by {})", kingdom.name(), b.name(), box.getCenter(), w, d);
		return new Castle.Keep(hall, hall, gate, hall, List.of());
	}

	// ---- the mason's table ---------------------------------------------------------------------------------------------

	/** Every book the mason's table can draw, as the screen lists them ({@link MasonNet.Book}). */
	static List<MasonNet.Book> list(MinecraftServer server) {
		List<MasonNet.Book> out = new ArrayList<>();
		for (Book b : all()) {
			Blueprint bp = whole(server, b);
			if (bp != null) {
				out.add(new MasonNet.Book(b.id(), b.name(), bp.width(), bp.depth(), bp.height(), bp.blocks().size(), author(server, b),
					b.lands().isEmpty() ? "" : landName(b.lands().getFirst()), !b.kinds().isEmpty()));
			}
		}
		return out;
	}

	/** Copies book {@code id} among {@code player}'s designs and draws it on a plan ({@link MasonNet#COPY}). */
	static void copy(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		Book b = book(id);
		CompoundTag tag = b == null ? null : resolved(server, b);
		if (tag == null) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.library.unreadable", id).withStyle(ChatFormatting.RED));
			return;
		}
		String base = id.length() > 28 ? id.substring(0, 28) : id;
		String name = Designs.keepCaptured(player, base, tag);
		if (name != null) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.library.copied", b.name(), author(server, b), name)
				.withStyle(ChatFormatting.GREEN));
			MasonTable.inscribe(player, name);
		}
	}

	// ---- commands -----------------------------------------------------------------------------------------------------

	/** {@code /kingdom library reload | list | tag <file> <kind> [land]} (the world's own folder; operators). */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("library").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("reload").executes(c -> {
				int n = reload(c.getSource().getServer());
				c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.library.reloaded", n, folder(c.getSource().getServer()).toString()),
					false);
				return n;
			}))
			.then(Commands.literal("list").executes(Library::listCommand))
			.then(Commands.literal("tag").then(Commands.argument("file", StringArgumentType.string())
				.suggests((c, builder) -> SharedSuggestionProvider.suggest(world.stream().map(b -> "\"" + b.source() + "\"").toList(), builder))
				.then(Commands.argument("kind", StringArgumentType.word()).suggests((c, builder) -> SharedSuggestionProvider.suggest(kinds, builder))
					.executes(c -> tagCommand(c, null))
					.then(Commands.argument("land", StringArgumentType.word()).suggests((c, builder) -> SharedSuggestionProvider.suggest(LANDS.keySet().stream()
						.map(s -> s.toLowerCase(Locale.ROOT)).toList(), builder)).executes(c -> tagCommand(c, StringArgumentType.getString(c, "land")))))));
	}

	private static int listCommand(CommandContext<CommandSourceStack> c) {
		for (Book b : world) {
			String lands = b.lands().stream().map(Library::landName).reduce((x, y) -> x + "," + y).orElse("-");
			c.getSource().sendSuccess(() -> (b.kinds().isEmpty() ? Component.translatable("message.jaymoskingdoms.library.line_unsorted", b.source(), lands)
				: Component.translatable("message.jaymoskingdoms.library.line", b.source(), String.join(",", b.kinds()), lands)).withStyle(ChatFormatting.GRAY), false);
		}
		if (world.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.library.none", folder(c.getSource().getServer()).toString()), false);
		}
		return world.size();
	}

	private static int tagCommand(CommandContext<CommandSourceStack> c, @Nullable String land) {
		MinecraftServer server = c.getSource().getServer();
		String file = StringArgumentType.getString(c, "file");
		String kind = StringArgumentType.getString(c, "kind").toLowerCase(Locale.ROOT);
		if (!Files.isRegularFile(folder(server).resolve(file)) || file.contains("/") || file.contains("\\")) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.library.no_file", file));
			return 0;
		}
		if (!kinds.contains(kind) && !kind.equals("none")) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.library.no_kind", kind, String.join(", ", kinds)));
			return 0;
		}
		if (land != null && !LANDS.containsKey(land.toUpperCase(Locale.ROOT))) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.library.no_land", land));
			return 0;
		}
		Map<String, JsonObject> tags = readTags(server);
		JsonObject t = new JsonObject();
		if (!kind.equals("none")) {
			t.addProperty("kind", kind);
		}
		if (land != null) {
			t.addProperty("land", land.toUpperCase(Locale.ROOT));
		}
		tags.put(file, t);
		JsonObject root = new JsonObject();
		tags.forEach(root::add);
		try (Writer out = Files.newBufferedWriter(folder(server).resolve(TAGS))) {
			out.write(root.toString());
		} catch (IOException e) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.library.no_file", file));
			return 0;
		}
		reload(server);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.library.tagged", file, kind, land == null ? "-" : land), true);
		return 1;
	}
}
