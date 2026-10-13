package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.MythicMounts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.56) The mounts of legend on the rider's side ({@link MythicMounts}): a ridden bat horse flies (the server takes its
 * gravity away while it's ridden): hold jump to climb, let go to glide down, forward to cruise; and nightmares trail
 * flame from their hooves.
 */
final class MythicMountsClient {
	/** Climb per tick while jump is held and the most it climbs; how fast it sinks gliding; its cruising speed (blocks/tick). */
	static final double LIFT = 0.08, MAX_CLIMB = 0.45, SINK = 0.025, MAX_SINK = 0.25, CRUISE = 0.55, THRUST = 0.06;
	/** How far off nightmares trail flame (blocks). */
	static final double FLAME_RANGE = 32.0;

	private MythicMountsClient() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MythicMountsClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.isPaused()) {
			return;
		}
		if (player.getVehicle() instanceof AbstractHorse horse && horse.isNoGravity() && MythicMounts.is(horse, Breed.BAT_HORSE)) {
			fly(minecraft, player, horse);
		}
		RandomSource random = level.getRandom();
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(FLAME_RANGE),
			h -> MythicMounts.is(h, Breed.NIGHTMARE))) {
			boolean moving = horse.getDeltaMovement().horizontalDistanceSqr() > 0.001;
			if (random.nextInt(moving ? 1 : 4) == 0) {
				level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, horse.getRandomX(0.6), horse.getY() + 0.1,
					horse.getRandomZ(0.6), 0.0, 0.02, 0.0);
			}
		}
	}

	private static void fly(Minecraft minecraft, LocalPlayer player, AbstractHorse horse) {
		Vec3 motion = horse.getDeltaMovement();
		double y = motion.y;
		if (minecraft.options.keyJump.isDown()) {
			y = Math.min(y + LIFT, MAX_CLIMB);
		} else if (!horse.onGround()) {
			y = Math.max(y - SINK, -MAX_SINK);
		}
		double x = motion.x, z = motion.z;
		if (!horse.onGround() && player.zza > 0.0F) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			x += -Mth.sin(yaw) * THRUST;
			z += Mth.cos(yaw) * THRUST;
			double speed = Math.sqrt(x * x + z * z);
			if (speed > CRUISE) {
				x *= CRUISE / speed;
				z *= CRUISE / speed;
			}
		}
		horse.setDeltaMovement(x, y, z);
	}
}
