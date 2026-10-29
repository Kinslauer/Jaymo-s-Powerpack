package com.jaymoskingdoms.client;

import com.jaymoskingdoms.VoiceMasks;
import org.jspecify.annotations.Nullable;
import org.sawiq.svvoicechanger.client.api.VoiceChangerApi;
import org.sawiq.svvoicechanger.client.api.VoiceOverride;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * (0.7.330) {@link VoiceMasks} on this client: the voice the server says ours is to be turned into, pushed on Simple Voice
 * Voice Changer as an override (its API; the player's own voice comes back the moment it's released). Only called with
 * that mod loaded ({@link VoiceMaskClient}), so its classes are never touched without it.
 */
public final class VoiceChangerCompat {
	static final String OWNER = "jaymoskingdoms:voice_mask";
	/** Override priorities (the voice changer's scale: 100-499 equipment, 500+ what you can't opt out of). */
	static final int DISGUISE_PRIORITY = 300, CURSE_PRIORITY = 600;
	/** A werewolf: deep, big and growling. */
	static final double BEAST_PITCH = 0.7, BEAST_FORMANT = 0.75, BEAST_GROWL = 0.7;
	/** A Great Ape: deeper still, a huge chest. */
	static final double APE_PITCH = 0.6, APE_FORMANT = 0.6, APE_GROWL = 0.4, APE_REVERB = 0.25;
	/** A goblin: high and small. */
	static final double GOBLIN_PITCH = 1.35, GOBLIN_FORMANT = 1.3, GOBLIN_GROWL = 0.15;
	/** Behind a mask: a little lower, muffled behind cloth. */
	static final double MASKED_PITCH = 0.88, MASKED_FORMANT = 0.92, MASKED_HIGH_EQ = -6.0;

	private static @Nullable VoiceOverride current;

	private VoiceChangerCompat() {
	}

	static void apply(VoiceMasks.Mask mask) {
		VoiceChangerApi api = VoiceChangerApi.get().orElse(null);
		if (api == null) {
			return;
		}
		if (current != null) {
			current.release();
			current = null;
		}
		if (mask == VoiceMasks.Mask.NONE) {
			api.releaseOverrides(OWNER);
			return;
		}
		VoiceProfile profile = switch (mask) {
			case BEAST -> base(api, "demon").with(VoiceParameter.PITCH, BEAST_PITCH).with(VoiceParameter.FORMANT, BEAST_FORMANT)
				.with(VoiceParameter.GROWL, BEAST_GROWL);
			case APE -> base(api, "titan").with(VoiceParameter.PITCH, APE_PITCH).with(VoiceParameter.FORMANT, APE_FORMANT)
				.with(VoiceParameter.GROWL, APE_GROWL).with(VoiceParameter.REVERB_MIX, APE_REVERB);
			case GOBLIN -> base(api, "kid").with(VoiceParameter.PITCH, GOBLIN_PITCH).with(VoiceParameter.FORMANT, GOBLIN_FORMANT)
				.with(VoiceParameter.GROWL, GOBLIN_GROWL);
			default -> VoiceProfile.builder().set(VoiceParameter.MIX, 1.0).set(VoiceParameter.PITCH, MASKED_PITCH)
				.set(VoiceParameter.FORMANT, MASKED_FORMANT).set(VoiceParameter.HIGH_EQ, MASKED_HIGH_EQ).build();
		};
		boolean curse = mask == VoiceMasks.Mask.BEAST || mask == VoiceMasks.Mask.APE;
		VoiceOverride.Builder request = VoiceOverride.request(OWNER, curse ? CURSE_PRIORITY : DISGUISE_PRIORITY).profile(profile);
		if (curse) {
			request.forceEnabled();
		}
		current = api.pushOverride(request.build());
	}

	/** A built-in voice to start from, or a plain full-strength one if the mod has dropped it. */
	private static VoiceProfile base(VoiceChangerApi api, String id) {
		return api.builtInProfile(id).orElseGet(() -> VoiceProfile.builder().set(VoiceParameter.MIX, 1.0).build());
	}
}
