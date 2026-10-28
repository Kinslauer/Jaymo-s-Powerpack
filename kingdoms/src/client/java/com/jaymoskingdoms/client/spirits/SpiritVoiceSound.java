package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Voices;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.7.280) A spirit's spoken line, heard from the spirit itself: behind and above you, where its figure stands
 * ({@link SpiritArms}), and following you as you move. Andy: "the voices will fade if you are moving while they activate.
 * they should emit from behind you." A sound sent by the server stays where it was played, so a line said as you ran
 * was left behind; this one is moved to your back every tick.
 */
public final class SpiritVoiceSound extends AbstractTickableSoundInstance {
	/** Blocks behind the body and above the eye the voice comes from. */
	static final double BEHIND = 1.2, ABOVE = 0.4;

	private final LocalPlayer player;

	private SpiritVoiceSound(SoundEvent sound, LocalPlayer player) {
		super(sound, SoundSource.VOICE, SoundInstance.createUnseededRandom());
		this.player = player;
		this.volume = 1.0F;
		this.pitch = 1.0F;
		this.looping = false;
		this.delay = 0;
		this.relative = false;
		this.place();
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Voices.Spoken.TYPE, (payload, context) -> {
			Holder<SoundEvent> sound = Voices.of(payload.key());
			LocalPlayer player = context.client().player;
			if (sound != null && player != null) {
				context.client().getSoundManager().play(new SpiritVoiceSound(sound.value(), player));
			}
		});
	}

	private void place() {
		float yaw = this.player.yBodyRot * Mth.DEG_TO_RAD;
		// (the body faces -sin(yaw), cos(yaw); behind it is the other way)
		this.x = this.player.getX() + Mth.sin(yaw) * BEHIND;
		this.y = this.player.getEyeY() + ABOVE;
		this.z = this.player.getZ() - Mth.cos(yaw) * BEHIND;
	}

	@Override
	public void tick() {
		if (this.player.isRemoved() || this.player != Minecraft.getInstance().player) {
			this.stop();
			return;
		}
		this.place();
	}
}
