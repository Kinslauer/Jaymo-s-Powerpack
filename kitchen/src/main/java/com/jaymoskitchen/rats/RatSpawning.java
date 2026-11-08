package com.jaymoskitchen.rats;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where rats come from: food left out and unguarded. Every {@link #INTERVAL} ticks, for each player, one food store
 * near them ({@link FoodStores}) is picked at random; if nobody's within {@link #GUARD_RADIUS} of it, no cat within
 * {@link #CAT_RADIUS}, and fewer than {@link #MAX_RATS} rats about, a rat may turn up in a dark corner near it. The more
 * food in the store, and the more mess around it (food and rotten flesh dropped on the floor), the likelier.
 */
public final class RatSpawning {
	/** How often each player's surroundings are checked (ticks). */
	static final int INTERVAL = 400;
	/** How far from a player stores are looked for (blocks). */
	static final double SCAN_RANGE = 40.0;
	/** Nobody this close to a store, and it's unguarded; any cat this close keeps rats away. */
	static final double GUARD_RADIUS = 10.0, CAT_RADIUS = 16.0;
	/** No more than this many rats around one store. */
	static final int MAX_RATS = 3;
	static final double CROWD_RADIUS = 24.0;
	/** The chance per check, and what each stack of food in the store and each bit of mess nearby adds (capped). */
	static final float BASE_CHANCE = 0.04F, PER_STACK = 0.01F, PER_MESS = 0.03F, MAX_CHANCE = 0.25F;
	/** How far round a store mess counts (blocks). */
	static final double MESS_RADIUS = 8.0;
	/** A rat turns up this far from the store (blocks), somewhere no brighter than {@link #MAX_LIGHT}. */
	static final int SPAWN_MIN = 4, SPAWN_MAX = 10, SPAWN_TRIES = 12, MAX_LIGHT = 7;
	/** And no closer than this to any player. */
	static final double PLAYER_CLEARANCE = 8.0;

	private RatSpawning() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (!level.getGameRules().get(GameRules.SPAWN_MOBS)) {
				continue;
			}
			for (ServerPlayer player : List.copyOf(level.players())) {
				if (!player.isSpectator()) {
					tryNear(level, player);
				}
			}
		}
	}

	private static void tryNear(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		List<FoodStores.Target> stores = FoodStores.around(level, player.blockPosition(), SCAN_RANGE, false);
		if (stores.isEmpty()) {
			return;
		}
		FoodStores.Target store = stores.get(random.nextInt(stores.size()));
		Vec3 at = store.at();
		if (level.getNearestPlayer(at.x, at.y, at.z, GUARD_RADIUS, e -> !e.isSpectator()) != null) {
			return;
		}
		AABB around = new AABB(BlockPos.containing(at)).inflate(CAT_RADIUS);
		if (!level.getEntitiesOfClass(Cat.class, around).isEmpty()) {
			return;
		}
		if (level.getEntitiesOfClass(Rat.class, new AABB(BlockPos.containing(at)).inflate(CROWD_RADIUS)).size() >= MAX_RATS) {
			return;
		}
		float chance = BASE_CHANCE + PER_STACK * FoodStores.foodStacks(level, store) + PER_MESS * mess(level, at);
		if (random.nextFloat() >= Math.min(MAX_CHANCE, chance)) {
			return;
		}
		BlockPos spot = spot(level, BlockPos.containing(at), random);
		if (spot == null) {
			return;
		}
		Rat rat = Rats.RAT.spawn(level, spot, EntitySpawnReason.NATURAL);
		if (rat != null) {
			rat.setYRot(random.nextFloat() * 360.0F);
		}
	}

	/** Food dropped on the floor and rotten flesh lying about near a store. */
	private static int mess(ServerLevel level, Vec3 at) {
		int mess = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(MESS_RADIUS))) {
			if (FoodStores.ratFood(item.getItem()) || item.getItem().is(Items.ROTTEN_FLESH)) {
				mess++;
			}
		}
		return mess;
	}

	/** A dark, open spot on solid ground near {@code store}, out of players' way; null if there's none. */
	private static BlockPos spot(ServerLevel level, BlockPos store, RandomSource random) {
		for (int i = 0; i < SPAWN_TRIES; i++) {
			int dist = SPAWN_MIN + random.nextInt(SPAWN_MAX - SPAWN_MIN + 1);
			double angle = random.nextDouble() * Math.PI * 2.0;
			BlockPos pos = store.offset((int) Math.round(Math.cos(angle) * dist), random.nextInt(5) - 2, (int) Math.round(Math.sin(angle) * dist));
			if (!level.hasChunkAt(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				|| !level.getFluidState(pos).isEmpty()) {
				continue;
			}
			BlockPos below = pos.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				continue;
			}
			if (level.getMaxLocalRawBrightness(pos) > MAX_LIGHT) {
				continue;
			}
			if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, PLAYER_CLEARANCE, e -> !e.isSpectator()) != null) {
				continue;
			}
			return pos;
		}
		return null;
	}
}
