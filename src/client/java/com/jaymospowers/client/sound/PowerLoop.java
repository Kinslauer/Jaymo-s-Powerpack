package com.jaymospowers.client.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * One layer of a held power's sound on the player's own side: a looping bed that follows the player and eases toward the
 * volume and pitch its {@link Mix} asks for each tick, with a slow drift on top so it never sits still. A power stacks
 * a few (a rush that follows speed, a low smoulder, a high whistle) and {@link #keep} keeps each one going while it's
 * wanted and lets it fade out when it isn't. Pairs with {@code core.Soundscape} for the one-shot accents.
 */
public final class PowerLoop extends AbstractTickableSoundInstance {
	/** How fast volume and pitch ease toward their target (share a tick), and the fade-out once unwanted. */
	public static final float EASE = 0.15F, FADE = 0.12F;
	/** The drift: a slow wobble of this much volume share and pitch, so a held loop breathes. */
	public static final float DRIFT_VOLUME = 0.12F, DRIFT_PITCH = 0.04F;
	/** Under this volume a fading loop stops. */
	public static final float SILENT = 0.005F;

	private static final Map<String, PowerLoop> LOOPS = new HashMap<>();

	/** What a loop should sound like this tick: its volume (0 = fade out) and pitch. */
	public interface Mix {
		float volume();

		float pitch();
	}

	private final LocalPlayer player;
	private final BooleanSupplier wanted;
	private final Mix mix;
	private final float phase;
	private float smoothVolume;
	private float smoothPitch;
	private int age;

	private PowerLoop(SoundEvent sound, SoundSource source, LocalPlayer player, BooleanSupplier wanted, Mix mix) {
		super(sound, source, SoundInstance.createUnseededRandom());
		this.player = player;
		this.wanted = wanted;
		this.mix = mix;
		this.phase = (float)(Math.random() * Math.PI * 2.0);
		this.looping = true;
		this.delay = 0;
		this.volume = SILENT;
		this.smoothPitch = mix.pitch();
		this.pitch = this.smoothPitch;
		this.relative = false;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}

	/**
	 * Keeps the loop called {@code key} playing while {@code wanted} holds (call every client tick while the power runs;
	 * the engine can drop a sound when too many play, so it's restarted if it went quiet).
	 */
	public static void keep(String key, SoundEvent sound, SoundSource source, LocalPlayer player, BooleanSupplier wanted, Mix mix) {
		if (!wanted.getAsBoolean()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		PowerLoop loop = LOOPS.get(key);
		if (loop == null || loop.isStopped() || loop.player != player || loop.age > 20 && !minecraft.getSoundManager().isActive(loop)) {
			if (loop != null && !loop.isStopped()) {
				minecraft.getSoundManager().stop(loop);
			}
			loop = new PowerLoop(sound, source, player, wanted, mix);
			LOOPS.put(key, loop);
			minecraft.getSoundManager().play(loop);
		}
	}

	@Override
	public void tick() {
		this.age++;
		if (this.player.isRemoved()) {
			this.stop();
			return;
		}
		this.x = this.player.getX();
		this.y = this.player.getY();
		this.z = this.player.getZ();
		boolean on = this.wanted.getAsBoolean();
		float target = on ? Math.max(0.0F, this.mix.volume()) : 0.0F;
		float drift = Mth.sin(this.age * 0.07F + this.phase) * 0.6F + Mth.sin(this.age * 0.023F + this.phase * 2.0F) * 0.4F;
		target *= 1.0F + DRIFT_VOLUME * drift;
		this.smoothVolume += (target - this.smoothVolume) * (on ? EASE : FADE);
		this.smoothPitch += (this.mix.pitch() - this.smoothPitch) * EASE;
		this.volume = Math.max(SILENT, this.smoothVolume);
		this.pitch = Math.max(0.5F, this.smoothPitch * (1.0F + DRIFT_PITCH * drift));
		if (!on && this.smoothVolume < SILENT) {
			this.stop();
		}
	}
}
