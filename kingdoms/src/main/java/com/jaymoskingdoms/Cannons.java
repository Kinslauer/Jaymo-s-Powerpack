package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * (0.5.64) The richest kingdoms ({@link Muskets#ARM_TREASURY} emeralds or more) set {@link #FEWEST} to {@link #MOST}
 * {@link Cannon}s about their seat: on the archers' posts on the walls and towers first, then beside the gate (or, with
 * no keep, round the seat), each facing out. An archer at the post mans it; a cannon by the gate with no archer near
 * gets a gunner of its own, hired as any archer is. A kingdom that falls below {@link Muskets#KEEP_TREASURY} sells
 * them off ({@link Cannon#tick}).
 */
final class Cannons {
	/** How often the kingdoms' cannons are counted (ticks). */
	static final int CHECK = 1200;
	/** Each qualifying kingdom keeps this many to this many (the same number for the same kingdom). */
	static final int FEWEST = 2, MOST = 4;
	/** How far from the seat its cannons are counted (blocks). */
	static final double RANGE = 96.0;
	/** Two cannons are never set closer than this (blocks). */
	static final double APART = 3.0;
	/** A cannon with no archer of its kingdom this near gets a gunner of its own (blocks). */
	static final double GUNNER_REACH = 6.0;
	/** Beside the gate this far out; with no keep, round the seat this far (blocks). */
	static final int GATE_SIDE = 3, SEAT_RING = 8;

	private Cannons() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % CHECK == 0) {
				tick(server, level);
			}
		});
	}

	static void tick(MinecraftServer server, ServerLevel level) {
		for (Faction f : Factions.all(server)) {
			if (!Muskets.afford(f, Muskets.ARM_TREASURY) || !level.isLoaded(f.seat())) {
				continue;
			}
			List<Cannon> have = new ArrayList<>(level.getEntitiesOfClass(Cannon.class, new AABB(f.seat()).inflate(RANGE), c -> c.isAlive() && c.faction() == f.id()));
			int want = FEWEST + Math.floorMod(f.id() * 31 + 7, MOST - FEWEST + 1);
			for (BlockPos spot : spots(level, f)) {
				if (have.size() >= want) {
					break;
				}
				if (!level.isLoaded(spot) || have.stream().anyMatch(c -> c.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot)) < APART)) {
					continue;
				}
				double dx = spot.getX() - f.seat().getX(), dz = spot.getZ() - f.seat().getZ();
				float yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
				have.add(Cannon.place(level, f, spot, yaw));
				JaymosKingdoms.LOGGER.debug("{} set a cannon at {}", f.name(), spot);
			}
			for (Cannon c : have) {
				if (c.gunner() == null && c.crew(level, GUNNER_REACH) == null) {
					gunner(level, f, c);
				}
			}
		}
	}

	/** Where its cannons go, best first: its archers' posts, then beside its gate, or round its seat with no keep. */
	private static List<BlockPos> spots(ServerLevel level, Faction f) {
		List<BlockPos> spots = new ArrayList<>();
		Castle.Keep keep = Factions.keep(level.getServer(), f.id());
		if (keep != null) {
			List<BlockPos> posts = new ArrayList<>(keep.posts());
			// (the same posts for the same kingdom every time)
			posts.sort(Comparator.comparingInt(p -> Math.floorMod(p.hashCode() * 31 + f.id(), 1009)));
			spots.addAll(posts);
			BlockPos gate = keep.gate();
			for (int[] o : new int[][] {{GATE_SIDE, 0}, {-GATE_SIDE, 0}, {0, GATE_SIDE}, {0, -GATE_SIDE}}) {
				ground(level, gate.offset(o[0], 0, o[1]), spots);
			}
		} else {
			for (int[] o : new int[][] {{SEAT_RING, 0}, {-SEAT_RING, 0}, {0, SEAT_RING}, {0, -SEAT_RING}}) {
				ground(level, f.seat().offset(o[0], 0, o[1]), spots);
			}
		}
		return spots;
	}

	/** The open ground at {@code at}'s column, if there's firm footing and room. */
	private static void ground(ServerLevel level, BlockPos at, List<BlockPos> out) {
		if (!level.isLoaded(at)) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		if (level.getBlockState(top.below()).isSolid() && level.getFluidState(top.below()).isEmpty() && level.getBlockState(top).isAir()
			&& level.getBlockState(top.above()).isAir()) {
			out.add(top);
		}
	}

	/** A gunner for {@code cannon}: an archer of its kingdom's, hired onto it and counted in its roster. */
	private static void gunner(ServerLevel level, Faction f, Cannon cannon) {
		Folk archer = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (archer == null) {
			return;
		}
		BlockPos at = cannon.post() != null ? cannon.post() : cannon.blockPosition();
		archer.snapTo(cannon.getX(), cannon.getY(), cannon.getZ(), cannon.getYRot(), 0.0F);
		archer.setup(Folk.ARCHER, f, at, 1);
		level.addFreshEntity(archer);
		archer.startRiding(cannon);
		Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(Folk.ARCHER, 1)));
	}
}
