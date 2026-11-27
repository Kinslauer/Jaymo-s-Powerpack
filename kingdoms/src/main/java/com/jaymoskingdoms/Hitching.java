package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27, a guard asleep in the saddle in a house's doorway): "it makes no sense for a cavalryman to wait in a
 * doorway, while mounted ... mounted cavalrymen should be able to dismount, hitch their horses to nearby fences, and go
 * into buildings, say for sleeping or purchasing from shops."
 * <p>
 * A realm's mounted guard or archer ({@link Cavalry}, an MCA villager) gets down when the saddle is no place for what
 * comes next:
 * <ul>
 * <li>the night (their brain's rest), or a guard's hours off ({@link Rulers#offDuty}), when they go to bed, to the shops
 * and the tavern ({@link Routines});</li>
 * <li>somewhere to walk to under a roof (a house, a shop);</li>
 * <li>work done on foot: carrying the dead, tending the hurt, a meal, sitting by the fire, a routine;</li>
 * <li>stood still in or right by a doorway, or under a roof, a horse's length from getting anywhere.</li>
 * </ul>
 * They lead their horse by the reins to the nearest fence out in the open ({@link #POST_REACH}) and tie it there (a
 * real lead and knot), or, with no fence about, leave it standing out in the open, reins down. Then they're on foot,
 * their brain their own. Once nothing keeps them on foot (on duty, morning, the work done) they walk back to it, untie it
 * and ride on. A horse whose rider never comes back (killed, gone) is untied after a while and is a spare the realm gives
 * the next guard ({@link Cavalry}).
 * <p>
 * Who never gets down here: a driver or carter, a caravan's or a war party's riders, a ruler's escort, anyone in a fight
 * or afraid, and anyone out of their realm's land.
 */
public final class Hitching {
	/** How often riders are looked at (ticks), and each hitch walked on. */
	static final int SCAN = 20, STEP = 5;
	/** A fence this far from the rider (across; up and down) can be tied to. */
	static final int POST_REACH = 14, POST_DEPTH = 3;
	/** With no fence, an open spot is looked for this far round. */
	static final int OPEN_REACH = 10;
	/** At most this many horses on one knot. */
	static final int PER_POST = 3;
	/** Close enough to the post (across) to tie the horse; to the horse to get up. */
	static final double TIE_REACH = 1.9, MOUNT_REACH = 2.4;
	/** Leading it, longest (ticks) before the horse is left where it is; fetching it, before trying again later. */
	static final long LEAD_TICKS = 600L, FETCH_TICKS = 800L;
	/** On foot at least this long (ticks), and nothing keeping them for this long, before they ride again. */
	static final long ON_FOOT_MIN = 600L, CALM_TICKS = 200L;
	/** Got down only for being in the way (a doorway, a roof, someone asking), on foot at least this long (ticks), so they don't ride straight back in. */
	static final long STALL_FOOT = 2400L;
	/** A rider stood this long (ticks) without going anywhere in or by a doorway, or under a roof, gets down. */
	static final int STALL_TICKS = 60;
	static final double HEADWAY = 0.5;
	/** A horse whose rider hasn't been seen this long (ticks) is untied and a spare. */
	static final long OWNER_GONE = 6000L;
	/** A horse left standing (no post) drifting further than this is walked back. */
	static final double GROUND_DRIFT = 4.0;
	/** A led horse further than this from its rider is brought along before the lead would snap. */
	static final double LEAD_SLACK = 7.0, LEAD_SNAP = 10.5;
	/** Walking pace, as navigation's speed. */
	static final double WALK = 0.6;

	/** Tags on a hitched horse: tied up, and by whom (their UUID after the dot). */
	static final String TAG = "jaymoskingdoms.hitched", BY = "jaymoskingdoms.hitched_by.";

	enum Stage { LEAD, TIED, FETCH }

	private static final class Hitch {
		final UUID rider, horse;
		Stage stage;
		/** The fence it's tied to (null: left standing); where the horse stands. */
		@Nullable BlockPos post;
		BlockPos stand;
		long since, calmSince = -1L, missingSince = -1L, fetchFrom;
		/** On foot at least this long once tied. */
		long minFoot = ON_FOOT_MIN;

		Hitch(UUID rider, UUID horse, Stage stage, @Nullable BlockPos post, BlockPos stand, long now) {
			this.rider = rider;
			this.horse = horse;
			this.stage = stage;
			this.post = post;
			this.stand = stand;
			this.since = now;
		}
	}

	private record Still(Vec3 at, int ticks) {
	}

	/** Rider → their hitch. */
	private static final Map<UUID, Hitch> HITCHES = new HashMap<>();
	/** Mounted riders' last spot, for a stall in a doorway. */
	private static final Map<UUID, Still> STILL = new HashMap<>();

	private Hitching() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Hitching::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HITCHES.clear();
			STILL.clear();
		});
	}

	// ---- asked by others -------------------------------------------------------------------------------------------

	/** {@code mob} has a horse of theirs tied up somewhere (so {@link Cavalry} counts them as mounted). */
	static boolean hasHorse(Entity mob) {
		return HITCHES.containsKey(mob.getUUID());
	}

	/** {@code horse} is tied up for a rider (not a spare). */
	static boolean hitched(Entity horse) {
		return horse.entityTags().contains(TAG);
	}

	/**
	 * From {@link Workers#step}: true while {@code mob} leads their horse to a post or walks back to it (their brain waits,
	 * this walks them).
	 */
	static boolean brainStep(PathfinderMob mob) {
		Hitch h = HITCHES.get(mob.getUUID());
		return h != null && h.stage != Stage.TIED && !mob.isPassenger();
	}

	/**
	 * {@link MakeWay}: {@code rider}, in the saddle, is in someone's way at a door or indoors: they get down and take the
	 * horse out of it. False if they're not one who does ({@link #rides}).
	 */
	static boolean getDown(ServerLevel level, PathfinderMob rider) {
		if (!(rider instanceof Worker w) || !(Cavalry.horse(rider) instanceof AbstractHorse horse) || !rides(level, w, horse) || HITCHES.containsKey(rider.getUUID())) {
			return false;
		}
		begin(level, w, horse, false, STALL_FOOT);
		return true;
	}

	// ---- the round -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % STEP == 0 && !HITCHES.isEmpty()) {
			for (Iterator<Hitch> it = HITCHES.values().iterator(); it.hasNext(); ) {
				if (!step(level, it.next(), now)) {
					it.remove();
				}
			}
		}
		if (now % SCAN == 0) {
			scan(level, now);
		}
	}

	/** Riders who should get down do; and hitched horses known only by their tags (after a restart) are taken up again. */
	private static void scan(ServerLevel level, long now) {
		List<AbstractHorse> horses = new ArrayList<>();
		level.getEntities(EntityTypeTest.forClass(AbstractHorse.class), h -> h.isAlive() && h.entityTags().contains(Cavalry.TAG), horses);
		for (AbstractHorse horse : horses) {
			if (hitched(horse)) {
				relearn(level, horse, now);
				continue;
			}
			if (!(horse.getFirstPassenger() instanceof PathfinderMob rider) || !(rider instanceof Worker w) || !rides(level, w, horse)) {
				continue;
			}
			boolean stalled = stalled(level, rider, horse);
			String why = reason(level, w);
			if (stalled || why != null) {
				STILL.remove(rider.getUUID());
				// (to a fence for the night, their hours off or a call indoors; work on foot, or just out of a doorway, near at hand)
				begin(level, w, horse, why != null && !why.equals("work"), why == null ? STALL_FOOT : ON_FOOT_MIN);
			}
		}
		if (now % (SCAN * 30) == 0) {
			STILL.keySet().removeIf(id -> !(level.getEntity(id) instanceof PathfinderMob m) || !m.isPassenger());
		}
	}

	/** A hitched horse whose hitch isn't known (the server restarted): its rider's hitch is made again. */
	private static void relearn(ServerLevel level, AbstractHorse horse, long now) {
		UUID rider = owner(horse);
		if (rider == null) {
			untie(horse);
			return;
		}
		Hitch known = HITCHES.get(rider);
		if (known != null && known.horse.equals(horse.getUUID())) {
			return;
		}
		if (known != null) {
			// (they have another horse tied up: this one's a spare)
			untie(horse);
			return;
		}
		BlockPos post = horse.getLeashHolder() instanceof LeashFenceKnotEntity knot ? knot.getPos() : null;
		HITCHES.put(rider, new Hitch(rider, horse.getUUID(), Stage.TIED, post, horse.blockPosition(), now - ON_FOOT_MIN));
	}

	/** Walks one hitch on; false when it's over. */
	private static boolean step(ServerLevel level, Hitch h, long now) {
		AbstractHorse horse = level.getEntity(h.horse) instanceof AbstractHorse a ? a : null;
		PathfinderMob rider = level.getEntity(h.rider) instanceof PathfinderMob m ? m : null;
		if (horse != null && !horse.isAlive() || horse != null && !hitched(horse)) {
			// (dead, or untied by someone else: a player took it, a stablehand)
			return false;
		}
		if (horse == null) {
			// (its chunk isn't loaded, or it's gone: after a while, forgotten)
			if (h.missingSince < 0L) {
				h.missingSince = now;
			}
			return now - h.missingSince < OWNER_GONE;
		}
		if (rider == null || !rider.isAlive()) {
			if (h.missingSince < 0L) {
				h.missingSince = now;
			}
			if (now - h.missingSince >= OWNER_GONE || rider != null) {
				// (killed, or gone for good: the horse is the realm's spare)
				untie(horse);
				return false;
			}
			holdGround(horse, h);
			return true;
		}
		h.missingSince = -1L;
		if (rider.isPassenger()) {
			if (rider.getVehicle() != horse) {
				// (in another saddle, a cart, a boat: this one's untied)
				untie(horse);
				return false;
			}
			unmark(horse);
			return false;
		}
		return switch (h.stage) {
			case LEAD -> lead(level, h, rider, horse, now);
			case TIED -> tied(level, h, rider, horse, now);
			case FETCH -> fetch(level, h, rider, horse, now);
		};
	}

	private static boolean lead(ServerLevel level, Hitch h, PathfinderMob rider, AbstractHorse horse, long now) {
		boolean held = rider.isSleeping() || Downed.isDowned(rider) || PowersCompat.isCarried(rider) || rider.getTarget() != null;
		if (held || now - h.since > LEAD_TICKS || horizontalSqr(rider.position(), h.stand) <= TIE_REACH * TIE_REACH) {
			tie(level, h, horse, now);
			return true;
		}
		if (horse.getLeashHolder() != rider) {
			horse.setLeashedTo(rider, true);
		}
		double d = horse.distanceTo(rider);
		if (d > LEAD_SNAP) {
			// (snagged on something: brought round before the lead would snap)
			horse.removeLeash();
			Vec3 at = rider.position();
			horse.teleportTo(at.x, at.y, at.z);
			horse.setLeashedTo(rider, true);
		} else if (d > LEAD_SLACK) {
			horse.getNavigation().moveTo(rider, 1.2);
		}
		if (rider.getNavigation().isDone() || now % 20 == 0) {
			rider.getNavigation().moveTo(h.stand.getX() + 0.5, h.stand.getY(), h.stand.getZ() + 0.5, WALK);
		}
		return true;
	}

	private static boolean tied(ServerLevel level, Hitch h, PathfinderMob rider, AbstractHorse horse, long now) {
		if (h.post != null && !(horse.getLeashHolder() instanceof LeashFenceKnotEntity)) {
			// (the knot was broken: it stands where it is now)
			h.post = null;
			h.stand = horse.blockPosition();
		}
		holdGround(horse, h);
		if (!(rider instanceof Worker w) || reason(level, w) != null || !Drivers.canMount(w) || Rulers.target(rider) != null
			|| Frights.afraid(rider.getUUID())) {
			h.calmSince = -1L;
			return true;
		}
		if (h.calmSince < 0L) {
			h.calmSince = now;
		}
		if (now - h.since >= h.minFoot && now - h.calmSince >= CALM_TICKS) {
			h.stage = Stage.FETCH;
			h.fetchFrom = now;
		}
		return true;
	}

	private static boolean fetch(ServerLevel level, Hitch h, PathfinderMob rider, AbstractHorse horse, long now) {
		if (!(rider instanceof Worker w) || reason(level, w) != null || !Drivers.canMount(w) || rider.getTarget() != null) {
			// (called away again: the horse waits)
			h.stage = Stage.TIED;
			h.calmSince = -1L;
			return true;
		}
		if (now - h.fetchFrom > FETCH_TICKS) {
			// (no way to it just now: tried again later)
			h.stage = Stage.TIED;
			h.since = now;
			h.calmSince = -1L;
			return true;
		}
		if (rider.distanceTo(horse) > MOUNT_REACH) {
			if (rider.getNavigation().isDone() || now % 20 == 0) {
				rider.getNavigation().moveTo(horse, WALK);
			}
			return true;
		}
		rider.getNavigation().stop();
		rider.getLookControl().setLookAt(horse);
		untie(horse);
		rider.startRiding(horse);
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 0.6F, 1.0F);
		return false;
	}

	// ---- getting down and tying up -----------------------------------------------------------------------------------

	/**
	 * {@code w} gets down from {@code horse} and takes it by the reins: to the nearest open fence if {@code post} (else, or
	 * with none about, to the nearest open ground if it's under a roof or in a doorway, or left right there).
	 */
	private static void begin(ServerLevel level, Worker w, AbstractHorse horse, boolean post, long minFoot) {
		PathfinderMob rider = w.mob();
		long now = level.getGameTime();
		BlockPos at = horse.blockPosition();
		BlockPos fence = post ? fence(level, at) : null;
		BlockPos stand = fence != null ? beside(level, fence, at) : open(level, at) ? at : openNear(level, at);
		if (fence != null && stand == null) {
			fence = null;
			stand = open(level, at) ? at : openNear(level, at);
		}
		rider.stopRiding();
		rider.getNavigation().stop();
		horse.getNavigation().stop();
		horse.addTag(TAG);
		horse.addTag(BY + rider.getUUID());
		Hitch h = new Hitch(rider.getUUID(), horse.getUUID(), Stage.LEAD, fence, stand != null ? stand : at, now);
		h.minFoot = minFoot;
		HITCHES.put(rider.getUUID(), h);
		if (stand == null || stand.equals(at) && fence == null) {
			// (out in the open already: left standing right here)
			tie(level, h, horse, now);
			return;
		}
		horse.setLeashedTo(rider, true);
	}

	/** Ties the horse at the post (a knot on the fence), or leaves it standing where it is. Then they're on foot. */
	private static void tie(ServerLevel level, Hitch h, AbstractHorse horse, long now) {
		if (h.post != null && level.getBlockState(h.post).is(BlockTags.FENCES) && knotted(level, h.post) < PER_POST
			&& horizontalSqr(horse.position(), h.post) <= 16.0) {
			LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, h.post);
			if (horse.getLeashHolder() != null) {
				horse.removeLeash();
			}
			horse.setLeashedTo(knot, true);
			knot.playPlacementSound();
		} else {
			h.post = null;
			if (horse.getLeashHolder() != null) {
				horse.removeLeash();
			}
			if (!open(level, horse.blockPosition()) && !h.stand.equals(horse.blockPosition()) && open(level, h.stand)) {
				// (the lead ran out of time with the horse still in a doorway or under a roof: it's out where it was going)
				horse.teleportTo(h.stand.getX() + 0.5, h.stand.getY(), h.stand.getZ() + 0.5);
			}
			level.playSound(null, horse.blockPosition(), SoundEvents.LEAD_UNTIED, SoundSource.NEUTRAL, 0.5F, 1.0F);
		}
		h.stand = horse.blockPosition();
		h.stage = Stage.TIED;
		h.since = now;
		h.calmSince = -1L;
		horse.getNavigation().stop();
	}

	/** Untied and no longer anyone's hitched horse (the knot goes once nothing's tied to it). */
	private static void untie(AbstractHorse horse) {
		if (horse.getLeashHolder() != null) {
			horse.removeLeash();
		}
		unmark(horse);
	}

	private static void unmark(AbstractHorse horse) {
		horse.removeTag(TAG);
		for (String tag : List.copyOf(horse.entityTags())) {
			if (tag.startsWith(BY)) {
				horse.removeTag(tag);
			}
		}
	}

	/** A horse left standing (reins down) doesn't wander off: walked back if it drifts. */
	private static void holdGround(AbstractHorse horse, Hitch h) {
		if (h.post == null && horse.getLeashHolder() == null && horizontalSqr(horse.position(), h.stand) > GROUND_DRIFT * GROUND_DRIFT) {
			horse.getNavigation().moveTo(h.stand.getX() + 0.5, h.stand.getY(), h.stand.getZ() + 0.5, 1.0);
		}
	}

	private static @Nullable UUID owner(AbstractHorse horse) {
		for (String tag : horse.entityTags()) {
			if (tag.startsWith(BY)) {
				try {
					return UUID.fromString(tag.substring(BY.length()));
				} catch (IllegalArgumentException e) {
					return null;
				}
			}
		}
		return null;
	}

	// ---- who gets down, and why -----------------------------------------------------------------------------------------

	/**
	 * {@code w} in {@code horse}'s saddle is one of a realm's mounted guards or archers who gets down when there's cause:
	 * not a driver, a caravan's, a war party's or a border clash's rider, a ruler's escort, a ship's crew; not in a fight,
	 * afraid or down; and in their own realm's land.
	 */
	static boolean rides(ServerLevel level, Worker w, AbstractHorse horse) {
		PathfinderMob rider = w.mob();
		if (!(rider instanceof Villager) || w.role() != Folk.GUARD && w.role() != Folk.ARCHER || !horse.entityTags().contains(Cavalry.TAG)
			|| horse.entityTags().contains(Cavalry.LENT) || horse.entityTags().contains(Caravans.TAG)) {
			return false;
		}
		if (Caravans.isCaravanFolk(w) || WarParties.isSoldier(w) || BorderClashes.isMan(w) || Royals.escortOf(w) != null || Crewmen.tagged(rider)
			|| Raids.isRaider(rider)) {
			return false;
		}
		if (rider.getTarget() != null || Rulers.target(rider) != null || Frights.afraid(rider.getUUID()) || Downed.isDowned(rider) || rider.isSleeping()) {
			return false;
		}
		Faction law = Theft.lawAt(level, rider.blockPosition());
		return law != null && law.id() == w.factionId();
	}

	/** Why {@code w} should be on foot just now (the night, their hours off, work on foot, somewhere indoors), or null. */
	static @Nullable String reason(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (!(mob instanceof Villager v)) {
			return null;
		}
		Optional<Activity> now = v.getBrain().getActiveNonCoreActivity();
		if (now.isPresent() && now.get() == Activity.REST) {
			return "rest";
		}
		if (w.role() == Folk.GUARD && Rulers.offDuty(mob, w, Royals.timeOfDay(level))) {
			return "off";
		}
		if (footWork(mob)) {
			return "work";
		}
		BlockPos to = walkTarget(v);
		if (to != null && to.closerThan(mob.blockPosition(), 48.0) && Overgrowth.roofed(level, to)) {
			return "indoors";
		}
		return null;
	}

	/** Work done on foot: carrying the dead, tending the hurt, a meal, the fire, a routine, a trade's work. */
	private static boolean footWork(PathfinderMob mob) {
		if (Bearers.busy(mob) || Undertakers.busy(mob) || FieldMedics.busy(mob) || Downed.helping(mob) || Firesides.sitting(mob)
			|| KitchenCompat.butchering(mob)) {
			return true;
		}
		return mob instanceof Villager v && (Routines.busy(v) || Meals.eating(v) || WorkScenes.working(v) || Whetting.working(v) || SmithMending.working(v));
	}

	private static @Nullable BlockPos walkTarget(Villager v) {
		Optional<WalkTarget> walk = v.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
		return walk == null || walk.isEmpty() ? null : walk.get().getTarget().currentBlockPosition();
	}

	/** {@code rider} has stood still {@link #STALL_TICKS} in a doorway or under a roof. */
	private static boolean stalled(ServerLevel level, PathfinderMob rider, AbstractHorse horse) {
		Vec3 here = horse.position();
		Still was = STILL.get(rider.getUUID());
		if (was == null || horizontalSqr(here, was.at()) > HEADWAY * HEADWAY) {
			STILL.put(rider.getUUID(), new Still(here, 0));
			return false;
		}
		int ticks = was.ticks() + SCAN;
		STILL.put(rider.getUUID(), new Still(was.at(), ticks));
		return ticks >= STALL_TICKS && (doorway(level, horse.blockPosition()) || Overgrowth.roofed(level, horse.blockPosition()));
	}

	/** A door or a gate at {@code at} or right beside it (at its feet or its head). */
	static boolean doorway(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) {
				return true;
			}
		}
		return false;
	}

	// ---- where ----------------------------------------------------------------------------------------------------------

	/** Out in the open: no roof over it and no door or gate beside it. */
	static boolean open(ServerLevel level, BlockPos at) {
		return !Overgrowth.roofed(level, at) && !doorway(level, at);
	}

	/** The nearest standing spot in the open within {@link #OPEN_REACH} of {@code at}, or null. */
	private static @Nullable BlockPos openNear(ServerLevel level, BlockPos at) {
		if (!Loaded.made(level, at.offset(-OPEN_REACH, 0, -OPEN_REACH), at.offset(OPEN_REACH, 0, OPEN_REACH))) {
			return null;
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-OPEN_REACH, -2, -OPEN_REACH), at.offset(OPEN_REACH, 2, OPEN_REACH))) {
			double d = p.distSqr(at);
			if (d < bestD && d >= 4.0 && standable(level, p) && open(level, p)) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** The nearest fence out in the open within {@link #POST_REACH} with room to tie another horse, or null. */
	private static @Nullable BlockPos fence(ServerLevel level, BlockPos at) {
		if (!Loaded.made(level, at.offset(-POST_REACH, 0, -POST_REACH), at.offset(POST_REACH, 0, POST_REACH))) {
			return null;
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-POST_REACH, -POST_DEPTH, -POST_REACH), at.offset(POST_REACH, POST_DEPTH, POST_REACH))) {
			double d = p.distSqr(at);
			if (d >= bestD || !level.getBlockState(p).is(BlockTags.FENCES) || Overgrowth.roofed(level, p) || knotted(level, p) >= PER_POST
	) {
				continue;
			}
			bestD = d;
			best = p.immutable();
		}
		return best;
	}

	/** A spot in the open beside {@code fence} (on the side nearest {@code from}) for the horse to stand, or null. */
	private static @Nullable BlockPos beside(ServerLevel level, BlockPos fence, BlockPos from) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(fence.offset(-2, -1, -2), fence.offset(2, 1, 2))) {
			if (p.getX() == fence.getX() && p.getZ() == fence.getZ()) {
				continue;
			}
			double d = p.distSqr(from);
			if (d < bestD && standable(level, p) && open(level, p)) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** Room for a horse to stand at {@code at}: two blocks clear over solid ground, no water. */
	private static boolean standable(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
			&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty() && level.getFluidState(at).isEmpty()
			&& !level.getBlockState(at.below()).is(BlockTags.FENCES);
	}

	/** How many horses are tied to the fence at {@code post} already. */
	private static int knotted(ServerLevel level, BlockPos post) {
		Optional<LeashFenceKnotEntity> knot = LeashFenceKnotEntity.getKnot(level, post);
		return knot.map(k -> Leashable.leashableLeashedTo(k).size()).orElse(0);
	}

	private static double horizontalSqr(Vec3 a, BlockPos b) {
		double dx = a.x - (b.getX() + 0.5), dz = a.z - (b.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	private static double horizontalSqr(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return dx * dx + dz * dz;
	}
}
