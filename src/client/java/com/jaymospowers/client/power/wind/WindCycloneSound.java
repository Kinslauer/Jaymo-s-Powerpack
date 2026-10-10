package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindCyclone;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** A Cyclone's roar: the elytra's rushing wind, low and loud, swelling in and dying away when it ends. */
final class WindCycloneSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 1.4F, PITCH = 0.5F, PITCH_WOBBLE = 0.06F;
	private static final float SWELL = 0.2F, FADE = 0.12F;
	private final Player player;
	private float level;
	private int age;

	WindCycloneSound(Player player) {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01F;
		this.pitch = PITCH;
		this.relative = false;
		this.x = player.getX();
		this.y = player.getY() + 1.0;
		this.z = player.getZ();
	}

	@Override
	public void tick() {
		this.age++;
		boolean active = !this.player.isRemoved() && WindCyclone.isActive(this.player);
		this.level = active ? Math.min(1.0F, this.level + SWELL) : this.level - FADE;
		if (this.level <= 0.0F) {
			this.stop();
			return;
		}
		this.x = this.player.getX();
		this.y = this.player.getY() + 1.0;
		this.z = this.player.getZ();
		this.volume = Math.max(0.01F, VOLUME * this.level);
		// the howl rises and falls as the column turns
		this.pitch = PITCH + PITCH_WOBBLE * Mth.sin(this.age * 0.21F) + 0.1F * this.level;
	}
}
