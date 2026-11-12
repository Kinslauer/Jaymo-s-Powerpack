package com.jaymoskingdoms.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A held world sound on this client (a blizzard's howl, a quake's rumble) as a looping bed at the camera that eases
 * toward the volume and pitch it's asked for each tick, with a slow drift so it never sits still, and fades out when
 * it's no longer wanted. Kingdoms' own take on Jaymo's Powers' {@code PowerLoop}, for weather rather than a player.
 */
public final class WorldLoop extends AbstractTickableSoundInstance {
	/** How fast volume and pitch ease (share a tick), and the fade once unwanted. */
	public static final float EASE = 0.06F, FADE = 0.05F;
	/** The drift: a slow wobble of this much volume share and pitch. */
	public static final float DRIFT_VOLUME = 0.25F, DRIFT_PITCH = 0.05F;
	/** Under this volume a fading loop stops. */
	public static final float SILENT = 0.005F;

	private static final Map<String, WorldLoop> LOOPS = new HashMap<>();

	private final DoubleSupplier volumeOf, pitchOf;
	private final float phase;
	private float smoothVolume, smoothPitch;
	private int age;

	private WorldLoop(SoundEvent sound, SoundSource source, DoubleSupplier volumeOf, DoubleSupplier pitchOf) {
		super(sound, source, SoundInstance.createUnseededRandom());
		this.volumeOf = volumeOf;
		this.pitchOf = pitchOf;
		this.phase = (float)(Math.random() * Math.PI * 2.0);
		this.looping = true;
		this.delay = 0;
		this.volume = SILENT;
		this.smoothPitch = (float)pitchOf.getAsDouble();
		this.pitch = this.smoothPitch;
		this.relative = false;
		this.follow();
	}

	/**
	 * Keeps the loop called {@code key} going while its volume is above silence (call every client tick); restarted if
	 * the engine dropped it.
	 */
	public static void keep(String key, SoundEvent sound, SoundSource source, DoubleSupplier volumeOf, DoubleSupplier pitchOf) {
		if (volumeOf.getAsDouble() <= SILENT) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		WorldLoop loop = LOOPS.get(key);
		if (loop == null || loop.isStopped() || loop.age > 20 && !minecraft.getSoundManager().isActive(loop)) {
			if (loop != null && !loop.isStopped()) {
				minecraft.getSoundManager().stop(loop);
			}
			loop = new WorldLoop(sound, source, volumeOf, pitchOf);
			LOOPS.put(key, loop);
			minecraft.getSoundManager().play(loop);
		}
	}

	private void follow() {
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		this.x = eye.x;
		this.y = eye.y;
		this.z = eye.z;
	}

	@Override
	public void tick() {
		this.age++;
		if (Minecraft.getInstance().level == null) {
			this.stop();
			return;
		}
		this.follow();
		float want = (float)Math.max(0.0, this.volumeOf.getAsDouble());
		boolean on = want > SILENT;
		float drift = Mth.sin(this.age * 0.05F + this.phase) * 0.6F + Mth.sin(this.age * 0.017F + this.phase * 2.0F) * 0.4F;
		want *= 1.0F + DRIFT_VOLUME * drift;
		this.smoothVolume += (want - this.smoothVolume) * (on ? EASE : FADE);
		this.smoothPitch += ((float)this.pitchOf.getAsDouble() - this.smoothPitch) * EASE;
		this.volume = Math.max(SILENT, this.smoothVolume);
		this.pitch = Math.max(0.5F, this.smoothPitch * (1.0F + DRIFT_PITCH * drift));
		if (!on && this.smoothVolume < SILENT * 2.0F) {
			this.stop();
		}
	}
}
