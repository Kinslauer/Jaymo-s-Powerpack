package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.36) Every walking mob (vanilla, MCA villagers, our folk) that has a path but makes no headway along it works
 * itself loose: first a hop toward its next step (a slab, a carpet, a fence post's corner), then a step back to the
 * middle of its block and a fresh path, and at last the walk is dropped so its AI picks something else. Our own folk,
 * out of a fight, turn up where they were going in a puff instead, as builders always have.
 *
 * <p>Vanilla's own check can't see this: it resets every time a goal asks for the path again, and chasing or
 * wandering goals ask every few ticks, so a mob snagged on a corner stays snagged.
 */
public final class Unstick {
	/** It has made no headway (moved less than {@link #HEADWAY} blocks across) for this long: it hops (and again every as long). */
	static final int HOP_TICKS = 30;
	/** Still none: back to the middle of its block and a fresh path. */
	static final int REPATH_TICKS = 70;
	/** Still none: the walk is dropped (not our folk; they puff). */
	static final int GIVE_UP_TICKS = 140;
	/** Our folk, out of a fight, still stuck after this long turn up where they were going. */
	static final int PUFF_TICKS = 240;
	/** Moving this far across counts as headway. */
	static final double HEADWAY = 0.6;
	/** Closer than this to where it's going and it's arrived, not stuck. */
	static final double ARRIVED = 1.5;
	/** The sideways push of a hop, and of the step back to the middle of its block. */
	static final double HOP_PUSH = 0.18, CENTRE_PUSH = 0.12;

	/** One mob's headway, kept on its navigation. */
	public static final class State {
		@Nullable Vec3 from;
		int still;

		void reset() {
			this.from = null;
			this.still = 0;
		}
	}

	private Unstick() {
	}

	/** From the end of {@link PathNavigation#tick}. */
	public static void tick(Mob mob, PathNavigation nav, State s, double speed, int reach) {
		if (!(mob.level() instanceof ServerLevel level) || nav.isDone() || mob.isPassenger() || mob.isNoAi() || mob.isVehicle() && mob.getControllingPassenger() != null) {
			s.reset();
			return;
		}
		BlockPos target = nav.getTargetPos();
		Vec3 here = mob.position();
		if (target == null || here.distanceTo(Vec3.atBottomCenterOf(target)) < ARRIVED) {
			s.reset();
			return;
		}
		if (s.from == null || horizontal(here, s.from) > HEADWAY) {
			s.from = here;
			s.still = 0;
			return;
		}
		s.still++;
		boolean ground = nav instanceof GroundPathNavigation;
		if (mob instanceof Folk folk && s.still >= PUFF_TICKS && puff(level, folk, target)) {
			s.reset();
			return;
		}
		if (s.still == REPATH_TICKS) {
			if (ground && mob.onGround()) {
				push(mob, Vec3.atBottomCenterOf(mob.blockPosition()).subtract(here), CENTRE_PUSH, false);
			}
			nav.stop();
			nav.moveTo(nav.createPath(target, reach), speed);
			return;
		}
		if (s.still >= GIVE_UP_TICKS && !(mob instanceof Folk)) {
			nav.stop();
			s.reset();
			return;
		}
		if (ground && s.still % HOP_TICKS == 0 && mob.onGround()) {
			Path path = nav.getPath();
			if (path != null && !path.isDone()) {
				push(mob, next(path, here).subtract(here), HOP_PUSH, true);
			}
		}
	}

	/** The next step it should be heading for (the one after, if it's standing on the next). */
	private static Vec3 next(Path path, Vec3 here) {
		Vec3 node = Vec3.atBottomCenterOf(path.getNextNodePos());
		if (horizontal(here, node) < 0.5 && path.getNextNodeIndex() + 1 < path.getNodeCount()) {
			node = Vec3.atBottomCenterOf(path.getNodePos(path.getNextNodeIndex() + 1));
		}
		return node;
	}

	private static void push(Mob mob, Vec3 toward, double strength, boolean hop) {
		Vec3 across = new Vec3(toward.x, 0.0, toward.z);
		if (across.lengthSqr() < 1.0E-4) {
			return;
		}
		across = across.normalize().scale(strength);
		mob.setDeltaMovement(mob.getDeltaMovement().add(across));
		if (hop) {
			mob.getJumpControl().jump();
		}
	}

	/** Our folk out of a fight, not breaking in and not on a caravan (they have their own), turn up where they were going. */
	private static boolean puff(ServerLevel level, Folk folk, BlockPos target) {
		if (folk.getTarget() != null || Assault.attacker(folk) || Caravans.isCaravanFolk(folk) || Attention.held(folk)) {
			return false;
		}
		BlockPos at = Construction.standable(level, target, 3);
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		folk.getNavigation().stop();
		folk.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
		return true;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- standing still: the log --------------------------------------------------------------------------------------

	/**
	 * Andy (2026-11-21) saw villagers standing still in their houses, not at work and not in bed. Before anything acts
	 * on it, every loaded villager that stays within {@link #STILL} blocks for {@link #LOG_TICKS} of watching (awake) is
	 * written to the server log once, with what its brain and our own holds say it's doing, so the cause can be read.
	 * Still there at {@link #STUCK_TICKS}, and nothing has it (not asleep, not at its job site, not held by a player, a
	 * talk or anything of ours), it's sent on: home at night, to its job site in work hours, else out to the bell; a
	 * shut door beside it is opened, and if there's still no way there it hops out to the nearest open ground.
	 */
	static final int WATCH_EVERY = 100;
	static final long LOG_TICKS = 2400L, STUCK_TICKS = 3600L;
	static final double STILL = 2.0;
	/** Standing this near its job site, it's at work. A shut door this near is opened. Its pace when sent on. */
	static final double AT_WORK = 4.0;
	static final int DOOR_REACH = 2;
	static final float SEND_SPEED = 0.6F;

	/** One villager under watch: where it stood, how long it has been seen about there, whether it was logged. */
	private record Still(Vec3 at, long ticks, boolean logged) {
	}

	private static final java.util.Map<java.util.UUID, Still> STILLS = new java.util.HashMap<>();

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Unstick::watch);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> STILLS.clear());
	}

	private static void watch(net.minecraft.server.MinecraftServer server) {
		if (server.overworld().getGameTime() % WATCH_EVERY != 0) {
			return;
		}
		java.util.Map<java.util.UUID, Still> seen = new java.util.HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Villager v : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class),
				v -> v.isAlive() && level.isPositionEntityTicking(v.blockPosition()))) {
				Still was = STILLS.get(v.getUUID());
				// (counted in looks, not game time, so one unloaded and loaded again doesn't read as a long stand)
				if (v.isSleeping() || was == null || horizontal(was.at(), v.position()) > STILL) {
					seen.put(v.getUUID(), new Still(v.position(), 0L, false));
					continue;
				}
				long ticks = was.ticks() + WATCH_EVERY;
				boolean logged = was.logged();
				if (!logged && ticks >= LOG_TICKS) {
					JaymosKingdoms.LOGGER.info("[stall] {}", describe(server, level, v, ticks));
					logged = true;
				}
				if (ticks >= STUCK_TICKS && free(level, v)) {
					JaymosKingdoms.LOGGER.info("[stall] {} sent on: {}", v.getName().getString(), sendOn(server, level, v));
					seen.put(v.getUUID(), new Still(v.position(), 0L, false));
					continue;
				}
				seen.put(v.getUUID(), new Still(was.at(), ticks, logged));
			}
		}
		STILLS.clear();
		STILLS.putAll(seen);
	}

	/**
	 * Nothing has {@code v}: awake, not at (or on its way to) its job site, not one of our own trades (they keep their
	 * stalls and kitchens without a job-site block), not in a player's pen, and no player, talk, errand or order of
	 * anyone's holding it there.
	 */
	private static boolean free(ServerLevel level, Villager v) {
		if (!Routines.idle(v) || Attention.held(v) || Routines.busy(v) || Meals.eating(v) || Meals.cook(v) || Rulers.is(v) || v.isLeashed()
			|| Downed.isDowned(v) || McaCompat.following(v) != null || (v instanceof Worker w && w.role() >= 0) || ShutIn.playerBuilt(level, v.blockPosition())) {
			return false;
		}
		net.minecraft.world.entity.npc.villager.VillagerProfession job = v.getVillagerData().profession().value();
		String name = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).getPath();
		if (name.equals("guard") || name.equals("archer")
			|| !v.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE)
			&& job.acquirableJobSite() == net.minecraft.world.entity.ai.village.poi.PoiType.NONE) {
			return false;
		}
		net.minecraft.world.entity.ai.memory.MemoryModuleType<?> staying = net.minecraft.core.registries.BuiltInRegistries.MEMORY_MODULE_TYPE
			.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(McaCompat.MOD, "staying_memory"));
		if (staying != null && v.getBrain().hasMemoryValue(staying)) {
			// (a player told them to stay)
			return false;
		}
		return memory(v, net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
			.filter(p -> p.dimension() == level.dimension() && p.pos().closerToCenterThan(v.position(), AT_WORK)).isEmpty();
	}

	/** {@code v}'s memory of {@code type}; empty if it has none, or its brain doesn't keep that kind. */
	private static <U> java.util.Optional<U> memory(Villager v, net.minecraft.world.entity.ai.memory.MemoryModuleType<U> type) {
		java.util.Optional<U> m = v.getBrain().getMemoryInternal(type);
		return m == null ? java.util.Optional.empty() : m;
	}

	/** Sends {@code v} on (see {@link #STUCK_TICKS}); says where, for the log. */
	private static String sendOn(net.minecraft.server.MinecraftServer server, ServerLevel level, Villager v) {
		BlockPos at = v.blockPosition();
		long t = Royals.timeOfDay(level);
		BlockPos to = null;
		String why;
		if (Homes.bedtime(level)) {
			to = McaCompat.home(v).filter(h -> h.dimension() == level.dimension()).map(net.minecraft.core.GlobalPos::pos)
				.or(() -> memory(v, net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).filter(h -> h.dimension() == level.dimension())
					.map(net.minecraft.core.GlobalPos::pos)).orElse(null);
			if (to == null) {
				// (night is MCA's: one with no bed is left where it is)
				return "night and no home, left be";
			}
			why = "home";
		} else if (t >= Routines.WORK_FROM && t < Routines.WORK_TO) {
			to = memory(v, net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).filter(p -> p.dimension() == level.dimension())
				.map(net.minecraft.core.GlobalPos::pos).orElse(null);
			why = "work";
		} else {
			why = "out";
		}
		BlockPos open = ShutIn.openGround(level, at);
		if (to == null) {
			Faction f = Factions.at(server, level, at);
			to = f != null && f.seat().closerThan(at, Routines.WATCHED) ? f.seat() : open;
			why += to == null ? ", nowhere" : f != null ? ", the bell" : ", open ground";
		}
		if (to == null) {
			return why;
		}
		v.getNavigation().stop();
		v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-DOOR_REACH, 0, -DOOR_REACH), at.offset(DOOR_REACH, 1, DOOR_REACH))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && net.minecraft.world.level.block.DoorBlock.isWoodenDoor(state)
				&& !door.isOpen(state)) {
				door.setOpen(v, level, state, p.immutable(), true);
				why += ", opened a door";
			}
		}
		// (boxed in, as ShutIn reckons it: no way even to the open ground nearby; out there in a puff, and on from there)
		Path out = open == null ? null : v.getNavigation().createPath(open, 1);
		if (open != null && (out == null || !out.canReach()) && !Attention.held(v)) {
			level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1.0, v.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			v.teleportTo(open.getX() + 0.5, open.getY(), open.getZ() + 0.5);
			level.sendParticles(ParticleTypes.POOF, open.getX() + 0.5, open.getY() + 1.0, open.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			why += ", hopped out to " + open.toShortString();
		}
		net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(v, to, SEND_SPEED, 1);
		return why + " (" + to.toShortString() + ")";
	}

	/** Everything about {@code v} that could be keeping it where it is, on one line. */
	private static String describe(net.minecraft.server.MinecraftServer server, ServerLevel level, Villager v, long ticks) {
		BlockPos at = v.blockPosition();
		net.minecraft.world.entity.ai.Brain<?> brain = v.getBrain();
		StringBuilder s = new StringBuilder();
		s.append(v.getName().getString()).append(McaCompat.isMca(v) ? " (mca" : " (vanilla");
		s.append(", ").append(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()));
		s.append(v.isBaby() ? ", child" : "").append(v instanceof Worker w ? ", worker role " + w.role() : "").append(')');
		s.append(" at ").append(at.toShortString()).append(' ').append(level.dimension().identifier());
		s.append(", still ").append(ticks / 20).append(" s, time ").append(Royals.timeOfDay(level));
		s.append(", sky ").append(level.canSeeSky(at.above())).append(", light ").append(level.getMaxLocalRawBrightness(at.above()));
		Faction f = Factions.at(server, level, at);
		s.append(", land ").append(f == null ? "none" : f.name() + " (" + f.type() + ", seat " + (int) Math.sqrt(f.seat().distSqr(at)) + " away)");
		s.append("; activity ").append(brain.getActiveNonCoreActivity().map(Object::toString).orElse("none"));
		s.append(", running [");
		brain.getRunningBehaviors().forEach(b -> s.append(b.debugString()).append(' '));
		s.append("]");
		s.append(", home ").append(far(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, level, at));
		s.append(", job ").append(far(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, level, at));
		s.append(", meeting ").append(far(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT, level, at));
		s.append(", walk ").append(memory(v, net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
			.map(w -> w.getTarget().currentBlockPosition().toShortString()).orElse("none"));
		s.append(", nav ").append(v.getNavigation().isDone() ? "done" : "to " + v.getNavigation().getTargetPos());
		net.minecraft.world.entity.ai.memory.MemoryModuleType<?> staying = net.minecraft.core.registries.BuiltInRegistries.MEMORY_MODULE_TYPE
			.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(McaCompat.MOD, "staying_memory"));
		s.append(", mca staying ").append(staying != null && brain.hasMemoryValue(staying));
		s.append(", mca home ").append(McaCompat.home(v).map(h -> h.dimension() == level.dimension() ? (int) Math.sqrt(h.pos().distSqr(at)) + " away" : "elsewhere").orElse("none"));
		s.append("; holds [").append(String.join(" ", holds(v))).append(']');
		return s.toString();
	}

	private static String far(net.minecraft.world.entity.ai.Brain<?> brain, net.minecraft.world.entity.ai.memory.MemoryModuleType<net.minecraft.core.GlobalPos> type,
		ServerLevel level, BlockPos at) {
		if (!brain.checkMemory(type, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
			return "n/a";
		}
		return brain.getMemory(type).map(p -> p.dimension() == level.dimension() ? (int) Math.sqrt(p.pos().distSqr(at)) + " away" : "elsewhere").orElse("none");
	}

	/** Which of our own systems has {@code v} just now (Routines' idle check, piece by piece, and the rest). */
	private static java.util.List<String> holds(Villager v) {
		java.util.List<String> h = new java.util.ArrayList<>();
		hold(h, "routine", Routines.busy(v));
		hold(h, "meal", Meals.eating(v));
		hold(h, "cook", Meals.cook(v));
		hold(h, "festival", Festivals.going(v));
		hold(h, "visitor", Visitors.is(v));
		hold(h, "attention", Attention.held(v));
		hold(h, "neighbours", Neighbours.meeting(v));
		hold(h, "courier", Couriers.carrying(v));
		hold(h, "envoy", Envoys.is(v));
		hold(h, "grudge", Grudges.busy(v));
		hold(h, "noai", v.isNoAi());
		hold(h, "passenger", v.isPassenger());
		hold(h, "leashed", v.isLeashed());
		hold(h, "target", v.getTarget() != null);
		hold(h, "afraid", Frights.afraid(v.getUUID()));
		hold(h, "riot", Theft.rioting(v));
		hold(h, "approach", Approaches.talking(v));
		hold(h, "quest", Quests.listening(v));
		hold(h, "tag", Tag.playing(v));
		hold(h, "trespass", Trespass.watching(v));
		hold(h, "mca-busy", McaCompat.busy(v));
		hold(h, "mca-panic", McaCompat.panicking(v));
		hold(h, "mca-chore", McaCompat.choring(v));
		hold(h, "mca-following", McaCompat.following(v) != null);
		hold(h, "hermit", Hermits.is(v));
		hold(h, "rickshaw", Rickshaws.onDuty(v));
		hold(h, "war-alert", WarAlert.carrying(v));
		hold(h, "downed-helping", Downed.helping(v));
		hold(h, "downed", Downed.isDowned(v));
		hold(h, "guiding", Directions.guiding(v));
		hold(h, "ruler", Rulers.is(v));
		return h;
	}

	private static void hold(java.util.List<String> h, String name, boolean on) {
		if (on) {
			h.add(name);
		}
	}
}
