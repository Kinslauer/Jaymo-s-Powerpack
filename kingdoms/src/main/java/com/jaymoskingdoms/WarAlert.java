package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.War;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.65) A realm at war is on alert, the whole of it (its vassals, and villages under it, too), from the day war is
 * declared until peace:
 * <ul>
 * <li><b>Bells.</b> The seat's bell peals when war comes, and tolls now and then all through it. Once a war party
 * musters against the realm the bell rings the alarm, over and over, until the party's beaten or gone.</li>
 * <li><b>Sandbags.</b> Its people carry sandbags out and lay short walls round the seat, most of them facing the
 * nearest enemy seat ({@link Sandbags}); roads, doors and fields are left clear. At peace they're taken down again.</li>
 * <li><b>Hiding.</b> When the alarm rings, every civilian runs home (a child too) and stays in until the party's gone,
 * as from a monster ({@link Frights#shelter}); the guards and archers go to the sandbags facing the enemy.</li>
 * </ul>
 * The sandbags it put up are saved (a restart mid-war doesn't lose them); the rest is worked out afresh.
 */
final class WarAlert {
	/** How often the alert is kept (ticks). */
	static final int EVERY = 20;
	/** All through the war, the bell tolls this many strokes, every this many ticks; when war comes, it peals this many. */
	static final int TOLL_STROKES = 3;
	static final long TOLL_TICKS = 20 * 120;
	static final int PEAL_STROKES = 6;
	/** With a war party coming, the bell rings every this many ticks. */
	static final long ALARM_TICKS = 40;
	/** A settlement's bell is looked for this near its seat; its folk this near it are on alert. */
	static final int BELL_SEARCH = 4;
	static final long BELL_LOOK_TICKS = 600L;
	static final double WATCHED = 96.0;
	/** Civilians run home if it's this near; they stay in this long after the alarm stops (ticks). */
	static final int HOME_NEAR = 64;
	static final long HIDE_TICKS = 200L;
	/** The walls: this many facing the enemy (this long, spread this far apart, radians), this many round the back (this long). */
	static final int FRONT_WALLS = 5, FRONT_LENGTH = 5, BACK_WALLS = 3, BACK_LENGTH = 3;
	static final double FRONT_SPREAD = 0.4, BACK_SPREAD = 0.9;
	/** Walls stand this far from the seat (blocks, front then back). */
	static final int FRONT_MIN = 16, FRONT_MAX = 22, BACK_MIN = 13, BACK_MAX = 18;
	/** Up to this many villagers carry sandbags at once; one fetches a bag from this far; lays it within this reach; gives up after this long (ticks). */
	static final int CARRIERS = 4;
	static final double CARRIER_RANGE = 48.0, LAY_REACH = 2.5;
	static final long CARRY_TICKS = 20 * 30;
	/** With nobody to carry them, a bag is laid anyway after this long (ticks); at peace, this many bags are taken down each time. */
	static final long UNCARRIED_TICKS = 20 * 6;
	static final int TAKEN_DOWN = 2;
	/** Guards stand this near their post behind the sandbags. */
	static final double POST_REACH = 2.5;

	/** Per realm (its id): the sandbags it has put up (packed places). Saved on the overworld. */
	static final AttachmentType<Map<String, List<Long>>> RAISED = AttachmentRegistry.create(JaymosKingdoms.id("war_sandbags"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())));

	/** A bag on its way: who carries it (null: nobody near), since when. */
	private record Carry(@Nullable UUID carrier, long since) {
	}

	/** A realm on alert. */
	private static final class Alert {
		/** Its walls' places still to lay (packed), where each lies along, and the front's (for the guards' posts). */
		final Deque<Long> todo = new ArrayDeque<>();
		final Map<Long, Direction.Axis> axes = new HashMap<>();
		final Map<Long, Carry> carrying = new HashMap<>();
		final List<BlockPos> front = new ArrayList<>();
		/** The direction the enemy's in, from the seat (unit, flat). */
		Vec3 toward = new Vec3(1.0, 0.0, 0.0);
		@Nullable BlockPos bell;
		long bellLooked = -1_000_000L;
		int strokes;
		long lastToll, lastAlarm, alarmed = -1_000_000L;
	}

	private static final Map<Integer, Alert> ALERTS = new HashMap<>();
	/** Villagers carrying a sandbag just now. */
	private static final Set<UUID> CARRIERS_NOW = new HashSet<>();

	private WarAlert() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WarAlert::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ALERTS.clear();
			CARRIERS_NOW.clear();
		});
	}

	/** {@code v} is carrying a sandbag out (its routines wait). */
	static boolean carrying(Villager v) {
		return CARRIERS_NOW.contains(v.getUUID());
	}

	/** {@code f} is at war, or has one declared on it: its own, or its overlord's (as far up as it goes). */
	static boolean atWar(MinecraftServer server, Faction f) {
		if (f.fallen() || f.type() == FactionType.BARBARIAN) {
			return false;
		}
		// (0.7.224) a realm pledged to a great war is at war with its side
		int top = Wars.warTop(server, f).id();
		for (War war : WarData.wars(server)) {
			if (war.involves(top)) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		Set<Integer> alerted = new HashSet<>();
		for (Faction f : Factions.all(server)) {
			if (!atWar(server, f)) {
				continue;
			}
			alerted.add(f.id());
			if (!level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Alert a = ALERTS.get(f.id());
			if (a == null) {
				a = new Alert();
				ALERTS.put(f.id(), a);
				plan(level, f, a);
				a.strokes = PEAL_STROKES;
				a.lastToll = now;
			}
			BlockPos incoming = WarParties.incoming(server, f.id());
			if (incoming != null) {
				a.alarmed = now;
				a.strokes = 0;
				if (now - a.lastAlarm >= ALARM_TICKS) {
					a.lastAlarm = now;
					ring(level, f, a, true);
				}
				man(level, f, a);
			} else if (now - a.lastToll >= TOLL_TICKS) {
				a.lastToll = now;
				a.strokes = TOLL_STROKES;
			}
			if (a.strokes > 0) {
				a.strokes--;
				ring(level, f, a, false);
			}
			if (now - a.alarmed <= HIDE_TICKS) {
				hide(level, f, incoming, now);
			}
			build(level, f, a, now, incoming != null);
		}
		for (Iterator<Map.Entry<Integer, Alert>> it = ALERTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Alert> e = it.next();
			if (!alerted.contains(e.getKey())) {
				for (Carry c : e.getValue().carrying.values()) {
					if (c.carrier() != null) {
						CARRIERS_NOW.remove(c.carrier());
					}
				}
				it.remove();
			}
		}
		takeDown(level, alerted);
	}

	// ---- bells ---------------------------------------------------------------------------------------------------

	/**
	 * The seat's bell rings: a stroke of a toll (heard, but nobody runs: {@code alarm} false), or the alarm, the bell
	 * swung so the vanilla villagers near it hide too. A seat with no bell has one heard there all the same.
	 */
	private static void ring(ServerLevel level, Faction f, Alert a, boolean alarm) {
		BlockPos bell = a.bell != null && level.getBlockState(a.bell).getBlock() instanceof BellBlock ? a.bell : null;
		if (bell == null && level.getGameTime() - a.bellLooked >= BELL_LOOK_TICKS) {
			a.bellLooked = level.getGameTime();
			bell = findBell(level, f.seat());
		}
		a.bell = bell;
		if (bell != null && alarm && level.getBlockState(bell).getBlock() instanceof BellBlock block) {
			block.attemptToRing(level, bell, null);
			return;
		}
		BlockPos at = bell != null ? bell : f.seat();
		level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, alarm ? 2.0F : 3.0F, alarm ? 1.0F : 0.85F);
	}

	private static @Nullable BlockPos findBell(ServerLevel level, BlockPos seat) {
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(seat.offset(-BELL_SEARCH, -BELL_SEARCH, -BELL_SEARCH), seat.offset(BELL_SEARCH, BELL_SEARCH, BELL_SEARCH))) {
			if (level.getBlockState(p).getBlock() instanceof BellBlock && (best == null || p.distSqr(seat) < best.distSqr(seat))) {
				best = p.immutable();
			}
		}
		return best;
	}

	// ---- sandbags ------------------------------------------------------------------------------------------------

	/** Where its walls go: short straight runs round the seat, most of them on the side toward the nearest enemy seat. */
	private static void plan(ServerLevel level, Faction f, Alert a) {
		MinecraftServer server = level.getServer();
		BlockPos seat = f.seat();
		BlockPos enemy = nearestEnemy(server, f);
		RandomSource random = level.getRandom();
		double bearing = enemy != null ? Math.atan2(enemy.getZ() - seat.getZ(), enemy.getX() - seat.getX()) : random.nextDouble() * Math.PI * 2.0;
		a.toward = new Vec3(Math.cos(bearing), 0.0, Math.sin(bearing));
		Set<Long> taken = new HashSet<>();
		for (int i = 0; i < FRONT_WALLS; i++) {
			double angle = bearing + (i - (FRONT_WALLS - 1) / 2.0) * FRONT_SPREAD;
			wall(level, f, a, angle, Mth.nextInt(random, FRONT_MIN, FRONT_MAX), FRONT_LENGTH, true, taken);
		}
		for (int i = 0; i < BACK_WALLS; i++) {
			double angle = bearing + Math.PI + (i - (BACK_WALLS - 1) / 2.0) * BACK_SPREAD;
			wall(level, f, a, angle, Mth.nextInt(random, BACK_MIN, BACK_MAX), BACK_LENGTH, false, taken);
		}
	}

	/** One wall: {@code length} bags in a straight run across the line from the seat, {@code radius} out at {@code angle}. */
	private static void wall(ServerLevel level, Faction f, Alert a, double angle, int radius, int length, boolean front, Set<Long> taken) {
		BlockPos seat = f.seat();
		int cx = Mth.floor(seat.getX() + 0.5 + Math.cos(angle) * radius), cz = Mth.floor(seat.getZ() + 0.5 + Math.sin(angle) * radius);
		// it runs along whichever axis is nearer square to the line from the seat
		Direction.Axis along = Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)) ? Direction.Axis.Z : Direction.Axis.X;
		for (int k = -(length / 2); k <= length / 2; k++) {
			int x = cx + (along == Direction.Axis.X ? k : 0), z = cz + (along == Direction.Axis.Z ? k : 0);
			if (!level.isLoaded(new BlockPos(x, seat.getY(), z))) {
				continue;
			}
			BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Sandbags.isRaised(level.getBlockState(p.below()))) {
				// (laid before a restart)
				if (front) {
					a.front.add(p.below());
				}
				continue;
			}
			if (taken.add(p.asLong()) && fit(level, p)) {
				a.todo.add(p.asLong());
				a.axes.put(p.asLong(), along);
				if (front) {
					a.front.add(p);
				}
			}
		}
	}

	/** A bag can lie at {@code p}: open ground under the sky (earth, sand, stone or snow: not a roof, a road or a field), and not across a door or gate. */
	private static boolean fit(ServerLevel level, BlockPos p) {
		BlockState here = level.getBlockState(p), below = level.getBlockState(p.below());
		if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || !level.canSeeSky(p) || !below.isFaceSturdy(level, p.below(), Direction.UP)
			|| !below.getFluidState().isEmpty()) {
			return false;
		}
		// (on the ground: not a roof, a road, a field or a wall)
		if (!below.is(BlockTags.DIRT) && !below.is(BlockTags.SAND) && !below.is(BlockTags.BASE_STONE_OVERWORLD) && !below.is(BlockTags.SNOW)
			&& !below.is(Blocks.GRAVEL)) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState by = level.getBlockState(p.relative(d));
			if (by.getBlock() instanceof DoorBlock || by.getBlock() instanceof FenceGateBlock || by.getBlock() instanceof BellBlock) {
				return false;
			}
		}
		return true;
	}

	/** The seat of the nearest realm on the other side of any of {@code f}'s wars. */
	private static @Nullable BlockPos nearestEnemy(MinecraftServer server, Faction f) {
		// (0.7.224) a realm pledged to a great war is at war with its side
		int top = Wars.warTop(server, f).id();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (War war : WarData.wars(server)) {
			if (!war.involves(top)) {
				continue;
			}
			for (Faction e : Wars.members(server, Wars.otherSide(war, top))) {
				double d = e.seat().distSqr(f.seat());
				if (d < bestD) {
					bestD = d;
					best = e.seat();
				}
			}
		}
		return best;
	}

	/** Its people carry the bags out and lay them, a few at a time; with a party coming, the guards lay what's left. */
	private static void build(ServerLevel level, Faction f, Alert a, long now, boolean coming) {
		while (a.carrying.size() < CARRIERS && !a.todo.isEmpty()) {
			long packed = a.todo.poll();
			BlockPos p = BlockPos.of(packed);
			Villager v = coming ? null : carrier(level, p);
			if (v != null) {
				CARRIERS_NOW.add(v.getUUID());
			}
			a.carrying.put(packed, new Carry(v == null ? null : v.getUUID(), now));
		}
		for (Iterator<Map.Entry<Long, Carry>> it = a.carrying.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Long, Carry> e = it.next();
			BlockPos p = BlockPos.of(e.getKey());
			Carry c = e.getValue();
			Villager v = c.carrier() != null && level.getEntity(c.carrier()) instanceof Villager found && found.isAlive() ? found : null;
			boolean lay;
			if (v == null || coming || Frights.afraid(v.getUUID())) {
				lay = c.carrier() == null ? now - c.since() >= UNCARRIED_TICKS : true;
			} else if (v.distanceToSqr(Vec3.atCenterOf(p)) <= LAY_REACH * LAY_REACH) {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				lay = true;
			} else {
				BehaviorUtils.setWalkAndLookTargetMemories(v, p, 0.6F, 1);
				lay = now - c.since() >= CARRY_TICKS;
			}
			if (lay) {
				if (c.carrier() != null) {
					CARRIERS_NOW.remove(c.carrier());
				}
				it.remove();
				lay(level, f, p, a.axes.getOrDefault(e.getKey(), Direction.Axis.X));
			}
		}
	}

	/** The nearest grown civilian who's free, near enough to fetch the bag to {@code p}. */
	private static @Nullable Villager carrier(ServerLevel level, BlockPos p) {
		Villager best = null;
		double bestD = CARRIER_RANGE * CARRIER_RANGE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(p).inflate(CARRIER_RANGE), v -> v.isAlive() && !v.isBaby())) {
			double d = v.distanceToSqr(Vec3.atCenterOf(p));
			if (d < bestD && civilian(v) && !CARRIERS_NOW.contains(v.getUUID()) && !v.isSleeping() && !v.isPassenger() && !v.isNoAi()
				&& v.getTarget() == null && !Frights.afraid(v.getUUID()) && !Festivals.going(v)) {
				bestD = d;
				best = v;
			}
		}
		return best;
	}

	private static void lay(ServerLevel level, Faction f, BlockPos p, Direction.Axis axis) {
		if (!level.isLoaded(p) || !fit(level, p)) {
			return;
		}
		BlockState bag = Sandbags.raised(axis);
		level.setBlockAndUpdate(p, bag);
		level.playSound(null, p, bag.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		Map<String, List<Long>> all = new HashMap<>(level.getAttachedOrElse(RAISED, Map.of()));
		List<Long> mine = new ArrayList<>(all.getOrDefault(Integer.toString(f.id()), List.of()));
		mine.add(p.asLong());
		all.put(Integer.toString(f.id()), List.copyOf(mine));
		level.setAttached(RAISED, all);
	}

	/** At peace (or fallen), a realm's sandbags are taken down, a few at a time, once its seat's loaded. */
	private static void takeDown(ServerLevel level, Set<Integer> alerted) {
		Map<String, List<Long>> all = level.getAttachedOrElse(RAISED, Map.of());
		if (all.isEmpty()) {
			return;
		}
		Map<String, List<Long>> left = new HashMap<>(all);
		boolean changed = false;
		for (Map.Entry<String, List<Long>> e : all.entrySet()) {
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				left.remove(e.getKey());
				changed = true;
				continue;
			}
			if (alerted.contains(id)) {
				continue;
			}
			List<Long> bags = new ArrayList<>(e.getValue());
			int done = 0;
			for (Iterator<Long> it = bags.iterator(); it.hasNext() && done < TAKEN_DOWN;) {
				BlockPos p = BlockPos.of(it.next());
				if (!level.isPositionEntityTicking(p)) {
					continue;
				}
				if (Sandbags.isRaised(level.getBlockState(p))) {
					level.destroyBlock(p, false);
					done++;
				}
				it.remove();
				changed = true;
			}
			if (bags.isEmpty()) {
				left.remove(e.getKey());
			} else {
				left.put(e.getKey(), List.copyOf(bags));
			}
		}
		if (changed) {
			level.setAttached(RAISED, left);
		}
	}

	// ---- the alarm -----------------------------------------------------------------------------------------------

	/** A villager who isn't a guard or an archer (the realm's, or MCA's). */
	private static boolean civilian(Villager v) {
		if (v instanceof Worker w && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER)) {
			return false;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		return !job.equals("guard") && !job.equals("archer");
	}

	/** Every civilian near the seat runs home and stays in (the children too); with no home near, away from the enemy. */
	private static void hide(ServerLevel level, Faction f, @Nullable BlockPos incoming, long now) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), v -> v.isAlive() && civilian(v))) {
			// (one carrying a bag drops it: the guards lay it)
			CARRIERS_NOW.remove(v.getUUID());
			Optional<GlobalPos> home = v.getBrain().getMemoryInternal(MemoryModuleType.HOME);
			if (home == null) {
				home = Optional.empty();
			}
			BlockPos to;
			if (home.isPresent() && home.get().dimension() == level.dimension() && home.get().pos().closerThan(v.blockPosition(), HOME_NEAR)) {
				to = home.get().pos();
			} else {
				Vec3 from = incoming != null ? Vec3.atCenterOf(incoming) : Vec3.atCenterOf(f.seat());
				Vec3 off = v.position().subtract(from).multiply(1.0, 0.0, 1.0);
				if (off.lengthSqr() < 1.0E-3) {
					off = new Vec3(1.0, 0.0, 0.0);
				}
				Vec3 at = v.position().add(off.normalize().scale(16.0));
				int x = Mth.floor(at.x), z = Mth.floor(at.z);
				to = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			}
			Frights.shelter(level, v, to, now + HIDE_TICKS);
		}
	}

	/** The realm's guards and archers who aren't busy go and stand behind the sandbags facing the enemy, each at his own. */
	private static void man(ServerLevel level, Faction f, Alert a) {
		List<BlockPos> posts = new ArrayList<>();
		for (BlockPos p : a.front) {
			if (Sandbags.isRaised(level.getBlockState(p))) {
				posts.add(p);
			}
		}
		if (posts.isEmpty()) {
			return;
		}
		int n = 0;
		for (Folk folk : level.getEntitiesOfClass(Folk.class, new AABB(f.seat()).inflate(WATCHED), x -> x.isAlive() && x.factionId() == f.id())) {
			if ((folk.role() != Folk.GUARD && folk.role() != Folk.ARCHER) || WarParties.isSoldier(folk) || !Workers.IDLE.contains(folk.task())
				|| folk.getTarget() != null || folk.isPassenger() || folk.isVehicle()) {
				continue;
			}
			// (one step back from his bag, on the seat's side)
			BlockPos bag = posts.get(n++ % posts.size());
			BlockPos post = BlockPos.containing(Vec3.atCenterOf(bag).subtract(a.toward.scale(1.5)));
			post = new BlockPos(post.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, post.getX(), post.getZ()), post.getZ());
			if (folk.distanceToSqr(Vec3.atBottomCenterOf(post)) > POST_REACH * POST_REACH) {
				folk.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
			} else {
				folk.getNavigation().stop();
				folk.getLookControl().setLookAt(Vec3.atCenterOf(bag).add(a.toward.scale(8.0)));
			}
		}
	}
}
