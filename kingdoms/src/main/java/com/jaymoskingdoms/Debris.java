package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A Grim hold's builders clear its rubble before they build anything new ({@link GrimSites}, {@link Planner#ready}):
 * they walk the structure's pieces and pull out the cobwebs, vines and dead bushes, and the loose heaps on its floors
 * (a lone block of gravel, cobble or cracked or mossy stone lying on a floor, open above and on three sides: never a
 * block of a wall, a battlement or a column), then patch the one-block holes in its walls with the wall's own block.
 * What they pull out that's worth keeping (gravel, cobble, stone) goes into the storehouse. The structure is read in a
 * few passes (a heap two high is two passes); when a pass finds nothing (or after {@link #MAX_PASSES}, or
 * {@link #GIVE_UP_TICKS} after it was founded) the hold is cleared and the planner takes over. Wraps the builders'
 * brain ({@link Builders#brain}): a builder with rubble to clear does that instead of anything else.
 */
final class Debris {
	/** How often the structure is read, and how many of its columns at a time. */
	static final int SCAN_TICKS = 20, SCAN_COLUMNS = 160;
	/** Passes over the structure at most; rubble and repairs queued per pass at most; repairs in all. */
	static final int MAX_PASSES = 4, QUEUE_MAX = 400, REPAIRS_MAX = 48;
	/** A hold not cleared this long after it was founded counts as cleared (so the builders aren't held up for ever). */
	static final long GIVE_UP_TICKS = 5 * 24000L;
	/** Ticks between a builder's pulls (and a random extra). */
	static final int ACT_TICKS = 18, ACT_JITTER = 10;
	/** Walking: speed, how close counts as there (across and up), path renewal, no progress this long means a puff. */
	static final double WALK_SPEED = 0.75, REACH = 4.0, VERTICAL = 4.0;
	static final int REPATH_TICKS = 20, STUCK_TICKS = 200, LEG = 20;
	/** A builder hurt this recently is left to run (ticks). */
	static final int FLEE_TICKS = 100;
	/** Players this near the seat hear the rubble is cleared. */
	static final double TELL_RANGE = 128.0;

	/** What a hold's reading has found so far: the pieces, where it's got to, and what's queued. */
	private static final class Work {
		final List<BoundingBox> boxes;
		int box, column, pass;
		boolean scanning = true;
		final List<BlockPos> rubble = new ArrayList<>();
		final Map<BlockPos, BlockState> holes = new HashMap<>();
		int repairs;

		Work(List<BoundingBox> boxes) {
			this.boxes = boxes;
		}
	}

	/** One builder's errand. */
	private static final class Job {
		@Nullable BlockPos target;
		double best = Double.MAX_VALUE;
		long improvedAt, repathAt, actAt;
	}

	private static final Map<Integer, Work> WORK = new HashMap<>();
	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private Debris() {
	}

	/** Wraps the builders' brain (after {@link Construction#register}) and reads the holds. */
	static void register() {
		Builders.Brain next = Builders.brain();
		Builders.setBrain((level, builder) -> {
			if (!tick(level, builder)) {
				next.tick(level, builder);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Debris::scan);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WORK.clear();
			JOBS.clear();
		});
	}

	/** Faction {@code id} holds a Grim structure whose rubble isn't cleared yet (its planner waits). */
	static boolean busy(MinecraftServer server, int id) {
		Holds.Hold h = Holds.get(server, id);
		return h != null && h.isGrim() && !h.cleared();
	}

	// ---- reading the structure ---------------------------------------------------------------------------------------

	private static void scan(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % SCAN_TICKS != 0) {
			return;
		}
		for (var e : Holds.all(server).entrySet()) {
			Holds.Hold h = e.getValue();
			if (!h.isGrim() || h.cleared()) {
				continue;
			}
			int id = Integer.parseInt(e.getKey());
			Faction f = Factions.get(server, id);
			if (f == null || f.fallen() || now - h.founded() > GIVE_UP_TICKS) {
				finish(level, id, f, false);
				continue;
			}
			if (!level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Work w = WORK.computeIfAbsent(id, k -> new Work(h.rubble()));
			if (w.scanning) {
				read(level, w);
			} else if (w.rubble.isEmpty() && w.holes.isEmpty()) {
				if (w.pass >= MAX_PASSES) {
					finish(level, id, f, true);
				} else {
					w.scanning = true;
					w.box = 0;
					w.column = 0;
				}
			}
		}
	}

	/** Reads the next {@link #SCAN_COLUMNS} columns; at the end of a pass that found nothing, the hold is cleared. */
	private static void read(ServerLevel level, Work w) {
		int done = 0;
		while (done < SCAN_COLUMNS && w.box < w.boxes.size()) {
			BoundingBox box = w.boxes.get(w.box);
			int columns = box.getXSpan() * box.getZSpan();
			if (w.column >= columns) {
				w.box++;
				w.column = 0;
				continue;
			}
			int x = box.minX() + w.column % box.getXSpan(), z = box.minZ() + w.column / box.getXSpan();
			w.column++;
			done++;
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			for (int y = box.minY() + 1; y <= box.maxY(); y++) {
				BlockPos at = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(at);
				if (state.isAir()) {
					if (w.repairs < REPAIRS_MAX && w.holes.size() < QUEUE_MAX) {
						BlockState fill = hole(level, at);
						if (fill != null) {
							w.holes.put(at, fill);
							w.repairs++;
						}
					}
				} else if (w.rubble.size() < QUEUE_MAX && rubble(level, at, state)) {
					w.rubble.add(at);
				}
			}
		}
		if (w.box >= w.boxes.size()) {
			w.scanning = false;
			w.pass++;
			if (w.rubble.isEmpty() && w.holes.isEmpty()) {
				// (nothing found this pass: done)
				w.pass = MAX_PASSES;
			}
		}
	}

	/** The rubble's gone: the hold is cleared and its builders go back to planning (players near are told, if {@code tell}). */
	private static void finish(ServerLevel level, int id, @Nullable Faction f, boolean tell) {
		MinecraftServer server = level.getServer();
		WORK.remove(id);
		Holds.update(server, id, h -> h.withCleared(true));
		if (f == null || !tell) {
			return;
		}
		JaymosKingdoms.LOGGER.info("{} has cleared the rubble from its hold", f.name());
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(f.seat()).inflate(TELL_RANGE))) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.grim.cleared", f.name()).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Loose blocks heaped on floors: cleared when they lie on a floor, open above and on three sides. */
	private static boolean loose(BlockState state) {
		return state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.CRACKED_STONE_BRICKS)
			|| state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.COBBLESTONE_SLAB) || state.is(Blocks.MOSSY_COBBLESTONE_SLAB)
			|| state.is(Blocks.STONE_BRICK_SLAB) || state.is(Blocks.MOSSY_STONE_BRICK_SLAB) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
			|| state.is(Blocks.COARSE_DIRT);
	}

	/** Always rubble: cobwebs, vines, dead bushes, lichen. */
	private static boolean litter(BlockState state) {
		return state.is(Blocks.COBWEB) || state.is(Blocks.VINE) || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.GLOW_LICHEN);
	}

	/** A block to clear: litter, or a loose block lying in a heap on a floor (not part of a wall, a merlon or a column). */
	static boolean rubble(ServerLevel level, BlockPos at, BlockState state) {
		if (litter(state)) {
			return true;
		}
		if (!loose(state) || level.getBlockEntity(at) != null || !open(level, at.above())) {
			return false;
		}
		int openSides = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (open(level, at.relative(d))) {
				openSides++;
			}
		}
		if (openSides < 3) {
			return false;
		}
		// on a floor: the block under it is sturdy and has floor all round it
		BlockPos under = at.below();
		if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
			return false;
		}
		int floor = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (Seats.wall(level, under.relative(d))) {
				floor++;
			}
		}
		return floor >= 3;
	}

	/**
	 * A one-block hole in a wall at {@code at} (air): solid wall left and right, above and below, open on both faces.
	 * Returns the block to patch it with (the wall's), or null.
	 */
	static @Nullable BlockState hole(ServerLevel level, BlockPos at) {
		if (!Seats.wall(level, at.above()) || !Seats.wall(level, at.below())) {
			return null;
		}
		for (Direction d : new Direction[] {Direction.EAST, Direction.SOUTH}) {
			BlockPos a = at.relative(d), b = at.relative(d.getOpposite());
			Direction across = d.getClockWise();
			if (Seats.wall(level, a) && Seats.wall(level, b) && open(level, at.relative(across)) && open(level, at.relative(across.getOpposite()))) {
				return level.getBlockState(a);
			}
		}
		return null;
	}

	private static boolean open(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
	}

	// ---- the builders --------------------------------------------------------------------------------------------------

	/** One builder's tick: true if he's clearing rubble (or running), false to leave him to the construction brain. */
	static boolean tick(ServerLevel level, Folk b) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, b.factionId());
		Work w = f == null ? null : WORK.get(f.id());
		if (f == null || f.fallen() || w == null || !busy(server, f.id()) || b.isCarrying() || !Economy.atPeace(server, f)) {
			JOBS.remove(b.getUUID());
			return false;
		}
		if (b.getLastHurtByMob() != null && b.tickCount - b.getLastHurtByMobTimestamp() < FLEE_TICKS) {
			return true;
		}
		long now = level.getGameTime();
		Job job = JOBS.computeIfAbsent(b.getUUID(), u -> new Job());
		if (job.target != null) {
			BlockState state = level.getBlockState(job.target);
			BlockState fill = w.holes.get(job.target);
			boolean still = fill != null ? state.isAir() : !state.isAir();
			if (!still) {
				w.holes.remove(job.target);
				job.target = null;
			}
		}
		if (job.target == null) {
			job.target = next(w, b.blockPosition());
			if (job.target == null) {
				return false;
			}
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			job.repathAt = 0;
			b.setHomeTo(Builders.depot(f), Construction.WORK_RADIUS);
			b.setTask(w.holes.containsKey(job.target) ? "repairing" : "clearing rubble");
		}
		BlockPos target = job.target;
		b.setSite(target);
		if (!approach(level, b, job, target, now) || now < job.actAt) {
			return true;
		}
		job.actAt = now + ACT_TICKS + level.getRandom().nextInt(ACT_JITTER);
		b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		BlockState fill = w.holes.remove(target);
		if (fill != null) {
			level.setBlock(target, fill, Block.UPDATE_ALL);
			SoundType sound = fill.getSoundType();
			level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, fill), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 6,
				0.25, 0.25, 0.25, 0.05);
		} else {
			BlockState state = level.getBlockState(target);
			level.destroyBlock(target, false, b);
			Item salvage = salvage(state);
			if (salvage != null && Storage.loaded(level, f)) {
				Storage.put(level, f, salvage, 1);
			}
		}
		job.target = null;
		return true;
	}

	/** What's kept of a block pulled out (gravel, cobble, stone), or null. */
	private static @Nullable Item salvage(BlockState state) {
		if (state.is(Blocks.GRAVEL)) {
			return Items.GRAVEL;
		}
		if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)
			|| state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)) {
			return Items.COBBLESTONE;
		}
		return null;
	}

	/** The queued block nearest {@code from} (rubble before repairs), taken off its queue (repairs stay queued till done). */
	private static @Nullable BlockPos next(Work w, BlockPos from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		int index = -1;
		for (int i = 0; i < w.rubble.size(); i++) {
			double d = w.rubble.get(i).distSqr(from);
			if (d < bestDist) {
				bestDist = d;
				best = w.rubble.get(i);
				index = i;
			}
		}
		if (best != null) {
			w.rubble.remove(index);
			return best;
		}
		for (BlockPos p : w.holes.keySet()) {
			double d = p.distSqr(from);
			if (d < bestDist && JOBS.values().stream().noneMatch(j -> p.equals(j.target))) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	/** Walks toward {@code goal}; true once within reach. Stuck too long: a puff of smoke, and he's there. */
	private static boolean approach(ServerLevel level, Folk b, Job job, BlockPos goal, long now) {
		double dx = goal.getX() + 0.5 - b.getX(), dz = goal.getZ() + 0.5 - b.getZ(), dy = goal.getY() - b.getY();
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= REACH && Math.abs(dy) <= VERTICAL) {
			b.getNavigation().stop();
			b.getLookControl().setLookAt(goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5);
			return true;
		}
		double dist = across + Math.max(0.0, Math.abs(dy) - VERTICAL);
		if (dist < job.best - 0.5) {
			job.best = dist;
			job.improvedAt = now;
		}
		if (now - job.improvedAt > STUCK_TICKS) {
			BlockPos to = Construction.standable(level, goal, 4);
			level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 1.0, b.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			b.getNavigation().stop();
			b.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
			level.sendParticles(ParticleTypes.POOF, to.getX() + 0.5, to.getY() + 1.0, to.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			return false;
		}
		if (now >= job.repathAt || b.getNavigation().isDone()) {
			job.repathAt = now + REPATH_TICKS;
			BlockPos leg = goal;
			if (across > LEG) {
				leg = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					BlockPos.containing(b.getX() + dx / across * LEG, b.getY(), b.getZ() + dz / across * LEG));
			}
			b.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, WALK_SPEED);
		}
		return false;
	}
}
