package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Kinship;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;

/**
 * (0.7.323) Monster kinship, heard: a vampire or werewolf with another of their kind in sight ({@link Kinship#HEARD}) hears
 * a cold, slow beat: a low heartbeat for a vampire, a soft growl for a werewolf. Nearer is louder and a little quicker.
 * Nothing is drawn: no red pulse.
 */
public final class KinshipClient {
	/** The beat's period (ticks) at the range's edge and face to face. */
	static final float PERIOD_FAR = 80.0F, PERIOD_NEAR = 44.0F;
	/** A vampire's beat: volume at the edge and close, and pitch. */
	static final float BEAT_QUIET = 0.15F, BEAT_LOUD = 0.5F, BEAT_PITCH = 0.4F;
	/** A werewolf's growl: volume at the edge and close, and pitch (and its spread). */
	static final float GROWL_QUIET = 0.1F, GROWL_LOUD = 0.35F, GROWL_PITCH = 0.55F, GROWL_SPREAD = 0.1F;

	private static float phase;

	private KinshipClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(KinshipClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused()) {
			phase = 0.0F;
			return;
		}
		int kind = Kinship.kind(player);
		if (kind == 0) {
			phase = 0.0F;
			return;
		}
		float close = Kinship.closeness(player);
		phase += 1.0F / Mth.lerp(close, PERIOD_FAR, PERIOD_NEAR);
		if (phase < 1.0F) {
			return;
		}
		phase -= 1.0F;
		if (kind == Kinship.VAMPIRE_KIN) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, BEAT_PITCH, Mth.lerp(close, BEAT_QUIET, BEAT_LOUD)));
		} else {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(),
				GROWL_PITCH + player.getRandom().nextFloat() * GROWL_SPREAD, Mth.lerp(close, GROWL_QUIET, GROWL_LOUD)));
		}
	}
}
