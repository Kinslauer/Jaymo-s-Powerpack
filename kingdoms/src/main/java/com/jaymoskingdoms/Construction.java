package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Real-time construction: the builders' brain ({@link Builders#setBrain}). For any faction with a building catalogue
 * (kingdoms, fiefdoms, villages, camps alike: nothing here is particular to a type), the foreman walks to a new
 * project's site and stakes it out (fences at the corners, a sign); then workers walk to the storage, take a load of
 * the raw materials the next few blocks need out of the real containers ({@link Storage#take}, priced by
 * {@link Materials}), carry it in their hands to the site, ready the ground column by column (dig what's in the way,
 * fill hollows under the floor) and lay the building bottom up, a few blocks a trip, then go back for more. They stop
 * when the materials run out (and look again later), or when the faction is attacked or at war ({@link Economy#atPeace}),
 * and pick up where they left off: progress is saved with the faction ({@link Works}). A worker who can't get where he's
 * going for a while is moved there in a puff of smoke, so the work never stalls.
 */
public final class Construction {
	/** Blocks a worker lays per trip; ticks between blocks (and a random extra, so the work has a rhythm). */
	static final int BLOCKS_PER_TRIP = 4, PLACE_TICKS = 24, PLACE_JITTER_TICKS = 12;
	/** Ticks spent loading at the storage; the most raw items in one load. */
	static final int LOAD_TICKS = 40, LOAD_MAX = 16;
	/** How many blocks ahead a worker looks to decide what to fetch. */
	static final int LOOKAHEAD = 12;
	/** Already-laid blocks skipped in one go. */
	static final int SKIP_PER_TICK = 24;
	/** Walking speed; how close counts as there (the depot; a block to lay, across and up). */
	static final double WALK_SPEED = 0.75, DEPOT_REACH = 2.5, PLACE_REACH = 4.5, TALL_REACH = 12.0, VERTICAL_SLACK = 3.0;
	/** Paths are renewed this often; a long walk goes in legs this long (paths are short); no progress this long means a puff. */
	static final int REPATH_TICKS = 20, LEG = 20, STUCK_TICKS = 200;
	/** How far builders may roam from their depot while working. */
	static final int WORK_RADIUS = 160;
	/** Out of materials: look again this much later (ticks). */
	static final long WAIT_TICKS = 600L;
	/** How often idle builders look for work, and the planner looks for a next project (ticks). */
	static final int DECIDE_TICKS = 40, PLAN_TICKS = 200;
	/** A faction that found no site waits this long before looking again (ticks). */
	static final long PLAN_RETRY_TICKS = 2400L;
	/** Readying the ground: the deepest hollow filled under a floor; air cleared above a building. */
	static final int FILL_MAX = 4, CLEAR_ABOVE = 2;
	/** A builder hurt this recently is left to run (ticks). */
	static final int FLEE_TICKS = 100;
	/** Players this close to the seat hear a building finished. */
	static final double ANNOUNCE_RANGE = 128.0;

	/** A builder's phases. */
	static final int IDLE = 0, FETCH = 1, LOAD = 2, HAUL = 3, WORK = 4, STAKE = 5;

	/** What a builder is doing (not saved: rebuilt from what he carries). */
	static final class Job {
		int phase = IDLE;
		@Nullable BlockPos goal;
		double best = Double.MAX_VALUE;
		long improvedAt, repathAt, actAt;
		int placed;
		@Nullable Item fetch;
		int amount;
		boolean working;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	private static final Map<String, Planner.Layout> LAYOUTS = new HashMap<>();
	/** Faction id → game time it may look in its storage again (it ran out). */
	private static final Map<Integer, Long> WAITING = new HashMap<>();
	/** Faction id → game time it may look for a site again (it found none). */
	private static final Map<Integer, Long> NEXT_PLAN = new HashMap<>();

	private Construction() {
	}

	static void register() {
		Builders.setBrain(Construction::tick);
		Economy.nextNeeds = Planner::needs;
		ServerTickEvents.END_SERVER_TICK.register(Construction::plan);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		JOBS.clear();
		LAYOUTS.clear();
		WAITING.clear();
		NEXT_PLAN.clear();
		Planner.clear();
		Ruins.clear();
	}

	/** Every so often, each faction whose seat and storage are loaded and whose crew is about looks for its next project. */
	private static void plan(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % PLAN_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || Blueprint.catalogue(f.type()).isEmpty() || !level.isPositionEntityTicking(f.seat()) || !Storage.loaded(level, f)) {
				continue;
			}
			if (NEXT_PLAN.getOrDefault(f.id(), 0L) > level.getGameTime() || Builders.crew(level, f).isEmpty() || !Planner.ready(level, f)) {
				continue;
			}
			if (Planner.plan(level, f, false, null) == null) {
				// nowhere to put it (or nothing left to build): look again later
				NEXT_PLAN.put(f.id(), level.getGameTime() + PLAN_RETRY_TICKS);
			}
		}
	}

	// ---- layouts --------------------------------------------------------------------------------------------------

	/** A project's layout (its blueprint drawn and set down), or null if its catalogue entry is gone. */
	public static Planner.@Nullable Layout layout(ServerLevel level, Faction f, Works.Project p) {
		String key = f.id() + "|" + p.entry() + "|" + p.origin().asLong() + "|" + p.rotation();
		Planner.Layout l = LAYOUTS.get(key);
		if (l != null) {
			return l;
		}
		Blueprint.Entry entry = Planner.entry(f, p.entry());
		if (entry == null) {
			return null;
		}
		l = Planner.layout(level, f, p.entry(), entry.maker().make(level, f), p.origin(), p.rotation(), p.plot() >= 0, p.ruins());
		LAYOUTS.put(key, l);
		return l;
	}

	/** What's left to pay for on a project (whole raw items), less what's already on site. */
	static Map<Item, Integer> remaining(ServerLevel level, Faction f, Works.Project p) {
		Planner.Layout l = layout(level, f, p);
		if (l == null) {
			return Map.of();
		}
		Map<Item, Integer> eighths = new LinkedHashMap<>();
		int from = p.stage() == Works.BUILD ? p.index() : 0;
		for (int i = from; i < l.ops().size(); i++) {
			Materials.Cost c = l.ops().get(i).cost();
			if (c != null) {
				eighths.merge(c.item(), c.eighths(), Integer::sum);
			}
		}
		p.credit().forEach((key, n) -> eighths.computeIfPresent(Materials.item(key), (item, v) -> v - n));
		return Materials.whole(eighths);
	}

	// ---- the brain ------------------------------------------------------------------------------------------------

	/** One builder's tick. */
	static void tick(ServerLevel level, Folk b) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Job job = JOBS.computeIfAbsent(b.getUUID(), u -> restore(b));
		if (b.getLastHurtByMob() != null && b.tickCount - b.getLastHurtByMobTimestamp() < FLEE_TICKS) {
			// (running for his life)
			return;
		}
		Faction f = Factions.get(server, b.factionId());
		List<Works.Project> projects = f == null ? List.of() : Works.of(server, f.id()).projects();
		if (f == null || f.fallen() || projects.isEmpty() || !Economy.atPeace(server, f)) {
			idle(level, f, b, job, now, true);
			return;
		}
		Works.Project p = projects.get(Math.floorMod(b.getUUID().hashCode(), projects.size()));
		Planner.Layout l = layout(level, f, p);
		if (l == null) {
			JaymosKingdoms.LOGGER.warn("{}'s {} at {} has no blueprint any more: dropped", f.name(), p.entry(), p.origin());
			cancel(level, f, p);
			return;
		}
		if (!job.working) {
			job.working = true;
			b.setHomeTo(Builders.depot(f), WORK_RADIUS);
		}
		b.setSite(p.origin());
		switch (job.phase) {
			case IDLE -> {
				if (now % DECIDE_TICKS == Math.floorMod(b.getId(), DECIDE_TICKS) || b.isCarrying()) {
					decide(level, f, p, l, b, job, now);
				}
				if (job.phase == IDLE) {
					idle(level, f, b, job, now, false);
				}
			}
			case STAKE -> stake(level, f, p, l, b, job, now);
			case FETCH -> {
				if (approach(level, b, job, Builders.depot(f), DEPOT_REACH, VERTICAL_SLACK, now)) {
					job.phase = LOAD;
					job.actAt = now + LOAD_TICKS;
				}
			}
			case LOAD -> load(level, f, p, l, b, job, now);
			case HAUL -> haul(level, f, p, l, b, job, now);
			case WORK -> work(level, f, p, l, b, job, now);
			default -> job.phase = IDLE;
		}
	}

	private static Job restore(Folk b) {
		Job job = new Job();
		if (b.isCarrying()) {
			job.phase = HAUL;
			job.fetch = raw(b.carried().getItem());
		}
		return job;
	}

	/** The raw item a load pays in (stone pays for cobblestone). */
	private static Item raw(Item item) {
		return item == Items.STONE ? Items.COBBLESTONE : item;
	}

	/** No work: back to the depot, his load (if any) put back in storage, and loafing about as builders do. */
	private static void idle(ServerLevel level, @Nullable Faction f, Folk b, Job job, long now, boolean offDuty) {
		job.phase = IDLE;
		if (f == null) {
			return;
		}
		BlockPos depot = Builders.depot(f);
		if (b.isCarrying()) {
			b.setTask("returning");
			if (approach(level, b, job, depot, DEPOT_REACH, VERTICAL_SLACK, now)) {
				ItemStack left = Storage.put(level, f, b.takeCarried());
				if (!left.isEmpty()) {
					Block.popResource(level, depot, left);
				}
			}
			return;
		}
		if (offDuty && job.working) {
			job.working = false;
			b.setHomeTo(depot, Builders.HOME_RADIUS);
			b.setTask("idle");
		}
	}

	/** Chooses his next step: stake the site (the foreman), fetch what the next blocks need, or lay them. */
	private static void decide(ServerLevel level, Faction f, Works.Project p, Planner.Layout l, Folk b, Job job, long now) {
		job.placed = 0;
		if (b.isCarrying()) {
			job.phase = HAUL;
			if (job.fetch == null) {
				job.fetch = raw(b.carried().getItem());
			}
			return;
		}
		if (p.stage() == Works.STAKE && (b.role() == Folk.FOREMAN || Builders.foreman(level, f) == null)) {
			job.phase = STAKE;
			b.setTask("staking");
			return;
		}
		Need need = deficit(level, p, l, inflight(level, f, b));
		if (need == null) {
			job.phase = WORK;
			b.setTask("building");
		} else if (WAITING.getOrDefault(f.id(), 0L) > now) {
			job.phase = IDLE;
			b.setTask("waiting for materials");
		} else {
			job.phase = FETCH;
			job.fetch = need.item();
			job.amount = need.amount();
			b.setTask("fetching");
		}
	}

	/** What the next blocks lack (beyond what's on site and on its way), or null. */
	record Need(Item item, int amount) {
	}

	private static @Nullable Need deficit(ServerLevel level, Works.Project p, Planner.Layout l, Map<Item, Integer> inflight) {
		Map<Item, Integer> avail = new HashMap<>(inflight);
		p.credit().forEach((key, n) -> avail.merge(Materials.item(key), n, Integer::sum));
		Map<Item, Integer> lack = new LinkedHashMap<>();
		int stage = p.stage() == Works.STAKE ? Works.PREP : p.stage();
		int i = p.stage() == Works.STAKE ? 0 : p.index();
		int uncovered = 0;
		for (int n = 0; n < LOOKAHEAD * 2 && uncovered < BLOCKS_PER_TRIP; n++) {
			if (stage == Works.PREP && i >= l.columns()) {
				stage = Works.BUILD;
				i = 0;
			}
			if (stage == Works.BUILD && i >= l.ops().size()) {
				break;
			}
			Materials.Cost c = cost(level, l, stage, i++);
			if (c == null) {
				continue;
			}
			int have = avail.getOrDefault(c.item(), 0);
			if (have >= c.eighths()) {
				avail.put(c.item(), have - c.eighths());
				continue;
			}
			avail.put(c.item(), 0);
			lack.merge(c.item(), c.eighths() - have, Integer::sum);
			uncovered++;
		}
		if (lack.isEmpty()) {
			return null;
		}
		var first = lack.entrySet().iterator().next();
		return new Need(first.getKey(), Math.min(LOAD_MAX, (first.getValue() + Materials.WHOLE - 1) / Materials.WHOLE));
	}

	/** What an op costs: a column's fill, or a block (nothing if it's already there). */
	private static Materials.@Nullable Cost cost(ServerLevel level, Planner.Layout l, int stage, int i) {
		if (stage == Works.PREP) {
			int fill = fill(level, l, i);
			return fill == 0 ? null : new Materials.Cost(Items.COBBLESTONE, fill * Materials.WHOLE);
		}
		Planner.Op op = l.ops().get(i);
		return op.cost() == null || level.getBlockState(op.pos()) == op.state() ? null : op.cost();
	}

	/** The loads other builders of the faction are carrying to the site (item → eighths). */
	private static Map<Item, Integer> inflight(ServerLevel level, Faction f, Folk self) {
		Map<Item, Integer> out = new HashMap<>();
		for (Folk other : Builders.crew(level, f)) {
			Job job = JOBS.get(other.getUUID());
			if (other != self && job != null && job.phase == HAUL && other.isCarrying()) {
				out.merge(job.fetch != null ? job.fetch : raw(other.carried().getItem()), other.carried().getCount() * Materials.WHOLE, Integer::sum);
			}
		}
		return out;
	}

	/** At the storage: takes the load out of the real containers (or finds there's none left, and waits). */
	private static void load(ServerLevel level, Faction f, Works.Project p, Planner.Layout l, Folk b, Job job, long now) {
		BlockPos depot = Builders.depot(f);
		b.getLookControl().setLookAt(depot.getX() + 0.5, depot.getY() + 0.5, depot.getZ() + 0.5);
		if (now < job.actAt) {
			return;
		}
		Need need = deficit(level, p, l, inflight(level, f, b));
		if (need == null) {
			job.phase = WORK;
			return;
		}
		ItemStack load = Storage.take(level, f, Materials.accepts(need.item()), need.amount());
		if (load.isEmpty()) {
			WAITING.put(f.id(), now + WAIT_TICKS);
			JaymosKingdoms.LOGGER.debug("{}'s builders are out of {}", f.name(), need.item());
			job.phase = IDLE;
			b.setTask("waiting for materials");
			return;
		}
		b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, depot, SoundEvents.BARREL_OPEN, SoundSource.NEUTRAL, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		b.setCarried(load);
		job.fetch = need.item();
		job.phase = HAUL;
		b.setTask("hauling");
	}

	/** Carrying a load to the site: there, it goes onto the pile (the project's credit) and he sets to work. */
	private static void haul(ServerLevel level, Faction f, Works.Project p, Planner.Layout l, Folk b, Job job, long now) {
		if (!b.isCarrying()) {
			decide(level, f, p, l, b, job, now);
			return;
		}
		if (!approach(level, b, job, target(l, p), PLACE_REACH, TALL_REACH, now)) {
			return;
		}
		ItemStack load = b.takeCarried();
		Item item = job.fetch != null ? job.fetch : raw(load.getItem());
		Works.Project cur = Works.project(level.getServer(), f.id(), p);
		if (cur == null) {
			b.setCarried(load);
			job.phase = IDLE;
			return;
		}
		Map<String, Integer> credit = new HashMap<>(cur.credit());
		credit.merge(Materials.key(item), load.getCount() * Materials.WHOLE, Integer::sum);
		Works.putProject(level.getServer(), f.id(), cur.withCredit(credit));
		level.playSound(null, b.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 0.8F);
		job.fetch = null;
		job.phase = WORK;
		job.placed = 0;
		job.actAt = now + PLACE_TICKS / 2;
		b.setTask("building");
	}

	/** Where the next op is (its column, or its block), or the site's middle while it's being staked. */
	private static BlockPos target(Planner.Layout l, Works.Project p) {
		if (p.stage() == Works.PREP && p.index() < l.columns()) {
			return l.column(p.index());
		}
		if (p.stage() == Works.BUILD && p.index() < l.ops().size()) {
			return l.ops().get(p.index()).pos();
		}
		return l.center();
	}

	/** The foreman stakes the site out: a fence post at each corner and a sign saying what goes up. */
	private static void stake(ServerLevel level, Faction f, Works.Project p, Planner.Layout l, Folk b, Job job, long now) {
		if (!approach(level, b, job, l.center(), PLACE_REACH + 2.0, TALL_REACH, now)) {
			return;
		}
		Works.Project cur = Works.project(level.getServer(), f.id(), p);
		if (cur == null || cur.stage() != Works.STAKE) {
			decide(level, f, cur == null ? p : cur, l, b, job, now);
			return;
		}
		Palette palette = Palette.at(level, f.seat());
		Direction toSeat = Direction.getApproximateNearest(f.seat().getX() - l.center().getX(), 0, f.seat().getZ() - l.center().getZ());
		List<BlockPos> stakes = new ArrayList<>();
		int[][] corners = {{l.min().getX() - 1, l.min().getZ() - 1}, {l.max().getX() + 1, l.min().getZ() - 1}, {l.min().getX() - 1, l.max().getZ() + 1},
			{l.max().getX() + 1, l.max().getZ() + 1}};
		// the sign on the corner nearest the seat
		int signAt = 0;
		double nearest = Double.MAX_VALUE;
		for (int i = 0; i < corners.length; i++) {
			double d = f.seat().distSqr(new BlockPos(corners[i][0], f.seat().getY(), corners[i][1]));
			if (d < nearest) {
				nearest = d;
				signAt = i;
			}
		}
		for (int i = 0; i < corners.length; i++) {
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(corners[i][0], 0, corners[i][1]));
			BlockState there = level.getBlockState(top);
			if (!(there.isAir() || there.canBeReplaced()) || !there.getFluidState().isEmpty() || !level.getBlockState(top.below()).isSolid()
				|| Math.abs(top.getY() - l.min().getY()) > Planner.LEVEL_TOLERANCE + 2) {
				continue;
			}
			BlockState state = i == signAt
				? Buildings.Kit.sign(palette).defaultBlockState().setValue(BlockStateProperties.ROTATION_16, RotationSegment.convertToSegment(toSeat))
				: palette.fence();
			level.setBlock(top, state, Block.UPDATE_ALL);
			effects(level, top, state);
			if (i == signAt && level.getBlockEntity(top) instanceof SignBlockEntity sign) {
				Component[] lines = {Component.literal(f.name()), Component.translatableWithFallback("building.jaymoskingdoms." + p.entry(), p.entry()),
					Component.translatable("sign.jaymoskingdoms.under_construction"), Component.empty()};
				sign.setText(new SignText(List.of(lines), List.of(lines), DyeColor.BLACK, false), SignTextSlot.FRONT);
				level.sendBlockUpdated(top, state, state, Block.UPDATE_ALL);
			}
			stakes.add(top);
		}
		b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Works.putProject(level.getServer(), f.id(), cur.withStakes(stakes).at(Works.PREP, 0));
		decide(level, f, cur, l, b, job, now);
	}

	/** At the site: readies the next column or lays the next block, if what it needs is on the pile; else goes for it. */
	private static void work(ServerLevel level, Faction f, Works.Project p, Planner.Layout l, Folk b, Job job, long now) {
		MinecraftServer server = level.getServer();
		Works.Project cur = Works.project(server, f.id(), p);
		if (cur == null) {
			job.phase = IDLE;
			return;
		}
		if (cur.stage() == Works.STAKE) {
			// waiting on the foreman's stakes
			if (b.role() == Folk.FOREMAN || Builders.foreman(level, f) == null) {
				job.phase = STAKE;
			} else {
				approach(level, b, job, l.center(), PLACE_REACH + 2.0, TALL_REACH, now);
			}
			return;
		}
		// skip what's already done (or isn't ours to touch) without stopping
		for (int skip = 0; skip < SKIP_PER_TICK && cur.stage() == Works.PREP && cur.index() < l.columns(); skip++) {
			if (needsPrep(level, l, cur.index())) {
				break;
			}
			cur = cur.at(Works.PREP, cur.index() + 1);
			Works.putProject(server, f.id(), cur);
		}
		for (int skip = 0; skip < SKIP_PER_TICK && cur.stage() == Works.BUILD && cur.index() < l.ops().size(); skip++) {
			Planner.Op op = l.ops().get(cur.index());
			BlockState there = level.getBlockState(op.pos());
			if (there != op.state() && (l.plot() || there.isAir() || clearable(there) || (l.ruins() && Ruins.tearable(level, op.pos(), there)))) {
				break;
			}
			cur = cur.at(Works.BUILD, cur.index() + 1);
			Works.putProject(server, f.id(), cur);
		}
		int total = cur.stage() == Works.PREP ? l.columns() : l.ops().size();
		if (cur.index() >= total) {
			if (cur.stage() == Works.PREP) {
				Works.putProject(server, f.id(), cur.at(Works.BUILD, 0));
			} else {
				finish(level, f, cur, l);
				job.phase = IDLE;
			}
			return;
		}
		BlockPos at = target(l, cur);
		if (now < job.actAt) {
			b.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
			return;
		}
		Materials.Cost cost = cost(level, l, cur.stage(), cur.index());
		String key = cost == null ? null : Materials.key(cost.item());
		if (cost != null && cur.credit().getOrDefault(key, 0) < cost.eighths()) {
			// the pile's short: fetch it, or wait for whoever is bringing it
			decide(level, f, cur, l, b, job, now);
			if (job.phase == WORK) {
				job.actAt = now + PLACE_TICKS;
				approach(level, b, job, at, PLACE_REACH, TALL_REACH, now);
			}
			return;
		}
		if (!approach(level, b, job, at, PLACE_REACH, TALL_REACH, now)) {
			return;
		}
		boolean done = cur.stage() == Works.PREP ? prep(level, f, l, cur.index()) : place(level, f, l, l.ops().get(cur.index()), b);
		if (!done) {
			// (someone in the way: try again shortly)
			job.actAt = now + PLACE_TICKS;
			return;
		}
		Map<String, Integer> credit = new HashMap<>(cur.credit());
		if (cost != null) {
			credit.merge(key, -cost.eighths(), Integer::sum);
		}
		Works.putProject(server, f.id(), cur.at(cur.stage(), cur.index() + 1).withCredit(credit));
		b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		job.placed++;
		job.actAt = now + PLACE_TICKS + level.getRandom().nextInt(PLACE_JITTER_TICKS + 1);
		if (job.placed >= BLOCKS_PER_TRIP) {
			decide(level, f, Works.project(server, f.id(), p) == null ? cur : Works.project(server, f.id(), p), l, b, job, now);
		}
	}

	/** Natural ground or plants: what may be dug or built over outside a plot. */
	static boolean clearable(BlockState state) {
		return state.isAir() || Storehouse.ground(state) || (state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	/** What may be broken where the building goes: anything breakable on a plot; else natural ground, or an old structure's blocks on a site over one. */
	private static boolean breakable(ServerLevel level, Planner.Layout l, BlockPos at, BlockState state) {
		if (l.plot()) {
			return state.getDestroySpeed(level, at) >= 0;
		}
		return clearable(state) || (l.ruins() && Ruins.tearable(level, at, state));
	}

	/** A block readying the ground breaks: {@link #breakable} up to the usual height; above it (an old structure's columns) only the structure's own. */
	private static boolean inWay(ServerLevel level, Planner.Layout l, BlockPos at, BlockState state) {
		int floor = l.min().getY();
		if (at.getY() > floor + l.height() + CLEAR_ABOVE) {
			return l.ruins() && Ruins.tearable(level, at, state);
		}
		return breakable(level, l, at, state);
	}

	/** A broken block goes into storage: anything on a plot; an old structure's blocks (not the land's own dirt and stone). */
	private static boolean salvaged(Planner.Layout l, BlockState state) {
		return l.plot() || (l.ruins() && !clearable(state));
	}

	/** How high a column is cleared: the building and a little air over it; on an old structure, as high as its pieces reach there. */
	private static int clearTop(ServerLevel level, Planner.Layout l, BlockPos column) {
		int top = column.getY() + l.height() + CLEAR_ABOVE;
		if (l.ruins()) {
			top = Math.max(top, Math.min(Ruins.pieceTop(level, column.getX(), column.getZ()), column.getY() + Ruins.TEAR_HEIGHT));
		}
		return top;
	}

	/** How many blocks of fill column {@code i} needs under its floor (only where the building has a floor block). */
	private static int fill(ServerLevel level, Planner.Layout l, int i) {
		BlockPos column = l.column(i);
		if (!l.filled().contains(column)) {
			return 0;
		}
		int n = 0;
		for (int y = column.getY() - 1; y >= column.getY() - FILL_MAX; y--) {
			BlockState state = level.getBlockState(column.atY(y));
			if (!(state.isAir() || state.canBeReplaced())) {
				break;
			}
			n++;
		}
		return n;
	}

	/** Column {@code i} has something to dig out or fill. */
	private static boolean needsPrep(ServerLevel level, Planner.Layout l, int i) {
		if (fill(level, l, i) > 0) {
			return true;
		}
		BlockPos column = l.column(i);
		for (int y = column.getY(), top = clearTop(level, l, column); y <= top; y++) {
			BlockPos at = column.atY(y);
			BlockState state = level.getBlockState(at);
			if (!state.isAir() && !l.filled().contains(at) && inWay(level, l, at, state)) {
				return true;
			}
		}
		return false;
	}

	/** Readies column {@code i}: digs out what's in the building's way above the floor, fills any hollow under it. */
	private static boolean prep(ServerLevel level, Faction f, Planner.Layout l, int i) {
		BlockPos column = l.column(i);
		int floor = column.getY();
		for (int y = clearTop(level, l, column); y >= floor; y--) {
			BlockPos at = column.atY(y);
			BlockState state = level.getBlockState(at);
			if (state.isAir() || l.filled().contains(at)) {
				continue;
			}
			if (inWay(level, l, at, state)) {
				remove(level, f, at, state, salvaged(l, state), !l.plot());
			}
		}
		int fill = fill(level, l, i);
		for (int y = floor - 1; y >= floor - fill; y--) {
			BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
			level.setBlock(column.atY(y), stone, Block.UPDATE_CLIENTS);
			effects(level, column.atY(y), stone);
		}
		return true;
	}

	/**
	 * Breaks a block out of the way: on a plot or an old structure, it (and anything in it) goes into the faction's
	 * storage. What doesn't fit is dropped at the depot, except an old structure's {@code rubble} (its blocks, not what
	 * its chests held), which is left.
	 */
	private static void remove(ServerLevel level, Faction f, BlockPos at, BlockState state, boolean keep, boolean rubble) {
		if (keep) {
			BlockEntity entity = level.getBlockEntity(at);
			List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, at, entity));
			List<ItemStack> held = new ArrayList<>();
			if (entity instanceof Container container) {
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					if (!container.getItem(slot).isEmpty()) {
						held.add(container.getItem(slot).copy());
					}
				}
				container.clearContent();
			}
			for (ItemStack stack : drops) {
				ItemStack left = Storage.put(level, f, stack);
				if (!left.isEmpty() && !rubble) {
					Block.popResource(level, Builders.depot(f), left);
				}
			}
			for (ItemStack stack : held) {
				ItemStack left = Storage.put(level, f, stack);
				if (!left.isEmpty()) {
					Block.popResource(level, Builders.depot(f), left);
				}
			}
		}
		if (!quiet) {
			level.levelEvent(2001, at, Block.getId(state));
		}
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	/** Lays one block. False if a player stands in the way (he'll try again). */
	private static boolean place(ServerLevel level, Faction f, Planner.Layout l, Planner.Op op, @Nullable Folk b) {
		BlockPos at = op.pos();
		BlockState there = level.getBlockState(at);
		if (there == op.state()) {
			return true;
		}
		if (!there.isAir() && !there.canBeReplaced()) {
			if (!breakable(level, l, at, there)) {
				// built since the site was chosen: left alone
				return true;
			}
			remove(level, f, at, there, salvaged(l, there), !l.plot());
		}
		if (!op.state().getCollisionShape(level, at).isEmpty()) {
			for (LivingEntity in : level.getEntitiesOfClass(LivingEntity.class, new AABB(at), LivingEntity::isAlive)) {
				if (in instanceof Player) {
					return false;
				}
				BlockPos out = standable(level, at.offset(0, 1, 0), 3);
				in.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
			}
		}
		BlockState state = shaped(level, at, op.state());
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos n = at.relative(side);
			BlockState ns = level.getBlockState(n);
			if (connects(ns)) {
				BlockState updated = Block.updateFromNeighbourShapes(ns, level, n);
				if (updated != ns) {
					level.setBlock(n, updated, Block.UPDATE_CLIENTS);
				}
			}
		}
		effects(level, at, state);
		return true;
	}

	private static boolean connects(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock;
	}

	/** Fences, panes, walls and stairs joined up with what's around them. */
	private static BlockState shaped(ServerLevel level, BlockPos at, BlockState state) {
		return connects(state) ? Block.updateFromNeighbourShapes(state, level, at) : state;
	}

	/** The sound of a block going down, and a few chips of it. */
	private static void effects(ServerLevel level, BlockPos at, BlockState state) {
		if (quiet) {
			return;
		}
		var sound = state.getSoundType();
		level.playSound(null, at, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.25, 0.25,
				0.25, 0.05);
		}
	}

	/** The building's done: stakes pulled, recorded among the faction's buildings, the news told round the seat. */
	private static void finish(ServerLevel level, Faction f, Works.Project p, Planner.Layout l) {
		MinecraftServer server = level.getServer();
		for (BlockPos stake : p.stakes()) {
			BlockState state = level.getBlockState(stake);
			if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof SignBlock) {
				level.levelEvent(2001, stake, Block.getId(state));
				level.setBlock(stake, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		Works.update(server, f.id(), s -> {
			List<Works.Project> projects = new ArrayList<>(s.projects());
			projects.removeIf(x -> x.same(p));
			List<Works.Built> built = new ArrayList<>(s.built());
			built.add(new Works.Built(p.entry(), l.min(), l.max()));
			List<Works.Plot> plots = new ArrayList<>(s.plots());
			plots.replaceAll(plot -> plot.id() == p.plot() ? plot.withBuilding(plot.building(), true) : plot);
			return s.withProjects(projects).withBuilt(built).withPlots(plots);
		});
		LAYOUTS.values().removeIf(x -> x == l);
		Planner.forget(f.id());
		level.playSound(null, l.center(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		Component news = Component.translatable("message.jaymoskingdoms.build.finished", f.name(),
			Component.translatableWithFallback("building.jaymoskingdoms." + p.entry(), p.entry())).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(f.seat().getX(), f.seat().getY(), f.seat().getZ()) <= ANNOUNCE_RANGE * ANNOUNCE_RANGE) {
				player.sendSystemMessage(news);
			}
		}
		JaymosKingdoms.LOGGER.info("{} finished its {} at {}", f.name(), p.entry(), p.origin());
		Gatherers.built(level, f, p.entry(), l.min(), l.max());
		Homes.built(level, f, p.entry(), l.min(), l.max());
	}

	/** Drops a project (half-built as it stands); its stakes are pulled and what was on site goes back to storage. */
	static void cancel(ServerLevel level, Faction f, Works.Project p) {
		for (BlockPos stake : p.stakes()) {
			BlockState state = level.getBlockState(stake);
			if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof SignBlock) {
				level.setBlock(stake, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		p.credit().forEach((key, eighths) -> Storage.put(level, f, Materials.item(key), eighths / Materials.WHOLE));
		Works.update(level.getServer(), f.id(), s -> {
			List<Works.Project> projects = new ArrayList<>(s.projects());
			projects.removeIf(x -> x.same(p));
			return s.withProjects(projects);
		});
		Planner.forget(f.id());
	}

	/** Finishes a project at once (operators): every remaining column readied and block laid, free. */
	static void finishNow(ServerLevel level, Faction f, Works.Project p) {
		Planner.Layout l = layout(level, f, p);
		if (l == null) {
			cancel(level, f, p);
			return;
		}
		int fromColumn = p.stage() == Works.BUILD ? l.columns() : p.stage() == Works.PREP ? p.index() : 0;
		for (int i = fromColumn; i < l.columns(); i++) {
			prep(level, f, l, i);
		}
		for (int i = p.stage() == Works.BUILD ? p.index() : 0; i < l.ops().size(); i++) {
			Planner.Op op = l.ops().get(i);
			BlockState there = level.getBlockState(op.pos());
			if (there != op.state() && (l.plot() || clearable(there) || (l.ruins() && Ruins.tearable(level, op.pos(), there)))) {
				if (!there.isAir() && salvaged(l, there)) {
					remove(level, f, op.pos(), there, true, !l.plot());
				}
				level.setBlock(op.pos(), shaped(level, op.pos(), op.state()), Block.UPDATE_CLIENTS);
			}
		}
		finish(level, f, Works.project(level.getServer(), f.id(), p) == null ? p : Works.project(level.getServer(), f.id(), p), l);
	}

	/** Sleep's catch-up ({@link Nights}): a builder's ticks a block, on average (fetching and hauling included), and the most done at once. */
	static final int CATCH_UP_TICKS_PER_BLOCK = 60, CATCH_UP_MOST = 1500;

	/**
	 * The night the players slept through (0.5.1, {@link Nights}): up to {@code blocks} more of the project done at once,
	 * columns readied and blocks laid just as the crew would have, each paid from what's on site and then from storage
	 * (a load at a time). It stops where storage runs short; a site that isn't wholly loaded is left alone. Returns how
	 * many it did.
	 */
	static int advance(ServerLevel level, Faction f, Works.Project p, int blocks) {
		MinecraftServer server = level.getServer();
		Planner.Layout l = layout(level, f, p);
		Works.Project cur = Works.project(server, f.id(), p);
		if (l == null || cur == null || blocks <= 0 || !level.hasChunksAt(l.min().getX() - 1, l.min().getZ() - 1, l.max().getX() + 1, l.max().getZ() + 1)) {
			return 0;
		}
		if (cur.stage() == Works.STAKE) {
			// (no stakes for a site that went up overnight)
			cur = cur.at(Works.PREP, 0);
		}
		Map<String, Integer> credit = new HashMap<>(cur.credit());
		int done = 0;
		quiet = true;
		try {
			while (done < blocks) {
				int total = cur.stage() == Works.PREP ? l.columns() : l.ops().size();
				int i = cur.index();
				if (i >= total) {
					if (cur.stage() == Works.PREP) {
						cur = cur.at(Works.BUILD, 0);
						continue;
					}
					Works.putProject(server, f.id(), cur.withCredit(credit));
					finish(level, f, cur, l);
					return done;
				}
				if (cur.stage() == Works.PREP ? !needsPrep(level, l, i) : !wanted(level, l, l.ops().get(i))) {
					cur = cur.at(cur.stage(), i + 1);
					continue;
				}
				Materials.Cost cost = cost(level, l, cur.stage(), i);
				if (cost != null && !pay(level, f, credit, cost)) {
					break;
				}
				if (cur.stage() == Works.PREP) {
					prep(level, f, l, i);
				} else if (!place(level, f, l, l.ops().get(i), null)) {
					// (a player standing in it: the crew will lay it in the morning)
					if (cost != null) {
						credit.merge(Materials.key(cost.item()), cost.eighths(), Integer::sum);
					}
					break;
				}
				cur = cur.at(cur.stage(), i + 1);
				done++;
			}
		} finally {
			quiet = false;
		}
		Works.putProject(server, f.id(), cur.withCredit(credit));
		return done;
	}

	/** A block of the layout still to lay (what {@link #work} stops at). */
	private static boolean wanted(ServerLevel level, Planner.Layout l, Planner.Op op) {
		BlockState there = level.getBlockState(op.pos());
		return there != op.state() && (l.plot() || there.isAir() || clearable(there) || (l.ruins() && Ruins.tearable(level, op.pos(), there)));
	}

	/** Takes {@code cost} off the site's pile ({@code credit}), topping it up from storage a load at a time. False if storage is short. */
	private static boolean pay(ServerLevel level, Faction f, Map<String, Integer> credit, Materials.Cost cost) {
		String key = Materials.key(cost.item());
		int have = credit.getOrDefault(key, 0);
		while (have < cost.eighths()) {
			ItemStack load = Storage.take(level, f, Materials.accepts(cost.item()), LOAD_MAX);
			if (load.isEmpty()) {
				credit.put(key, have);
				return false;
			}
			have += load.getCount() * Materials.WHOLE;
		}
		credit.put(key, have - cost.eighths());
		return true;
	}

	/** (sleep's catch-up lays its blocks without a sound) */
	private static boolean quiet;

	// ---- getting about --------------------------------------------------------------------------------------------

	/**
	 * Walks {@code b} toward {@code goal} (in legs, as paths are short). True once he's within {@code reach} across and
	 * {@code up} up or down. If he makes no progress for {@link #STUCK_TICKS}, he's moved there in a puff of smoke.
	 */
	private static boolean approach(ServerLevel level, Folk b, Job job, BlockPos goal, double reach, double up, long now) {
		if (!goal.equals(job.goal)) {
			job.goal = goal;
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			job.repathAt = 0;
		}
		double dx = goal.getX() + 0.5 - b.getX(), dz = goal.getZ() + 0.5 - b.getZ(), dy = goal.getY() - b.getY();
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= reach && Math.abs(dy) <= up) {
			b.getNavigation().stop();
			b.getLookControl().setLookAt(goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5);
			return true;
		}
		double dist = across + Math.max(0.0, Math.abs(dy) - up);
		if (dist < job.best - 0.5) {
			job.best = dist;
			job.improvedAt = now;
		}
		if (now - job.improvedAt > STUCK_TICKS) {
			BlockPos to = standable(level, goal, 4);
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

	/** (0.5.1) Where to walk next on the way to {@code goal}: the goal itself when it's near, else a spot {@link #LEG} along the way (paths are short). */
	static BlockPos leg(ServerLevel level, net.minecraft.world.entity.Entity walker, BlockPos goal) {
		double dx = goal.getX() + 0.5 - walker.getX(), dz = goal.getZ() + 0.5 - walker.getZ();
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= LEG) {
			return goal;
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			BlockPos.containing(walker.getX() + dx / across * LEG, walker.getY(), walker.getZ() + dz / across * LEG));
	}

	/** A spot near {@code near} where one can stand (solid below, two blocks of room); the surface there if none. */
	static BlockPos standable(ServerLevel level, BlockPos near, int radius) {
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = 0; dy <= 3; dy = dy <= 0 ? 1 - dy : -dy) {
						BlockPos at = near.offset(dx, dy, dz);
						if (room(level, at) && room(level, at.above()) && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()
							&& level.getFluidState(at).isEmpty()) {
							return at;
						}
						if (dy < -2) {
							break;
						}
					}
				}
			}
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
	}

	private static boolean room(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
	}

	// ---- for commands -------------------------------------------------------------------------------------------

	/** Whether the faction's builders are waiting on materials. */
	static boolean waiting(ServerLevel level, Faction f) {
		return WAITING.getOrDefault(f.id(), 0L) > level.getGameTime();
	}

	/** Look in storage again now. */
	static void stopWaiting(Faction f) {
		WAITING.remove(f.id());
		NEXT_PLAN.remove(f.id());
	}
}
