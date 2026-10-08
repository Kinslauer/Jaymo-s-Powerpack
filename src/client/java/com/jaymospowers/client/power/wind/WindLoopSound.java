package com.jaymospowers.client.power.wind;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The rushing-wind loop the flyer hears, louder and higher the faster they go (the elytra wind sound). */
final class WindLoopSound extends AbstractTickableSoundInstance {
	private final LocalPlayer player;
	private final WindFlightController flight;
	private float smoothed;

	WindLoopSound(LocalPlayer player, WindFlightController flight) {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.flight = flight;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01F;
		this.relative = false;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}

	@Override
	public void tick() {
		if (this.player.isRemoved() || !this.flight.state().flying() || this.flight.player() != this.player) {
			this.stop();
			return;
		}
		this.x = this.player.getX();
		this.y = this.player.getY();
		this.z = this.player.getZ();
		float speed = (float)this.flight.speedFraction();
		// a soft breeze while hovering, a roar at full cruise
		float target = this.flight.state().fast() ? 0.15F + 0.85F * speed : 0.06F + 0.3F * (float)Math.min(1.0, this.flight.velocity().length() / 0.6);
		this.smoothed += (target - this.smoothed) * 0.2F;
		this.volume = Math.max(0.01F, this.smoothed);
		this.pitch = 0.75F + 0.55F * speed;
	}
}
