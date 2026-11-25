package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The settlement buries its own dead. A villager that isn't busy and has a villager's body lying free near it, a while
 * after it fell (so a player who wants it gets there first), goes out, picks a spot of soft ground close by, digs a hole
 * two blocks deep a block at a time, lays the body in it, and fills the dirt back in a block at a time under a mound
 * ({@link Graves}); the Kitchen's {@code Burial} keeps the body whole under it, so digging the mound out brings it up.
 * It fits "The nights at the grave": a buried villager's ghost keeps its three nights over the mound. Never a dead
 * player's body (their own are for the rite, {@link Mourning}) and nothing hostile (guards burn those, {@link Undertakers}).
 */
public final class Gravediggers {
	/** How far a villager looks for a body; how far from the body the hole may be dug; how close it must come to the work. */
	static final double SIGHT = 24.0, DIG_WITHIN = 5.0, REACH = 2.6;
	/** A body must have lain this long (ticks) before it's taken in hand. */
	static final long SETTLE_TICKS = 1200L;
	/** How long a job may take before it's given up (ticks); a body given up on is left this long. */
	static final long GIVE_UP = 1600L, RETRY = 2400L;
	/** How often work is walked on, and new work handed out (ticks); the most jobs started per round. */
	static final int STEP = 5, ASSIGN = 60, MOST_PER_ROUND = 2;
	/** How long each step of the work takes (ticks). */
	static final int DIG_TICKS = 30, LAY_TICKS = 25, FILL_TICKS = 30;

	/** The steps: walk to the spot, dig the top block and the bottom block, lay the body in, fill the bottom, fill the top. */
	enum Stage { WALK, DIG_TOP, DIG_BOTTOM, LAY, FILL_BOTTOM, FILL_TOP }

	private static final class Job {
		final UUID digger, body;
		/** The block a villager stands on or beside: the hole is dug under it ({@code top} is the first block dug, {@code top.below()} the second). */
		final BlockPos top;
		final long until;
		Stage stage = Stage.WALK;
		long stageEnd = -1L;

		Job(UUID digger, UUID body, BlockPos top, long until) {
			this.digger = digger;
			this.body = body;
			this.top = top;
			this.until = until;
		}
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	/** When each body was first seen lying free (and last), and when a body given up on may be tried again. */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();
	private static final Map<UUID, Long> LEFT = new HashMap<>();

