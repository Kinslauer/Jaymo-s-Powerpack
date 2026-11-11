package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * The Great Ape's sprint, Andy: "the giant sprint is a lumbering haul ... they cover alot of ground but dont flail their
 * limbs". He used to lift off and skim over the ground; now he stays on his feet and hauls himself along the way he's
 * looking, fast, each step a long one ({@code ZFighterLineage#APE_HAUL_STRIDE}) so his legs keep their slow swing, the
 * ground shaking under every one, dust kicking up behind him. What's in his way the server smashes
 * ({@code GiantTrample#smash}).
 */
final class ZFighterApeHaul {
	/** His hauling speed once full grown (blocks a tick), and how quickly he gets up to it (a share of the way a tick). */
	static final double SPEED = 1.2, PICK_UP = 0.08;
	/** Only once he's this big (the scale he's grown to); smaller, his sprint is a sprint. */
	static final float MIN_SCALE = 8.0F;

	private ZFighterApeHaul() {
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (!ZFighterLineage.ape(player) || !player.isSprinting() || player.getScale() < MIN_SCALE || player.isInWater() || player.isPassenger()
			|| player.getAbilities().flying || !player.onGround()) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		Vec3 flat = new Vec3(motion.x, 0.0, motion.z).lerp(look.scale(SPEED), PICK_UP);
		player.setDeltaMovement(flat.x, motion.y, flat.z);
		if (player.tickCount % 2 == 0) {
			Vec3 at = player.position();
			double spread = player.getBbWidth() * 0.4;
			for (int i = 0; i < 3; i++) {
				player.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + (player.getRandom().nextDouble() - 0.5) * spread, at.y + 0.1,
					at.z + (player.getRandom().nextDouble() - 0.5) * spread, -flat.x * 0.05, 0.02, -flat.z * 0.05);
			}
		}
	}
}
