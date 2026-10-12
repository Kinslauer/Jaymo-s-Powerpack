package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's gatherers (0.5.2), who fill its storehouse with what they get with their own hands (you see them at
 * it, and what they carry): {@link Folk#MINER}s work its mine ({@link Mines}) once it's dug, taking any ore they can see
 * from its tunnel or the cave it broke into, and otherwise driving side tunnels off it for stone (following any vein
 * they uncover); {@link Folk#WOODCUTTER}s fell trees (the tree farm's first, then the wild ones round it) and plant a
 * sapling where each stood. Both carry their load to storage when their arms are full and rest at night. The farmers
 * ({@link Farmers}) keep the fields and the pens (the butchering is theirs). This class also sees to a new tree farm,
 * pen or field: its keeper taken on, a pen's first beasts bought in.
 */
public final class Gatherers {
	/** Miners for a mine; woodcutters for a tree farm. */
	static final int MINERS_PER_MINE = 2, WOODCUTTERS_PER_FARM = 1;
	/** How far from home they may go (to storage and back, and the trees round about). */
	static final int ROAM = 128;
	/** How often an idle one looks for work (ticks). */
	static final int LOOK_TICKS = 40;
	/** They carry this many items before taking them to storage. */
	static final int ARMFUL = 48;
	/** A block of stone takes this long to dig, an ore this long, a log this long (ticks); from this far. */
	/** (0.5.21) A miner puts a torch where he's dug out if the block light there is under this. */
	static final int TORCH_BELOW = 7;
	static final int STONE_TICKS = 30, ORE_TICKS = 50, LOG_TICKS = 16, FELL_START_TICKS = 30, FELL_WAIT_TICKS = 40;
	/** (0.5.25) A woodcutter's chops at a tree come this far apart (ticks). */
	static final int CHOP_TICKS = 12;
	static final double REACH = 4.5, STAND_REACH = 1.5, DEPOT_REACH = 2.5, VERTICAL_SLACK = 3.0;
	/** With nothing else to dig, a miner quarries the rock this far round the mine's end. */
	static final int QUARRY_REACH = 6;
	/** Side tunnels: off every {@link Mines#SUPPORT_EVERY}th step (between the posts), both ways, this long, two high. */
	static final int BRANCH_LONG = 16;
	/** Ore is sought this far either side of the tunnel, and this far round the end of a mine that found a cave. */
	static final int ORE_SIDE = 3, CAVE_REACH = 8;
	/** Trees are sought this far round a woodcutter's home; one has at most this many logs. */
	static final int TREE_RANGE = 28, TREE_MOST = 160, TREE_SPREAD = 8;
	/** No nearer his goal for this long (ticks) and he gives it up. */
	static final int STUCK_TICKS = 240;
	static final double WALK_SPEED = 0.65;
	/** A pen's first beasts: this many of each kind. */
	static final int PEN_STOCK = 2;
	/** (0.5.27) The horses bought in for a new stable. */
	static final int STABLE_HORSES = 2;
	/** Sleep's catch-up ({@link Nights}): a gatherer's ticks an item, the most at once, and a sapling's ticks a stage. */
	static final int MINER_TICKS_PER_ITEM = 50, WOOD_TICKS_PER_LOG = 40, CATCH_UP_MOST = 256, SAPLING_TICKS_PER_STAGE = 5000;

	private static final int IDLE = 0, DIG = 1, FELL = 2, DELIVER = 3;

	// ---- what they carry, and getting about -------------------------------------------------------------------------

	/** What one carries (the biggest stack shows in his hands; on his death only that is dropped). */
	static final class Bag {
		final List<ItemStack> items = new ArrayList<>();

		int count() {
			return this.items.stream().mapToInt(ItemStack::getCount).sum();
		}

		boolean isEmpty() {
			return this.items.isEmpty();
		}

		void add(Worker folk, ItemStack stack) {
			if (stack.isEmpty()) {
				return;
			}
			ItemStack rest = stack.copy();
			for (ItemStack held : this.items) {
				if (ItemStack.isSameItemSameComponents(held, rest) && held.getCount() < held.getMaxStackSize()) {
					int n = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
					held.grow(n);
					rest.shrink(n);
				}
			}
			if (!rest.isEmpty()) {
				this.items.add(rest);
			}
			ItemStack show = this.items.stream().max(Comparator.comparingInt(ItemStack::getCount)).orElse(ItemStack.EMPTY);
			folk.setCarried(show.copyWithCount(1));
		}

		/** Into storage (what won't fit is dropped by the depot), his tool back in his hands. */
		void unload(ServerLevel level, Faction f, Worker folk) {
			BlockPos depot = Builders.depot(f);
			for (ItemStack stack : this.items) {
				ItemStack left = Storage.put(level, f, stack);
				if (!left.isEmpty()) {
					Block.popResource(level, depot, left);
				}
			}
			this.items.clear();
			folk.setCarried(ItemStack.EMPTY);
			folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, depot, SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.5F, 1.0F);
		}
	}

	/** How a walk is going: the nearest he's been, and when he last got nearer. */
	static final class Trip {
		double best = Double.MAX_VALUE;
		long improvedAt;

		void reset(long now) {
			this.best = Double.MAX_VALUE;
			this.improvedAt = now;
		}
	}

	/** Walks {@code folk} toward {@code to} (a leg at a time); true once within {@code reach} across and {@link #VERTICAL_SLACK} up or down. */
	static boolean walk(Worker folk, Trip trip, BlockPos to, double reach, double speed, long now) {
		Vec3 goal = Vec3.atBottomCenterOf(to);
		double dx = folk.getX() - goal.x, dz = folk.getZ() - goal.z;
		double d = Math.sqrt(folk.distanceToSqr(goal));
		if (Math.sqrt(dx * dx + dz * dz) <= reach && Math.abs(folk.getY() - goal.y) <= VERTICAL_SLACK) {
			folk.getNavigation().stop();
			return true;
		}
		if (d < trip.best - 0.5) {
			trip.best = d;
			trip.improvedAt = now;
		}
		ServerLevel level = (ServerLevel)folk.level();
		if (now - trip.improvedAt > STUCK_TICKS) {
			// (no way there, shut in a house or up a hill: he turns up there in a puff, as builders do)
			BlockPos at = Construction.standable(level, to, 3);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			folk.getNavigation().stop();
			folk.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			trip.reset(now);
			return false;
		}
		if (folk.getNavigation().isDone() || now % 20 == 0) {
			BlockPos leg = Construction.leg(level, folk.mob(), to);
			folk.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, speed);
		}
		return false;
	}

	// ---- taking them on ---------------------------------------------------------------------------------------------

	private static final class Job {
		int phase = IDLE;
		@Nullable BlockPos goal, stand;
		final Bag bag = new Bag();
		final Trip trip = new Trip();
		final Deque<BlockPos> vein = new ArrayDeque<>();
		final List<BlockPos> logs = new ArrayList<>();
		final Set<BlockPos> skip = new HashSet<>();
		@Nullable BlockState sapling;
		boolean felled;
		long actAt;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private Gatherers() {
	}

	static void clear() {
		JOBS.clear();
	}

	/** (0.5.26) {@code w} is a miner or woodcutter walking a load to storage. */
	static boolean delivering(Worker w) {
		Job job = isGatherer(w.role()) ? JOBS.get(w.getUUID()) : null;
		return job != null && job.phase == DELIVER && !job.bag.isEmpty();
	}

	/** (0.5.26) A rickshaw met him on his way to storage: his load's taken (returned) and he goes back to work. */
	static List<ItemStack> handOver(Worker w) {
		Job job = isGatherer(w.role()) ? JOBS.get(w.getUUID()) : null;
		if (job == null || job.phase != DELIVER) {
			return List.of();
		}
		List<ItemStack> load = new ArrayList<>(job.bag.items);
		job.bag.items.clear();
		w.setCarried(ItemStack.EMPTY);
		job.phase = IDLE;
		w.setTask("idle");
		return load;
	}

	static boolean isGatherer(int role) {
		return role == Folk.MINER || role == Folk.WOODCUTTER;
	}

	/** Its tree farms. */
	static List<Works.Built> treeFarms(MinecraftServer server, Faction f) {
		return Works.of(server, f.id()).built().stream().filter(b -> b.entry().equals("tree_farm")).toList();
	}

	/** It wants one more of {@code role}: a mine dug short of miners, or a tree farm without its woodcutter (counting places waiting to be filled). */
	static boolean wanted(MinecraftServer server, Faction f, int role) {
		int have = f.holdings().count(role) + f.holdings().vacancies().getOrDefault(Folk.roleName(role), 0);
		if (role == Folk.MINER) {
			return Mines.dug(server, f) && have < Focus.miners(server, f, MINERS_PER_MINE);
		}
		return role == Folk.WOODCUTTER && have < treeFarms(server, f).size() * WOODCUTTERS_PER_FARM;
	}

	/** Takes on {@code n} of {@code role} at once (from its villagers if it can spare them, else new hands). */
	static void hire(ServerLevel level, Faction f, int role, int n) {
		for (int i = 0; i < n; i++) {
			Faction now = Factions.get(level.getServer(), f.id());
			if (now == null || spawn(level, now, role, Economy.recruit(level, now)) == null) {
				return;
			}
		}
	}

	/**
	 * One of {@code role} for {@code f} (counted in its roster): a miner lives by its mine's mouth, a woodcutter at the
	 * tree farm with fewest woodcutters. {@code from} where he comes from (a villager taken into service), else he starts
	 * at home. Null if there's nothing for him to work.
	 */
	static @Nullable Worker spawn(ServerLevel level, Faction f, int role, @Nullable BlockPos from) {
		BlockPos home, site;
		if (role == Folk.MINER) {
			Mines.Mine m = Mines.of(level.getServer(), f.id());
			if (m == null) {
				return null;
			}
			home = Construction.standable(level, m.entrance(), 3);
			site = m.end();
		} else {
			List<Works.Built> farms = treeFarms(level.getServer(), f);
			if (farms.isEmpty()) {
				return null;
			}
			List<Worker> cutters = Workers.near(level, new AABB(f.seat()).inflate(Builders.CREW_RANGE),
				x -> x.isAlive() && x.factionId() == f.id() && x.role() == Folk.WOODCUTTER);
			Works.Built farm = farms.stream().min(Comparator.comparingLong(b -> cutters.stream().filter(x -> middle(b).closerThan(x.getHomePosition(), 3)).count()))
				.orElseThrow();
			home = Construction.standable(level, middle(farm).above(), 4);
			site = home;
		}
		Worker folk = Workers.body(level, f, role, from != null ? from : home, home, ROAM);
		if (folk == null) {
			return null;
		}
		folk.setSite(site);
		Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(role, 1)));
		JaymosKingdoms.LOGGER.debug("{} took on a {}", f.name(), Folk.roleName(role));
		return folk;
	}

	private static BlockPos middle(Works.Built b) {
		return new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY(), (b.min().getZ() + b.max().getZ()) / 2);
	}

	/** A building's just been finished ({@link Construction}): a tree farm gets its woodcutter, a pen its first beasts, a stable its horses, either kind of farm its farmer. */
	static void built(ServerLevel level, Faction f, String entry, BlockPos min, BlockPos max) {
		MinecraftServer server = level.getServer();
		if (entry.equals("tree_farm") && wanted(server, f, Folk.WOODCUTTER)) {
			hire(level, f, Folk.WOODCUTTER, 1);
		}
		if (entry.equals("pen")) {
			BlockPos mid = new BlockPos((min.getX() + max.getX()) / 2, min.getY() + 1, (min.getZ() + max.getZ()) / 2);
			for (EntityType<?> kind : List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.CHICKEN)) {
				for (int i = 0; i < PEN_STOCK; i++) {
					BlockPos at = Construction.standable(level, mid.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2), 2);
					kind.spawn(level, at, EntitySpawnReason.SPAWN_ITEM_USE);
				}
			}
		}
		if (entry.equals("stable")) {
			BlockPos mid = new BlockPos((min.getX() + max.getX()) / 2, min.getY() + 1, (min.getZ() + max.getZ()) / 2);
			for (int i = 0; i < STABLE_HORSES; i++) {
				BlockPos at = Construction.standable(level, mid.offset(level.getRandom().nextInt(3) - 1, 0, level.getRandom().nextInt(3) - 1), 2);
				com.jaymoskingdoms.equestrian.Equestrian.spawn(level, stableBreed(level, f, i), at);
			}
			if (f.type().isRealm() && Climate.at(level, f.seat()).arid()) {
				// (0.5.34) a desert realm's stable keeps a war elephant
				com.jaymoskingdoms.equestrian.Equestrian.spawn(level, com.jaymoskingdoms.equestrian.Breed.WAR_ELEPHANT, Construction.standable(level, mid, 3));
			}
		}
		if ((entry.equals("pen") || entry.equals("farm")) && Farmers.wanted(server, f)) {
			Faction now = Factions.get(server, f.id());
			if (now != null) {
				Farmers.spawn(level, now, Economy.recruit(level, now));
			}
		}
	}

	/**
	 * (0.5.34) The breed of a new stable's {@code i}th horse: a village's palfreys and drafts; a realm's destriers and
	 * coursers, or desert horses in the desert, steppe ponies on the savanna, drafts in the snow.
	 */
	static com.jaymoskingdoms.equestrian.Breed stableBreed(ServerLevel level, Faction f, int i) {
		Climate climate = Climate.at(level, f.seat());
		if (climate.arid()) {
			return com.jaymoskingdoms.equestrian.Breed.DESERT_HORSE;
		} else if (climate == Climate.SAVANNA) {
			return com.jaymoskingdoms.equestrian.Breed.STEPPE_PONY;
		} else if (climate == Climate.SNOWY && i == 0) {
			return com.jaymoskingdoms.equestrian.Breed.DRAFT;
		}
		if (!f.type().isRealm()) {
			return i % 2 == 0 ? com.jaymoskingdoms.equestrian.Breed.PALFREY : com.jaymoskingdoms.equestrian.Breed.DRAFT;
		}
		return i % 2 == 0 ? com.jaymoskingdoms.equestrian.Breed.DESTRIER : com.jaymoskingdoms.equestrian.Breed.COURSER;
	}

	// ---- a day's work -----------------------------------------------------------------------------------------------

	/** One gatherer's tick (from {@link Folk}). */
	static void tick(ServerLevel level, Worker folk) {
		if (folk.getLastHurtByMob() != null && folk.ticksAlive() - folk.getLastHurtByMobTimestamp() < 100) {
			// (running for his life)
			return;
		}
		Faction f = Factions.get(level.getServer(), folk.factionId());
		if (f == null || f.fallen()) {
			return;
		}
		Job job = JOBS.computeIfAbsent(folk.getUUID(), u -> new Job());
		long now = level.getGameTime();
		switch (job.phase) {
			case DIG -> dig(level, folk, job, now);
			case FELL -> fell(level, folk, job, now);
			case DELIVER -> {
				folk.setTask("delivering");
				if (walk(folk, job.trip, Builders.depot(f), DEPOT_REACH, WALK_SPEED, now)) {
					job.bag.unload(level, f, folk);
					job.phase = IDLE;
					folk.setTask("idle");
				}
			}
			default -> {
				if (now % LOOK_TICKS == Math.floorMod(folk.getId(), LOOK_TICKS)) {
					decide(level, f, folk, job, now);
				}
			}
		}
	}

	private static void decide(ServerLevel level, Faction f, Worker folk, Job job, long now) {
		if (job.bag.isEmpty() && folk.isCarrying()) {
			// (what he held when the world was last saved: only its token was kept)
			folk.setCarried(ItemStack.EMPTY);
		}
		job.trip.reset(now);
		job.actAt = 0;
		boolean day = Royals.daytime(level);
		if (job.bag.count() >= ARMFUL || (!day && !job.bag.isEmpty())) {
			job.phase = DELIVER;
			return;
		}
		if (!day) {
			folk.setTask("resting");
			if (folk.distanceToSqr(Vec3.atBottomCenterOf(folk.getHomePosition())) > 16.0) {
				walk(folk, job.trip, folk.getHomePosition(), 2.0, WALK_SPEED, now);
			}
			return;
		}
		if (folk.role() == Folk.MINER) {
			Mines.Mine m = Mines.of(level.getServer(), f.id());
			if (m == null || !m.done() || !(oreOrStone(level, m, folk, job))) {
				folk.setTask(m != null && m.done() ? "the mine is worked out" : "waiting for the mine");
				return;
			}
			job.phase = DIG;
			folk.setTask("mining");
		} else {
			BlockPos base = tree(level, f, folk, job);
			if (base == null) {
				folk.setTask("no trees to fell");
				return;
			}
			job.goal = base;
			job.stand = Construction.standable(level, base.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom())), 2);
			job.logs.clear();
			job.felled = false;
			job.phase = FELL;
			folk.setTask("felling a tree");
		}
	}

	// ---- miners -----------------------------------------------------------------------------------------------------

	/** His next block: an ore of a vein he's on, an ore he can see from the tunnel or in the cave, else the next of a side tunnel. */
	private static boolean oreOrStone(ServerLevel level, Mines.Mine m, Worker folk, Job job) {
		while (!job.vein.isEmpty()) {
			BlockPos ore = job.vein.poll();
			if (level.getBlockState(ore).is(BlockTags.ORES)) {
				return aim(level, job, ore, null);
			}
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		Direction side = m.side();
		List<BlockPos> look = new ArrayList<>();
		for (int i = 0; i <= m.step(); i++) {
			BlockPos c = m.cell(i);
			for (int s = -ORE_SIDE; s <= ORE_SIDE; s++) {
				for (int h = -1; h <= Mines.HIGH; h++) {
					look.add(c.relative(side, s).above(h));
				}
			}
		}
		if (m.state() == Mines.CAVE) {
			BlockPos end = m.end();
			for (BlockPos q : BlockPos.betweenClosed(end.offset(-CAVE_REACH, -CAVE_REACH / 2, -CAVE_REACH), end.offset(CAVE_REACH, CAVE_REACH, CAVE_REACH))) {
				look.add(q.immutable());
			}
		}
		for (BlockPos q : look) {
			if (job.skip.contains(q) || !level.getBlockState(q).is(BlockTags.ORES) || open(level, q) == null) {
				continue;
			}
			double d = q.distSqr(folk.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = q;
			}
		}
		if (best != null) {
			return aim(level, job, best, null);
		}
		// a side tunnel: the first not yet driven to its end (the nearer ones first; the two miners take alternate ones)
		int lane = Math.floorMod(folk.getUUID().hashCode(), 2);
		for (int pass = 0; pass < 2; pass++) {
			for (int i = Mines.COVER + 1; i <= m.step(); i++) {
				if (i % Mines.SUPPORT_EVERY != 2) {
					continue;
				}
				for (int sign : new int[] {1, -1}) {
					int n = (i / Mines.SUPPORT_EVERY) * 2 + (sign > 0 ? 0 : 1);
					if (pass == 0 && n % 2 != lane) {
						continue;
					}
					BlockPos c = m.cell(i);
					for (int j = Mines.WIDE / 2 + 1; j <= Mines.WIDE / 2 + BRANCH_LONG; j++) {
						BlockPos low = c.relative(side, sign * j);
						BlockPos target = !Mines.clear(level, low) ? low : !Mines.clear(level, low.above()) ? low.above() : null;
						if (target == null) {
							continue;
						}
						if (wet(level, target) || level.getBlockState(target).getDestroySpeed(level, target) < 0 || job.skip.contains(target)) {
							break;
						}
						return aim(level, job, target, c.relative(side, sign * (j - 1)));
					}
				}
			}
		}
		// nothing else: the rock round the mine's end (or its cave), quarried back a block at a time
		BlockPos end = m.end();
		BlockPos rock = null;
		double rockD = Double.MAX_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(end.offset(-QUARRY_REACH, -1, -QUARRY_REACH), end.offset(QUARRY_REACH, Mines.HIGH, QUARRY_REACH))) {
			BlockState state = level.getBlockState(q);
			if (!(state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.ORES)) || job.skip.contains(q) || level.canSeeSky(q.above())
				|| wet(level, q) || open(level, q) == null || q.getY() < end.getY()) {
				continue;
			}
			double d = q.distSqr(end);
			if (d < rockD) {
				rockD = d;
				rock = q.immutable();
			}
		}
		return rock != null && aim(level, job, rock, null);
	}

	private static boolean aim(ServerLevel level, Job job, BlockPos target, @Nullable BlockPos stand) {
		job.goal = target;
		BlockPos air = stand != null ? stand : open(level, target);
		job.stand = air == null ? target : Construction.standable(level, air, 2);
		return true;
	}

	/** An open block beside {@code at} (so it can be reached), or null. */
	private static @Nullable BlockPos open(ServerLevel level, BlockPos at) {
		for (Direction d : Direction.values()) {
			BlockPos n = at.relative(d);
			if (level.getBlockState(n).isAir()) {
				return n;
			}
		}
		return null;
	}

	/** Water or lava beside it: left alone, so the mine stays dry. */
	private static boolean wet(ServerLevel level, BlockPos at) {
		for (Direction d : Direction.values()) {
			if (!level.getFluidState(at.relative(d)).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static void dig(ServerLevel level, Worker folk, Job job, long now) {
		BlockPos target = job.goal;
		if (target == null || Mines.clear(level, target)) {
			job.phase = IDLE;
			return;
		}
		if (folk.mob().getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > REACH * REACH) {
			BlockPos stand = job.stand != null ? job.stand : target;
			boolean there = walk(folk, job.trip, stand, STAND_REACH, WALK_SPEED, now);
			if (there || (job.trip.best < 2.0 && now - job.trip.improvedAt > STUCK_TICKS / 2)) {
				// (there, but it can't be reached from where he stands: leave it be)
				job.skip.add(target);
				job.phase = IDLE;
			}
			return;
		}
		folk.getNavigation().stop();
		folk.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		boolean ore = level.getBlockState(target).is(BlockTags.ORES);
		if (job.actAt == 0) {
			job.actAt = now + (ore ? ORE_TICKS : STONE_TICKS);
			folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			return;
		}
		if (now < job.actAt) {
			if ((job.actAt - now) % 8 == 0) {
				folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			return;
		}
		for (ItemStack d : Mines.dig(level, target, folk.mob(), false)) {
			job.bag.add(folk, d);
		}
		light(level, target);
		// a vein: any ore it uncovered is next
		for (Direction d : Direction.values()) {
			BlockPos n = target.relative(d);
			if (level.getBlockState(n).is(BlockTags.ORES) && !job.vein.contains(n)) {
				job.vein.add(n);
			}
		}
		job.phase = IDLE;
	}

	/**
	 * (0.5.21) A miner lights his way: where he's just dug out is dark (underground, block light under
	 * {@link #TORCH_BELOW}), a torch goes on its floor, or on a wall beside it.
	 */
	static void light(ServerLevel level, BlockPos open) {
		for (BlockPos at : new BlockPos[] {open.below(), open}) {
			if (!Mines.clear(level, at) || !level.getBlockState(at).isAir() || level.canSeeSky(at)
				|| level.getBrightness(LightLayer.BLOCK, at) >= TORCH_BELOW) {
				continue;
			}
			BlockState torch = Blocks.TORCH.defaultBlockState();
			if (!torch.canSurvive(level, at)) {
				torch = null;
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side);
					if (wall.canSurvive(level, at)) {
						torch = wall;
						break;
					}
				}
			}
			if (torch != null) {
				level.setBlock(at, torch, Block.UPDATE_ALL);
				level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
				return;
			}
		}
	}

	// ---- woodcutters ------------------------------------------------------------------------------------------------

	/** The nearest tree to fell: a trunk on soil under living (not placed) leaves, his tree farm's first; or null. */
	private static @Nullable BlockPos tree(ServerLevel level, Faction f, Worker folk, Job job) {
		BlockPos home = folk.getHomePosition();
		List<Works.Built> others = Works.of(level.getServer(), f.id()).built().stream().filter(b -> !b.entry().equals("tree_farm")).toList();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -TREE_RANGE; dx <= TREE_RANGE; dx++) {
			for (int dz = -TREE_RANGE; dz <= TREE_RANGE; dz++) {
				BlockPos column = home.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				if (!level.getBlockState(top).is(BlockTags.LOGS)) {
					continue;
				}
				BlockPos base = top;
				while (level.getBlockState(base.below()).is(BlockTags.LOGS) && top.getY() - base.getY() < 40) {
					base = base.below();
				}
				if (job.skip.contains(base) || Trees.young(level.getServer(), base) || !level.getBlockState(base.below()).is(BlockTags.DIRT) || !wild(level, top) || inside(others, base)) {
					continue;
				}
				// (his tree farm's trees count as nearer)
				double d = base.distSqr(folk.blockPosition()) - (base.distSqr(home) < 64 ? 4096 : 0);
				if (d < bestD) {
					bestD = d;
					best = base;
				}
			}
		}
		return best;
	}

	/** Living leaves round the top of a trunk (grown, not placed: a building's logs aren't a tree). */
	private static boolean wild(ServerLevel level, BlockPos top) {
		for (BlockPos q : BlockPos.betweenClosed(top.offset(-2, -2, -2), top.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(q);
			if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inside(List<Works.Built> boxes, BlockPos at) {
		for (Works.Built b : boxes) {
			if (at.getX() >= b.min().getX() - 1 && at.getX() <= b.max().getX() + 1 && at.getZ() >= b.min().getZ() - 1 && at.getZ() <= b.max().getZ() + 1) {
				return true;
			}
		}
		return false;
	}

	private static void fell(ServerLevel level, Worker folk, Job job, long now) {
		BlockPos base = job.goal;
		if (base == null) {
			job.phase = IDLE;
			return;
		}
		if (job.logs.isEmpty()) {
			if (!level.getBlockState(base).is(BlockTags.LOGS)) {
				job.phase = IDLE;
				return;
			}
			BlockPos stand = job.stand != null ? job.stand : base;
			if (folk.mob().getEyePosition().distanceToSqr(Vec3.atCenterOf(base)) > REACH * REACH) {
				boolean there = walk(folk, job.trip, stand, STAND_REACH + 1.0, WALK_SPEED, now);
				if (there || (job.trip.best < 2.0 && now - job.trip.improvedAt > STUCK_TICKS / 2)) {
					job.skip.add(base);
					job.phase = IDLE;
				}
				return;
			}
			folk.getNavigation().stop();
			job.logs.addAll(trunk(level, base));
			job.sapling = sapling(ChoppedLog.real(level, base, level.getBlockState(base)));
			job.actAt = now + FELL_START_TICKS;
			folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			return;
		}
		folk.getLookControl().setLookAt(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
		if (now < job.actAt) {
			if ((job.actAt - now) % 8 == 0 && job.actAt - now > CHOP_TICKS) {
				folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			return;
		}
		// (0.5.25) he chops at its foot as a player would (Trees.chop: the log narrows), a chop every CHOP_TICKS, until the
		// whole tree comes down (its logs from the foot up, then the leaves no other tree holds), into his bag
		if (!job.felled) {
			int logs = Trees.chop(level, base, folk.mob(), new ItemStack(Items.IRON_AXE), d -> job.bag.add(folk, d));
			folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			if (logs == 0) {
				job.actAt = now + CHOP_TICKS;
				return;
			}
			if (logs < 0) {
				job.skip.add(base);
				job.logs.clear();
				job.phase = IDLE;
				return;
			}
			job.felled = true;
			job.actAt = now + FELL_WAIT_TICKS;
			return;
		}
		job.logs.clear();
		job.felled = false;
		// and a sapling where it stood
		if (job.sapling != null && level.getBlockState(base).isAir() && job.sapling.canSurvive(level, base)) {
			level.setBlock(base, job.sapling, Block.UPDATE_ALL);
			level.playSound(null, base, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		job.phase = IDLE;
	}

	/** A tree's logs (joined to its foot, not far from it), lowest first. */
	static List<BlockPos> trunk(ServerLevel level, BlockPos base) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		open.add(base);
		seen.add(base);
		while (!open.isEmpty() && out.size() < TREE_MOST) {
			BlockPos at = open.poll();
			out.add(at);
			for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) {
				if (n.getY() < base.getY() || Math.abs(n.getX() - base.getX()) > TREE_SPREAD || Math.abs(n.getZ() - base.getZ()) > TREE_SPREAD) {
					continue;
				}
				BlockPos q = n.immutable();
				if (!seen.contains(q) && level.getBlockState(q).is(BlockTags.LOGS)) {
					seen.add(q);
					open.add(q);
				}
			}
		}
		out.sort(Comparator.comparingInt(BlockPos::getY));
		return out;
	}

	/** The sapling a log's tree grows from (an oak's if it can't tell). */
	static @Nullable BlockState sapling(BlockState log) {
		String name = BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath().replace("stripped_", "");
		String wood = name.endsWith("_log") ? name.substring(0, name.length() - 4) : name.endsWith("_wood") ? name.substring(0, name.length() - 5) : "oak";
		Block sapling = Castle.byName(wood.equals("mangrove") ? "mangrove_propagule" : wood + "_sapling", Blocks.OAK_SAPLING).getBlock();
		return sapling == Blocks.AIR ? null : sapling.defaultBlockState();
	}

	// ---- sleep ------------------------------------------------------------------------------------------------------

	/**
	 * Sleep's catch-up ({@link Nights}): what its miners and woodcutters (loaded about the seat) would have brought in
	 * meanwhile, straight into storage (stone with a little coal, iron and copper; its own logs), and its tree farms'
	 * saplings grown on. Returns how many items.
	 */
	static int overnight(ServerLevel level, Faction f, long ticks) {
		List<Worker> folk = Workers.near(level, new AABB(f.seat()).inflate(Builders.CREW_RANGE),
			x -> x.isAlive() && x.factionId() == f.id() && isGatherer(x.role()));
		long miners = folk.stream().filter(x -> x.role() == Folk.MINER).count();
		long cutters = folk.size() - miners;
		int gathered = 0;
		if (Storage.loaded(level, f)) {
			int stone = (int)Math.min(CATCH_UP_MOST, miners * ticks / MINER_TICKS_PER_ITEM);
			int coal = stone / 16, iron = stone / 32, copper = stone / 48;
			Storage.put(level, f, Items.COBBLESTONE, stone - coal - iron - copper);
			Storage.put(level, f, Items.COAL, coal);
			Storage.put(level, f, Items.RAW_IRON, iron);
			Storage.put(level, f, Items.RAW_COPPER, copper);
			int logs = (int)Math.min(CATCH_UP_MOST, cutters * ticks / WOOD_TICKS_PER_LOG);
			Storage.put(level, f, Production.logItem(level, f), logs);
			gathered = stone + logs;
		}
		int stages = (int)(ticks / SAPLING_TICKS_PER_STAGE);
		for (Works.Built farm : treeFarms(level.getServer(), f)) {
			for (BlockPos q : BlockPos.betweenClosed(farm.min(), farm.max().above(2))) {
				BlockState state = level.getBlockState(q);
				for (int i = 0; i < stages && state.getBlock() instanceof SaplingBlock s; i++) {
					s.advanceTree(level, q.immutable(), state, level.getRandom());
					state = level.getBlockState(q);
				}
			}
		}
		return gathered;
	}
}
