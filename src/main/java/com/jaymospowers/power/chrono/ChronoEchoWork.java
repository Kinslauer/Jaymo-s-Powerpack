package com.jaymospowers.power.chrono;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.143) What a bound echo (its owner is Timeless) does under its owner's orders ({@link ChronoDirectives}). It picks
 * targets and jobs and sets its own path; the echo's melee goal does the chasing, and its ally fight
 * ({@link ChronoEchoFight}) adds its owner's Chrono abilities to any fight.
 */
final class ChronoEchoWork {
	// ---- tuning (ticks / blocks) ----
	/** Standby keeps within this of its owner, and blink over to them past {@link #CATCH_UP}. */
	static final double FOLLOW_NEAR = 3.5, CATCH_UP = 40.0;
	static final double FOLLOW_SPEED = 1.1;
	/** A blow from or by its owner this recent still counts for Standby. */
	static final int RECENT = 100;
	/** Guard: monsters this close to the spot are fought; it goes back once more than this from it. */
	static final double GUARD_RADIUS = 14.0, GUARD_HOME = 3.0;
	/** Harvest: jobs within this of the spot, looked for this often; a chest or barrel this close to it takes the drops. */
	static final int HARVEST_RADIUS = 8, HARVEST_LOOK = 20, CHEST_RADIUS = 5;
	static final double WORK_REACH = 2.5;
	/** Give up on a job, path point or step after this long. */
	static final int STUCK_TICKS = 120;
	/** Standby: a new Time Bloom where it stands this long after the last one ended. */
	static final int BLOOM_EVERY = 30 * 20;
	/** Loop: rest this long at the end before going round again. */
	static final int LOOP_PAUSE = 40;
	static final double STRIKE_REACH = 4.0;

	private final ChronoEcho echo;
	private @Nullable BlockPos job;
	private int jobTicks;
	private int lookIn;
	private @Nullable TimeBloom bloom;
	private int bloomIn = 60;
	private int step;
	private int stepTicks;
	private int loopPause;

	ChronoEchoWork(ChronoEcho echo) {
		this.echo = echo;
	}

	void tick(ServerLevel level, ChronoDirectives.Orders orders) {
		@Nullable ServerPlayer owner = this.echo.ownerPlayer() instanceof ServerPlayer p && p.level() == level ? p : null;
		dropBadTarget(owner);
		if (orders.kind() != ChronoDirectives.Directive.STANDBY && this.bloom != null) {
			this.bloom = null;
		}
		switch (orders.kind()) {
			case STANDBY -> {
				defend(owner);
				follow(level, owner);
				bloom(level, owner);
			}
			case GUARD -> guard(level, owner, anchor(level, orders));
			case HARVEST -> harvest(level, owner, anchor(level, orders));
			case LOOP -> loop(level, owner, orders);
		}
	}

	/** The order's spot, when it's in this world; null otherwise (it then keeps by its owner). */
	private static @Nullable BlockPos anchor(ServerLevel level, ChronoDirectives.Orders orders) {
		return orders.anchor().filter(a -> a.dimension() == level.dimension()).map(GlobalPos::pos).orElse(null);
	}

	/** Never its owner, nor any other player. */
	private void dropBadTarget(@Nullable ServerPlayer owner) {
		LivingEntity target = this.echo.getTarget();
		if (target == null) {
			return;
		}
		if (!target.isAlive() || target == owner || target instanceof ChronoEcho || target instanceof Player) {
			this.echo.setTarget(null);
		}
	}

	private boolean fighting() {
		LivingEntity target = this.echo.getTarget();
		return target != null && target.isAlive();
	}

	/** Whatever struck it, or (recently) its owner, is fought. */
	private void defend(@Nullable ServerPlayer owner) {
		if (fighting()) {
			return;
		}
		LivingEntity by = this.echo.getLastHurtByMob();
		if (usable(by, owner) && this.echo.tickCount - this.echo.getLastHurtByMobTimestamp() < RECENT) {
			this.echo.setTarget(by);
			return;
		}
		if (owner != null) {
			LivingEntity at = owner.getLastHurtByMob();
			if (usable(at, owner) && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT && !(at instanceof Player)) {
				this.echo.setTarget(at);
			}
		}
	}

	private static boolean usable(@Nullable LivingEntity e, @Nullable ServerPlayer owner) {
		return e != null && e.isAlive() && e != owner && !(e instanceof ChronoEcho);
	}

