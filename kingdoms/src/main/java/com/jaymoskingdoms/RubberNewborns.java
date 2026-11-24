package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Newborn Rubber-folk (Andy, 2026-11-24, picking "Home ground + newborns"; Lore.md, "Where the Rubber-folk come from"): the
 * Mad Tinkerer's vats under the Candy Cavity still turn them out. Now and then, while a player is down in Alex's Candy
 * Cavity ({@link RubberFolk#CANDY_CAVITY}), a newborn Rubber-folk MCA villager stands up on the cave floor some way off
 * ({@link #COME_MIN} to {@link #COME_MAX} blocks). It has nowhere to be yet: it makes for the seat of the nearest
 * settlement ({@link Captives#nearestSettlement}), saved as {@link #BOUND_FOR}. Out of every player's sight it climbs up
 * to the surface above, and from there it walks a leg at a time ({@link Construction#leg}) until it is within
 * {@link #ARRIVE} of the seat, where the town's own life takes it over. With no settlement in reach it just stays.
 */
public final class RubberNewborns {
	// ---- tuning ----
	/** How often (ticks) the cavity may send one up for each player down there, and the chance each time (about one an hour). */
	static final int COME_EVERY = 1200;
	static final float COME_CHANCE = 1.0F / 60.0F;
	/** Blocks from the player it stands up, and the most newborns near one player at a time. */
	static final int COME_MIN = 16, COME_MAX = 32, MAX_NEAR = 1;
	static final double NEAR = 96.0;
	/** How often (ticks) a newborn on its way picks its next leg, its walking speed, and how near the seat counts as there. */
	static final int WALK_EVERY = 40;
	static final float WALK_SPEED = 0.6F;
	static final double ARRIVE = 24.0;
	/** It only climbs to the surface with no player within this (blocks), so nobody sees it pass through rock. */
	static final double UNSEEN = 48.0;
	static final int TRIES = 16;

	/** Where a newborn is making for: the seat of the settlement nearest where it stood up. Saved. */
	static final AttachmentType<BlockPos> BOUND_FOR = AttachmentRegistry.create(JaymosKingdoms.id("rubber_newborn_bound_for"),
		builder -> builder.persistent(BlockPos.CODEC));

	private RubberNewborns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RubberNewborns::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % WALK_EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR), RubberNewborns::newborn)) {
				walk(level, v);
			}
			if (now % COME_EVERY == 0 && !player.isSpectator() && !player.isCreative()
				&& level.getBiome(player.blockPosition()).is(RubberFolk.CANDY_CAVITY) && random.nextFloat() < COME_CHANCE
				&& level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR), RubberNewborns::newborn).size() < MAX_NEAR) {
				come(level, player, random);
			}
		}
	}

	static boolean newborn(Villager v) {
		return v.hasAttached(BOUND_FOR) && v.isAlive();
	}

	/** One stands up on the cave floor some way off, and sets out for the nearest settlement. */
	private static void come(ServerLevel level, ServerPlayer player, RandomSource random) {
		Faction home = Captives.nearestSettlement(level.getServer(), player.blockPosition());
		if (home == null) {
			return;
		}
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = COME_MIN + random.nextInt(COME_MAX - COME_MIN + 1);
			BlockPos near = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(near) || !level.getBiome(near).is(RubberFolk.CANDY_CAVITY)) {
				continue;
			}
			BlockPos at = Construction.standable(level, near, 3);
			if (at.getY() > player.getY() + 8 || !level.getBiome(at).is(RubberFolk.CANDY_CAVITY)) {
				// (found only the surface: not down in the cavity)
				continue;
			}
			Vec3 pos = Vec3.atBottomCenterOf(at);
			Villager v = McaCompat.newVillager(level, pos);
			if (v == null) {
				return;
			}
			v.snapTo(pos.x, pos.y, pos.z, random.nextFloat() * 360.0F, 0.0F);
			// (set up as a fresh spawn would be: MCA rolls their genes and skin here)
			v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			v.setPersistenceRequired();
			level.addFreshEntity(v);
			VillagerHeritage.born(v, Heritage.Kind.RUBBERFOLK, null);
			v.setAttached(BOUND_FOR, home.seat());
			JaymosKingdoms.LOGGER.debug("a newborn Rubber-folk stood up in the Candy Cavity at {}, bound for {}", at, home.name());
			return;
		}
	}

	/** A newborn on its way: up out of the caves unseen, then a leg at a time to the seat; there, it's the town's. */
	private static void walk(ServerLevel level, Villager v) {
		BlockPos seat = v.getAttached(BOUND_FOR);
		double dx = seat.getX() + 0.5 - v.getX(), dz = seat.getZ() + 0.5 - v.getZ();
		if (dx * dx + dz * dz <= ARRIVE * ARRIVE) {
			v.removeAttached(BOUND_FOR);
			return;
		}
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, v.blockPosition());
		if (v.getY() < surface.getY() - 4) {
			if (level.getNearestPlayer(v, UNSEEN) == null) {
				BlockPos up = Construction.standable(level, surface, 4);
				v.getNavigation().stop();
				v.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
			}
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(v, Construction.leg(level, v, seat), WALK_SPEED, 1);
	}
}
