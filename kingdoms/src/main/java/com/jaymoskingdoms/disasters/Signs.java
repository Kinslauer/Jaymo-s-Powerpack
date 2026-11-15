package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Herds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Signs before it comes (medieval picks, nd-signs). A little before a foretold disaster arrives, and before the great ones
 * (a volcano waking, a wave on its way, the world's ending), the land round the place knows first, and nothing is said:
 * the herd animals bolt away from it ({@link Herds#dread}), villagers drop what they're doing and hide indoors as if the
 * bell had rung, storm bells ring ({@link Instruments}), and on every client near it the ground trembles faintly, the sky
 * darkens and something rumbles far off (a {@link Calamity#SIGN}, drawn by {@code CalamitiesClient} and {@code RedSky}).
 * The almanac and the court wizards have told of it for hours; this is the last warning.
 */
public final class Signs {
	/** The signs begin this long before a foretold disaster (ticks: two minutes); a sign's build-up (ticks). */
	static final int LEAD = 2400, GATHER = 200;
	/** How far round the place the signs reach (blocks); the great ones' reach further. */
	static final float RADIUS = 160.0F, GREAT_RADIUS = 260.0F;
	/** How often the animals near someone in the signs are set running (ticks), from how far round them (blocks). */
	static final int FLEE_EVERY = 60;
	static final double FLEE_RANGE = 40.0, HIDE_RANGE = 48.0;

	private Signs() {
	}

	/** The signs of a foretold {@code kind} coming at {@code at}. */
	static void before(ServerLevel level, BlockPos at, String kind) {
		boolean great = kind.equals("volcano") || kind.equals("tsunami") || kind.equals("hurricane") || kind.equals("quake");
		omen(level, Vec3.atCenterOf(at), great ? GREAT_RADIUS : RADIUS, LEAD + GATHER, great ? Magnitude.GREAT : Magnitude.MODERATE);
	}

	/** Signs round {@code at}, {@code radius} across, for {@code life} ticks, as strong as {@code magnitude}. */
	public static void omen(ServerLevel level, Vec3 at, float radius, int life, int magnitude) {
		Calamities.SERVER.add(new Calamity(Storms.nextId(), Calamity.SIGN, at.x, at.z, 0.0, 0.0, radius, 0, life, -1).magnitude(magnitude));
	}

	/** The animals near anyone in the signs run from where it's coming; the villagers there hide. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getGameTime() % FLEE_EVERY != 0) {
			return;
		}
		for (Calamity c : Calamities.SERVER) {
			if (c.kind != Calamity.SIGN || c.strength() < 0.2F) {
				continue;
			}
			Vec3 from = new Vec3(c.x, 0.0, c.z);
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator() || c.over(p.getX(), p.getZ()) <= 0.05F) {
					continue;
				}
				Vec3 spot = new Vec3(c.x, p.getY(), c.z);
				Herds.dread(level, p.position(), spot, FLEE_RANGE, FLEE_EVERY + 40);
				hide(level, p.position());
			}
		}
	}

	/** The villagers round {@code near} hide, as when the bell rings for a raid. */
	static void hide(ServerLevel level, Vec3 near) {
		long now = level.getGameTime();
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(near, near).inflate(HIDE_RANGE), v -> v.isAlive() && !v.isSleeping())) {
			if (v.getBrain().checkMemory(MemoryModuleType.HEARD_BELL_TIME, MemoryStatus.REGISTERED)) {
				v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
			}
		}
	}

	/** Whether the signs of something are on the land at (x, z) (the storm bell rings for them). */
	static boolean at(double x, double z) {
		for (Calamity c : Calamities.SERVER) {
			if (c.kind == Calamity.SIGN && c.over(x, z) > 0.1F) {
				return true;
			}
		}
		return false;
	}
}
