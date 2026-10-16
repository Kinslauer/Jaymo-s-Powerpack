package com.jaymospowers.core;

import com.jaymospowers.mixin.MobGoalsAccessor;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spiders lurk in leaves ({@link Leaves}): an idle spider now and then picks a covered spot in a nearby bush or
 * canopy, walks to the ground under it, scurries up into the leaves and clings there, hidden, until someone walks
 * past close enough to pounce on, or it grows bored and moves on.
 */
public final class LeafSpiders {
	/** Chance a tick (while idle, not already hiding) that a spider goes looking for leaves to hide in. */
	public static final float HIDE_CHANCE = 1.0F / 300.0F;
	/** How far it looks for a hiding spot: sideways, and up and down from its feet. */
	public static final int SEARCH_RADIUS = 10, SEARCH_UP = 8, SEARCH_DOWN = 2, SEARCH_TRIES = 40;
	/** It gives up getting there after this long. */
	public static final int TRAVEL_TICKS = 300;
	/** Once in the leaves it lurks this long (plus up to as much again) before moving on. */
	public static final int LURK_TICKS = 1200;
	/** After it moves on, it won't hide again for this long. */
	public static final int BORED_TICKS = 600;
	/** A player (not in creative or spectator) this close to a hidden spider gets pounced on, day or night. */
	public static final double AMBUSH_RANGE = 5.0;
	/** How fast it scurries up into the leaves from below (blocks a tick). */
	public static final double SCURRY_SPEED = 0.2;

	/** Spiders already given the goal (goals aren't saved, so a reload gives it again to the new spider). */
	private static final Map<Spider, Boolean> GIVEN = new WeakHashMap<>();

	private LeafSpiders() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Spider spider && GIVEN.put(spider, true) == null) {
				// beside its attack (4): only one runs, as this one only wants a spider with nothing to hunt
				((MobGoalsAccessor)spider).jaymospowers$goals().addGoal(4, new HideGoal(spider));
			}
		});
	}

	/** Whether the spider's body is in leaves. */
	static boolean hidden(Spider spider) {
		Level level = spider.level();
		return Leaves.is(level.getBlockState(spider.blockPosition())) || Leaves.is(level.getBlockState(BlockPos.containing(spider.getEyePosition())));
	}

	private static final class HideGoal extends Goal {
		private final Spider spider;
		/** The leaves it means to hide in, and the ground under them it walks to first. */
		private @Nullable BlockPos spot;
		private @Nullable BlockPos stand;
		private int ticks;
		private int lurkFor;
		private boolean lurking;
		/** Game time it may hide again (bored of the last spot). */
		private long restUntil;

		HideGoal(Spider spider) {
			this.spider = spider;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (this.spider.getTarget() != null || this.spider.isVehicle() || this.spider.isPassenger()
				|| this.spider.level().getGameTime() < this.restUntil) {
				return false;
			}
			if (hidden(this.spider)) {
				// already in leaves (it climbed in, or spawned there): it stays put
				this.spot = this.spider.blockPosition();
				this.stand = null;
				return true;
			}
			return this.spider.getRandom().nextFloat() < HIDE_CHANCE && this.findSpot();
		}

		@Override
		public boolean canContinueToUse() {
			if (this.spider.getTarget() != null) {
				return false;
			}
			return this.lurking ? this.ticks < this.lurkFor && hidden(this.spider) : this.ticks < TRAVEL_TICKS;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.lurking = this.stand == null;
			this.lurkFor = LURK_TICKS + this.spider.getRandom().nextInt(LURK_TICKS + 1);
			if (this.stand != null) {
				this.spider.getNavigation().moveTo(this.stand.getX() + 0.5, this.stand.getY(), this.stand.getZ() + 0.5, 1.0);
			}
		}

		@Override
		public void stop() {
			if (this.lurking) {
				this.restUntil = this.spider.level().getGameTime() + BORED_TICKS;
			}
			this.spot = null;
			this.stand = null;
			this.lurking = false;
		}

		@Override
		public void tick() {
			this.ticks++;
			if (this.lurking) {
				this.spider.getNavigation().stop();
				this.ambush();
				return;
			}
			if (hidden(this.spider)) {
				this.lurking = true;
				this.ticks = 0;
				return;
			}
			BlockPos spot = this.spot;
			if (spot == null) {
				return;
			}
			Vec3 centre = Vec3.atBottomCenterOf(spot);
			Vec3 to = centre.subtract(this.spider.position());
			if (to.horizontalDistance() < 1.5 && to.y > 0.0) {
				// under it: up the trunk (or straight up into the bush), drifting in towards the middle
				this.spider.getNavigation().stop();
				this.spider.setDeltaMovement(to.x * 0.1, SCURRY_SPEED, to.z * 0.1);
			} else if (this.spider.getNavigation().isDone() && this.stand != null) {
				this.spider.getNavigation().moveTo(this.stand.getX() + 0.5, this.stand.getY(), this.stand.getZ() + 0.5, 1.0);
			}
		}

		/** Someone walked into reach: it drops on them. */
		private void ambush() {
			Player prey = this.spider.level().getNearestPlayer(this.spider, AMBUSH_RANGE);
			if (prey != null && !prey.isCreative() && !prey.isSpectator() && !prey.isInvisible() && this.spider.hasLineOfSight(prey)) {
				this.spider.setTarget(prey);
			}
		}

		/** A covered leaves block near by, with ground under it the spider can walk to. */
		private boolean findSpot() {
			Level level = this.spider.level();
			BlockPos from = this.spider.blockPosition();
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int i = 0; i < SEARCH_TRIES; i++) {
				int dx = this.spider.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
				int dz = this.spider.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
				int dy = this.spider.getRandom().nextInt(SEARCH_UP + SEARCH_DOWN + 1) - SEARCH_DOWN;
				BlockPos spot = from.offset(dx, dy, dz);
				// covered: leaves above it too, so it's out of sight
				if (!Leaves.is(level.getBlockState(spot)) || !Leaves.is(level.getBlockState(spot.above()))) {
					continue;
				}
				BlockPos stand = null;
				at.set(spot);
				for (int down = 0; down <= SEARCH_UP + SEARCH_DOWN && at.getY() > level.getMinY(); down++) {
					at.move(0, -1, 0);
					BlockState state = level.getBlockState(at);
					if (!state.isAir() && !Leaves.is(state)) {
						if (!state.getCollisionShape(level, at).isEmpty()) {
							stand = at.above();
						}
						break;
					}
				}
				if (stand == null) {
					continue;
				}
				// a bush on the ground is its own stand: it walks straight in
				if (this.spider.getNavigation().createPath(stand, 1) == null) {
					continue;
				}
				this.spot = spot;
				this.stand = stand;
				return true;
			}
			return false;
		}
	}
}
