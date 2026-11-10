package com.jaymoskingdoms;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;

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
 * </ul>
 */
public final class VoiceRange implements VoicechatPlugin {
	/** A titan's voice: this much further (blocks), plus this much for every block it stands taller than a man. */
	static final float TITAN_RANGE = 16.0F, SPEAK_PER_BLOCK = 2.0F;
	/** A giant listener: taller than a man by this much or more (blocks), he hears this much further for every block. */
	static final float HEAR_FROM = 1.0F, HEAR_PER_BLOCK = 1.4F;

	@Override
	public String getPluginId() {
		return JaymosKingdoms.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoiceDistanceEvent.class, VoiceRange::speaking);
		registration.registerEvent(EntitySoundPacketEvent.class, VoiceRange::heard);
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
