package com.jaymospowers.power.zfighter.pod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Landing legs ({@link PodModules.Kind#LEGS}). With them fitted the whole pod comes down and stands. Without them only the
 * cabin does: the pod comes down under a chute ({@link #chute}), and where it lands the fins, the bell, the tank and the mast
 * are torn off and lie about as worn parts, the tank empty, the hammer's finish gone: the pod must be mended (the parts put
 * back or replaced, then hammered to a finish) before it flies again. The cabin (keel, hull plates and canopy) and every module
 * stay on.
 */
public final class PodLegs {
	// ---- tuning ----
	/** The chute slows the pod's fall to at most this (blocks a tick). */
	public static final double CHUTE_SPEED = 0.3;
	/** The chute's size (blocks) over the pod, and how high above it. */
	static final double CHUTE_RADIUS = 1.6, CHUTE_HEIGHT = 3.4;
	/** Particles in the chute's rim a tick. */
	static final int CHUTE_RIM = 8;

	private PodLegs() {
	}

	/** Whether {@code pod} lands whole. */
	public static boolean lands(PodEntity pod) {
		return PodModules.has(pod, PodModules.Kind.LEGS);
	}

	/** The chute over a pod coming down at {@code at}: a rim of cloud and a cap. */
	public static void chute(ServerLevel level, Vec3 at) {
		for (int i = 0; i < CHUTE_RIM; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.CLOUD, at.x + Math.cos(angle) * CHUTE_RADIUS, at.y + CHUTE_HEIGHT, at.z + Math.sin(angle) * CHUTE_RADIUS, 1,
				0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + CHUTE_HEIGHT + 0.4, at.z, 2, 0.5, 0.1, 0.5, 0.0);
	}

	/**
	 * A pod that has just come down: with no legs, the parts outside the cabin tear off (dropped worn beside it) and the tank is
	 * empty. True when it was torn.
	 */
	public static boolean tearOff(ServerLevel level, PodEntity pod, @Nullable ServerPlayer pilot) {
		if (lands(pod)) {
			return false;
		}
		Vec3 at = pod.position().add(0.0, 0.5, 0.0);
		int torn = 0;
		for (PodPart part : PodPart.ORDER) {
			if (part == PodPart.KEEL || part == PodPart.PLATE || part == PodPart.CANOPY) {
				continue;
			}
			for (int i = 0; i < part.count; i++) {
				if (!pod.has(part, i)) {
					continue;
				}
				boolean[] was = new boolean[1];
				int bit = part.bit(i);
				int forged = pod.forgedAt(bit);
				pod.detach(bit, was);
				Block.popResource(level, pod.blockPosition().above(), PodParts.stackOf(part, true, 0, forged, PodModules.FEWEST_SOCKETS));
				torn++;
			}
		}
		pod.setFuel(0);
		pod.setFinished(false);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 16, 0.6, 0.5, 0.6, 0.04);
		if (pilot != null) {
			pilot.sendOverlayMessage(Component.translatable(torn > 0 ? "message.jaymospowers.pod.no_legs" : "message.jaymospowers.pod.no_legs_bare")
				.withStyle(ChatFormatting.RED));
		}
		return true;
	}
}
