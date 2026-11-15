package com.jaymoskingdoms;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.Vec3;

/**
 * How far voices carry for the big forms, through Simple Voice Chat's server plugin API (the mod is all rights reserved,
 * henkelmax; only its published API is used). Simple Voice Chat makes this class itself, from Kingdoms' {@code voicechat}
 * entrypoint, so nothing here is touched without it. Its events come on its own thread: who is a titan and how tall each
 * player is are read from what {@link VoiceMasks} saw on the server's last check.
 * <ul>
 * <li><b>A titan's voice carries</b> ({@link #speaking}). Andy: a transformed great ape and every xeno form "is audible to
 * everyone on the ground: their voice range must reach players far below them". Their proximity range grows by
 * {@link #TITAN_RANGE}, and by {@link #SPEAK_PER_BLOCK} for every block they stand taller than a man, since the voice
 * comes from their head. A whisper stays a whisper.</li>
 * <li><b>A giant hears the ground</b> ({@link #heard}). Andy: "make sure a transformed great ape still hears the players on
 * the ground". Voice chat hears from the camera, and a giant's rides far above and behind him (a full Great Ape's is about
 * 40 blocks from his feet), so the players about his feet were too far off to hear. Every voice sent to someone taller than
 * a man by {@link #HEAR_FROM} or more is sent to him again with {@link #HEAR_PER_BLOCK} more range for every block of his
 * height past a man's, so he hears the ground about as well as a man standing at his feet.</li>
 * <li><b>A ghost speaks and hears from where it is</b> ({@link #spoken}). A player out of their body, left for dead
 * (Powers' OutOfBody, {@link VoiceMasks.Mask#GHOST}), is at their ghost as far as voices go: what they say is taken off the
 * body and sent from the ghost's place to whoever is near that, and whatever is said near the ghost reaches them though
 * their body lies out of earshot. A ghost whose client has no voice changer isn't heard at all: Andy, "the voice changer
 * automatically makes you sound like a ghost if you speak otherwise it is muted". Group calls are left as they are.</li>
 * <li><b>Mobs hear you talk</b> ({@link #overheard}, the medieval picks' Hearing). Every word spoken by proximity is
 * decoded and its loudness measured (RMS, in dB) against that player's own normal voice: the median of their last
 * {@link #WINDOW} spoken frames (a minute of talk), learned slowly and leaving out frames more than {@link #BASELINE_ABOVE_DB}
 * dB over it, so a yell never becomes the normal and a quiet microphone is no free stealth. At their normal level a voice carries
 * {@link #VOICE_REACH} blocks, twice that for every {@link #DOUBLING_DB} dB louder (between {@link #SOFTEST} and
 * {@link #LOUDEST} times), a whisper {@link #WHISPER_SHARE} of that; {@link Hearing} sends it out from their head, through
 * the walls, to the monsters and (at night, a trespasser's) the guards. After Mob Senses' voice tracking (Shamanalle, MIT;
 * its {@code VoiceTracker}'s way of it, our own code).</li>
 * </ul>
 */
public final class VoiceRange implements VoicechatPlugin {
	/** A titan's voice: this much further (blocks), plus this much for every block it stands taller than a man. */
	static final float TITAN_RANGE = 16.0F, SPEAK_PER_BLOCK = 2.0F;
	/** A giant listener: taller than a man by this much or more (blocks), he hears this much further for every block. */
	static final float HEAR_FROM = 1.0F, HEAR_PER_BLOCK = 1.4F;

	/** A ghost's whisper carries this share of its voice's range; its voice comes from this far up its shape (blocks). */
	static final float GHOST_WHISPER = 0.5F;
	static final double GHOST_MOUTH = 1.5;

	/** Mobs hearing voices: how far normal speech carries (blocks), the share a whisper keeps, and the least and most a voice is scaled by. */
	static final double VOICE_REACH = 16.0, WHISPER_SHARE = 0.3, SOFTEST = 0.35, LOUDEST = 2.25;
	/** This many dB over a player's normal voice doubles its reach; at this scale or more it's a shout. */
	static final double DOUBLING_DB = 10.0, SHOUT_FROM = 1.6;
	/** Quieter than this (dBFS) is silence; a player's normal level before they've said enough, and the bounds it's kept within. */
	static final double SILENCE_DB = -52.0, START_DB = -24.0, NORMAL_MIN_DB = -44.0, NORMAL_MAX_DB = -8.0;
	/**
	 * Spoken frames (20 ms each) a player's normal level is taken over (a minute of talk), the fewest before it's theirs, and
	 * how often (frames) the normal is worked out again. Once it is theirs, frames more than {@link #BASELINE_ABOVE_DB} over it
	 * (shouting) are left out of it, so a yell carries far however long it lasts.
	 */
	static final int WINDOW = 3000, CALIBRATED = 100, RECOUNT = 50;
	static final double BASELINE_ABOVE_DB = 5.0;
	/** One in this many frames left out still goes in, so a microphone turned up for good is learned in a few minutes. */
	static final int LOUD_KEPT = 10;

