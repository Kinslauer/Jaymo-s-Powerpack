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
 * Los Perrito City by Uospag (https://www.curseforge.com/minecraft/worlds/los-perrito), CC BY 4.0: the city the future
 * is made of. At 764 MB zipped it can't ride in a jar, so the first time anyone tries to leap forward the server fetches
 * the map from CurseForge (or uses {@code LosPerrito2.0.1.zip} if someone dropped it in the server or world folder),
 * unpacks its region, entity and POI files into the {@link Future#FUTURE} dimension's folder, and copies its 5,800-odd
 * map arts (billboards, posters) in above every map id the world has used, so they never clash with players' own maps.
 * Its chunks were saved in 1.19.2; the game upgrades each one the first time it loads. A marker file records that it's
 * done, and the map id offset ({@link #mapOffset()}) that {@link Future} applies to the city's item frames.
 * <p>Changes made to the map (ruins, overgrowth, dead lights, its biome) are ours: {@link FutureRuins}.
 */
public final class LosPerrito {
	/** The file we fetch: Los Perrito 2.0.1 (for 26.3). */
	static final String FILE_NAME = "LosPerrito2.0.1.zip";
	static final long FILE_SIZE = 801_180_636L;
	static final String DOWNLOAD = "https://www.curseforge.com/api/v1/mods/992693/files/9033035/download";
	public static final String CREDIT_URL = "https://www.curseforge.com/minecraft/worlds/los-perrito";
	/** The folder inside the zip, and the parts of it we take. */
	static final String ROOT = "Los Perrito/";
	static final String[] TAKEN = {"region/", "entities/", "poi/"};
	static final Pattern MAP_FILE = Pattern.compile("Los Perrito/data/map_(\\d+)\\.dat");
	static final String MARKER = "los_perrito.txt";
	/** Where the map's own spawn was: the arrival point. */
	public static final int SPAWN_X = -753, SPAWN_Z = -238;
	/** The island's extent in blocks (its region files run r.-7.-7 to r.10.6). */
	public static final int MIN_X = -7 * 512, MAX_X = 11 * 512 - 1, MIN_Z = -7 * 512, MAX_Z = 7 * 512 - 1;

	public enum Stage { IDLE, DOWNLOADING, UNPACKING, READY, FAILED }

	private static volatile Stage stage = Stage.IDLE;
	private static volatile long done, total = FILE_SIZE;
	private static volatile int mapOffset;
	private static volatile String failure = "";

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
		Path marker = dimensionFolder(server).resolve(MARKER);
		if (Files.isRegularFile(marker)) {
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
		}
	}

	/** Starts fetching and unpacking the city, unless that's already under way (or done). */
	static synchronized void begin(MinecraftServer server) {
		if (stage == Stage.DOWNLOADING || stage == Stage.UNPACKING || stage == Stage.READY) {
			return;
		}
		stage = Stage.DOWNLOADING;
		done = 0;
		total = FILE_SIZE;
		failure = "";
		Path folder = dimensionFolder(server);
		Path worldRoot = server.getWorldPath(LevelResource.ROOT).normalize();
		Path serverDir = server.getServerDirectory();
		Thread thread = new Thread(() -> {
			try {
				Path zip = fetch(serverDir, worldRoot);
				stage = Stage.UNPACKING;
				List<Integer> maps = unpack(zip, folder);
				server.execute(() -> finish(server, folder, maps, zip, worldRoot));
			} catch (Exception e) {
				JaymosPowers.LOGGER.error("Couldn't fetch Los Perrito for the future", e);
				failure = String.valueOf(e.getMessage());
				stage = Stage.FAILED;
			}
		}, "Jaymo's Powers: Los Perrito");
		thread.setDaemon(true);
		thread.start();
	}

	/** A zip someone left for us, or a fresh download (kept as a .part until it's whole). */
	private static Path fetch(Path serverDir, Path worldRoot) throws IOException, InterruptedException {
		for (Path candidate : new Path[] {serverDir.resolve(FILE_NAME), worldRoot.resolve(FILE_NAME)}) {
			if (Files.isRegularFile(candidate) && Files.size(candidate) == FILE_SIZE) {
				done = FILE_SIZE;
				return candidate;
			}
		}
		Path part = worldRoot.resolve("jaymospowers-" + FILE_NAME + ".part");
		Path whole = worldRoot.resolve("jaymospowers-" + FILE_NAME);
		if (Files.isRegularFile(whole) && Files.size(whole) == FILE_SIZE) {
			done = FILE_SIZE;
			return whole;
		}
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(30)).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(DOWNLOAD)).header("User-Agent", "JaymosPowers").GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("CurseForge answered " + response.statusCode());
		}
		long length = response.headers().firstValueAsLong("Content-Length").orElse(FILE_SIZE);
		total = length > 0 ? length : FILE_SIZE;
		try (InputStream in = response.body(); var out = Files.newOutputStream(part)) {
			byte[] buffer = new byte[1 << 16];
			int n;
			while ((n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				done += n;
			}
		}
		if (Files.size(part) != FILE_SIZE) {
			throw new IOException("The download came out " + Files.size(part) + " bytes, not " + FILE_SIZE);
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
				} else {
					for (String part : TAKEN) {
						String prefix = ROOT + part;
						if (name.startsWith(prefix) && name.endsWith(".mca") && name.indexOf('/', prefix.length()) < 0) {
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
	private static void finish(MinecraftServer server, Path folder, List<Integer> maps, Path zip, Path worldRoot) {
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
			Files.writeString(folder.resolve(MARKER), "Los Perrito City by Uospag, " + CREDIT_URL + " (CC BY 4.0), file " + FILE_NAME
				+ "\nmapOffset=" + offset + "\n");
			mapOffset = offset;
			// our own download goes once it's unpacked (a zip someone left for us stays where they put it)
			if (zip.startsWith(worldRoot) && zip.getFileName().toString().startsWith("jaymospowers-")) {
				Files.deleteIfExists(zip);
			}
			stage = Stage.READY;
			JaymosPowers.LOGGER.info("Los Perrito is in place for the future ({} map arts from id {})", maps.size(), offset);
		} catch (IOException e) {
			JaymosPowers.LOGGER.error("Couldn't finish placing Los Perrito", e);
			failure = String.valueOf(e.getMessage());
			stage = Stage.FAILED;
		}
	}
}
