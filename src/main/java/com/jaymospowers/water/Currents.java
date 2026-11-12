package com.jaymospowers.water;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rivers that flow (Andy's TFC Hardrock picks, after Dynamic Waters and Realistic Water Engine, our own code).
 * <ul>
 * <li><b>Currents</b>: running water (waterfalls, springs, any flowing block) carries what floats in it
 * downstream, {@link #CURRENT} on top of the game's own push, half again in rain and twice in a storm: swimmers, boats
 * (Shippy Ships' and our ships too, anything standing on the flow), items and mobs. Each side moves what it owns: the
 * server mobs, items and empty boats, a player's own client the player and the boat they steer ({@code CurrentsClient}).</li>
 * <li><b>Splashes</b>: something big ({@link #SPLASH_WIDTH} wide or more: a giant, a great whale breaching, a ship) coming
 * down into water sends out a ring of waves {@link #WAVE_RADIUS} across that rocks boats and pushes swimmers away.</li>
 * </ul>
 * Rivers freezing is Kitchen's Seasons: winter cools temperate land enough for still water to freeze over.
 * <p>The big rivers are Dynamic Waters' (ports/dynamicwaters): their channels are still water that Dynamic Waters pushes
 * along itself (its own current, with its rain and storm boosts) and where it floats its ice floes, so this only adds to
 * water that is really flowing.</p>
 */
public final class Currents {
	/** Extra push a tick along the flow (blocks a tick squared), and how much more in rain and in a thunderstorm. */
	public static final double CURRENT = 0.012, RAIN = 1.5, STORM = 2.0;
	/** How far round each player the server moves things (blocks), and how often (ticks). */
	static final double RANGE = 48.0;
	static final int EVERY = 2;
	/** How wide a thing must be to splash, how fast it must come down, the ring's reach and push. */
	static final float SPLASH_WIDTH = 1.8F;
	static final double SPLASH_FALL = -0.3, WAVE_RADIUS = 10.0, WAVE_PUSH = 0.7, WAVE_LIFT = 0.25;

	private static final Set<UUID> WET = new HashSet<>();

	private Currents() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Currents::tick);
	}

	/** The flow carrying {@code e} (0 if none): the water it's in, or just under it for a boat on the top. */
	public static Vec3 flow(Level level, Entity e) {
		BlockPos pos = e.blockPosition();
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.is(FluidTags.WATER)) {
			pos = pos.below();
			fluid = level.getFluidState(pos);
			if (!fluid.is(FluidTags.WATER)) {
				return Vec3.ZERO;
			}
		}
		if (fluid.isSource()) {
			return Vec3.ZERO;
		}
		Vec3 flow = fluid.getFlow(level, pos);
		return new Vec3(flow.x, 0.0, flow.z);
	}

	/** Carries {@code e} downstream for {@code ticks} ticks' worth. */
	public static void carry(Level level, Entity e, int ticks) {
		if (e.isNoGravity() || e.isSpectator() || e.onGround() && !e.isInWater()) {
			return;
		}
		Vec3 flow = flow(level, e);
		if (flow.lengthSqr() < 1.0E-6) {
			return;
		}
		BlockPos pos = e.blockPosition();
		double strength = CURRENT * ticks * (level.isThundering() && level.isRainingAt(pos) ? STORM : level.isRainingAt(pos) ? RAIN : 1.0);
		e.push(flow.normalize().scale(strength));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Set<UUID> wetNow = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			Set<Entity> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (Entity e : level.getEntities((Entity) null, player.getBoundingBox().inflate(RANGE), x -> x.isAlive() && x.isInWater() || x instanceof AbstractBoat)) {
					if (!seen.add(e)) {
						continue;
					}
					if (!(e instanceof Player) && !(e.getControllingPassenger() instanceof Player)) {
						carry(level, e, EVERY);
					}
					if (e.getBbWidth() >= SPLASH_WIDTH && e.isInWater()) {
						wetNow.add(e.getUUID());
						if (!WET.contains(e.getUUID()) && e.getDeltaMovement().y <= SPLASH_FALL) {
							splash(level, e);
						}
					}
				}
			}
		}
		WET.clear();
		WET.addAll(wetNow);
	}

	/** A ring of waves out from where {@code big} came down. */
	static void splash(ServerLevel level, Entity big) {
		double x = big.getX(), y = big.getY() + 0.2, z = big.getZ();
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2.0 / 48.0;
			double r = big.getBbWidth() * 0.6;
			level.sendParticles(ParticleTypes.SPLASH, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 3, 0.1, 0.1, 0.1, 0.2);
			level.sendParticles(ParticleTypes.BUBBLE_POP, x + Math.cos(a) * r * 2.0, y, z + Math.sin(a) * r * 2.0, 1, 0.2, 0.0, 0.2, 0.05);
		}
		level.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 2.0F, 0.6F);
		AABB box = big.getBoundingBox().inflate(WAVE_RADIUS, 3.0, WAVE_RADIUS);
		for (Entity e : level.getEntities(big, box, x2 -> x2.isInWater() || x2 instanceof AbstractBoat)) {
			double dx = e.getX() - x, dz = e.getZ() - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > WAVE_RADIUS || d < 1.0E-3) {
				continue;
			}
			double f = (1.0 - d / WAVE_RADIUS) * WAVE_PUSH;
			e.push(dx / d * f, WAVE_LIFT * (1.0 - d / WAVE_RADIUS), dz / d * f);
			e.needsSync = true;
		}
	}
}