	/** A player's microphone: their recent spoken levels and its median, their normal voice. */
	private static final class Mic {
		private final double[] window = new double[WINDOW];
		private int count, next, since, loud;
		private double normal = START_DB;

		/** A frame at {@code db}: how much further than their normal voice it carries. A whisper doesn't move the normal. */
		synchronized double scale(double db, boolean whisper) {
			boolean kept = this.count < CALIBRATED || db <= this.normal + BASELINE_ABOVE_DB || ++this.loud % LOUD_KEPT == 0;
			if (!whisper && kept) {
				this.window[this.next] = db;
				this.next = (this.next + 1) % WINDOW;
				this.count = Math.min(WINDOW, this.count + 1);
				if (this.count >= CALIBRATED && (++this.since >= RECOUNT || this.count == CALIBRATED)) {
					this.since = 0;
					double[] sorted = java.util.Arrays.copyOf(this.window, this.count);
					java.util.Arrays.sort(sorted);
					double median = (this.count & 1) == 1 ? sorted[this.count / 2] : (sorted[this.count / 2 - 1] + sorted[this.count / 2]) / 2.0;
					this.normal = Math.max(NORMAL_MIN_DB, Math.min(NORMAL_MAX_DB, median));
				}
			}
			return Math.max(SOFTEST, Math.min(LOUDEST, Math.pow(2.0, (db - this.normal) / DOUBLING_DB)));
		}
	}

	private static final Map<UUID, OpusDecoder> DECODERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Mic> MICS = new ConcurrentHashMap<>();

