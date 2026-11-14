package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.equestrian.HorseKit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/** A harnessed horse's glide on its rider's side ({@link HorseKit}): its fall is capped, with a little wind off the wings. */
final class GlideHarnessClient {
	private GlideHarnessClient() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(GlideHarnessClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused() || !(player.getVehicle() instanceof AbstractHorse horse) || !HorseKit.harnessed(horse)
			|| horse.onGround() || horse.isInWater() || horse.isInLava()) {
			return;
		}
		Vec3 motion = horse.getDeltaMovement();
		if (motion.y < -HorseKit.GLIDE_SINK) {
			horse.setDeltaMovement(motion.x, -HorseKit.GLIDE_SINK, motion.z);
			if (horse.tickCount % 3 == 0) {
				horse.level().addParticle(ParticleTypes.CLOUD, horse.getRandomX(1.2), horse.getY() + 0.8, horse.getRandomZ(1.2), 0.0, 0.05, 0.0);
			}
		}
	}
}
