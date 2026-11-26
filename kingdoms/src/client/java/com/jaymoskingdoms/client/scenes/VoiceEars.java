package com.jaymoskingdoms.client.scenes;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The game's ears: speech recognition for story scenes (a spoken yes or no, the words of a rite) and a Warlock's spoken spells
 * ({@link SpokenSpells}). Andy (2026-11-26), on Endless Lantern Corps: "It also has a voice recognition integration that we
 * could use for warlock spellcasting." Ours is its own: it asks Windows' own speech recogniser (System.Speech, there on every
 * Windows PC, nothing to download) through a hidden PowerShell, listening only for the few phrases the moment wants (a
 * grammar, not dictation), so it is quick and doesn't mishear chatter as a spell.
 * <ul>
 * <li>Off until the player switches it on ({@link #setEnabled}, the microphone button on a scene's question or rite);
 * the choice is kept in {@code config/jaymoskingdoms-voice.properties}.</li>
 * <li>It only listens while something asks it to ({@link #listen}): a scene's question or rite, or a Warlock with the seal in
 * hand. Nothing is recorded or sent anywhere: only the phrase heard comes back, as text, to this game.</li>
 * <li>Not on Windows (the Steam Deck, a Mac), or with no English recogniser installed, it says so ({@link #problem}) and
 * the click and key ways still work.</li>
 * </ul>
 */
public final class VoiceEars {
	/** The least confidence (0 to 1) Windows must have in a phrase for it to count. */
	static final float MIN_CONFIDENCE = 0.55F;
	/** How long each listen waits before checking in (seconds), so a lost game shuts the listener within that time. */
	static final int LISTEN_SECONDS = 3;
	private static final String FILE = "jaymoskingdoms-voice.properties";

	/** What hears a phrase: on the game's own thread. */
	public interface Heard {
		void heard(String phrase, float confidence);
	}

	private static boolean loaded, enabled;
	private static @Nullable Process process;
	private static @Nullable String owner;
	private static List<String> phrases = List.of();
	private static @Nullable String problem;
	private static boolean ready;
	/** What the last failed listen was for, so it isn't started again and again. */
	private static @Nullable String failedFor;
	private static List<String> failedPhrases = List.of();

	private VoiceEars() {
	}

	/** Whether speech can work on this computer at all (Windows). */
	public static boolean possible() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	/** Whether the player has switched the microphone on. */
	public static boolean enabled() {
		load();
		return enabled;
	}

	public static void setEnabled(boolean on) {
		load();
		enabled = on;
		problem = null;
		failedFor = null;
		if (!on) {
			stopAll();
		}
		Properties p = new Properties();
		p.setProperty("microphone", Boolean.toString(on));
		try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
			p.store(w, "Jaymo's Kingdoms: speech for story scenes and spoken spells");
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't save the microphone setting: {}", e.toString());
		}
	}

	/** Why the last listen failed, or null. */
	public static @Nullable String problem() {
		return problem;
	}

	/** Whether a listener is up and hearing. */
	public static boolean listening() {
		return process != null && process.isAlive() && ready;
	}

	/** Who is listening now ({@link #listen}'s owner), or null. */
	public static @Nullable String owner() {
		return process != null && process.isAlive() ? owner : null;
	}

	/** Listens for {@code wanted} on behalf of {@code who}, instead of whatever it listened for; does nothing when switched off or not possible. */
	public static void listen(String who, List<String> wanted, Heard heard) {
		if (!enabled() || !possible() || wanted.isEmpty()) {
			return;
		}
		if (who.equals(owner()) && wanted.equals(phrases)) {
			return;
		}
		if (problem != null && who.equals(failedFor) && wanted.equals(failedPhrases)) {
			return; // it failed for this already: not again until the microphone is switched off and on, or the phrases change
		}
		stopAll();
		owner = who;
		phrases = List.copyOf(wanted);
		ready = false;
		problem = null;
		try {
			ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-EncodedCommand", encode(script(phrases)));
			builder.redirectErrorStream(true);
			Process started = builder.start();
			process = started;
			Thread reader = new Thread(() -> read(started, heard), "Jaymo's Kingdoms speech");
			reader.setDaemon(true);
			reader.start();
		} catch (IOException e) {
			failed(e.getMessage());
			process = null;
			owner = null;
		}
	}

	private static void failed(@Nullable String why) {
		problem = why == null ? "unknown" : why;
		failedFor = owner;
		failedPhrases = phrases;
		JaymosKingdoms.LOGGER.info("Speech recognition isn't listening: {}", problem);
	}

	/** Stops listening, if {@code who} is the one listening. */
	public static void stop(String who) {
		if (who.equals(owner)) {
			stopAll();
		}
	}

	public static void stopAll() {
		Process p = process;
		process = null;
		owner = null;
		phrases = List.of();
		ready = false;
		if (p != null) {
			p.destroyForcibly();
		}
	}

	private static void read(Process p, Heard heard) {
		try (Reader r = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8); BufferedReader in = new BufferedReader(r)) {
			String line;
			while ((line = in.readLine()) != null) {
				if (p != process) {
					break;
				}
				if (line.startsWith("READY")) {
					ready = true;
				} else if (line.startsWith("FAIL|")) {
					String why = line.substring(5);
					Minecraft.getInstance().execute(() -> failed(why));
				} else if (line.startsWith("HEARD|")) {
					String[] parts = line.split("\\|", 3);
					if (parts.length == 3) {
						float confidence;
						try {
							confidence = Float.parseFloat(parts[1]);
						} catch (NumberFormatException e) {
							confidence = 0.0F;
						}
						String phrase = parts[2].trim();
						float c = confidence;
						if (c >= MIN_CONFIDENCE) {
							Minecraft.getInstance().execute(() -> {
								if (p == process) {
									heard.heard(phrase, c);
								}
							});
						}
					}
				}
			}
		} catch (IOException ignored) {
			// the listener was stopped
		} finally {
			if (p == process) {
				Minecraft.getInstance().execute(() -> {
					if (p == process) {
						ready = false;
						if (problem == null) {
							failed("the listener stopped");
						}
						process = null;
						owner = null;
					}
				});
			}
		}
	}

	/** The PowerShell that listens: Windows' recogniser for the first English culture installed, a grammar of {@code wanted}. */
	static String script(List<String> wanted) {
		StringBuilder list = new StringBuilder();
		for (String w : wanted) {
			if (list.length() > 0) {
				list.append(',');
			}
			list.append('\'').append(w.replace("'", "''")).append('\'');
		}
		return String.join("\n",
			"$ErrorActionPreference = 'Stop'",
			"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
			"try {",
			"  Add-Type -AssemblyName System.Speech",
			"  $info = [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers() | Where-Object { $_.Culture.TwoLetterISOLanguageName -eq 'en' } | Select-Object -First 1",
			"  if ($null -eq $info) { [Console]::Out.WriteLine('FAIL|no English speech recogniser is installed in Windows'); exit }",
			"  $r = New-Object System.Speech.Recognition.SpeechRecognitionEngine $info",
			"  $r.SetInputToDefaultAudioDevice()",
			"  $c = New-Object System.Speech.Recognition.Choices",
			"  foreach ($p in @(" + list + ")) { [void]$c.Add($p) }",
			"  $gb = New-Object System.Speech.Recognition.GrammarBuilder",
			"  $gb.Culture = $info.Culture",
			"  $gb.Append($c)",
			"  $r.LoadGrammar((New-Object System.Speech.Recognition.Grammar $gb))",
			"  [Console]::Out.WriteLine('READY'); [Console]::Out.Flush()",
			"  $inv = [System.Globalization.CultureInfo]::InvariantCulture",
			"  while ($true) {",
			"    $res = $r.Recognize([TimeSpan]::FromSeconds(" + LISTEN_SECONDS + "))",
			"    if ($null -ne $res) { [Console]::Out.WriteLine('HEARD|' + $res.Confidence.ToString($inv) + '|' + $res.Text) } else { [Console]::Out.WriteLine('TICK') }",
			"    [Console]::Out.Flush()",
			"  }",
			"} catch { [Console]::Out.WriteLine('FAIL|' + $_.Exception.Message) }");
	}

	private static String encode(String script) {
		return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = path();
		if (Files.exists(file)) {
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(r);
				enabled = Boolean.parseBoolean(p.getProperty("microphone", "false"));
			} catch (IOException e) {
				enabled = false;
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread(VoiceEars::stopAll, "Jaymo's Kingdoms speech shutdown"));
	}
}
