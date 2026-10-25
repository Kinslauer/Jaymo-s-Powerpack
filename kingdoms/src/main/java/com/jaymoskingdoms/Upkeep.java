package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.201) A settlement's upkeep: what {@link Weathering} wears in a standing settlement is noted for its builders
 * ({@link #note}, kept on the Overworld so it lasts past a restart), and one of each crew (the only one, with a crew of
 * one, once {@link #BATCH} have piled up) goes round by day scrubbing the moss off, mending the cracked bricks, raking
 * up the leaf litter and spreading the mud back to dirt. Only what weathering did: a Grim hold's old moss stays.
 * Wraps the builders' brain after {@link Construction} (rubble, mines and groundwork still come first).
 */
final class Upkeep {
	/** How far from a seat a weathered block still belongs to that settlement, and how many are noted at most. */
	static final double NOTE_RADIUS = 96.0;
	static final int QUEUE_MAX = 256;
	/** A lone builder only turns to upkeep with this many waiting. */
	static final int BATCH = 8;
	/** How often (ticks) each crew's upkeep hand is picked again. */
	static final int PICK_TICKS = 600;
	/** Ticks between a builder's fixes (and a random extra). */
	static final int ACT_TICKS = 30, ACT_JITTER = 20;
	/** Walking: speed, reach (across and up), path renewal, no progress this long means a puff, legs of a long walk. */
	static final double WALK_SPEED = 0.7, REACH = 4.0, VERTICAL = 4.0;
	static final int REPATH_TICKS = 20, STUCK_TICKS = 200, LEG = 20;
	/** A builder hurt this recently is left to the brain under him (ticks). */
	static final int FLEE_TICKS = 100;

	/** Faction id → weathered blocks ({@link BlockPos#asLong}) waiting for its builders. */
	static final AttachmentType<Map<String, List<Long>>> QUEUE = AttachmentRegistry.create(JaymosKingdoms.id("upkeep"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())));

	private static final class Job {
		@Nullable BlockPos target;
		double best = Double.MAX_VALUE;
		long improvedAt, repathAt, actAt;
	}

	private record Hand(UUID builder, long until) {
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	private static final Map<Integer, Hand> HANDS = new HashMap<>();

	private Upkeep() {
	}

	static void register() {
		Builders.Brain next = Builders.brain();
		Builders.setBrain((level, builder) -> {
			if (!tick(level, builder)) {
				next.tick(level, builder);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			HANDS.clear();
		});
	}

	/** Weathering changed {@code at}: if it's in a standing settlement, its builders will see to it. */
	static void note(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return;
		}
		Faction f = Factions.around(server, level, at, NOTE_RADIUS);
		if (f == null || f.fallen()) {
			return;
		}
		Map<String, List<Long>> all = new HashMap<>(level.getAttachedOrElse(QUEUE, Map.of()));
		List<Long> queue = new ArrayList<>(all.getOrDefault(Integer.toString(f.id()), List.of()));
		long key = at.asLong();
		if (queue.size() >= QUEUE_MAX || queue.contains(key)) {
			return;
		}
		queue.add(key);
		all.put(Integer.toString(f.id()), List.copyOf(queue));
		level.setAttached(QUEUE, Map.copyOf(all));
	}

	private static List<Long> queue(ServerLevel level, int id) {
		return level.getAttachedOrElse(QUEUE, Map.of()).getOrDefault(Integer.toString(id), List.of());
	}

	private static void remove(ServerLevel level, int id, BlockPos at) {
		Map<String, List<Long>> all = new HashMap<>(level.getAttachedOrElse(QUEUE, Map.of()));
		List<Long> queue = new ArrayList<>(all.getOrDefault(Integer.toString(id), List.of()));
		if (queue.remove(Long.valueOf(at.asLong()))) {
			if (queue.isEmpty()) {
				all.remove(Integer.toString(id));
			} else {
				all.put(Integer.toString(id), List.copyOf(queue));
			}
			level.setAttached(QUEUE, Map.copyOf(all));
		}
	}

	/** Whether {@code b} is the crew's upkeep hand just now. */
	private static boolean hand(ServerLevel level, Faction f, Worker b, int waiting) {
		long now = level.getGameTime();
		Hand hand = HANDS.get(f.id());
		if (hand == null || now >= hand.until()) {
			List<Worker> crew = Builders.crew(level, f);
			if (crew.isEmpty()) {
				return false;
			}
			// the last of a crew (the first are the ones building)
			hand = new Hand(crew.get(crew.size() - 1).getUUID(), now + PICK_TICKS);
			HANDS.put(f.id(), hand);
			if (crew.size() == 1 && waiting < BATCH) {
				HANDS.put(f.id(), new Hand(new UUID(0L, 0L), now + PICK_TICKS));
				return false;
			}
		}
		return hand.builder().equals(b.getUUID());
	}

	/** One builder's tick: true while he's at upkeep, false to leave him to the brain under this one. */
	static boolean tick(ServerLevel level, Worker b) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, b.factionId());
		if (f == null || f.fallen() || b.isCarrying() || !level.isBrightOutside() || level != Factions.level(server) || !Economy.atPeace(server, f)) {
			JOBS.remove(b.getUUID());
			return false;
		}
		List<Long> waiting = queue(level, f.id());
		if (waiting.isEmpty() || !hand(level, f, b, waiting.size())) {
			JOBS.remove(b.getUUID());
			return false;
		}
		if (b.getLastHurtByMob() != null && b.ticksAlive() - b.getLastHurtByMobTimestamp() < FLEE_TICKS) {
			return false;
		}
		long now = level.getGameTime();
		Job job = JOBS.computeIfAbsent(b.getUUID(), u -> new Job());
		if (job.target != null && Weathering.undone(level.getBlockState(job.target)) == null) {
			// someone else saw to it (or it's gone)
			remove(level, f.id(), job.target);
			job.target = null;
		}
		if (job.target == null) {
			job.target = nearest(level, f.id(), waiting, b.blockPosition());
			if (job.target == null) {
				return false;
			}
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			job.repathAt = 0;
			b.setHomeTo(Builders.depot(f), Construction.WORK_RADIUS);
			b.setTask("upkeep");
		}
		BlockPos target = job.target;
		b.setSite(target);
		if (!approach(level, b, job, target, now) || now < job.actAt) {
			return true;
		}
		job.actAt = now + ACT_TICKS + level.getRandom().nextInt(ACT_JITTER);
		b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		BlockState was = level.getBlockState(target);
		BlockState fixed = Weathering.undone(was);
		if (fixed != null) {
			level.setBlock(target, fixed, Block.UPDATE_ALL);
			SoundType sound = (fixed.isAir() ? was : fixed).getSoundType();
			level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 6,
				0.25, 0.25, 0.25, 0.05);
		}
		remove(level, f.id(), target);
		job.target = null;
		return true;
	}

	/** The waiting block nearest {@code from} that no one else is on; ones that aren't weathered any more are dropped. */
	private static @Nullable BlockPos nearest(ServerLevel level, int id, List<Long> waiting, BlockPos from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (long key : waiting) {
			BlockPos p = BlockPos.of(key);
			if (!level.isLoaded(p)) {
				continue;
			}
			if (Weathering.undone(level.getBlockState(p)) == null) {
				remove(level, id, p);
				continue;
			}
			double d = p.distSqr(from);
			if (d < bestDist && JOBS.values().stream().noneMatch(j -> p.equals(j.target))) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	/** Walks toward {@code goal}; true once within reach. Stuck too long: a puff of smoke, and he's there. */
	private static boolean approach(ServerLevel level, Worker b, Job job, BlockPos goal, long now) {
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
