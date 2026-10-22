package com.jaymoskingdoms;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.171) Andy (2026-10-22): "all villagers should pick from the clothing library randomly and respective to their
 * jobs", and a ruler from the clothes tagged king, queen or royal, "respecting gender".
 * <p>
 * The clothing library is MCA's Immersive Library (the skin editor's Library tab): clothes players have shared, each
 * with tags. When the server starts, the clothes tagged for each job (its name and a few others, {@link #ALIASES}) and
 * for royalty ({@link #ROYAL}) are looked up there, most liked first, and their gender read from their tags (king, queen,
 * male, female) or what the uploader set. MCA then dresses a villager from these when it picks their clothes
 * ({@code McaClothingPoolMixin}): a job with at least {@link #MIN_POOL} of its own in the library is dressed only from
 * the library, otherwise from MCA's own clothes as before. Every grown villager is dressed again once from the library
 * ({@link #TAG}) unless their clothes are locked in MCA's editor. Clothes are drawn by the players' own game from the
 * library (as any library clothes are), so a player offline from it sees MCA's default.
 * <p>
 * What was found is kept in {@code config/jaymoskingdoms/clothing_library.json}, so a server that can't reach the
 * library still dresses from the last list (looked up again after {@link #REFRESH_MS}).
 */
public final class ClothingLibrary {
	/** How many of each tag's clothes to take (the most liked). */
	static final int PER_TAG = 30;
	/** A job is dressed only from the library when it has at least this many there. */
	static final int MIN_POOL = 2;
	/** How long a found list is good for before it's looked up again (ms). */
	static final long REFRESH_MS = 24L * 60 * 60 * 1000;
	static final int TIMEOUT_SECONDS = 15;

	/** Each job's tags (by the path of its profession, or of the clothes MCA gives it, {@link Professions#clothes}). */
	static final Map<String, List<String>> ALIASES = Map.ofEntries(
		Map.entry("none", List.of("villager", "peasant", "commoner", "unemployed")),
		Map.entry("nitwit", List.of("nitwit", "beggar")),
		Map.entry("armorer", List.of("armorer", "armourer", "blacksmith", "smith")),
		Map.entry("butcher", List.of("butcher")),
		Map.entry("cartographer", List.of("cartographer", "explorer")),
		Map.entry("cleric", List.of("cleric", "priest", "monk")),
		Map.entry("farmer", List.of("farmer", "peasant")),
		Map.entry("fisherman", List.of("fisherman", "fisher", "sailor")),
		Map.entry("fletcher", List.of("fletcher", "hunter")),
		Map.entry("leatherworker", List.of("leatherworker", "tanner")),
		Map.entry("librarian", List.of("librarian", "scholar")),
		Map.entry("mason", List.of("mason", "builder")),
		Map.entry("shepherd", List.of("shepherd")),
		Map.entry("toolsmith", List.of("toolsmith", "blacksmith", "smith")),
		Map.entry("weaponsmith", List.of("weaponsmith", "blacksmith", "smith")),
		Map.entry("guard", List.of("guard", "knight", "soldier")),
		Map.entry("archer", List.of("archer")),
		Map.entry("miner", List.of("miner")),
		Map.entry("baker", List.of("baker")),
		Map.entry("lumberjack", List.of("lumberjack", "woodcutter")),
		Map.entry("woodcutter", List.of("woodcutter", "lumberjack")),
		Map.entry("builder", List.of("builder", "mason")),
		Map.entry("stablehand", List.of("stablehand", "stable")),
		Map.entry("farrier", List.of("farrier", "blacksmith")),
		Map.entry("puller", List.of("puller", "porter")),
		Map.entry("wizard", List.of("wizard", "mage", "sorcerer")),
		Map.entry("cultist", List.of("cultist")));
	/** A ruler's tags. */
	static final List<String> ROYAL = List.of("king", "queen", "royal", "royalty");
	static final Set<String> FEMALE_TAGS = Set.of("female", "woman", "girl", "queen", "princess", "lady");
	static final Set<String> MALE_TAGS = Set.of("male", "man", "boy", "king", "prince", "lord");
	/** On a villager dressed once from the library. */
	static final String TAG = "jaymoskingdoms.library_dressed";

	/** One of the library's clothes: its id there, its tags, and who it's for (MCA's gender id; 0 or 3 for anyone). */
	record Entry(int id, Set<String> tags, int gender, int likes) {
		String clothes() {
			return "immersive_library:" + this.id;
		}

		boolean fits(Gender gender) {
			return this.gender == 0 || this.gender == Gender.NEUTRAL.getId() || this.gender == gender.getId();
		}
	}

	private static final Gson GSON = new Gson();
	/** Everything found, by id (written once a look-up is done, read on the server thread). */
	private static volatile Map<Integer, Entry> entries = Map.of();
	private static volatile boolean sweep;
	private static @Nullable MinecraftServer server;

	private ClothingLibrary() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			load();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> redress(entity));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (sweep) {
				// (the list came in: the villagers already about are dressed from it)
				sweep = false;
				for (ServerLevel level : s.getAllLevels()) {
					for (Entity e : level.getAllEntities()) {
						redress(e);
					}
				}
			}
		});
	}

	// ---- who wears what -------------------------------------------------------------------------------------------------

	/**
	 * The library's clothes for {@code villager} (a ruler's royal ones, else their job's), or null for MCA's own pick: too
	 * few there, a child, or nothing found.
	 */
	public static @Nullable WeightedPool<String> pool(VillagerEntityMCA villager) {
		if (entries.isEmpty() || villager.isBaby()) {
			return null;
		}
		Gender gender = villager.getGenetics().getGender();
		List<Entry> options = Rulers.is(villager) ? tagged(ROYAL, gender) : List.of();
		if (options.isEmpty()) {
			options = tagged(tags(villager), gender);
		}
		if (options.size() < MIN_POOL && !Rulers.is(villager) || options.isEmpty()) {
			return null;
		}
		WeightedPool.Mutable<String> pool = new WeightedPool.Mutable<>(options.get(0).clothes());
		for (Entry e : options) {
			pool.add(e.clothes(), 1.0F);
		}
		return pool;
	}

	/** {@code villager}'s job's tags: their profession's, then those of the clothes MCA gives it. */
	private static List<String> tags(VillagerEntityMCA villager) {
		Identifier job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value());
		Set<String> tags = new LinkedHashSet<>();
		if (villager.isGuard()) {
			tags.addAll(ALIASES.get("guard"));
		}
		if (job != null) {
			tags.addAll(ALIASES.getOrDefault(job.getPath(), List.of(job.getPath())));
			String clothes = Professions.clothes(job.toString());
			if (clothes != null) {
				Identifier mapped = Identifier.tryParse(clothes);
				if (mapped != null) {
					tags.addAll(ALIASES.getOrDefault(mapped.getPath(), List.of(mapped.getPath())));
				}
			}
		}
		return List.copyOf(tags);
	}

	private static List<Entry> tagged(List<String> tags, Gender gender) {
		List<Entry> found = new ArrayList<>();
		for (Entry e : entries.values()) {
			if (e.fits(gender) && tags.stream().anyMatch(e.tags()::contains)) {
				found.add(e);
			}
		}
		return found;
	}

	/** {@code entity} (a grown villager not yet dressed from the library, their clothes not locked) is dressed from it once. */
	static void redress(Entity entity) {
		if (entries.isEmpty() || !(entity instanceof VillagerEntityMCA villager) || villager.isBaby() || villager.entityTags().contains(TAG)) {
			return;
		}
		villager.addTag(TAG);
		if (!villager.isClothingLocked() && pool(villager) != null) {
			villager.forceRandomizeClothes();
		}
	}

	/** {@code villager} wears royal clothes from the library. */
	static boolean royal(VillagerEntityMCA villager) {
		String id = villager.getClothes();
		if (id == null || !id.startsWith("immersive_library:")) {
			return false;
		}
		try {
			Entry e = entries.get(Integer.parseInt(id.substring("immersive_library:".length())));
			return e != null && ROYAL.stream().anyMatch(e.tags()::contains);
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	/** {@code villager} takes up a throne or leaves one: dressed again for it (unless their clothes are locked). */
	static void ruled(VillagerEntityMCA villager) {
		if (!entries.isEmpty() && !villager.isClothingLocked()) {
			villager.forceRandomizeClothes();
		}
	}

	// ---- the look-up ----------------------------------------------------------------------------------------------------

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("jaymoskingdoms").resolve("clothing_library.json");
	}

	/** The last list found (if any), then a fresh look-up off the server thread if it's old. */
	private static void load() {
		long fetched = 0L;
		try {
			if (Files.exists(file())) {
				JsonObject saved = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
				fetched = saved.has("fetched") ? saved.get("fetched").getAsLong() : 0L;
				Map<Integer, Entry> found = new HashMap<>();
				for (JsonElement e : saved.getAsJsonArray("entries")) {
					Entry entry = GSON.fromJson(e, Entry.class);
					found.put(entry.id(), entry);
				}
				entries = Map.copyOf(found);
				sweep = true;
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't read the saved clothing library list: {}", e.toString());
		}
		if (System.currentTimeMillis() - fetched < REFRESH_MS) {
			return;
		}
		Thread thread = new Thread(ClothingLibrary::fetch, "Jaymo's Kingdoms clothing library");
		thread.setDaemon(true);
		thread.start();
	}

	private static void fetch() {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build();
		Map<Integer, Entry> saved = entries;
		Map<Integer, Entry> found = new LinkedHashMap<>();
		Set<String> tags = new LinkedHashSet<>(ROYAL);
		ALIASES.values().forEach(tags::addAll);
		try {
			for (String tag : tags) {
				JsonObject list = get(client, "v2/content/mca", Map.of("whitelist", tag, "blacklist", "invalid,hair", "order", "likes",
					"descending", "true", "offset", "0", "limit", Integer.toString(PER_TAG)));
				if (list == null || !list.has("contents")) {
					continue;
				}
				for (JsonElement el : list.getAsJsonArray("contents")) {
					JsonObject c = el.getAsJsonObject();
					Set<String> theirs = new LinkedHashSet<>();
					if (c.has("tags") && c.get("tags").isJsonArray()) {
						for (JsonElement t : c.getAsJsonArray("tags")) {
							theirs.add(t.getAsString().toLowerCase(Locale.ROOT).strip());
						}
					}
					int id = c.get("contentid").getAsInt();
					if (!theirs.contains("clothing") || !theirs.contains(tag) || found.containsKey(id)) {
						continue;
					}
					int likes = c.has("likes") ? c.get("likes").getAsInt() : 0;
					Entry known = saved.get(id);
					int gender = known != null ? known.gender() : gender(client, id);
					found.put(id, new Entry(id, Set.copyOf(theirs), genderOf(theirs, gender), likes));
				}
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't reach MCA's clothing library ({}); villagers keep the last list found", e.toString());
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		if (found.isEmpty()) {
			return;
		}
		entries = Map.copyOf(found);
		sweep = true;
		JaymosKingdoms.LOGGER.info("Found {} clothes in MCA's clothing library for villagers' jobs and rulers", found.size());
		try {
			JsonObject out = new JsonObject();
			out.addProperty("fetched", System.currentTimeMillis());
			JsonArray list = new JsonArray();
			found.values().forEach(e -> list.add(GSON.toJsonTree(e)));
			out.add("entries", list);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(out));
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't save the clothing library list: {}", e.toString());
		}
	}

	/** Who the uploader made {@code id} for (MCA's gender id), from its details; 0 (anyone) if they can't be had. */
	private static int gender(HttpClient client, int id) throws IOException, InterruptedException {
		JsonObject content = get(client, "content/mca/" + id, Map.of());
		if (content == null || !content.has("content")) {
			return 0;
		}
		JsonObject c = content.getAsJsonObject("content");
		if (!c.has("meta") || c.get("meta").isJsonNull()) {
			return 0;
		}
		try {
			JsonObject meta = JsonParser.parseString(c.get("meta").getAsString()).getAsJsonObject();
			return meta.has("gender") ? meta.get("gender").getAsInt() : 0;
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** Their tags say who they're for (a queen's gown, a king's robe), else the uploader's word. */
	private static int genderOf(Set<String> tags, int fallback) {
		boolean female = tags.stream().anyMatch(FEMALE_TAGS::contains), male = tags.stream().anyMatch(MALE_TAGS::contains);
		if (female != male) {
			return (female ? Gender.FEMALE : Gender.MALE).getId();
		}
		return fallback;
	}

	/** GET from the library, as MCA's editor asks it ({@code v2/...} as is, anything else under {@code v1/}). */
	private static @Nullable JsonObject get(HttpClient client, String path, Map<String, String> params) throws IOException, InterruptedException {
		String base = Config.getInstance().immersiveLibraryUrl;
		StringBuilder url = new StringBuilder(base.endsWith("/") ? base : base + "/").append(path.startsWith("v2/") ? path : "v1/" + path);
		String sep = "?";
		for (Map.Entry<String, String> p : params.entrySet()) {
			url.append(sep).append(p.getKey()).append('=').append(URLEncoder.encode(p.getValue(), StandardCharsets.UTF_8));
			sep = "&";
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString())).timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
			.header("Accept", "application/json").header("Accept-Encoding", "gzip").GET().build();
		HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			return null;
		}
		byte[] body = response.body();
		if (response.headers().firstValue("Content-Encoding").map("gzip"::equalsIgnoreCase).orElse(false)) {
			try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
				body = in.readAllBytes();
			}
		}
		JsonElement json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
		return json.isJsonObject() ? json.getAsJsonObject() : null;
	}
}