	@Override
	public String getPluginId() {
		return JaymosKingdoms.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoiceDistanceEvent.class, VoiceRange::speaking);
		registration.registerEvent(EntitySoundPacketEvent.class, VoiceRange::heard);
		registration.registerEvent(MicrophonePacketEvent.class, VoiceRange::spoken);
		registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
			MICS.remove(event.getPlayerUuid());
			OpusDecoder decoder = DECODERS.remove(event.getPlayerUuid());
			if (decoder != null) {
				synchronized (decoder) {
					decoder.close();
				}
			}
		});
	}

	/** A titan speaks: further. */
	private static void speaking(VoiceDistanceEvent event) {
		VoicechatConnection from = event.getSenderConnection();
		if (from == null || event.getPacket() == null || event.getPacket().isWhispering()) {
			return;
		}
		java.util.UUID who = from.getPlayer().getUuid();
		if (VoiceMasks.titan(who)) {
			event.setDistance(event.getDistance() + TITAN_RANGE + SPEAK_PER_BLOCK * VoiceMasks.grown(who));
		}
	}

	/** Someone speaks into their microphone: a ghost's words leave from its ghost, and ghosts near a speaker hear them. */
	private static void spoken(MicrophonePacketEvent event) {
		VoicechatConnection from = event.getSenderConnection();
		MicrophonePacket packet = event.getPacket();
		if (from == null || packet == null || from.isInGroup()) {
			return;
		}
		VoicechatServerApi api = event.getVoicechat();
		ServerPlayer speaker = from.getPlayer();
		UUID who = speaker.getUuid();
		if (PowersCompat.ghostOf(who) == null) {
			overheard(api, who, packet);
		}
		ServerLevel level = speaker.getServerLevel();
		Map<UUID, Vec3> ghosts = VoiceMasks.ghosts();
		float range = (float)api.getVoiceChatDistance() * (packet.isWhispering() ? GHOST_WHISPER : 1.0F);
		Vec3 ghost = PowersCompat.ghostOf(who);
		if (ghost == null) {
			if (ghosts.isEmpty()) {
				return;
			}
			// the living, with a ghost near whose body is too far off to be sent this
			Position at = speaker.getPosition();
			EntitySoundPacket sound = null;
			for (Map.Entry<UUID, Vec3> entry : ghosts.entrySet()) {
				VoicechatConnection to = entry.getKey().equals(who) ? null : api.getConnectionOf(entry.getKey());
				if (to == null || to.getPlayer().getServerLevel().getServerLevel() != level.getServerLevel()
					|| !near(entry.getValue(), at, range) || near(to.getPlayer().getPosition(), at, range)) {
					continue;
				}
				if (sound == null) {
					sound = packet.entitySoundPacketBuilder().entityUuid(who).whispering(packet.isWhispering()).distance(range).build();
				}
				api.sendEntitySoundPacketTo(to, sound);
			}
			return;
		}
		if (!event.cancel() || !VoiceMasks.changer(who)) {
			return;
		}
		Position at = api.createPosition(ghost.x, ghost.y + GHOST_MOUTH, ghost.z);
		LocationalSoundPacket sound = packet.locationalSoundPacketBuilder().position(at).distance(range).build();
		Set<UUID> sent = new HashSet<>();
		sent.add(who);
		// those whose ears are near: other ghosts by their ghosts, the rest by their bodies
		for (Map.Entry<UUID, Vec3> entry : ghosts.entrySet()) {
			if (sent.add(entry.getKey()) && near(entry.getValue(), at, range)) {
				VoicechatConnection to = api.getConnectionOf(entry.getKey());
				if (to != null && to.getPlayer().getServerLevel().getServerLevel() == level.getServerLevel()) {
					api.sendLocationalSoundPacketTo(to, sound);
				}
			}
		}
		for (ServerPlayer near : api.getPlayersInRange(level, at, range, player -> !sent.contains(player.getUuid()))) {
			VoicechatConnection to = api.getConnectionOf(near.getUuid());
			if (to != null) {
				api.sendLocationalSoundPacketTo(to, sound);
			}
		}
	}

	/** What {@code who} said ({@code packet}), measured against their own normal voice, is a noise mobs hear ({@link Hearing#spoke}). */
	private static void overheard(VoicechatServerApi api, UUID who, MicrophonePacket packet) {
		byte[] opus = packet.getOpusEncodedData();
		if (opus == null || opus.length == 0) {
			// (the end of what they said: the next words start the decoder afresh)
			OpusDecoder decoder = DECODERS.get(who);
			if (decoder != null) {
				synchronized (decoder) {
					decoder.resetState();
				}
			}
			return;
		}
		OpusDecoder decoder = DECODERS.computeIfAbsent(who, id -> api.createDecoder());
		if (decoder == null) {
			return;
		}
		short[] pcm;
		synchronized (decoder) {
			if (decoder.isClosed()) {
				DECODERS.remove(who);
				return;
			}
			pcm = decoder.decode(opus);
		}
		double db = loudness(pcm);
		if (db < SILENCE_DB) {
			return;
		}
		boolean whisper = packet.isWhispering();
		double scale = MICS.computeIfAbsent(who, id -> new Mic()).scale(db, whisper);
		int how = whisper ? Hearing.WHISPER : scale >= SHOUT_FROM ? Hearing.SHOUT : Hearing.TALK;
		Hearing.spoke(who, VOICE_REACH * scale * (whisper ? WHISPER_SHARE : 1.0), how);
	}

	/** The RMS level of a frame of 16-bit audio, in dBFS (-96 to 0). */
	private static double loudness(short @org.jspecify.annotations.Nullable [] pcm) {
		if (pcm == null || pcm.length == 0) {
			return -96.0;
		}
		double sum = 0.0;
		for (short s : pcm) {
			double v = s / 32768.0;
			sum += v * v;
		}
		double rms = Math.sqrt(sum / pcm.length);
		return rms <= 1.0E-5 ? -96.0 : Math.max(-96.0, Math.min(0.0, 20.0 * Math.log10(rms)));
	}

	private static boolean near(Vec3 a, Position b, float range) {
		return a.distanceToSqr(b.getX(), b.getY(), b.getZ()) <= (double)range * range;
	}

	private static boolean near(Position a, Position b, float range) {
		double x = a.getX() - b.getX(), y = a.getY() - b.getY(), z = a.getZ() - b.getZ();
		return x * x + y * y + z * z <= (double)range * range;
	}

	/** A voice reaching a giant by proximity: sent again, carrying as far as his height asks. */
	private static void heard(EntitySoundPacketEvent event) {
		VoicechatConnection to = event.getReceiverConnection();
		// (only proximity: what this sends again comes back as a plugin's, and is let through)
		if (to == null || !SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource())) {
			return;
		}
		float grown = VoiceMasks.grown(to.getPlayer().getUuid());
		if (grown < HEAR_FROM) {
			return;
		}
		EntitySoundPacket packet = event.getPacket();
		if (!event.cancel()) {
			return;
		}
		event.getVoicechat().sendEntitySoundPacketTo(to, packet.entitySoundPacketBuilder()
			.entityUuid(packet.getEntityUuid())
			.whispering(packet.isWhispering())
			.distance(packet.getDistance() + HEAR_PER_BLOCK * grown)
			.build());
	}
}
