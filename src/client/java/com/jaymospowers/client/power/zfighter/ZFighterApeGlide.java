package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Great Ape's sprint: he lifts off his feet and skims along just over the ground at speed, the way he's looking,
 * dust kicking up under him. Jump while skimming and he leaps (his big, slow-falling jump).
 */
final class ZFighterApeGlide {
	/** His skimming speed (blocks a tick) and how quickly he gets up to it (a share of the way a tick). */
	static final double SPEED = 1.4, PICK_UP = 0.15;
	/** How high over the ground he skims, and how far down he still feels it under him (blocks). */
	static final double HOVER = 0.8, FEEL = 4.0;
	/** How hard he's held at that height. */
	static final double HOLD = 0.3;
	/** Only once he's this big (the scale he's grown to). */
	static final float MIN_SCALE = 8.0F;

	private ZFighterApeGlide() {
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (!ZFighterLineage.ape(player) || !player.isSprinting() || player.getScale() < MIN_SCALE || player.isInWater() || player.isPassenger()
			|| player.getAbilities().flying) {
			return;
		}
		Vec3 feet = player.position();
		HitResult under = player.level().clip(new ClipContext(feet.add(0.0, 0.5, 0.0), feet.subtract(0.0, FEEL, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.ANY, player));
		Vec3 motion = player.getDeltaMovement();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		Vec3 flat = new Vec3(motion.x, 0.0, motion.z).lerp(look.scale(SPEED), PICK_UP);
		double up = motion.y;
		if (under.getType() != HitResult.Type.MISS) {
			double height = feet.y - under.getLocation().y;
			if (minecraft.options.keyJump.isDown() && height < HOVER + 0.6 && motion.y < 0.3) {
				up = player.getAttributeValue(Attributes.JUMP_STRENGTH);
			} else if (up < 0.3) {
				up = Math.clamp((HOVER - height) * HOLD, -0.3, 0.4);
			}
			if (player.tickCount % 2 == 0) {
				Vec3 at = under.getLocation();
				double spread = player.getBbWidth() * 0.4;
				for (int i = 0; i < 3; i++) {
					player.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + (player.getRandom().nextDouble() - 0.5) * spread, at.y + 0.1,
						at.z + (player.getRandom().nextDouble() - 0.5) * spread, -flat.x * 0.05, 0.02, -flat.z * 0.05);
				}
			}
		}
		player.setDeltaMovement(flat.x, up, flat.z);
	}
}
