package com.jaymospowers.client.sound;

import com.jaymospowers.core.Soundscape;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The player's own one-shot accents over a {@link PowerLoop}: {@link Soundscape} layers, heard only on this side. */
public final class Accents {
	private Accents() {
	}

	/** Plays each layer (one variant each, jittered, by its chance) at the player, for them alone. */
	public static void play(LocalPlayer player, Soundscape.Layer... layers) {
		RandomSource random = player.getRandom();
		for (Soundscape.Layer layer : layers) {
			if (layer.chance() < 1.0F && random.nextFloat() >= layer.chance()) {
				continue;
			}
			player.level().playLocalSound(player, layer.pick(random), SoundSource.PLAYERS, layer.volume(random), layer.pitch(random));
		}
	}

	/** {@link #play}, when {@code key} is {@link Soundscape#due} (every {@code min}..{@code max} ticks). */
	public static void every(LocalPlayer player, String key, int min, int max, Soundscape.Layer... layers) {
		if (Soundscape.due(player, key, min, max)) {
			play(player, layers);
		}
	}
}
