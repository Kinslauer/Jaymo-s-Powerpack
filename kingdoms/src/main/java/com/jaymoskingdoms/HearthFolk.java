package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a settlement's people live with the climate (docs/Climate-Plan.md, track F: vl-npcs). Once a few seconds, for the
 * settlements a player is near, and only the people who are free ({@link ClimateVisits#free}: nobody working, fighting, asleep, bound,
 * busy or in a talk is moved):
 * <ul>
 * <li><b>Round the fire on a cold night.</b> From dusk, in a cold land (snowy or taiga) or in winter in a temperate one
 * (plains, cherry, swamp), a few villagers (until {@link #VILLAGER_TO}, when they go to their beds) and a third of the guards
 * (all night: the rest keep the watch) walk to the village's lit fire, a campfire or a Kitchen bonfire ({@link #fireNear}), and
 * sit on its benches or the ground ({@link Firesides#seats}, {@link GroundSeats}) for a while, at most {@link #MAX_SITTERS} to a fire.</li>
 * <li><b>Shade at noon in a hot land</b> (desert, badlands, savanna): half the villagers and a third of the guards caught in the open at
 * midday walk to the nearest shade, a roof or a tree ({@link #shadeNear}), and stand there a while.</li>
 * <li><b>Wear more in the cold.</b> In the same cold, those with nothing over their clothes pull on a harness cloak and a hood (a fur
 * hood in a cold land): the harness, which MCA's villagers, guards and our own folk all wear and draw. They take them off when it warms.
 * What they put on is marked ({@link #WINTER}) so a guard's issued kit ({@link GuardKit}) never counts it as his own.</li>
 * </ul>
 * Nothing fights the day's work: the lists are the ones {@link Approaches} comes over by, and a villager's sleep, a guard's fight,
 * a captive's bonds or opening their talk menu ends it at once.
 */
public final class HearthFolk {
	/** Errands are followed this often, the settlements looked over this often (ticks), and who's dressed this often. */
	static final int EVERY = 10, SWEEP = 100, DRESS_EVERY = 200;
	/** A settlement is looked at when a player is this near its seat (blocks); its people are those this near, and a fire's company those this near it. */
	static final double WATCHED = 96.0, PEOPLE = 48.0, BAND = 32.0;
	/** The fire is sought this far from the seat (blocks), and remembered this long (ticks). */
	static final int FIRE_RANGE = 28, FIRE_CACHE = 1200;
	/** At most this many sit at one fire; this many may join each look, each with this chance. */
	static final int MAX_SITTERS = 8, JOIN_EACH = 2;
	static final float JOIN = 0.5F;
	/** Dusk, when villagers go to their beds, and the end of the night (day-time ticks). */
	static final long EVENING_FROM = 12500L, VILLAGER_TO = 16500L, NIGHT_TO = 23000L;
	/** How long they stay at the fire (ticks, a least and how much more), and how long they take to get there. */
	static final int STAY_MIN = 1200, STAY_MORE = 2400, WALK_GIVE_UP = 500;
	/** One guard in this many goes to the fire, or to the shade; one villager in this many goes to the shade. */
	static final int GUARD_SHARE = 3, VILLAGER_SHADE_SHARE = 2;
	/** Noon, for the shade (day-time ticks), how long they stand in it (ticks), how many go each look, and how far they look for it (blocks). */
	static final long NOON_FROM = 5000L, NOON_TO = 8500L;
	static final int SHADE_STAY = 700, SHADE_EACH = 3, SHADE_RANGE = 12;
	/** Close enough to the seat to sit (blocks); stuck short of it, this close will do. */
	static final double ARRIVE = 1.6, NEAR_ENOUGH = 2.8;
	/** Walking speed (a villager's walk target; a guard's navigation). */
	static final float VILLAGER_SPEED = 0.5F;
	static final double FOLK_SPEED = 0.9;
	/** What cold weather puts on, marked so it can be taken off again. */
	public static final String WINTER = "jaymoskingdoms_winterwear";

	/** Kitchen's tag of blocks that give off flame (campfires, bonfires, fire pits), read as data: empty without it. */
	static final TagKey<Block> FLAME = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "heat_sources/flame"));

	private enum Kind {
		FIRE, SHADE
	}

	private static final class Errand {
		final Kind kind;
		final @Nullable BlockPos fire;
		final BlockPos spot;
		final long since, until;
		boolean sat;

		Errand(Kind kind, @Nullable BlockPos fire, BlockPos spot, long since, long until) {
			this.kind = kind;
			this.fire = fire;
			this.spot = spot;
			this.since = since;
			this.until = until;
		}
	}

	private record Fire(@Nullable BlockPos at, long looked) {
	}

	private static final Map<Mob, Errand> ERRANDS = new IdentityHashMap<>();
	/** Settlement id → its fire as last looked for. */
	private static final Map<Integer, Fire> FIRES = new HashMap<>();

	private HearthFolk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HearthFolk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ERRANDS.clear();
			FIRES.clear();
		});
	}

	/** {@code m} is on its way to a fire or shade, or there. */
	static boolean errand(Mob m) {
		return ERRANDS.containsKey(m);
	}

	/** Whether {@code stack} is a piece the cold put on someone ({@link #WINTER}). */
	public static boolean marked(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(WINTER, false);
	}

	// ---- the land and the season ---------------------------------------------------------------------------------------

	static boolean coldLand(Climate land) {
		return land == Climate.SNOWY || land == Climate.TAIGA;
	}

	static boolean temperateLand(Climate land) {
		return land == Climate.PLAINS || land == Climate.CHERRY || land == Climate.SWAMP;
	}

	/** A cold land, or a temperate one in winter. */
	static boolean cold(ServerLevel level, Climate land) {
		return coldLand(land) || temperateLand(land) && "winter".equals(KitchenCompat.season(level));
	}

	static boolean hot(Climate land) {
		return land.arid() || land == Climate.SAVANNA;
	}

	// ---- the fire and the shade ----------------------------------------------------------------------------------------

	/** A lit fire: a campfire or any block of Kitchen's flame tag that is lit (not loose fire). */
	static boolean lit(BlockState state) {
		if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || !state.is(BlockTags.CAMPFIRES) && !state.is(FLAME)) {
			return false;
		}
		return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
	}

	/** The lit fire nearest {@code from} within {@code range} blocks (and a few up and down), or null. Not for every tick. */
	static @Nullable BlockPos fireNear(ServerLevel level, BlockPos from, int range) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-range, -6, -range), from.offset(range, 6, range))) {
			double d = p.distSqr(from);
			if (d < bestD && level.hasChunkAt(p) && lit(level.getBlockState(p))) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** The nearest place to {@code near} within {@code radius} that one can stand in under a roof or a tree, or null. */
	static @Nullable BlockPos shadeNear(ServerLevel level, BlockPos near, int radius, RandomSource random) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < 30; i++) {
			BlockPos spot = Construction.standable(level, near.offset(random.nextInt(2 * radius + 1) - radius, 0, random.nextInt(2 * radius + 1) - radius), 1);
			double d = spot.distSqr(near);
			if (d < bestD && Math.abs(spot.getY() - near.getY()) <= 3 && !level.canSeeSky(spot.above(2)) && level.getFluidState(spot).isEmpty()) {
				bestD = d;
				best = spot;
			}
		}
		return best;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (!ERRANDS.isEmpty() && tick % EVERY == 0) {
			follow(Factions.level(server));
		}
		if (tick % SWEEP == 0 && !server.getPlayerList().getPlayers().isEmpty()) {
			sweep(server, tick % DRESS_EVERY == 0);
		}
	}

	private static void sweep(MinecraftServer server, boolean dress) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean night = day >= EVENING_FROM && day < NIGHT_TO, noon = day >= NOON_FROM && day < NOON_TO;
		if (!night && !noon && !dress) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat()) || !watched(level, f.seat())) {
				continue;
			}
			Climate land = Climate.at(level, f.seat());
			boolean cold = cold(level, land), hot = hot(land);
			if (!cold && !hot && !dress) {
				continue;
			}
			List<PathfinderMob> people = level.getEntitiesOfClass(PathfinderMob.class, new AABB(f.seat()).inflate(PEOPLE), m -> settler(m, f));
			if (dress) {
				for (PathfinderMob m : people) {
					if (cold) {
						warm(m, coldLand(land));
					} else {
						bare(m);
					}
				}
			}
			if (cold && night) {
				gather(level, f, people, day, now);
			} else if (hot && noon) {
				shade(level, people, now);
			}
		}
	}

	private static boolean watched(ServerLevel level, BlockPos seat) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(seat, WATCHED)) {
				return true;
			}
		}
		return false;
	}

	/** One of {@code f}'s people: its guards and workers, and its villagers (not outlaws, cultists, drifters or thralls). */
	private static boolean settler(PathfinderMob m, Faction f) {
		if (!m.isAlive() || m.isBaby() || !Recollections.rememberer(m) || !Temperaments.humanoid(m) || Thralls.isThrall(m)) {
			return false;
		}
		if (m instanceof Folk folk) {
			return folk.factionId() == f.id();
		}
		if (m instanceof Villager v) {
			String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
			return !job.equals("outlaw") && !job.equals("cultist") && !job.equals("nitwit") && !Drifters.is(v);
		}
		return false;
	}

	private static boolean share(Mob m, int n) {
		return Math.floorMod(m.getUUID().hashCode(), n) == 0;
	}

	// ---- round the fire --------------------------------------------------------------------------------------------------

	private static void gather(ServerLevel level, Faction f, List<PathfinderMob> people, long day, long now) {
		Fire cached = FIRES.get(f.id());
		if (cached == null || now - cached.looked() > FIRE_CACHE) {
			cached = new Fire(fireNear(level, f.seat(), FIRE_RANGE), now);
			FIRES.put(f.id(), cached);
		}
		BlockPos fire = cached.at();
		if (fire == null || !lit(level.getBlockState(fire))) {
			return;
		}
		Set<BlockPos> taken = new HashSet<>();
		int sitters = 0;
		for (Errand e : ERRANDS.values()) {
			if (e.kind == Kind.FIRE && fire.equals(e.fire)) {
				sitters++;
				taken.add(e.spot);
			}
		}
		if (sitters >= MAX_SITTERS) {
			return;
		}
		List<BlockPos> seats = null;
		RandomSource random = level.getRandom();
		int joined = 0;
		for (PathfinderMob m : people) {
			if (joined >= JOIN_EACH || sitters >= MAX_SITTERS) {
				break;
			}
			boolean guard = Downed.isGuard(m);
			if (!m.blockPosition().closerThan(fire, BAND) || (guard ? !share(m, GUARD_SHARE) : day >= VILLAGER_TO)) {
				continue;
			}
			if (!ClimateVisits.free(m) || errand(m) || m.hurtTime > 0 || random.nextFloat() >= JOIN) {
				continue;
			}
			if (seats == null) {
				seats = Firesides.seats(level, fire);
			}
			BlockPos seat = nearestFree(seats, taken, m.blockPosition());
			if (seat == null) {
				return;
			}
			taken.add(seat);
			ERRANDS.put(m, new Errand(Kind.FIRE, fire, seat, now, now + STAY_MIN + random.nextInt(STAY_MORE)));
			joined++;
			sitters++;
		}
	}

	private static @Nullable BlockPos nearestFree(List<BlockPos> seats, Set<BlockPos> taken, BlockPos from) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : seats) {
			double d = p.distSqr(from);
			if (!taken.contains(p) && d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	// ---- shade at noon ---------------------------------------------------------------------------------------------------

	private static void shade(ServerLevel level, List<PathfinderMob> people, long now) {
		RandomSource random = level.getRandom();
		int sent = 0;
		for (PathfinderMob m : people) {
			if (sent >= SHADE_EACH) {
				return;
			}
			if (!share(m, Downed.isGuard(m) ? GUARD_SHARE : VILLAGER_SHADE_SHARE) || !level.canSeeSky(m.blockPosition().above()) || !ClimateVisits.free(m)
				|| errand(m)) {
				continue;
			}
			BlockPos spot = shadeNear(level, m.blockPosition(), SHADE_RANGE, random);
			if (spot != null) {
				ERRANDS.put(m, new Errand(Kind.SHADE, null, spot, now, now + SHADE_STAY + random.nextInt(SHADE_STAY)));
				sent++;
			}
		}
	}

	// ---- following them --------------------------------------------------------------------------------------------------

	private static void follow(ServerLevel level) {
		long now = level.getGameTime();
		for (Map.Entry<Mob, Errand> entry : new ArrayList<>(ERRANDS.entrySet())) {
			Mob m = entry.getKey();
			Errand e = entry.getValue();
			if (!step(level, m, e, now)) {
				ERRANDS.remove(m);
				GroundSeats.standUp(m);
				if (m instanceof Villager v && v.isAlive()) {
					v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				}
			}
		}
	}

	/** One step of an errand; false once it's over (or they're wanted elsewhere). */
	private static boolean step(ServerLevel level, Mob m, Errand e, long now) {
		if (!m.isAlive() || m.isRemoved() || m.level() != level || now >= e.until || m.isSleeping() || m.getTarget() != null || Downed.isDowned(m)
			|| Captives.bound(m) || m.hurtTime > 0 || ClimateVisits.visiting(m) || m instanceof PathfinderMob pm && (TalkBusy.occupied(pm) || Approaches.talking(pm))) {
			return false;
		}
		if (e.kind == Kind.FIRE && (e.fire == null || !lit(level.getBlockState(e.fire)))) {
			return false;
		}
		if (e.sat) {
			return GroundSeats.sitting(m);
		}
		if (now - e.since > WALK_GIVE_UP) {
			return false;
		}
		Vec3 seat = Vec3.atBottomCenterOf(e.spot);
		double d = m.position().distanceToSqr(seat);
		if (d <= ARRIVE * ARRIVE || d <= NEAR_ENOUGH * NEAR_ENOUGH && m.getNavigation().isDone()) {
			if (e.kind == Kind.FIRE) {
				e.sat = GroundSeats.sitDown(m, Vec3.atBottomCenterOf(e.fire));
				return e.sat || now - e.since < WALK_GIVE_UP;
			}
			// in the shade: they stand
			if (m instanceof Villager v) {
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			m.getNavigation().stop();
			return true;
		}
		if (m instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, e.spot, VILLAGER_SPEED, 1);
		} else {
			m.getNavigation().moveTo(seat.x, seat.y, seat.z, FOLK_SPEED);
		}
		return true;
	}

	// ---- wearing more in the cold ----------------------------------------------------------------------------------------

	/** Pulls on a cloak and a hood (fur in a cold land) over whatever bare places there are. */
	private static void warm(PathfinderMob m, boolean fur) {
		if (m.isSleeping() || Harness.get(m, Harness.Slot.OVER).isEmpty() && !put(m, HeraldryArmour.stack("cloak"))) {
			return;
		}
		if (Harness.get(m, Harness.Slot.HOOD).isEmpty()) {
			put(m, HeraldryArmour.stack(fur ? "fur_hood" : "hood"));
		}
	}

	private static boolean put(PathfinderMob m, ItemStack piece) {
		if (piece.isEmpty()) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, piece, tag -> tag.putBoolean(WINTER, true));
		Harness.dress(m, piece);
		return true;
	}

	/** Takes off what the cold put on, and nothing else. */
	private static void bare(PathfinderMob m) {
		for (Harness.Slot slot : new Harness.Slot[] {Harness.Slot.OVER, Harness.Slot.HOOD}) {
			if (marked(Harness.get(m, slot))) {
				Harness.set(m, slot, ItemStack.EMPTY);
			}
		}
	}
}