	private Gravediggers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Gravediggers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			SEEN.clear();
			LEFT.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (!KitchenCompat.loaded() || !DeadBodies.present()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % STEP == 0 && !JOBS.isEmpty()) {
			for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext();) {
				if (!step(level, it.next(), now)) {
					it.remove();
				}
			}
		}
		if (now % ASSIGN == 0) {
			LEFT.values().removeIf(until -> until < now);
			SEEN.values().removeIf(seen -> now - seen[1] > ASSIGN * 3L);
			assign(level, now);
		}
	}

	/** Hands free villagers the villagers' bodies lying near them. */
	private static void assign(ServerLevel level, long now) {
		int started = 0;
		for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), Villager::isAlive)) {
			if (started >= MOST_PER_ROUND) {
				return;
			}
			if (villager.isBaby() || villager.isSleeping() || JOBS.containsKey(villager.getUUID()) || Undertakers.busy(villager) || Bearers.busy(villager)
				|| Looters.busy(villager) || !com.jaymoskingdoms.Loaded.made(level, villager.blockPosition())) {
				continue;
			}
			for (Entity body : level.getEntities((Entity) null, villager.getBoundingBox().inflate(SIGHT, 8.0, SIGHT), DeadBodies::isVillagerBody)) {
				UUID id = body.getUUID();
				long[] seen = SEEN.computeIfAbsent(id, k -> new long[] {now, now});
				seen[1] = now;
				if (!KitchenCompat.isFreeCorpse(body) || now - seen[0] < SETTLE_TICKS || LEFT.containsKey(id) || claimed(id) || Bearers.claimed(id)
					|| Looters.claimed(id) || DeadBodies.kept(body)) {
					continue;
				}
				BlockPos top = site(level, body);
				if (top == null) {
					LEFT.put(id, now + RETRY);
					continue;
				}
				JOBS.put(villager.getUUID(), new Job(villager.getUUID(), id, top, now + GIVE_UP));
				started++;
				break;
			}
		}
	}

	private static boolean claimed(UUID body) {
		for (Job job : JOBS.values()) {
			if (job.body.equals(body)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where to dig for {@code body}: the nearest standing place within {@link #DIG_WITHIN} of it (not under it) with two blocks
	 * of soft ground under it, each walled in on at least {@link Graves#WALLS_MIN} sides and dry, and clear air over it.
	 */
	static @Nullable BlockPos site(ServerLevel level, Entity body) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int reach = (int) DIG_WITHIN;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = body.getBlockX() + dx, z = body.getBlockZ() + dz;
				if (!com.jaymoskingdoms.Loaded.made(level, x, z)) {
					continue;
				}
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				double d = top.distToCenterSqr(body.position());
				if (d < 2.0 || d > DIG_WITHIN * DIG_WITHIN || d >= bestD || Math.abs(top.getY() - body.getBlockY()) > 2 || !diggable(level, top)) {
					continue;
				}
				bestD = d;
				best = top;
			}
		}
		return best;
	}

	/** {@code top} is open air with room over it, and the two blocks under it are soft ground a hole can be dug in. */
	private static boolean diggable(ServerLevel level, BlockPos top) {
		if (!level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir() || !level.getFluidState(top).isEmpty()) {
			return false;
		}
		for (BlockPos pos : new BlockPos[] {top.below(), top.below(2)}) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.is(BlockTags.MINEABLE_WITH_SHOVEL) || level.getBlockEntity(pos) != null || !state.getFluidState().isEmpty()
				|| state.getDestroySpeed(level, pos) < 0.0F) {
				return false;
			}
			// (the walls round it are the soil it was dug from: solid and dry)
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos next = pos.relative(side);
				if (!level.getFluidState(next).isEmpty()) {
					return false;
				}
			}
			if (!wallsHold(level, pos)) {
				return false;
			}
		}
		BlockPos floor = top.below(3);
		return level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP);
	}

	/** At least {@link Graves#WALLS_MIN} of the four sides of {@code pos} (soil, about to be a hole) are solid. */
	private static boolean wallsHold(ServerLevel level, BlockPos pos) {
		int walls = 0;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(side);
			if (level.getBlockState(next).isFaceSturdy(level, next, side.getOpposite())) {
				walls++;
			}
		}
		return walls >= Graves.WALLS_MIN;
	}

	/** Walks one job on; false when it's done or given up. */
	private static boolean step(ServerLevel level, Job job, long now) {
		Entity body = level.getEntity(job.body);
		if (!(level.getEntity(job.digger) instanceof Villager villager) || !villager.isAlive() || body == null || body.isRemoved()
			|| !KitchenCompat.isFreeCorpse(body)) {
			return false;
		}
		if (now > job.until) {
			LEFT.put(job.body, now + RETRY);
			return false;
		}
		BlockPos top = job.top;
		BlockPos upper = top.below(), lower = top.below(2);
		if (job.stage == Stage.WALK) {
			if (villager.distanceToSqr(top.getX() + 0.5, top.getY(), top.getZ() + 0.5) > REACH * REACH) {
				Undertakers.walk(villager, top);
				return true;
			}
			job.stage = Stage.DIG_TOP;
			job.stageEnd = now + DIG_TICKS;
		}
		villager.getNavigation().stop();
		villager.getLookControl().setLookAt(top.getX() + 0.5, top.getY() - 0.5, top.getZ() + 0.5);
		if (now % 10 == 0 || now >= job.stageEnd) {
			villager.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			if (job.stage == Stage.DIG_TOP || job.stage == Stage.DIG_BOTTOM) {
				level.playSound(null, top, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
		}
		if (now < job.stageEnd) {
			return true;
		}
		switch (job.stage) {
			case DIG_TOP -> {
				if (!diggable(level, top)) {
					LEFT.put(job.body, now + RETRY);
					return false;
				}
				level.destroyBlock(upper, false);
				job.stage = Stage.DIG_BOTTOM;
				job.stageEnd = now + DIG_TICKS;
			}
			case DIG_BOTTOM -> {
				if (level.getBlockState(lower).isAir() || !level.getBlockState(lower).is(BlockTags.MINEABLE_WITH_SHOVEL)) {
					return false;
				}
				level.destroyBlock(lower, false);
				job.stage = Stage.LAY;
				job.stageEnd = now + LAY_TICKS;
			}
			case LAY -> {
				// the body is taken to the hole's edge and lowered in: it goes down into the ground, whole
				level.playSound(null, lower, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8F, 0.7F);
				job.stage = Stage.FILL_BOTTOM;
				job.stageEnd = now + FILL_TICKS;
			}
			case FILL_BOTTOM -> {
				level.setBlockAndUpdate(lower, Graves.soilAround(level, lower));
				level.playSound(null, lower, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.9F, 0.8F);
				job.stage = Stage.FILL_TOP;
				job.stageEnd = now + FILL_TICKS;
			}
			case FILL_TOP -> {
				// the mound goes over it and the body is laid under it, saved whole
				if (Graves.lay(level, body, upper)) {
					JaymosKingdoms.LOGGER.debug("{} buried a villager at {}", villager.getName().getString(), upper);
				}
				return false;
			}
			default -> {
			}
		}
		return true;
	}
}
