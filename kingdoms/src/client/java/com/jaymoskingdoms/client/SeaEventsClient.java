package com.jaymoskingdoms.client;

import com.jaymoskingdoms.sailing.SeaEvents;
import com.jaymoskingdoms.sailing.Ship;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The client's side of the sea's events ({@link SeaEvents}): keeps its copy moving between syncs, thickens the fog in a
 * fog bank ({@code SeaFogMixin}), turns a whirlpool's foam round its eye, and lights the water of a glowing tide where
 * anything moves through it.
 */
public final class SeaEventsClient {
	/** Inside a fog bank you see this far (blocks), in this grey. */
	public static final float FOG_SIGHT = 24.0F;
	public static final float[] FOG_COLOUR = {0.62F, 0.66F, 0.70F};
	/** How fast the fog closes in and lifts (per tick). */
	static final float EASE = 0.01F;
	/** Whirlpools and glowing tides are drawn within this distance; foam flecks per tick; glow motes per tick. */
	static final double VIEW = 96.0;
	static final int FOAM = 10, GLOW = 6;

	/** The fog bank's thickness where the camera is (eased), 0..1. */
	public static float fog;

	private SeaEventsClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SeaEvents.Sync.TYPE, (payload, context) -> SeaEvents.CLIENT = new ArrayList<>(payload.seas()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SeaEvents.CLIENT = List.of();
			fog = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(SeaEventsClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		if (level.dimension() != Level.OVERWORLD) {
			fog = Math.max(0.0F, fog - EASE * 4.0F);
			return;
		}
		for (SeaEvents.Sea s : SeaEvents.CLIENT) {
			s.move();
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		// only near the water: climb a sea cliff and you're out of it
		float target = Math.abs(eye.y - level.getSeaLevel()) < 30.0 ? SeaEvents.fog(level, eye.x, eye.z) : 0.0F;
		fog = fog < target ? Math.min(target, fog + EASE) : Math.max(target, fog - EASE);
		RandomSource random = level.getRandom();
		for (SeaEvents.Sea s : SeaEvents.CLIENT) {
			double dx = s.x - eye.x, dz = s.z - eye.z;
			if (dx * dx + dz * dz > (VIEW + s.radius) * (VIEW + s.radius)) {
				continue;
			}
			if (s.kind == SeaEvents.Kind.WHIRLPOOL) {
				whirlpool(level, s, random);
			} else if (s.kind == SeaEvents.Kind.GLOW && level.isDarkOutside()) {
				glow(level, s, eye, random);
			}
		}
	}

	/** Foam turning round the eye, quickest near it, and the sea sucked down at its middle. */
	private static void whirlpool(ClientLevel level, SeaEvents.Sea s, RandomSource random) {
		float f = s.formed();
		int n = Math.round(FOAM * f);
		for (int i = 0; i < n; i++) {
			double r = 1.0 + Math.sqrt(random.nextDouble()) * (s.radius - 1.0), a = random.nextDouble() * Math.PI * 2.0;
			double x = s.x + Math.cos(a) * r, z = s.z + Math.sin(a) * r;
			double spin = 0.35 * (1.0 - r / s.radius) + 0.05, in = 0.06 * (1.0 - r / s.radius);
			double vx = -Math.sin(a) * spin - Math.cos(a) * in, vz = Math.cos(a) * spin - Math.sin(a) * in;
			level.addParticle(ParticleTypes.FISHING, x, s.y + 0.05, z, vx, 0.0, vz);
		}
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.CURRENT_DOWN, s.x + random.nextGaussian(), s.y - 0.5, s.z + random.nextGaussian(), 0.0, -0.2, 0.0);
			level.addParticle(ParticleTypes.SPLASH, s.x + random.nextGaussian() * 1.5, s.y + 0.1, s.z + random.nextGaussian() * 1.5, 0.0, 0.1, 0.0);
		}
	}

	/** The glowing tide: motes lighting on the water around you, and bright where a hull, an oar or a swimmer stirs it. */
	private static void glow(ClientLevel level, SeaEvents.Sea s, Vec3 eye, RandomSource random) {
		float f = s.at(eye.x, eye.z);
		if (f <= 0.0F) {
			return;
		}
		int n = Math.round(GLOW * f);
		for (int i = 0; i < n; i++) {
			double x = eye.x + (random.nextDouble() - 0.5) * 40.0, z = eye.z + (random.nextDouble() - 0.5) * 40.0;
			level.addParticle(ParticleTypes.GLOW, x, s.y + 0.02, z, 0.0, 0.005, 0.0);
		}
		if (random.nextInt(2) != 0) {
			return;
		}
		AABB box = new AABB(eye.x - 32.0, s.y - 3.0, eye.z - 32.0, eye.x + 32.0, s.y + 3.0, eye.z + 32.0);
		for (Entity e : level.getEntities((Entity)null, box, e -> e instanceof Ship || e.isInWater()
			|| e instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat)) {
			Vec3 v = e.getDeltaMovement();
			double speed = e instanceof Ship ship ? Math.max(v.horizontalDistance(), ship.position().subtract(ship.xo, ship.yo, ship.zo).horizontalDistance()) : v.horizontalDistance();
			if (speed < 0.03) {
				continue;
			}
			double width = e.getBbWidth() * 0.5;
			int wake = Mth.clamp((int)(speed * 20.0), 1, e instanceof Ship ? 12 : 4);
			for (int i = 0; i < wake; i++) {
				level.addParticle(ParticleTypes.GLOW, e.getX() + (random.nextDouble() - 0.5) * 2.0 * width, s.y + 0.05,
					e.getZ() + (random.nextDouble() - 0.5) * 2.0 * width, 0.0, 0.01, 0.0);
			}
		}
	}
}
