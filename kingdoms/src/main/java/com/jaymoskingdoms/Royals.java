package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ruler's day (0.5.1): a king (or a fiefdom's lord) doesn't sit on his throne all day. After dawn he comes down
 * from it (up from the panic room if he slept there) and walks his town, stop by stop among his people, greeting the
 * villagers he meets (they stop and look at him, glad of it); his escort of {@link #ESCORT_LEAST} to {@link #ESCORT_MOST}
 * guards walks in pairs behind him; with fewer than that about he dares not go out at all, unless one of his sworn
 * wardens among the players is at his side ({@link Wardens}). Before dusk he heads back to the seat, takes his throne again, and his guards go
 * back to their posts. A fight comes first: a ruler or guard with a foe drops all this until it's over.
 */
public final class Royals {
	/** The day, by the Overworld's clock (ticks into the day): out after this, home from this on. */
	static final long DAWN = 1000L, DUSK = 11000L;
	/** The guards at his back: at least (with fewer he keeps to his hall, {@link Wardens}), at most. */
	static final int ESCORT_LEAST = Wardens.GUARDS_LEAST, ESCORT_MOST = 8;
	/** A guard posted this close to the gate keeps it (unless he's needed to make up the least). */
	static final double GATE_POST = 8.0;
	/** Guards are called to his side from this far round the seat. */
	static final double ESCORT_CALL = 96.0;
	/** A round: how many stops, how far from the seat they're chosen; a stop is reached this close. */
	static final int STOPS = 6, ROUND_RANGE = 64;
	static final double STOP_REACH = 3.0;
	/** How long he stays at a stop (ticks), and how close a villager must be to be greeted. */
	static final int GREET_TICKS = 80;
	static final double GREET_RANGE = 7.0;
	/** Walking speeds: his, his escort's (and theirs when they've fallen behind). */
	static final double KING_SPEED = 0.55, ESCORT_SPEED = 0.7, CATCH_UP_SPEED = 1.0;
	/** No closer to his stop for this long (ticks) and he gives it up; going home, he's taken there instead. */
	static final int STUCK_TICKS = 240;
	/** While out he may roam this far from his seat (his restriction). */
	static final int ROAM_RADIUS = 160;
	/** An escort further than this from him is brought to him (fallen far behind, or blocked). */
	static final double ESCORT_LOST = 40.0;

	private Royals() {
	}

	/** The time of day by the Overworld's clock (0 = sunrise). */
	static long timeOfDay(ServerLevel level) {
		return Math.floorMod(level.getOverworldClockTime(), Production.DAY_TICKS);
	}

	static boolean daytime(ServerLevel level) {
		long t = timeOfDay(level);
		return t >= DAWN && t < DUSK;
	}

	/** A ruler who keeps a seat (a king or a lord; chiefs and warlords keep their camps). */
	static boolean walks(Folk folk) {
		return folk.role() == Folk.KING || folk.role() == Folk.LORD;
	}

	static void clear() {
	}

	// ---- the ruler's round ------------------------------------------------------------------------------------------

	/** His day: out on his round while it's light, back on his throne when it isn't. */
	static final class Round extends Goal {
		private final Folk king;
		private final List<BlockPos> stops = new ArrayList<>();
		private boolean out;
		private int greeting;
		private BlockPos goal;
		private double best;
		private long improvedAt;

		Round(Folk king) {
			this.king = king;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!walks(this.king) || this.king.getTarget() != null || !(this.king.level() instanceof ServerLevel level)) {
				return false;
			}
			Castle.Keep keep = Factions.keep(level.getServer(), this.king.factionId());
			if (keep == null) {
				return false;
			}
			// out by day (unless he's too few guards to dare it); by night, or kept in, only to get home
			Faction faction = Factions.get(level.getServer(), this.king.factionId());
			boolean out = daytime(level) && faction != null && !Wardens.confined(level, faction, this.king) && Tempers.staysOut(level, faction);
			return out || this.out || this.king.distanceToSqr(Vec3.atBottomCenterOf(keep.throne())) > 4.0;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.king.getNavigation().stop();
			this.goal = null;
		}

		@Override
		public void tick() {
			ServerLevel level = (ServerLevel)this.king.level();
			Faction faction = Factions.get(level.getServer(), this.king.factionId());
			Castle.Keep keep = Factions.keep(level.getServer(), this.king.factionId());
			if (faction == null || keep == null) {
				return;
			}
			long now = level.getGameTime();
			if (daytime(level) && !Wardens.confined(level, faction, this.king) && Tempers.staysOut(level, faction)) {
				if (!this.out) {
					this.setOut(level, faction, keep);
				}
				this.walkRound(level, faction, now);
				return;
			}
			// night (or too few guards to stay out): home to the throne
			if (this.out) {
				this.out = false;
				this.stops.clear();
				release(level, this.king);
			}
			// (0.5.19) driven home in his carriage: he gets down at his gate and walks in
			if (Carriages.riding(this.king)) {
				if (this.walk(level, keep.gate(), now, Carriages.ALIGHT_REACH) || now - this.improvedAt > STUCK_TICKS) {
					Carriages.alight(this.king);
					this.goal = null;
				}
				return;
			}
			if (this.walk(level, keep.throne(), now, 1.5)) {
				this.king.getNavigation().stop();
				this.king.setHomeTo(keep.throne(), 3);
			} else if (now - this.improvedAt > STUCK_TICKS) {
				// (the way in is blocked: his guards see him to his throne)
				poof(level, this.king);
				Carriages.alight(this.king);
				this.king.teleportTo(keep.throne().getX() + 0.5, keep.throne().getY(), keep.throne().getZ() + 0.5);
				poof(level, this.king);
				this.king.setHomeTo(keep.throne(), 3);
				this.goal = null;
			}
		}

		/** Dawn: up from the panic room if he slept there, his escort called, his round planned. */
		private void setOut(ServerLevel level, Faction faction, Castle.Keep keep) {
			this.out = true;
			if (this.king.distanceToSqr(Vec3.atBottomCenterOf(keep.cellar())) < 9.0 && !keep.cellar().equals(keep.throne())) {
				this.king.teleportTo(keep.throne().getX() + 0.5, keep.throne().getY(), keep.throne().getZ() + 0.5);
			}
			this.king.setHomeTo(faction.seat(), ROAM_RADIUS);
			escort(level, faction, this.king);
			this.plan(level, faction, keep);
		}

		/** His stops: among his villagers (a few of them, picked at random), the bell, and his gate at the last. */
		private void plan(ServerLevel level, Faction faction, Castle.Keep keep) {
			this.stops.clear();
			List<Villager> people = level.getEntitiesOfClass(Villager.class, new AABB(faction.seat()).inflate(ROUND_RANGE), v -> v.isAlive());
			java.util.Collections.shuffle(people, new java.util.Random(level.getRandom().nextLong()));
			for (Villager v : people) {
				if (this.stops.size() >= STOPS - 2) {
					break;
				}
				this.stops.add(v.blockPosition());
			}
			this.stops.add(faction.seat());
			// (nearest first after the gate, so he doesn't cross the town back and forth)
			BlockPos from = keep.gate();
			List<BlockPos> ordered = new ArrayList<>();
			while (!this.stops.isEmpty()) {
				BlockPos here = from;
				BlockPos next = this.stops.stream().min(Comparator.comparingDouble(p -> p.distSqr(here))).get();
				this.stops.remove(next);
				ordered.add(next);
				from = next;
			}
			this.stops.add(keep.gate());
			this.stops.addAll(ordered);
			this.stops.add(keep.gate());
		}

		private void walkRound(ServerLevel level, Faction faction, long now) {
			if (this.greeting > 0) {
				this.greeting--;
				if (this.greeting == GREET_TICKS / 2) {
					this.greet(level);
				}
				return;
			}
			if (this.stops.isEmpty()) {
				Castle.Keep keep = Factions.keep(level.getServer(), faction.id());
				if (keep != null) {
					this.plan(level, faction, keep);
				}
				return;
			}
			// (0.5.19) a wealthy king rides his round in his carriage, if it's waiting for him
			Carriages.board(level, faction, this.king);
			BlockPos stop = this.stops.getFirst();
			if (this.walk(level, stop, now, STOP_REACH)) {
				this.stops.removeFirst();
				Carriages.stop(this.king);
				this.greeting = GREET_TICKS;
			} else if (now - this.improvedAt > STUCK_TICKS) {
				// (no way there, penned in by a fence or a gate: he turns up there in a puff, as builders do; his escort follows)
				BlockPos at = Construction.standable(level, stop, 3);
				poof(level, this.king);
				Carriages.stop(this.king);
				if (Carriages.riding(this.king)) {
					Carriages.teleport(this.king, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				} else {
					this.king.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				}
				poof(level, this.king);
				this.goal = null;
			}
		}

		/** The nearest villager stops and looks at him; he raises a hand to them, and they're glad of it. */
		private void greet(ServerLevel level) {
			Villager v = level.getEntitiesOfClass(Villager.class, this.king.getBoundingBox().inflate(GREET_RANGE), x -> x.isAlive()).stream()
				.min(Comparator.comparingDouble(x -> x.distanceToSqr(this.king))).orElse(null);
			if (v == null) {
				return;
			}
			// (0.5.12) as his temper has it
			if (Tempers.meet(level, this.king, v)) {
				return;
			}
			this.king.getLookControl().setLookAt(v, 30.0F, 30.0F);
			this.king.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			v.getNavigation().stop();
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new EntityTracker(this.king, true), GREET_TICKS);
			v.getBrain().setMemoryWithExpiry(MemoryModuleType.INTERACTION_TARGET, this.king, GREET_TICKS);
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.8, v.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}

		/** Walks him toward {@code to} (in legs); true once he's within {@code reach}. */
		private boolean walk(ServerLevel level, BlockPos to, long now, double reach) {
			if (!to.equals(this.goal)) {
				this.goal = to;
				this.best = Double.MAX_VALUE;
				this.improvedAt = now;
			}
			double d = Math.sqrt(this.king.distanceToSqr(Vec3.atBottomCenterOf(to)));
			if (d <= reach + Carriages.reach(this.king)) {
				return true;
			}
			if (d < this.best - 0.5) {
				this.best = d;
				this.improvedAt = now;
			}
			if (this.king.getNavigation().isDone() || now % 20 == 0) {
				BlockPos leg = Construction.leg(level, this.king, to);
				Carriages.moveTo(this.king, leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, KING_SPEED);
			}
			return false;
		}
	}

	// ---- his escort -------------------------------------------------------------------------------------------------

	/** Calls up to {@link #ESCORT_MOST} of the faction's guards (the nearest) to {@code king}'s back. */
	static void escort(ServerLevel level, Faction faction, Folk king) {
		List<Folk> guards = new ArrayList<>(level.getEntitiesOfClass(Folk.class, new AABB(faction.seat()).inflate(ESCORT_CALL),
			g -> g.isAlive() && g.factionId() == faction.id() && g.role() == Folk.GUARD && g.getTarget() == null && g.task().isEmpty()
				&& (g.escort() == null || g.escort().equals(king.getUUID()))));
		// the hall's and the town's guards first; the gate's keep it, unless they're needed to make up the least
		Castle.Keep keep = Factions.keep(level.getServer(), faction.id());
		java.util.function.Predicate<Folk> atGate = g -> keep != null && g.escort() == null && g.getHomePosition().closerThan(keep.gate(), GATE_POST);
		guards.sort(Comparator.comparing((Folk g) -> atGate.test(g)).thenComparingDouble(g -> g.distanceToSqr(king)));
		int slot = 0;
		for (Folk g : guards) {
			if (slot < ESCORT_MOST && (slot < ESCORT_LEAST || !atGate.test(g))) {
				g.follow(king.getUUID(), slot++);
			} else if (king.getUUID().equals(g.escort())) {
				g.unfollow();
			}
		}
	}

	/** Night: his escort goes back to their posts. */
	static void release(ServerLevel level, Folk king) {
		for (Folk g : level.getEntitiesOfClass(Folk.class, king.getBoundingBox().inflate(ESCORT_CALL * 2), g -> king.getUUID().equals(g.escort()))) {
			g.unfollow();
		}
	}

	/** A guard at his ruler's back: his place in the column (pairs, two blocks apart, behind him), kept up with. */
	static final class Follow extends Goal {
		private final Folk guard;

		Follow(Folk guard) {
			this.guard = guard;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		private @Nullable Folk leader() {
			UUID id = this.guard.escort();
			if (id == null || !(this.guard.level() instanceof ServerLevel level)) {
				return null;
			}
			Entity e = level.getEntity(id);
			return e instanceof Folk f && f.isAlive() ? f : null;
		}

		@Override
		public boolean canUse() {
			if (this.guard.escort() == null || this.guard.getTarget() != null) {
				return false;
			}
			if (this.leader() == null) {
				// (his ruler fell, or is out of reach: back to his post)
				this.guard.unfollow();
				return false;
			}
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.guard.getNavigation().stop();
		}

		@Override
		public void tick() {
			Folk king = this.leader();
			if (king == null) {
				return;
			}
			int slot = this.guard.escortSlot();
			// behind him: rows of two, a block and a half either side
			Vec3 facing = Vec3.directionFromRotation(0.0F, king.getYRot());
			Vec3 right = new Vec3(-facing.z, 0.0, facing.x);
			// (0.5.19) wider apart on horseback, and further back behind a carriage
			double spread = Cavalry.mounted(this.guard) ? 1.7 : 1.0;
			double back = (Carriages.riding(king) ? 4.5 : 2.0) + (slot / 2) * 1.8 * spread;
			double side = (slot % 2 == 0 ? -1.4 : 1.4) * spread;
			Vec3 place = king.position().subtract(facing.scale(back)).add(right.scale(side));
			double d = this.guard.position().distanceTo(place);
			if (this.guard.tickCount % 20 == 0) {
				this.guard.setHomeTo(king.blockPosition(), ROAM_RADIUS);
			}
			if (this.guard.distanceTo(king) > ESCORT_LOST) {
				ServerLevel level = (ServerLevel)this.guard.level();
				BlockPos to = Construction.standable(level, BlockPos.containing(place), 3);
				poof(level, this.guard);
				Cavalry.teleport(this.guard, to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
				poof(level, this.guard);
				return;
			}
			if (d > 1.5) {
				if (this.guard.getNavigation().isDone() || this.guard.tickCount % 10 == 0) {
					this.guard.getNavigation().moveTo(place.x, place.y, place.z, d > 6.0 ? CATCH_UP_SPEED : ESCORT_SPEED);
				}
			} else {
				this.guard.getNavigation().stop();
				this.guard.getLookControl().setLookAt(king.getX() + Vec3.directionFromRotation(0.0F, king.getYRot()).x * 8.0, king.getEyeY(),
					king.getZ() + Vec3.directionFromRotation(0.0F, king.getYRot()).z * 8.0);
			}
		}
	}

	private static void poof(ServerLevel level, Entity e) {
		level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
	}
}
