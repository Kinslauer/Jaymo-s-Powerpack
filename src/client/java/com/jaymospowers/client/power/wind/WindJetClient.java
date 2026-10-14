package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindJet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Gale Stream as everyone in view sees it ({@link WindJet#JETTING}): pale wisps driven hard out of the hands along
 * where the player looks, breeze whorls turning along it, and a puff of cloud where it breaks on a wall.
 */
final class WindJetClient {
	/** How fast its cloud leaves the hands (blocks/tick) and how much it wanders; how many a tick. */
	private static final double SPEED = 0.9, WANDER = 0.06;
	private static final int WISPS = 7;
	/** How far ahead of the eyes it starts (blocks); your own in first person. */
	private static final double OUT = 0.9, OWN_OUT = 1.6;

	private WindJetClient() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			if (!WindJet.isActive(player)) {
				continue;
			}
			Vec3 look = player.getLookAngle();
			// your own starts a little further out, so it doesn't fill your view
			double out = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() ? OWN_OUT : OUT;
			Vec3 hands = player.getEyePosition().add(look.scale(out)).add(0.0, -0.25, 0.0);
			HitResult hit = level.clip(new ClipContext(hands, hands.add(look.scale(WindJet.RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			double reach = hit.getType() == HitResult.Type.MISS ? WindJet.RANGE : hit.getLocation().distanceTo(hands);
			// wisps out of the hands, and all along it, driven the way it blows
			for (int i = 0; i < WISPS; i++) {
				double t = i == 0 ? 0.0 : random.nextDouble() * reach;
				double r = (WindJet.MOUTH + t * WindJet.SPREAD) * 0.6;
				Vec3 p = hands.add(look.scale(t));
				Vec3 v = look.scale(SPEED * (0.8 + random.nextDouble() * 0.4)).add(random.nextGaussian() * WANDER, random.nextGaussian() * WANDER,
					random.nextGaussian() * WANDER);
				level.addParticle(ParticleTypes.WHITE_SMOKE, p.x + random.nextGaussian() * r, p.y + random.nextGaussian() * r,
					p.z + random.nextGaussian() * r, v.x, v.y, v.z);
			}
			// whorls along it, and the wind breaking on what stops it
			if (player.tickCount % 2 == 0) {
				double t = 2.0 + random.nextDouble() * Math.max(0.0, reach - 2.0);
				double r = WindJet.MOUTH + t * WindJet.SPREAD;
				Vec3 p = hands.add(look.scale(t));
				level.addParticle(ParticleTypes.SMALL_GUST, p.x + random.nextGaussian() * r * 0.5, p.y + random.nextGaussian() * r * 0.5,
					p.z + random.nextGaussian() * r * 0.5, 0.0, 0.0, 0.0);
			}
			if (hit.getType() != HitResult.Type.MISS && player.tickCount % 3 == 0) {
				Vec3 p = hit.getLocation().subtract(look.scale(0.3));
				level.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08);
			}
		}
	}
}
