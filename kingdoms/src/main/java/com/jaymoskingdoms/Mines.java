package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's mine (0.5.2): the first thing its builders make. The site is a rock or cliff face near the seat if
 * there is one (they tunnel {@link #INTO_CLIFF} blocks straight in, then down), else open ground (a stair shaft straight
 * down). The tunnel is {@link #WIDE} wide and {@link #HIGH} high, a step at a time, its mouth framed in logs and roofed
 * over, with posts every {@link #SUPPORT_EVERY} steps and torches between. They stop when they break into a cave (that's
 * the point: the cave is the mine's floor now), or reach water or lava, bedrock, {@link #LONGEST} steps or near the
 * world's floor. What they dig (stone and any ore it holds; never dirt) they carry to storage. While a mine is being dug,
 * nothing else is started unless the crew is {@link #CREW_TO_SHARE} or more (then {@link #DIGGERS} of them dig and the
 * rest build); once it's done the settlement takes on {@link Gatherers#MINERS_PER_MINE} miners. Kept on the Overworld,
 * by faction id.
 */
public final class Mines {
	/** How far from the seat a mine may go (blocks; a realm's nearest clears its castle), and how often a settlement without one looks (ticks). */
	static final int SITE_MIN = 14, SITE_MIN_REALM = Citadel.HALF + Citadel.TOWER_HALF + 6, SITE_MAX = 72, SITE_STEP = 3;
	static final long LOOK_TICKS = 400L, RETRY_TICKS = 6000L;
	/** A cliff: the land this many blocks ahead rises at least this high, and this many of the blocks it digs first are rock. */
	static final int CLIFF_AHEAD = 3, CLIFF_HIGH = 4, CLIFF_ROCK = 5;
	/** Straight in this far into a cliff before going down (open ground goes down at once). */
	static final int INTO_CLIFF = 6;
	/** The tunnel: its width (odd), its height; the mouth roofed over this many steps; the most steps; how far above the world's floor it stops. */
	static final int WIDE = 3, HIGH = 3, COVER = 3, LONGEST = 40, FLOOR_MARGIN = 10;
	/** Posts every this many steps, torches between. */
	static final int SUPPORT_EVERY = 4;
	/** A crew this big (or bigger) digs and builds at once: this many of them dig. */
	static final int CREW_TO_SHARE = 5, DIGGERS = 2;
	/** A block takes this long to dig (ticks), and a little more at random; from this far. */
	static final int DIG_TICKS = 30, DIG_JITTER = 10;
	static final double DIG_REACH = 4.5, STAND_REACH = 1.5;
	/** He carries this many items before taking them to storage. */
	static final int ARMFUL = 48;
	/** No nearer his goal for this long and he's put where he was going (it's a tunnel: he can't be far wrong). */
	static final int STUCK_TICKS = 300;
	static final double WALK_SPEED = 0.7;
	/** Sleep's catch-up ({@link Nights}): a digger's ticks a block, on average (hauling included). */
	static final int CATCH_UP_TICKS_PER_BLOCK = 45;
	/** Players this close to the seat hear that the mine is done. */
	static final double TELL_RANGE = 128.0;

	static final int DIGGING = 0, CAVE = 1, DEEP = 2, FLOODED = 3, BLOCKED = 4;

	/** A mine: its mouth (the floor block one stands in), the way it runs, its steps level before it goes down, the step being dug, its state. */
	record Mine(BlockPos entrance, Direction facing, int flat, int step, int state) {
		static final Codec<Mine> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("entrance").forGetter(Mine::entrance),
				Direction.CODEC.fieldOf("facing").forGetter(Mine::facing),
				Codec.INT.fieldOf("flat").forGetter(Mine::flat),
				Codec.INT.fieldOf("step").forGetter(Mine::step),
				Codec.INT.fieldOf("state").forGetter(Mine::state))
			.apply(i, Mine::new));

		boolean done() {
			return this.state != DIGGING;
		}

		Mine with(int step, int state) {
			return new Mine(this.entrance, this.facing, this.flat, step, state);
		}

		/** Step {@code i}'s floor height. */
		int floor(int i) {
			return this.entrance.getY() - (i < this.flat ? 0 : i - this.flat + 1);
		}

		/** The middle of step {@code i}, at its floor. */
		BlockPos cell(int i) {
			BlockPos at = this.entrance.relative(this.facing, i);
			return new BlockPos(at.getX(), this.floor(i), at.getZ());
		}

		/** The deepest step dug. */
		BlockPos end() {
			return this.cell(Math.max(0, this.done() ? this.step : this.step - 1));
		}

		Direction side() {
			return this.facing.getClockWise();
		}

		/** Step {@code i}'s blocks, in the order they're dug: the middle first, bottom up (so one can walk in), then the sides. */
		List<BlockPos> section(int i) {
			BlockPos c = this.cell(i);
			List<BlockPos> out = new ArrayList<>();
			for (int k = 0; k < WIDE; k++) {
				// 0, -1, 1, -2, 2, ...
				int s = k % 2 == 1 ? -(k + 1) / 2 : k / 2;
				for (int h = 0; h < HIGH; h++) {
					out.add(c.relative(this.side(), s).above(h));
				}
			}
			return out;
		}
	}

	static final AttachmentType<Map<String, Mine>> MINES = AttachmentRegistry.create(JaymosKingdoms.id("mines"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Mine.CODEC)));

	/** A digger's errand. */
	private static final class Dig {
		final Gatherers.Bag bag = new Gatherers.Bag();
		final Gatherers.Trip trip = new Gatherers.Trip();
		@Nullable BlockPos target;
		long actAt;
		boolean homed;
	}

	private static final Map<UUID, Dig> DIGS = new HashMap<>();
	/** Faction id → when it may look for a mine site again. */
	private static final Map<Integer, Long> NEXT_LOOK = new HashMap<>();
	/** Faction id → its diggers while it builds as well, and when they were chosen. */
	private static final Map<Integer, Set<UUID>> DIGGER_SET = new HashMap<>();
	private static final Map<Integer, Long> DIGGERS_AT = new HashMap<>();

	private Mines() {
	}

	/** Wraps the builders' brain (after {@link Construction#register}; {@link Debris} wraps this in turn), and looks for sites. */
	static void register() {
		Builders.Brain next = Builders.brain();
		Builders.setBrain((level, builder) -> {
			if (!tick(level, builder)) {
				next.tick(level, builder);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Mines::look);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		DIGS.clear();
		NEXT_LOOK.clear();
		DIGGER_SET.clear();
		DIGGERS_AT.clear();
	}

	// ---- the record -------------------------------------------------------------------------------------------------

	static @Nullable Mine of(MinecraftServer server, int faction) {
		return Factions.level(server).getAttachedOrElse(MINES, Map.<String, Mine>of()).get(Integer.toString(faction));
	}

	private static void put(MinecraftServer server, int faction, @Nullable Mine mine) {
		Map<String, Mine> map = new HashMap<>(Factions.level(server).getAttachedOrElse(MINES, Map.<String, Mine>of()));
		if (mine == null) {
			map.remove(Integer.toString(faction));
		} else {
			map.put(Integer.toString(faction), mine);
		}
		Factions.level(server).setAttached(MINES, Map.copyOf(map));
	}

	/** Its mine is being dug (nothing else starts meanwhile, unless its crew is big enough to share). */
	static boolean digging(MinecraftServer server, Faction faction) {
		Mine m = of(server, faction.id());
		return m != null && !m.done();
	}

	/** Its mine is dug. */
	static boolean dug(MinecraftServer server, Faction faction) {
		Mine m = of(server, faction.id());
		return m != null && m.done();
	}

	/** May start other work while its mine is dug: none being dug, or a crew big enough to share. */
	static boolean mayBuild(MinecraftServer server, Faction faction) {
		return !digging(server, faction) || Builders.crewCount(faction) >= CREW_TO_SHARE;
	}

	// ---- choosing the site ------------------------------------------------------------------------------------------

	/** Every so often, a settlement with a crew and no mine looks for where to dig one. */
	private static void look(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % LOOK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || Blueprint.catalogue(f.type()).isEmpty() || of(server, f.id()) != null
				|| NEXT_LOOK.getOrDefault(f.id(), 0L) > now || !level.isPositionEntityTicking(f.seat()) || Builders.crew(level, f).isEmpty()) {
				continue;
			}
			Mine mine = site(level, f);
			if (mine == null) {
				NEXT_LOOK.put(f.id(), now + RETRY_TICKS);
				continue;
			}
			put(server, f.id(), mine);
			// its mouth is kept among the buildings, so nothing's built on it
			BlockPos a = mine.entrance().relative(mine.facing().getOpposite()).relative(mine.side(), -2);
			BlockPos b = mine.cell(COVER).relative(mine.side(), 2).atY(mine.entrance().getY() + HIGH + 1);
			Works.update(server, f.id(), s -> {
				List<Works.Built> built = new ArrayList<>(s.built());
				built.add(new Works.Built("mine", min(a, b), max(a, b)));
				return s.withBuilt(built);
			});
			JaymosKingdoms.LOGGER.info("{} begins a mine at {} facing {} ({})", f.name(), mine.entrance(), mine.facing(),
				mine.flat() > 1 ? "into a cliff" : "a shaft");
		}
	}

	private static BlockPos min(BlockPos a, BlockPos b) {
		return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
	}

	private static BlockPos max(BlockPos a, BlockPos b) {
		return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	/** The nearest rock or cliff face round the seat (rings outward), else the nearest open ground fit for a shaft; or null. */
	static @Nullable Mine site(ServerLevel level, Faction f) {
		BlockPos seat = f.seat();
		List<int[]> taken = taken(level.getServer());
		Mine shaft = null;
		double shaftD = Double.MAX_VALUE;
		Mine cliff = null;
		double cliffD = Double.MAX_VALUE;
		int least = f.type().isRealm() ? SITE_MIN_REALM : SITE_MIN;
		for (int dx = -SITE_MAX; dx <= SITE_MAX; dx += SITE_STEP) {
			for (int dz = -SITE_MAX; dz <= SITE_MAX; dz += SITE_STEP) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < least || d > SITE_MAX || d >= cliffD) {
					continue;
				}
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockState ground = level.getBlockState(at.below());
				if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()
					|| !(ground.is(BlockTags.SUBSTRATE_OVERWORLD) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.GRAVEL))) {
					// (natural ground only: not a road, a floor or a roof)
					continue;
				}
				for (Direction way : Direction.Plane.HORIZONTAL) {
					Mine m = new Mine(at, way, INTO_CLIFF, 0, DIGGING);
					if (clash(level.getServer(), m, taken)) {
						continue;
					}
					if (cliff(level, m)) {
						cliff = m;
						cliffD = d;
						break;
					}
					if (cliff == null && d < shaftD && flatGround(level, at)) {
						shaft = new Mine(at, way, 1, 0, DIGGING);
						shaftD = d;
					}
				}
			}
		}
		return cliff != null ? cliff : shaft;
	}

	/** A face of rock rising ahead: the land {@link #CLIFF_AHEAD} on stands {@link #CLIFF_HIGH} higher, and the first blocks in are mostly rock. */
	private static boolean cliff(ServerLevel level, Mine m) {
		BlockPos ahead = m.entrance().relative(m.facing(), CLIFF_AHEAD);
		if (!level.isLoaded(ahead) || level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead).getY() < m.entrance().getY() + CLIFF_HIGH) {
			return false;
		}
		int rock = 0;
		for (int i = 1; i <= CLIFF_AHEAD; i++) {
			for (BlockPos p : m.section(i)) {
				BlockState state = level.getBlockState(p);
				if (!level.getFluidState(p).isEmpty()) {
					return false;
				}
				if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.ORES)) {
					rock++;
				}
			}
		}
		return rock >= CLIFF_ROCK;
	}

	/** Open, dry ground level enough for a shaft's mouth (the 5 x 5 round it within a block). */
	private static boolean flatGround(ServerLevel level, BlockPos at) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(dx, 0, dz));
				if (Math.abs(top.getY() - at.getY()) > 1 || !level.getFluidState(top.below()).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	/** Every building and plot's footprint (x, z corners). */
	private static List<int[]> taken(MinecraftServer server) {
		List<int[]> out = new ArrayList<>();
		for (Works.Site s : Works.all(server).values()) {
			for (Works.Built b : s.built()) {
				out.add(new int[] {b.min().getX(), b.min().getZ(), b.max().getX(), b.max().getZ()});
			}
			for (Works.Plot p : s.plots()) {
				out.add(new int[] {p.min().getX(), p.min().getZ(), p.max().getX(), p.max().getZ()});
			}
			for (Works.Project p : s.projects()) {
				out.add(new int[] {p.origin().getX() - 12, p.origin().getZ() - 12, p.origin().getX() + 12, p.origin().getZ() + 12});
			}
		}
		return out;
	}

	/** Its mouth (and the first steps) would cross something already there (a building, a plot, a building held). */
	private static boolean clash(MinecraftServer server, Mine m, List<int[]> taken) {
		BlockPos a = m.entrance().relative(m.facing().getOpposite(), 2);
		BlockPos b = m.cell(COVER + 1);
		int x0 = Math.min(a.getX(), b.getX()) - 3, x1 = Math.max(a.getX(), b.getX()) + 3;
		int z0 = Math.min(a.getZ(), b.getZ()) - 3, z1 = Math.max(a.getZ(), b.getZ()) + 3;
		if (Holds.guarded(server, x0, z0, x1, z1, 0)) {
			return true;
		}
		for (int[] t : taken) {
			if (x0 <= t[2] && x1 >= t[0] && z0 <= t[3] && z1 >= t[1]) {
				return true;
			}
		}
		return false;
	}

	// ---- digging ----------------------------------------------------------------------------------------------------

	/** Nothing to dig here: open, or a plant or snow. */
	static boolean clear(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	/**
	 * Digs out the block at {@code at} as though with an iron pickaxe; returns what it gave (dirt and the like thrown
	 * away: it's the storehouse's stone and ore that's wanted). {@code quiet}: no sound or crumbs (sleep's catch-up).
	 */
	static List<ItemStack> dig(ServerLevel level, BlockPos at, @Nullable Entity digger, boolean quiet) {
		BlockState state = level.getBlockState(at);
		if (state.isAir()) {
			return List.of();
		}
		List<ItemStack> drops = new ArrayList<>();
		if (!state.canBeReplaced()) {
			for (ItemStack d : Block.getDrops(state, level, at, level.getBlockEntity(at), digger, new ItemStack(Items.IRON_PICKAXE))) {
				if (!d.isEmpty() && !Block.byItem(d.getItem()).defaultBlockState().is(BlockTags.DIRT)) {
					drops.add(d);
				}
			}
		}
		if (!quiet) {
			level.levelEvent(2001, at, Block.getId(state));
		}
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return drops;
	}

	/** One builder's tick, if the mine wants him (true: it's taken care of). */
	static boolean tick(ServerLevel level, Folk b) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, b.factionId());
		if (f == null || f.fallen()) {
			return false;
		}
		Mine m = of(server, f.id());
		Dig job = DIGS.get(b.getUUID());
		boolean mining = m != null && !m.done() && Economy.atPeace(server, f) && digger(level, f, b);
		if (!mining && (job == null || job.bag.isEmpty())) {
			if (job != null) {
				DIGS.remove(b.getUUID());
				b.setCarried(ItemStack.EMPTY);
			}
			return false;
		}
		if (b.getLastHurtByMob() != null && b.tickCount - b.getLastHurtByMobTimestamp() < Construction.FLEE_TICKS) {
			// (running for his life)
			return true;
		}
		if (job == null && b.isCarrying()) {
			// (a load for the building site in his hands: he sees to that first)
			return false;
		}
		long now = level.getGameTime();
		if (job == null) {
			job = new Dig();
			DIGS.put(b.getUUID(), job);
			job.trip.reset(now);
		}
		if (!job.homed) {
			job.homed = true;
			b.setHomeTo(Builders.depot(f), Construction.WORK_RADIUS);
		}
		if (!mining || job.bag.count() >= ARMFUL) {
			b.setTask("delivering");
			if (Gatherers.walk(b, job.trip, Builders.depot(f), Construction.DEPOT_REACH, WALK_SPEED, now)) {
				job.bag.unload(level, f, b);
				job.trip.reset(now);
			} else if (now - job.trip.improvedAt > STUCK_TICKS * 2) {
				job.bag.unload(level, f, b);
			}
			return true;
		}
		b.setTask("digging a mine");
		b.setSite(m.entrance());
		if (job.target == null || clear(level, job.target)) {
			job.target = next(level, m, b);
			job.actAt = 0;
			job.trip.reset(now);
			if (job.target == null) {
				advance(level, f, m, false);
				return true;
			}
		}
		BlockPos target = job.target;
		Vec3 eye = b.getEyePosition();
		if (eye.distanceToSqr(Vec3.atCenterOf(target)) > DIG_REACH * DIG_REACH) {
			BlockPos stand = m.step() == 0 ? m.entrance().relative(m.facing().getOpposite()) : m.cell(m.step() - 1);
			if (!Gatherers.walk(b, job.trip, stand, STAND_REACH, WALK_SPEED, now) && now - job.trip.improvedAt > STUCK_TICKS) {
				b.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
				b.getNavigation().stop();
				job.trip.reset(now);
			}
			return true;
		}
		b.getNavigation().stop();
		b.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		if (job.actAt == 0) {
			job.actAt = now + DIG_TICKS + level.getRandom().nextInt(DIG_JITTER);
			b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			return true;
		}
		if (now < job.actAt) {
			if ((job.actAt - now) % 8 == 0) {
				b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			return true;
		}
		for (ItemStack d : dig(level, target, b, false)) {
			job.bag.add(b, d);
		}
		job.target = null;
		return true;
	}

	/** The next block of the step being dug (spread among the diggers), or null when the step is open. */
	private static @Nullable BlockPos next(ServerLevel level, Mine m, Folk b) {
		List<BlockPos> left = new ArrayList<>();
		for (BlockPos p : m.section(m.step())) {
			if (!clear(level, p)) {
				left.add(p);
			}
		}
		if (left.isEmpty()) {
			return null;
		}
		// the first two (the middle, so one can walk in) before anything else; the rest shared out
		if (left.size() > WIDE * HIGH - 2 && !clear(level, m.section(m.step()).getFirst())) {
			return left.getFirst();
		}
		return left.get(Math.floorMod(b.getUUID().hashCode(), left.size()));
	}

	/** Whether this builder digs: all of them while there's nothing else to build, else the last {@link #DIGGERS} workers (if the crew can share). */
	private static boolean digger(ServerLevel level, Faction f, Folk b) {
		if (Works.of(level.getServer(), f.id()).projects().isEmpty()) {
			return true;
		}
		if (Builders.crewCount(f) < CREW_TO_SHARE || b.role() != Folk.WORKER) {
			return false;
		}
		long now = level.getGameTime();
		if (now - DIGGERS_AT.getOrDefault(f.id(), Long.MIN_VALUE / 2) > 200L) {
			List<Folk> workers = new ArrayList<>(Builders.crew(level, f).stream().filter(x -> x.role() == Folk.WORKER).toList());
			workers.sort(Comparator.comparing(Entity::getUUID));
			Set<UUID> set = new HashSet<>();
			for (int i = 0; i < Math.min(DIGGERS, workers.size()); i++) {
				set.add(workers.get(workers.size() - 1 - i).getUUID());
			}
			DIGGER_SET.put(f.id(), set);
			DIGGERS_AT.put(f.id(), now);
		}
		return DIGGER_SET.getOrDefault(f.id(), Set.of()).contains(b.getUUID());
	}

	/**
	 * Step {@code m.step()} is open: its floor made good, its posts and torch (and at the mouth its frame and roof) put
	 * up, then on to the next, unless it broke into a cave, or what's ahead is water, lava, bedrock or too deep. Returns
	 * the mine as stored.
	 */
	static Mine advance(ServerLevel level, Faction f, Mine m, boolean quiet) {
		int i = m.step();
		BlockPos c = m.cell(i);
		Direction side = m.side();
		Palette p = Palette.at(level, f.seat());
		// a cave: open air (that isn't open sky) round the step just dug, or just ahead
		boolean cave = false;
		boolean wet = false;
		List<BlockPos> shell = new ArrayList<>();
		for (int h = -1; h <= HIGH; h++) {
			shell.add(c.relative(side, -(WIDE / 2 + 1)).above(h));
			shell.add(c.relative(side, WIDE / 2 + 1).above(h));
		}
		for (int s = -WIDE / 2; s <= WIDE / 2; s++) {
			shell.add(c.relative(side, s).below());
			shell.add(c.relative(side, s).above(HIGH));
		}
		shell.addAll(m.section(i + 1));
		for (BlockPos q : shell) {
			if (!level.getFluidState(q).isEmpty()) {
				wet = true;
			}
			if (i >= COVER - 1 && level.getBlockState(q).isAir() && !level.canSeeSky(q)) {
				cave = true;
			}
		}
		if (!cave) {
			// a floor to walk on
			for (int s = -WIDE / 2; s <= WIDE / 2; s++) {
				BlockPos under = c.relative(side, s).below();
				if (clear(level, under) && level.getFluidState(under).isEmpty()) {
					level.setBlock(under, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		shore(level, m, i, p);
		if (i == COVER - 1) {
			cover(level, m, p);
		}
		int state = DIGGING;
		if (cave) {
			state = CAVE;
		} else if (wet) {
			state = FLOODED;
		} else if (i + 1 >= LONGEST || m.floor(i + 1) <= level.getMinY() + FLOOR_MARGIN) {
			state = DEEP;
		} else {
			for (BlockPos q : m.section(i + 1)) {
				if (level.getBlockState(q).getDestroySpeed(level, q) < 0) {
					state = BLOCKED;
				}
			}
		}
		Mine after = m.with(state == DIGGING ? i + 1 : i, state);
		put(level.getServer(), f.id(), after);
		if (after.done()) {
			finish(level, f, after, quiet);
		}
		return after;
	}

	/** Posts (fence below a plank, each side) every {@link #SUPPORT_EVERY} steps, a torch on the wall between. */
	private static void shore(ServerLevel level, Mine m, int i, Palette p) {
		if (i < COVER) {
			return;
		}
		BlockPos c = m.cell(i);
		Direction side = m.side();
		int half = WIDE / 2;
		if (i % SUPPORT_EVERY == SUPPORT_EVERY - 1) {
			for (int s : new int[] {-half, half}) {
				BlockPos post = c.relative(side, s);
				if (clear(level, post) && clear(level, post.above())) {
					level.setBlock(post, p.fence(), Block.UPDATE_ALL);
					level.setBlock(post.above(), p.fence(), Block.UPDATE_ALL);
					level.setBlock(post.above(2), p.planks(), Block.UPDATE_ALL);
				}
			}
		} else if (i % SUPPORT_EVERY == 1) {
			BlockPos torch = c.relative(side, half).above(1);
			BlockPos wall = torch.relative(side);
			Direction facing = side.getOpposite();
			if (clear(level, torch) && level.getBlockState(wall).isFaceSturdy(level, wall, facing)) {
				level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing), Block.UPDATE_ALL);
			}
		}
	}

	/** The mouth: log posts each side, walls where it's open, a plank roof over the first {@link #COVER} steps, a lantern. */
	private static void cover(ServerLevel level, Mine m, Palette p) {
		Direction side = m.side();
		int half = WIDE / 2 + 1;
		int y0 = m.entrance().getY();
		for (int i = 0; i < COVER; i++) {
			BlockPos c = m.entrance().relative(m.facing(), i);
			for (int h = 0; h < HIGH; h++) {
				for (int s : new int[] {-half, half}) {
					BlockPos q = c.relative(side, s).atY(y0 + h);
					if (clear(level, q)) {
						level.setBlock(q, i == 0 ? p.log() : p.planks(), Block.UPDATE_ALL);
					}
				}
			}
			for (int s = -half; s <= half; s++) {
				BlockPos q = c.relative(side, s).atY(y0 + HIGH);
				if (clear(level, q)) {
					level.setBlock(q, i == 0 && Math.abs(s) == half ? p.log() : p.planks(), Block.UPDATE_ALL);
				}
			}
			// (a shaft: the ground over the stair, below the roof, walled in behind)
			if (m.flat() <= 1 && i == COVER - 1) {
				BlockPos back = m.entrance().relative(m.facing(), COVER);
				for (int s = -half; s <= half; s++) {
					for (int h = 0; h <= HIGH; h++) {
						BlockPos q = back.relative(side, s).atY(y0 + h);
						if (clear(level, q)) {
							level.setBlock(q, p.planks(), Block.UPDATE_ALL);
						}
					}
				}
			}
		}
		BlockPos lantern = m.entrance().atY(y0 + HIGH - 1);
		if (clear(level, lantern) && !level.getBlockState(lantern.above()).isAir()) {
			level.setBlock(lantern, Castle.byName("lantern", Blocks.AIR).getBlock().defaultBlockState()
				.trySetValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), Block.UPDATE_ALL);
		}
	}

	/** Done: the news told round the seat, and miners taken on to work it. */
	private static void finish(ServerLevel level, Faction f, Mine m, boolean quiet) {
		String how = switch (m.state()) {
			case CAVE -> "cave";
			case FLOODED -> "water";
			default -> "deep";
		};
		JaymosKingdoms.LOGGER.info("{}'s mine is dug ({} steps, {})", f.name(), m.step() + 1, how);
		Component news = Component.translatable("message.jaymoskingdoms.mine.done." + how, f.name()).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(f.seat().getX(), f.seat().getY(), f.seat().getZ()) <= TELL_RANGE * TELL_RANGE) {
				player.sendSystemMessage(news);
			}
		}
		Gatherers.hire(level, f, Folk.MINER, Gatherers.MINERS_PER_MINE - f.holdings().count(Folk.MINER));
	}

	/** Sleep's catch-up ({@link Nights}): up to {@code blocks} of the mine dug at once, what they give straight into storage. Returns how many. */
	static int catchUp(ServerLevel level, Faction f, int blocks) {
		int dug = 0;
		Mine m = of(level.getServer(), f.id());
		while (m != null && !m.done() && dug < blocks) {
			boolean any = false;
			for (BlockPos q : m.section(m.step())) {
				if (dug >= blocks) {
					break;
				}
				if (!clear(level, q)) {
					for (ItemStack d : dig(level, q, null, true)) {
						Storage.put(level, f, d);
					}
					dug++;
					any = true;
				}
			}
			if (dug >= blocks && any) {
				break;
			}
			m = advance(level, f, m, true);
		}
		return dug;
	}
}
