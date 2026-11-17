package com.jaymoskingdoms.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Youth hears its name said aloud (Andy: "Can youth respond to voice input? Maybe just 'Youth'"). What players say over
 * Simple Voice Chat reaches the server already ({@code VoiceRange} decodes it for the mobs that hear you); here it also
 * goes through Vosk (Apache 2.0, Alpha Cephei), an offline speech recogniser told to listen for one word, "youth", and
 * when it hears it that player's wisp comes ({@link YouthWisp#calledByVoice}). Only the name: the question is still typed.
 *
 * <p>Dedicated servers only (the friends' server), and nothing is shipped in the mod: on the first start the server
 * fetches Vosk's library, JNA and Vosk's small English model (about 70 MB, checked against the hashes below) into
 * {@code config/jaymoskingdoms/youth-ears/}, and loads them in a class loader of their own. Everything runs on one
 * background thread; if any of it fails (no network, an unsupported machine), Youth just doesn't hear voices, and says
 * why in the log once. An empty file {@code config/jaymoskingdoms/youth-ears/off} turns it off.
 *
 * <p>While you talk, a line at the top of your screen says whether Youth can hear you (Andy): the server sends
 * {@link Hearing} a couple of times a second while your voice comes in, open or not, and the client shows it a moment
 * past the last one.
 */
public final class YouthEars {
	/** What it fetches: where from, the file it keeps, and its SHA-256. */
	record Fetch(String url, String file, String sha256) {
	}

	static final Fetch VOSK = new Fetch("https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar", "vosk-0.3.45.jar",
		"9e38e96e4448a41d889bb254b2f8424945554945c6e77e1cd97a3d8633fac2ba");
	static final Fetch JNA = new Fetch("https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar", "jna-5.13.0.jar",
		"66d4f819a062a51a1d5627bffc23fac55d1677f0e0a1feba144aabdd670a64bb");
	static final Fetch MODEL = new Fetch("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", "vosk-model-small-en-us-0.15.zip",
		"30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498");
	/** The only word it listens for ("[unk]" soaks up everything else). */
	static final String GRAMMAR = "[\"youth\", \"[unk]\"]";
	/** Vosk's rate (Hz); voice chat's frames are 48 kHz, so every three samples become one. */
	static final float RATE = 16000.0F;
	static final int DOWNSAMPLE = 3;
	/** One call per player this often at most (milliseconds). */
	static final long COOLDOWN_MS = 4000;
	/** While someone talks, they're told whether Youth hears them this often (milliseconds). */
	static final long TELL_EVERY_MS = 400;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Youth's ears");
		t.setDaemon(true);
		return t;
	});
	private static final Map<UUID, Object> RECOGNISERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_CALL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOLD = new ConcurrentHashMap<>();
	private static volatile @Nullable MinecraftServer server;
	private static volatile @Nullable Object model;
	private static @Nullable Constructor<?> newRecogniser;
	private static @Nullable Method accept, partial, reset, close;

	private YouthEars() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			if (s.isDedicatedServer()) {
				WORKER.execute(YouthEars::load);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			server = null;
			WORKER.execute(() -> {
				RECOGNISERS.values().forEach(YouthEars::closeQuietly);
				RECOGNISERS.clear();
			});
		});
	}

	/** A frame of what {@code who} said (16-bit, 48 kHz mono), from voice chat's thread. */
	public static void heard(UUID who, short[] pcm) {
		tell(who);
		if (model == null || pcm.length < DOWNSAMPLE) {
			return;
		}
		WORKER.execute(() -> listen(who, pcm));
	}

	/** {@code who} stopped talking (or left): what they were saying is forgotten. */
	public static void ended(UUID who) {
		if (model == null) {
			return;
		}
		WORKER.execute(() -> {
			Object recogniser = RECOGNISERS.get(who);
			if (recogniser != null) {
				invoke(reset, recogniser);
			}
		});
	}

	/** Tells {@code who} (now and then, while they talk) whether Youth can hear them. */
	private static void tell(UUID who) {
		MinecraftServer s = server;
		long now = System.currentTimeMillis();
		Long last = LAST_TOLD.get(who);
		if (s == null || last != null && now - last < TELL_EVERY_MS) {
			return;
		}
		LAST_TOLD.put(who, now);
		boolean open = model != null;
		s.execute(() -> {
			ServerPlayer player = s.getPlayerList().getPlayer(who);
			if (player != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, Hearing.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new Hearing(open));
			}
		});
	}

	/** Server to a player who is talking: whether Youth can hear them ({@code open}) or not. */
	public record Hearing(boolean open) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<Hearing> TYPE = new Type<>(JaymosKingdoms.id("youth_hearing"));
		public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Hearing> CODEC =
			net.minecraft.network.codec.ByteBufCodecs.BOOL.map(Hearing::new, Hearing::open).cast();

		@Override
		public Type<Hearing> type() {
			return TYPE;
		}
	}

	public static void left(UUID who) {
		LAST_TOLD.remove(who);
		WORKER.execute(() -> closeQuietly(RECOGNISERS.remove(who)));
	}

	// ---------------------------------------------------------------- on the worker thread

	private static void listen(UUID who, short[] pcm) {
		try {
			Object recogniser = RECOGNISERS.get(who);
			if (recogniser == null) {
				recogniser = newRecogniser.newInstance(model, RATE, GRAMMAR);
				RECOGNISERS.put(who, recogniser);
			}
			short[] low = new short[pcm.length / DOWNSAMPLE];
			for (int i = 0; i < low.length; i++) {
				int at = i * DOWNSAMPLE;
				low[i] = (short) ((pcm[at] + pcm[at + 1] + pcm[at + 2]) / 3);
			}
			accept.invoke(recogniser, low, low.length);
			// the partial result is enough: the word is heard as it's said, not at the end of the sentence
			String said = (String) partial.invoke(recogniser);
			if (said != null && said.contains("youth")) {
				invoke(reset, recogniser);
				long now = System.currentTimeMillis();
				Long last = LAST_CALL.get(who);
				MinecraftServer s = server;
				if ((last == null || now - last > COOLDOWN_MS) && s != null) {
					LAST_CALL.put(who, now);
					s.execute(() -> {
						ServerPlayer player = s.getPlayerList().getPlayer(who);
						if (player != null) {
							YouthWisp.calledByVoice(player);
						}
					});
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Youth's ears: couldn't listen, voices won't call it", e);
			model = null;
		}
	}

	/** Fetches (first time) and loads Vosk, JNA and the model. */
	private static void load() {
		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("jaymoskingdoms").resolve("youth-ears");
			if (Files.exists(dir.resolve("off"))) {
				JaymosKingdoms.LOGGER.info("Youth's ears are off ({} exists)", dir.resolve("off"));
				return;
			}
			Files.createDirectories(dir);
			Path vosk = fetch(dir, VOSK), jna = fetch(dir, JNA);
			Path modelDir = dir.resolve("model");
			if (!Files.isDirectory(modelDir.resolve("am"))) {
				unzip(fetch(dir, MODEL), modelDir);
			}
			URLClassLoader loader = new URLClassLoader("youth-ears", new URL[] {vosk.toUri().toURL(), jna.toUri().toURL()},
				YouthEars.class.getClassLoader());
			Class<?> libVosk = Class.forName("org.vosk.LibVosk", true, loader);
			Class<?> logLevel = Class.forName("org.vosk.LogLevel", true, loader);
			libVosk.getMethod("setLogLevel", logLevel).invoke(null, logLevel.getField("WARNINGS").get(null));
			Class<?> modelClass = Class.forName("org.vosk.Model", true, loader);
			Class<?> recogniser = Class.forName("org.vosk.Recognizer", true, loader);
			Object loaded = modelClass.getConstructor(String.class).newInstance(modelDir.toString());
			newRecogniser = recogniser.getConstructor(modelClass, float.class, String.class);
			accept = recogniser.getMethod("acceptWaveForm", short[].class, int.class);
			partial = recogniser.getMethod("getPartialResult");
			reset = recogniser.getMethod("reset");
			close = recogniser.getMethod("close");
			model = loaded;
			JaymosKingdoms.LOGGER.info("Youth's ears are open: saying \"youth\" in voice chat calls the wisp");
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Youth's ears: couldn't set up voice recognition, so voices won't call the wisp", e);
		}
	}

	/** {@code dir/<file>}, downloaded first if it isn't there or doesn't match its hash. */
	private static Path fetch(Path dir, Fetch what) throws IOException, InterruptedException {
		Path file = dir.resolve(what.file());
		if (Files.exists(file) && what.sha256().equals(sha256(file))) {
			return file;
		}
		JaymosKingdoms.LOGGER.info("Youth's ears: fetching {}", what.url());
		Path part = dir.resolve(what.file() + ".part");
		try (HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
			HttpResponse<Path> response = http.send(HttpRequest.newBuilder(URI.create(what.url())).build(),
				HttpResponse.BodyHandlers.ofFile(part));
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode() + " for " + what.url());
			}
		}
		String got = sha256(part);
		if (!what.sha256().equals(got)) {
			Files.deleteIfExists(part);
			throw new IOException("wrong SHA-256 for " + what.url() + ": " + got);
		}
		Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
		return file;
	}

	private static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[1 << 16];
			for (int n; (n = in.read(buffer)) > 0;) {
				digest.update(buffer, 0, n);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	/** Unpacks the model zip into {@code into}, dropping the zip's own top folder. */
	private static void unzip(Path zip, Path into) throws IOException {
		Path tmp = into.resolveSibling("model.part");
		if (Files.exists(tmp)) {
			try (var walk = Files.walk(tmp)) {
				walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
				String name = entry.getName();
				int slash = name.indexOf('/');
				String inner = slash < 0 ? name : name.substring(slash + 1);
				if (inner.isEmpty()) {
					continue;
				}
				Path out = tmp.resolve(inner).normalize();
				if (!out.startsWith(tmp)) {
					throw new IOException("bad zip entry " + name);
				}
				if (entry.isDirectory()) {
					Files.createDirectories(out);
				} else {
					Files.createDirectories(out.getParent());
					Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Files.move(tmp, into, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void invoke(@Nullable Method method, Object target) {
		try {
			if (method != null) {
				method.invoke(target);
			}
		} catch (ReflectiveOperationException ignored) {
			// a recogniser that can't reset just carries on
		}
	}

	private static void closeQuietly(@Nullable Object recogniser) {
		if (recogniser != null) {
			invoke(close, recogniser);
		}
	}
}
