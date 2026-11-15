package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The city the future is made of. It used to be Los Perrito City by Uospag
 * (https://www.curseforge.com/minecraft/worlds/los-perrito, CC BY 4.0); new futures are built from Mosslorn by
 * milkshake_mp3 ({@link #MOSSLORN}), a city slowly overgrown with moss. Whichever map it is, in the world it is still
 * Los Perrito. A world whose future was already built from the old map keeps it until an operator runs
 * {@code /future swap} ({@link #requestSwap}).
 * <p>A map at half a gigabyte can't ride in a jar, so the first time anyone tries to leap forward the server fetches it
 * from CurseForge (or uses the zip if someone dropped it in the server or world folder), unpacks its region, entity and
 * POI files into the {@link Future#FUTURE} dimension's folder, and copies any map arts (billboards, posters) in above
 * every map id the world has used, so they never clash with players' own maps. The game upgrades each old chunk the
 * first time it loads. A marker file records which map is in place and the map id offset ({@link #mapOffset()}) that
 * {@link Future} applies to the city's item frames.
 * <p>Changes made to the map (ruins, overgrowth, dead lights, its biome, its loot) are ours: {@link FutureRuins}.
 */
public final class LosPerrito {
	/** One map the future can be built from: its file on CurseForge, where its folder sits in the zip, its spawn and extent. */
	public record CityMap(String name, String fileName, long fileSize, String download, String creditUrl, String creditKey,
		String marker, int spawnX, int spawnZ, int minX, int maxX, int minZ, int maxZ) {
		public boolean covers(net.minecraft.world.level.ChunkPos pos) {
			return pos.getMaxBlockX() >= minX && pos.getMinBlockX() <= maxX && pos.getMaxBlockZ() >= minZ && pos.getMinBlockZ() <= maxZ;
		}
	}

	/** Los Perrito 2.0.1 (its region files run r.-7.-7 to r.10.6); the map's own spawn is the arrival point. */
	public static final CityMap PERRITO = new CityMap("Los Perrito City", "LosPerrito2.0.1.zip", 801_180_636L,
		"https://www.curseforge.com/api/v1/mods/992693/files/9033035/download", "https://www.curseforge.com/minecraft/worlds/los-perrito",
		"message.jaymospowers.future.credit", "los_perrito.txt", -753, -238, -7 * 512, 11 * 512 - 1, -7 * 512, 7 * 512 - 1);
	/**
	 * Mosslorn v4.0 for Java 1.21+ (its city fills r.-3.-3 to r.2.2). The map starts players in a sky lobby whose command
	 * block sends them to the street at -140, -85; that street is the arrival point.
	 */
	public static final CityMap MOSSLORN = new CityMap("Mosslorn", "Mosslorn-v4.0-java-1.21.zip", 553_585_569L,
		"https://www.curseforge.com/api/v1/mods/1272043/files/8007945/download",
		"https://www.curseforge.com/minecraft/worlds/mosslorn-1000x1000-abandoned-overgrown-city",
		"message.jaymospowers.future.credit.mosslorn", "mosslorn.txt", -140, -85, -3 * 512, 3 * 512 - 1, -3 * 512, 3 * 512 - 1);
	/** What a new future is built from. */
	static final CityMap NEWEST = MOSSLORN;
	static final CityMap[] ALL = {MOSSLORN, PERRITO};
	/** The parts of the world folder inside the zip we take (the folder's own name differs from map to map). */
	static final String[] TAKEN = {"region/", "entities/", "poi/"};
	static final Pattern MAP_FILE = Pattern.compile("[^/]+/data/map_(\\d+)\\.dat");

	public enum Stage { IDLE, DOWNLOADING, UNPACKING, READY, FAILED }

	private static volatile Stage stage = Stage.IDLE;
	private static volatile long done, total = NEWEST.fileSize();
	private static volatile int mapOffset;
	private static volatile String failure = "";
	/** The map this world's future is (or is being) built from. */
	private static volatile CityMap map = NEWEST;

	private LosPerrito() {
	}

	public static Stage stage() {
		return stage;
	}

	/** How far along the download or unpacking is, 0-100. */
	public static int percent() {
		return total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
	}

	public static String failure() {
		return failure;
	}

	public static CityMap map() {
		return map;
	}

	/** Where newcomers arrive. */
	public static int spawnX() {
		return map.spawnX();
	}

	public static int spawnZ() {
		return map.spawnZ();
	}

	/** Whether a chunk is part of the map (not Lost Cities' or plain ground beyond it). */
	public static boolean covers(net.minecraft.world.level.ChunkPos pos) {
		return map.covers(pos);
	}

	/** What the city's map ids are shifted by. */
	public static int mapOffset() {
		return mapOffset;
	}

	static Path dimensionFolder(MinecraftServer server) {
		return DimensionType.getStorageFolder(Future.FUTURE, server.getWorldPath(LevelResource.ROOT).normalize());
	}

	/** On server start: is the city already here? */
	static void load(MinecraftServer server) {
		stage = Stage.IDLE;
		done = 0;
		mapOffset = 0;
		map = NEWEST;
		for (CityMap built : ALL) {
			Path marker = dimensionFolder(server).resolve(built.marker());
			if (!Files.isRegularFile(marker)) {
				continue;
			}
			map = built;
			try {
				for (String line : Files.readAllLines(marker)) {
					if (line.startsWith("mapOffset=")) {
						mapOffset = Integer.parseInt(line.substring("mapOffset=".length()).trim());
					}
				}
				stage = Stage.READY;
			} catch (IOException | NumberFormatException e) {
				JaymosPowers.LOGGER.warn("Couldn't read {}", marker, e);
			}
			return;
		}
	}

	/** The request {@code /future swap} leaves for the next start. */
	static final String SWAP_REQUEST = "swap_city.txt";

	/**
	 * {@code /future swap}: asks for this world's future to be rebuilt from {@link #NEWEST} at the next server start, when
	 * the dimension isn't loaded yet. Returns false if it already is built from that map.
	 */
	static boolean requestSwap(MinecraftServer server) throws IOException {
		if (map == NEWEST) {
			return false;
		}
		Files.createDirectories(dimensionFolder(server));
		Files.writeString(dimensionFolder(server).resolve(SWAP_REQUEST), "Rebuild the future from " + NEWEST.name() + " at the next start.\n");
		return true;
	}

	/**
	 * On server start, before the dimension loads: if a swap was asked for, the old city goes (its regions, entities,
	 * POIs, the saved arrival square and the marker), so the next leap fetches {@link #NEWEST} into an empty future.
	 */
	static void swapIfAsked(MinecraftServer server) {
		Path folder = dimensionFolder(server);
		Path request = folder.resolve(SWAP_REQUEST);
		if (!Files.isRegularFile(request)) {
			return;
		}
		try {
			for (String part : TAKEN) {
				Path dir = folder.resolve(part);
				if (Files.isDirectory(dir)) {
					try (var files = Files.walk(dir)) {
						for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
							Files.delete(file);
						}
					}
				}
			}
			for (CityMap old : ALL) {
				Files.deleteIfExists(folder.resolve(old.marker()));
			}
			Files.deleteIfExists(folder.resolve(FutureStart.FILE));
			Files.delete(request);
			JaymosPowers.LOGGER.info("Cleared the future's old city; it will be rebuilt from {}", NEWEST.name());
		} catch (IOException e) {
			JaymosPowers.LOGGER.error("Couldn't clear the future's old city", e);
		}
	}

	/** Starts fetching and unpacking the city, unless that's already under way (or done). */
	static synchronized void begin(MinecraftServer server) {
		if (stage == Stage.DOWNLOADING || stage == Stage.UNPACKING || stage == Stage.READY) {
			return;
		}
		stage = Stage.DOWNLOADING;
		done = 0;
		CityMap building = map;
		total = building.fileSize();
		failure = "";
		Path folder = dimensionFolder(server);
		Path worldRoot = server.getWorldPath(LevelResource.ROOT).normalize();
		Path serverDir = server.getServerDirectory();
		Thread thread = new Thread(() -> {
			try {
				Path zip = fetch(building, serverDir, worldRoot);
				stage = Stage.UNPACKING;
				List<Integer> maps = unpack(zip, folder);
				server.execute(() -> finish(building, server, folder, maps, zip, worldRoot));
			} catch (Exception e) {
				JaymosPowers.LOGGER.error("Couldn't fetch {} for the future", building.name(), e);
				failure = String.valueOf(e.getMessage());
				stage = Stage.FAILED;
			}
		}, "Jaymo's Powers: the future's city");
		thread.setDaemon(true);
		thread.start();
	}

	/** A zip someone left for us, or a fresh download (kept as a .part until it's whole). */
	private static Path fetch(CityMap building, Path serverDir, Path worldRoot) throws IOException, InterruptedException {
		String fileName = building.fileName();
		long fileSize = building.fileSize();
		for (Path candidate : new Path[] {serverDir.resolve(fileName), worldRoot.resolve(fileName)}) {
			if (Files.isRegularFile(candidate) && Files.size(candidate) == fileSize) {
				done = fileSize;
				return candidate;
			}
		}
		Path part = worldRoot.resolve("jaymospowers-" + fileName + ".part");
		Path whole = worldRoot.resolve("jaymospowers-" + fileName);
		if (Files.isRegularFile(whole) && Files.size(whole) == fileSize) {
			done = fileSize;
			return whole;
		}
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(30)).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(building.download())).header("User-Agent", "JaymosPowers").GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("CurseForge answered " + response.statusCode());
		}
		long length = response.headers().firstValueAsLong("Content-Length").orElse(fileSize);
		total = length > 0 ? length : fileSize;
		try (InputStream in = response.body(); var out = Files.newOutputStream(part)) {
			byte[] buffer = new byte[1 << 16];
			int n;
			while ((n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				done += n;
			}
		}
		if (Files.size(part) != fileSize) {
			throw new IOException("The download came out " + Files.size(part) + " bytes, not " + fileSize);
		}
		Files.move(part, whole, StandardCopyOption.REPLACE_EXISTING);
		return whole;
	}

	/** Region, entity and POI files into the dimension's folder; the map arts aside for {@link #finish}. Returns the map ids. */
	private static List<Integer> unpack(Path zip, Path folder) throws IOException {
		List<Integer> maps = new ArrayList<>();
		Path mapsAside = folder.resolve("los_perrito_maps");
		Files.createDirectories(mapsAside);
		try (ZipFile file = new ZipFile(zip.toFile())) {
			long sum = 0;
			for (Enumeration<? extends ZipEntry> e = file.entries(); e.hasMoreElements(); ) {
				sum += Math.max(0, e.nextElement().getSize());
			}
			total = sum;
			done = 0;
			for (Enumeration<? extends ZipEntry> e = file.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = e.nextElement();
				String name = entry.getName();
				done += Math.max(0, entry.getSize());
				if (entry.isDirectory()) {
					continue;
				}
				Path target = null;
				Matcher map = MAP_FILE.matcher(name);
				if (map.matches()) {
					int id = Integer.parseInt(map.group(1));
					maps.add(id);
					target = mapsAside.resolve(id + ".dat");
				} else if (entry.getSize() != 0) {
					// the map's own folder name ("Los Perrito/", "§aMosslorn v4.0_java 1.21+/") is skipped, whatever it is
					int slash = name.indexOf('/');
					for (String part : TAKEN) {
						String prefix = name.substring(0, slash + 1) + part;
						if (slash > 0 && name.startsWith(prefix) && name.endsWith(".mca") && name.indexOf('/', prefix.length()) < 0) {
							target = folder.resolve(part).resolve(name.substring(prefix.length()));
						}
					}
				}
				if (target == null || !target.normalize().startsWith(folder)) {
					continue;
				}
				Files.createDirectories(target.getParent());
				try (InputStream in = file.getInputStream(entry)) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		return maps;
	}

	/** On the server thread: the map arts move in above every id in use, and the marker is written. */
	private static void finish(CityMap building, MinecraftServer server, Path folder, List<Integer> maps, Path zip, Path worldRoot) {
		try {
			int offset = server.overworld().getFreeMapId().id();
			int highest = offset;
			Path mapsDir = server.getWorldPath(LevelResource.DATA).resolve("minecraft").resolve("maps");
			Files.createDirectories(mapsDir);
			Path aside = folder.resolve("los_perrito_maps");
			for (int map : maps) {
				int id = offset + map;
				Files.move(aside.resolve(map + ".dat"), mapsDir.resolve(id + ".dat"), StandardCopyOption.REPLACE_EXISTING);
				highest = Math.max(highest, id);
			}
			Files.deleteIfExists(aside);
			// claim every id up to the highest, so the next map anyone makes comes after the city's
			MapId next;
			do {
				next = server.overworld().getFreeMapId();
			} while (next.id() < highest);
			Files.writeString(folder.resolve(building.marker()), building.name() + ", " + building.creditUrl() + ", file " + building.fileName()
				+ "\nmapOffset=" + offset + "\n");
			mapOffset = offset;
			// our own download goes once it's unpacked (a zip someone left for us stays where they put it)
			if (zip.startsWith(worldRoot) && zip.getFileName().toString().startsWith("jaymospowers-")) {
				Files.deleteIfExists(zip);
			}
			stage = Stage.READY;
			JaymosPowers.LOGGER.info("{} is in place for the future ({} map arts from id {})", building.name(), maps.size(), offset);
		} catch (IOException e) {
			JaymosPowers.LOGGER.error("Couldn't finish placing {}", building.name(), e);
			failure = String.valueOf(e.getMessage());
			stage = Stage.FAILED;
		}
	}
}
