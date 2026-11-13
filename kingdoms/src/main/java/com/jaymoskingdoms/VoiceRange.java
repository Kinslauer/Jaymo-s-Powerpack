package com.jaymoskingdoms;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
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

	@Override
	public String getPluginId() {
		return JaymosKingdoms.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoiceDistanceEvent.class, VoiceRange::speaking);
		registration.registerEvent(EntitySoundPacketEvent.class, VoiceRange::heard);
		registration.registerEvent(MicrophonePacketEvent.class, VoiceRange::spoken);
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
