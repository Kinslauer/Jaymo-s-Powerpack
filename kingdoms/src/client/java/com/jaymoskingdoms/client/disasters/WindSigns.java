package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.sailing.Winds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The wind you can see (Andy's sea-life pick list, "Wind on show"): the same wind that fills a ship's sails
 * ({@link Winds}) carries smoke, ash and falling leaves along with it wherever they're under open sky
 * ({@code WindDriftMixin} reads {@link #pushX}/{@link #pushZ}), and in a fresh wind or harder, whitecaps break on the sea
 * round the camera. Drawn by each client alone, from the wind the server already syncs.
 */
public final class WindSigns {
	/** How far (blocks a tick) smoke drifts for each unit of the wind's strength; falling leaves this share of it. */
	static final double SMOKE_DRIFT = 0.06;
	public static final double LEAF_SHARE = 0.75;
	/** Whitecaps: from this strength of wind (Winds' "fresh"), this many looked for a tick at the most, this near and far. */
	static final float WHITECAPS_FROM = 0.62F, WHITECAPS_HARDEST = 1.2F;
	static final int WHITECAPS = 6, CAP_NEAR = 10, CAP_FAR = 56;

	/** This tick's drift of smoke under open sky (blocks), along the wind. */
	public static volatile double pushX, pushZ;

	private WindSigns() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WindSigns::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || level.dimension() != Level.OVERWORLD) {
			pushX = pushZ = 0.0;
			return;
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		float strength = Winds.clientStrength(level, eye.x, eye.z);
		double push = SMOKE_DRIFT * strength;
		pushX = Math.cos(Storms.clientWindAngle) * push;
		pushZ = Math.sin(Storms.clientWindAngle) * push;
		whitecaps(minecraft, level, eye, strength, level.getRandom());
	}

	/** White breaking crests on open water round the camera, blown along the wind. */
	private static void whitecaps(Minecraft minecraft, ClientLevel level, Vec3 eye, float strength, RandomSource random) {
		float blow = (strength - WHITECAPS_FROM) / (WHITECAPS_HARDEST - WHITECAPS_FROM);
		if (blow <= 0.0F || CalamitiesClient.sprites == null || Math.abs(eye.y - level.getSeaLevel()) > 24.0) {
			return;
		}
		int n = Math.round(WHITECAPS * Math.min(1.0F, blow) + random.nextFloat() - 0.5F);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		double wx = Math.cos(Storms.clientWindAngle), wz = Math.sin(Storms.clientWindAngle);
		for (int i = 0; i < n; i++) {
			double a = random.nextDouble() * Mth.TWO_PI, r = CAP_NEAR + random.nextDouble() * (CAP_FAR - CAP_NEAR);
			int x = Mth.floor(eye.x + Math.cos(a) * r), z = Mth.floor(eye.z + Math.sin(a) * r);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
			if (y != level.getSeaLevel() - 1 || !level.getFluidState(at.set(x, y, z)).is(FluidTags.WATER)
				|| !level.getFluidState(at.set(x, y - 3, z)).is(FluidTags.WATER)) {
				continue;
			}
			// a crest is a short white streak running downwind, a few flecks long
			double px = x + random.nextDouble(), pz = z + random.nextDouble(), speed = 0.05 + 0.05 * strength;
			int flecks = 3 + random.nextInt(4);
			for (int f = 0; f < flecks; f++) {
				float tone = random.nextFloat() * 0.05F;
				minecraft.particleEngine.add(new GustParticle(level, px - wx * f * 0.35, y + 1.02, pz - wz * f * 0.35, wx * speed, 0.0, wz * speed,
					CalamitiesClient.sprites.get(random), GustParticle.SAND, 0.09F + random.nextFloat() * 0.08F, 18 + random.nextInt(16),
					0.93F + tone, 0.96F + tone * 0.5F, 0.98F, 0.8F));
			}
		}
	}
}