	private void follow(ServerLevel level, @Nullable ServerPlayer owner) {
		if (owner == null || fighting()) {
			return;
		}
		double d = this.echo.distanceTo(owner);
		if (d > CATCH_UP) {
			BlockPos to = ChronoEchoes.findGround(level, owner.getBlockX() + this.echo.getRandom().nextInt(5) - 2, owner.getBlockY(),
				owner.getBlockZ() + this.echo.getRandom().nextInt(5) - 2, 4, this.echo.getType());
			if (to != null) {
				ChronoEchoes.shatterFx(level, this.echo);
				this.echo.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
				this.echo.getNavigation().stop();
			}
		} else if (d > FOLLOW_NEAR) {
			if (this.echo.tickCount % 10 == 0) {
				this.echo.getNavigation().moveTo(owner, FOLLOW_SPEED);
			}
		} else if (!this.echo.getNavigation().isDone()) {
			this.echo.getNavigation().stop();
		}
	}

	private void guard(ServerLevel level, @Nullable ServerPlayer owner, @Nullable BlockPos home) {
		if (home == null) {
			defend(owner);
			follow(level, owner);
			return;
		}
		LivingEntity target = this.echo.getTarget();
		if (target != null && target.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(home)) > (GUARD_RADIUS + 6) * (GUARD_RADIUS + 6)) {
			this.echo.setTarget(null); // chased too far from what it's guarding
		}
		if (!fighting() && this.echo.tickCount % 10 == 0) {
			LivingEntity foe = nearestFoe(level, home, GUARD_RADIUS);
			if (foe != null) {
				this.echo.setTarget(foe);
			}
		}
		defend(owner);
		if (!fighting()) {
			goHome(home, GUARD_HOME);
		}
	}

	private @Nullable LivingEntity nearestFoe(ServerLevel level, BlockPos at, double radius) {
		LivingEntity best = null;
		double bestD = radius * radius;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(radius),
			e -> e instanceof Enemy && e.isAlive() && !(e instanceof ChronoEcho) && !e.isSpectator())) {
			double d = e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at));
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private void goHome(BlockPos home, double within) {
		if (this.echo.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(home)) > within * within && this.echo.tickCount % 20 == 0) {
			this.echo.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, ChronoEcho.ROUTE_SPEED);
		}
	}

	// ---------------------------------------------------------------- harvest

	private void harvest(ServerLevel level, @Nullable ServerPlayer owner, @Nullable BlockPos home) {
		defend(owner);
		if (home == null) {
			follow(level, owner);
			return;
		}
		if (fighting()) {
			return;
		}
		if (this.job != null && !worth(level, this.job, this.echo.minedKinds())) {
			this.job = null;
		}
		if (this.job == null) {
			if (--this.lookIn > 0) {
				goHome(home, GUARD_HOME);
				return;
			}
			this.lookIn = HARVEST_LOOK;
			this.job = findJob(level, home);
			this.jobTicks = 0;
			if (this.job == null) {
				goHome(home, GUARD_HOME);
				return;
			}
		}
		BlockPos at = this.job;
		if (this.echo.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) <= WORK_REACH * WORK_REACH || this.jobTicks > STUCK_TICKS) {
			if (this.echo.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) <= (WORK_REACH + 1.5) * (WORK_REACH + 1.5)) {
				reap(level, at, home);
			}
			this.job = null;
			return;
		}
		if (this.jobTicks++ % 20 == 0) {
			this.echo.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ChronoEcho.ROUTE_SPEED);
		}
	}

	/** Nearest ripe crop, or log or ore of a kind its owner mines, round the spot. */
	private @Nullable BlockPos findJob(ServerLevel level, BlockPos home) {
		List<Identifier> habits = this.echo.minedKinds();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int r = HARVEST_RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-r, -3, -r), home.offset(r, 4, r))) {
			if (!worth(level, pos, habits)) {
				continue;
			}
			double d = this.echo.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));
			if (d < bestD) {
				bestD = d;
				best = pos.immutable();
			}
		}
		return best;
	}

	private static boolean worth(ServerLevel level, BlockPos pos, List<Identifier> habits) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (state.hasBlockEntity() || !(state.is(BlockTags.LOGS) || state.is(ConventionalBlockTags.ORES))) {
			return false;
		}
		return habits.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())) && state.getDestroySpeed(level, pos) >= 0.0F;
	}

	/** Breaks the block with its drops (a crop is replanted from its own seeds); drops go to a chest near {@code home}. */
	private void reap(ServerLevel level, BlockPos pos, BlockPos home) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, be, this.echo, this.echo.getMainHandItem()));
		this.echo.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		this.echo.swingForAttack(InteractionHand.MAIN_HAND);
		level.destroyBlock(pos, false, this.echo);
		if (state.getBlock() instanceof CropBlock crop) {
			for (ItemStack stack : drops) {
				if (stack.getItem() instanceof BlockItem item && item.getBlock() == crop) {
					stack.shrink(1);
					level.setBlockAndUpdate(pos, crop.defaultBlockState());
					break;
				}
			}
		}
		Container chest = chestNear(level, home);
		for (ItemStack stack : drops) {
			if (!stack.isEmpty() && chest != null) {
				deposit(chest, stack);
			}
			if (!stack.isEmpty()) {
				Block.popResource(level, pos, stack);
			}
		}
		ChronoFx.particles(level, this.echo.ownerPlayer(), ChronoEcho.SHARD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
	}

	private static @Nullable Container chestNear(ServerLevel level, BlockPos home) {
		int r = CHEST_RADIUS;
		Container best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-r, -2, -r), home.offset(r, 2, r))) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
				double d = pos.distSqr(home);
				if (d < bestD) {
					bestD = d;
					best = (Container) be;
				}
			}
		}
		return best;
	}

	/** Into the container: onto matching stacks first, then into empty slots. What doesn't fit stays in {@code stack}. */
	private static void deposit(Container chest, ItemStack stack) {
		int size = chest.getContainerSize();
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < size && !stack.isEmpty(); i++) {
				ItemStack in = chest.getItem(i);
				if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack)) {
					int move = Math.min(stack.getCount(), Math.min(in.getMaxStackSize(), chest.getMaxStackSize()) - in.getCount());
					if (move > 0) {
						in.grow(move);
						stack.shrink(move);
					}
				} else if (pass == 1 && in.isEmpty() && chest.canPlaceItem(i, stack)) {
					chest.setItem(i, stack.split(Math.min(stack.getCount(), chest.getMaxStackSize())));
				}
			}
		}
		chest.setChanged();
	}

		/** On standby, between fights, it casts Time Bloom where it stands now and then (once its owner has Time Bloom). */
	private void bloom(ServerLevel level, @Nullable ServerPlayer owner) {
		if (this.bloom != null) {
			if (!this.bloom.tick()) {
				this.bloom = null;
				this.bloomIn = BLOOM_EVERY;
			}
			return;
		}
		// it can only bend time the way its owner can
		boolean knows = owner == null || Mastery.isUnlocked(owner, PowerKey.ABILITY_4);
		if (knows && !fighting() && --this.bloomIn <= 0 && this.echo.onGround()) {
			this.echo.swingForAttack(InteractionHand.MAIN_HAND);
			this.bloom = new TimeBloom(level, this.echo.blockPosition(), ChronoFx.style(owner));
			this.bloom.start();
		}
	}

	// ---------------------------------------------------------------- loop

	private void loop(ServerLevel level, @Nullable ServerPlayer owner, ChronoDirectives.Orders orders) {
		defend(owner);
		List<ChronoDirectives.Step> steps = orders.loop();
		if (steps.isEmpty() || orders.loopDim().filter(d -> d == level.dimension()).isEmpty()) {
			follow(level, owner);
			return;
		}
		if (fighting()) {
			return;
		}
		if (this.loopPause > 0) {
			this.loopPause--;
			return;
		}
		if (this.step >= steps.size()) {
			this.step = 0;
		}
		ChronoDirectives.Step s = steps.get(this.step);
		BlockPos pos = s.pos();
		double reach = s.kind() == ChronoDirectives.Step.MOVE ? 1.6 : s.kind() == ChronoDirectives.Step.BREAK ? WORK_REACH + 1.0 : STRIKE_REACH;
		boolean there = this.echo.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= reach * reach;
		if (!there && this.stepTicks <= STUCK_TICKS) {
			if (this.stepTicks++ % 20 == 0) {
				this.echo.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ChronoEcho.ROUTE_SPEED);
			}
			return;
		}
		if (there) {
			if (s.kind() == ChronoDirectives.Step.BREAK) {
				BlockState state = level.getBlockState(pos);
				boolean same = s.block().map(id -> id.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))).orElse(false);
				boolean ripe = !(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state);
				if (same && ripe && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F) {
					reap(level, pos, pos);
				}
			} else if (s.kind() == ChronoDirectives.Step.STRIKE) {
				this.echo.swingForAttack(InteractionHand.MAIN_HAND);
				LivingEntity foe = nearestFoe(level, pos, STRIKE_REACH);
				if (foe != null) {
					this.echo.setTarget(foe);
				}
			}
		}
		this.stepTicks = 0;
		if (++this.step >= steps.size()) {
			this.step = 0;
			this.loopPause = LOOP_PAUSE;
			ChronoFx.particles(level, owner, ParticleTypes.REVERSE_PORTAL, this.echo.getX(), this.echo.getY() + 1.0, this.echo.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
		}
	}

	/** New orders: start them fresh. */
	void reset() {
		this.job = null;
		this.bloom = null;
		this.bloomIn = 60;
		this.step = 0;
		this.stepTicks = 0;
		this.loopPause = 0;
		this.echo.getNavigation().stop();
	}
}
