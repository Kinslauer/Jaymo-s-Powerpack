package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ScentTrails;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.332) The client's half of {@link ScentTrails}: the trail a werewolf has taken the scent of is drawn as faint specks
 * hanging in the air along the footsteps, only on their own screen (client-side particles). The older a footstep, the
 * fainter and sparser; the whole scent fades out over the time it was given.
 */
final class ScentClient {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Specks are only drawn this near (blocks); between footsteps further apart than {@link #GAP} the trail is broken (a teleport, a ride). */
	static final double DRAW_RANGE = 40.0, GAP = 16.0;
	/** The chance a speck appears each block of trail each check at its freshest, how often it's checked (ticks), and the most specks a check makes. */
	static final float SPECKS_PER_BLOCK = 0.35F;
	static final int EVERY = 3, MOST = 40;
	/** The scent thins out over its last ticks. */
	static final int FADE_OUT = 200;

	private static List<Vec3> points = List.of();
	private static List<Integer> ages = List.of();
	private static long received;
	private static int life;

	private ScentClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ScentTrails.Trail.TYPE, (payload, context) -> {
			points = payload.points();
			ages = payload.ages();
			life = payload.lifeTicks();
			received = context.client().level == null ? 0L : context.client().level.getGameTime();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			points = List.of();
			ages = List.of();
			life = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ScentClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || minecraft.isPaused() || points.size() < 2 || level.getGameTime() % EVERY != 0) {
			return;
		}
		long since = level.getGameTime() - received;
		float whole = Mth.clamp((life - since) / (float)FADE_OUT, 0.0F, 1.0F);
		if (whole <= 0.0F) {
			points = List.of();
			return;
		}
		RandomSource random = level.getRandom();
		int made = 0;
		for (int i = 0; i + 1 < points.size() && made < MOST; i++) {
			Vec3 a = points.get(i), b = points.get(i + 1);
			double length = a.distanceTo(b);
			if (length > GAP || a.distanceToSqr(player.position()) > DRAW_RANGE * DRAW_RANGE) {
				continue;
			}
			// (older footsteps are fainter)
			float age = (ages.get(i) + since + ages.get(i + 1) + since) / 2.0F;
			float fade = Mth.clamp(1.0F - age / ScentTrails.KEEP_TICKS, 0.0F, 1.0F) * whole;
			if (random.nextFloat() >= Math.min(1.0F, length * SPECKS_PER_BLOCK * fade)) {
				continue;
			}
			Vec3 at = a.lerp(b, random.nextDouble());
			level.addParticle(ParticleTypes.MYCELIUM, at.x + (random.nextDouble() - 0.5) * 0.4, at.y + 0.2 + random.nextDouble() * 0.5,
				at.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.01, 0.0);
			made++;
		}
	}
}
