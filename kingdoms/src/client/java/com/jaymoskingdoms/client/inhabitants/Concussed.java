package com.jaymoskingdoms.client.inhabitants;

import com.jaymoskingdoms.inhabitants.ImpalerGear;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Concussion as the one who has it feels it ({@link ImpalerGear}): the eyes lag behind the head (the mouse turns as the
 * cinematic camera does, ConcussionMouseMixin), the world goes muffled (sounds that start while it lasts play at
 * {@link #MUFFLED} of their volume, ConcussionSoundMixin), and the ears ring as it sets in.
 */
public final class Concussed {
	/** Sounds that start while concussed play at this share of their volume. */
	public static final float MUFFLED = 0.35F;

	private static boolean was;

	private Concussed() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Concussed::tick);
	}

	/** Whether the local player is concussed now. */
	public static boolean now() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && ImpalerGear.CONCUSSION != null && player.hasEffect(ImpalerGear.CONCUSSION);
	}

	/** Whether this sound rings through the muffle: the ringing itself, and the game's own menus and music. */
	public static boolean heardClearly(SoundInstance sound) {
		SoundSource source = sound.getSource();
		return source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.UI
			|| ImpalerGear.CONCUSSION_BUZZ != null && sound.getIdentifier().equals(ImpalerGear.CONCUSSION_BUZZ.location());
	}

	private static void tick(Minecraft minecraft) {
		boolean concussed = now();
		if (concussed && !was) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ImpalerGear.CONCUSSION_BUZZ, 1.0F, 0.6F));
		}
		was = concussed;
	}
}
