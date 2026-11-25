package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Crows on the fields (Seasonal: The Wicked Harvest's crows, our own take): by day, in dry weather, a flock comes down on
 * open farmland near a player and pecks crops back a stage ({@link Crow}'s field goal). Living things' three rules:
 * <ul>
 * <li>Sympathy: crows go for open ground in the daylight, are off at dusk, and flee a stranger's step; they take the young
 * crop, not the ripe.</li>
 * <li>Feedback: the more open crop there is in a field, the bigger the flock it draws ({@link #CROPS_PER_CROW} to a bird,
 * {@link #MOST_IN_FLOCK} at most); a scarecrow with a pumpkin within 16 blocks (Kitchen's, {@link KitchenCompat#guardedFromCrows})
 * keeps a field out of the count altogether, and the odd bold bird apart, they leave its crops alone; a crow that has
 * pecked its fill leaves.</li>
 * <li>Stewardship: a field with a scarecrow loses next to nothing, and one whose crop is picked in good time has little
 * young crop to lose. Nobody who leaves the crows alone is punished beyond the pecks.</li>
 * </ul>
 */
public final class FieldCrows {
	// ---- tuning ----
	/** Ticks between a look near each player, and the chance a look that finds a field brings a flock. */
	static final int EVERY = 200;
	static final float FLOCK_CHANCE = 0.35F;
	/** How far from the player (blocks) a field is looked for, at least and at most. */
	static final int NEAR = 14, FAR = 40;
	/** The look at a field: how far across (blocks) and up and down. */
	static final int LOOK = 6, LOOK_UP = 2;
	/** Open crop (blocks) a field needs to draw any flock, and per crow it draws; the biggest flock; the most flock crows near a player. */
	static final int MIN_OPEN = 10, CROPS_PER_CROW = 10, MOST_IN_FLOCK = 5, MOST_NEAR_PLAYER = 10;
	/** Daylight, in ticks of the day. */
	static final long DAWN = 500L, DUSK = 11500L;

	private FieldCrows() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FieldCrows::tick);
	}

	/** Dry weather in the daytime on this level's clock: when crows are out on the fields. */
	static boolean daylight(ServerLevel level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= DAWN && time <= DUSK && !level.isRaining();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!daylight(level)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || level.getRandom().nextFloat() >= FLOCK_CHANCE) {
				continue;
			}
			try {
				visit(level, player);
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("field crows failed", e);
			}
		}
	}

	private static void visit(ServerLevel level, ServerPlayer player) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double reach = NEAR + level.getRandom().nextDouble() * (FAR - NEAR);
		int x = (int)Math.floor(player.getX() + Math.cos(angle) * reach);
		int z = (int)Math.floor(player.getZ() + Math.sin(angle) * reach);
		if (!Loaded.made(level, x - LOOK, z - LOOK, x + LOOK, z + LOOK)) {
			return;
		}
		BlockPos centre = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
		int open = 0;
		BlockPos first = null;
		for (BlockPos at : BlockPos.betweenClosed(centre.offset(-LOOK, -LOOK_UP - 1, -LOOK), centre.offset(LOOK, LOOK_UP, LOOK))) {
			if (level.getBlockState(at).getBlock() instanceof CropBlock && level.canSeeSky(at)) {
				open++;
				if (first == null) {
					first = at.immutable();
				}
			}
		}
		if (first == null || open < MIN_OPEN || KitchenCompat.guardedFromCrows(level, first)) {
			return;
		}
		AABB near = new AABB(first).inflate(FAR);
		List<Crow> there = level.getEntitiesOfClass(Crow.class, near, Crow::isFlock);
		if (level.getEntitiesOfClass(Crow.class, player.getBoundingBox().inflate(FAR * 2.0), Crow::isFlock).size() >= MOST_NEAR_PLAYER) {
			return;
		}
		int want = Math.min(MOST_IN_FLOCK, open / CROPS_PER_CROW);
		for (int i = there.size(); i < want; i++) {
			Crow crow = Crow.TYPE.create(level, EntitySpawnReason.EVENT);
			if (crow == null) {
				return;
			}
			double sx = first.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 8.0;
			double sz = first.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 8.0;
			crow.snapTo(sx, first.getY() + 5.0 + level.getRandom().nextDouble() * 3.0, sz, level.getRandom().nextFloat() * 360.0F, 0.0F);
			crow.joinFlock();
			level.addFreshEntity(crow);
		}
	}
}
