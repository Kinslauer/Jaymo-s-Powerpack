package com.jaymoskingdoms;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Original SVC plugin: bounded microphone clips, kept only for the current voice connection. */
public final class ChangelingVoices implements VoicechatPlugin {
	static final int CLIPS = 3, SAMPLE_RATE = 48000, CLIP_SAMPLES = SAMPLE_RATE * 3;
	static final int MIN_SAMPLES = SAMPLE_RATE / 2;
	static final double SILENCE_RMS = 82.0;
	static final float RANGE = 32.0F;
	private static final Map<UUID, Mic> MICS = new HashMap<>();
	private static final Map<UUID, Playback> PLAYING = new HashMap<>();
	private static VoicechatServerApi api;

	private static final class Mic {
		final OpusDecoder decoder;
		final ArrayDeque<short[]> clips = new ArrayDeque<>();
		short[] pending = new short[CLIP_SAMPLES];
		int size;

		Mic(OpusDecoder decoder) {
			this.decoder = decoder;
		}

		void finish() {
			if (size >= MIN_SAMPLES) {
				if (clips.size() == CLIPS) clips.removeFirst();
				clips.addLast(java.util.Arrays.copyOf(pending, size));
			}
			size = 0;
		}

		void add(short[] pcm) {
			for (int offset = 0; offset < pcm.length;) {
				int n = Math.min(pcm.length - offset, CLIP_SAMPLES - size);
				System.arraycopy(pcm, offset, pending, size, n);
				size += n;
				offset += n;
				if (size == CLIP_SAMPLES) finish();
			}
		}
	}

	private record Playback(UUID speaker, Villager copy, ServerPlayer target, LocationalAudioChannel channel, AudioPlayer player) {
	}

	@Override
	public String getPluginId() {
		return "jaymoskingdoms_changeling_voices";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoicechatServerStartedEvent.class, event -> started(event.getVoicechat()));
		registration.registerEvent(VoicechatServerStoppedEvent.class, event -> clear());
		registration.registerEvent(MicrophonePacketEvent.class, ChangelingVoices::record);
		registration.registerEvent(PlayerDisconnectedEvent.class, event -> forget(event.getPlayerUuid()));
		registration.registerEvent(PlayerStateChangedEvent.class, event -> {
			if (event.isDisabled() || event.isDisconnected()) forget(event.getPlayerUuid());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	private static synchronized void started(VoicechatServerApi serverApi) {
		clear();
		api = serverApi;
	}

	private static boolean enabled(VoicechatConnection connection) {
		return connection != null && connection.isInstalled() && connection.isConnected() && !connection.isDisabled();
	}

	private static synchronized void record(MicrophonePacketEvent event) {
		VoicechatConnection from = event.getSenderConnection();
		if (api == null || from == null) return;
		UUID who = from.getPlayer().getUuid();
		if (!enabled(from)) {
			forget(who);
			return;
		}
		if (event.isCancelled()) return;
		byte[] opus = event.getPacket().getOpusEncodedData();
		Mic mic = MICS.get(who);
		if (opus == null || opus.length == 0) {
			if (mic != null) {
				mic.finish();
				mic.decoder.resetState();
			}
			return;
		}
		if (mic == null) {
			mic = new Mic(api.createDecoder());
			MICS.put(who, mic);
		}
		short[] pcm = mic.decoder.decode(opus);
		if (pcm == null || pcm.length == 0) return;
		double sum = 0.0;
		for (short sample : pcm) sum += (double) sample * sample;
		if (sum / pcm.length < SILENCE_RMS * SILENCE_RMS) {
			mic.finish();
		} else {
			mic.add(pcm);
		}
	}

	static synchronized boolean lure(Villager copy, ServerPlayer target) {
		if (api == null || PLAYING.containsKey(copy.getUUID())) return false;
		ArrayList<UUID> speakers = new ArrayList<>();
		for (var entry : MICS.entrySet()) {
			if (!entry.getKey().equals(target.getUUID()) && !entry.getValue().clips.isEmpty()
				&& enabled(api.getConnectionOf(entry.getKey()))) speakers.add(entry.getKey());
		}
		if (speakers.isEmpty()) return false;
		UUID speaker = speakers.get(copy.getRandom().nextInt(speakers.size()));
		var clips = new ArrayList<>(MICS.get(speaker).clips);
		short[] clip = clips.get(copy.getRandom().nextInt(clips.size()));
		LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(copy.level()),
			api.createPosition(copy.getX(), copy.getEyeY(), copy.getZ()));
		channel.setDistance(RANGE);
		var encoder = api.createEncoder();
		AudioPlayer player = api.createAudioPlayer(channel, encoder, clip);
		PLAYING.put(copy.getUUID(), new Playback(speaker, copy, target, channel, player));
		player.startPlaying();
		return true;
	}

	private static synchronized void tick() {
		if (api == null) return;
		for (UUID who : new ArrayList<>(MICS.keySet())) {
			if (!enabled(api.getConnectionOf(who))) forget(who);
		}
		PLAYING.values().removeIf(play -> {
			if (play.player.isStopped() || !ChangelingLures.stalking(play.copy, play.target)) {
				play.player.stopPlaying();
				return true;
			}
			play.channel.updateLocation(api.createPosition(play.copy.getX(), play.copy.getEyeY(), play.copy.getZ()));
			return false;
		});
	}

	private static synchronized void forget(UUID who) {
		Mic mic = MICS.remove(who);
		if (mic != null) mic.decoder.close();
		PLAYING.values().removeIf(play -> {
			if (!play.speaker.equals(who)) return false;
			play.player.stopPlaying();
			return true;
		});
	}

	private static synchronized void clear() {
		api = null;
		PLAYING.values().forEach(play -> play.player.stopPlaying());
		PLAYING.clear();
		MICS.values().forEach(mic -> mic.decoder.close());
		MICS.clear();
	}
}
