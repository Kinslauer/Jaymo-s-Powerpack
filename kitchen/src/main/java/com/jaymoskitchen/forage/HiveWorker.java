package com.jaymoskitchen.forage;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A bee sent out by a {@link HarvestHiveEntity}. The job (its hive, the crop it is going to, what it carries) is kept on
 * the bee itself, so it survives a save; a goal added to every bee on load (it only runs for a bee that has a job) flies
 * it to the ripe crop, picks it, replants it, and brings the harvest home. A bee out when night falls or rain starts
 * comes home at once; one whose hive is gone drops what it carries and is a wild bee again.
 */
public final class HiveWorker {
	/** Ticks a trip may last before the bee gives up the crop and heads home, and before it is let go as lost. */
	private static final int TRIP_TICKS = 600, LOST_TICKS = 1800;
	/** The bee counts as at the crop within this many blocks squared, and as home within the second. */
	private static final double AT_CROP_SQR = 2.25, AT_HOME_SQR = 6.25;
	/** Flying speed given to the path. */
	private static final double SPEED = 1.0;
	/** Ticks between telling the bee's path where to go. */
	private static final int REPATH_TICKS = 10;
	/** A ripe sweet berry bush goes back to this age after picking. */
	private static final int BERRY_REGROW_AGE = 1, BERRY_RIPE_AGE = 3;

	/** Where a worker is going. {@code since} is the game time it set out. */
	public record Job(BlockPos hive, Optional<BlockPos> target, boolean returning, List<ItemStack> load, long since) {
		public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("hive").forGetter(Job::hive),
			BlockPos.CODEC.optionalFieldOf("target").forGetter(Job::target),
			Codec.BOOL.fieldOf("returning").forGetter(Job::returning),
			ItemStack.CODEC.listOf().fieldOf("load").forGetter(Job::load),
			Codec.LONG.fieldOf("since").forGetter(Job::since)).apply(i, Job::new));
	}

	public static final AttachmentType<Job> JOB = AttachmentRegistry.create(JaymosKitchen.id("hive_worker"), b -> b.persistent(Job.CODEC));

	private HiveWorker() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Bee bee) {
				bee.getGoalSelector().addGoal(1, new WorkGoal(bee));
			}
		});
	}

	/** Whether a crop is ready to be picked. */
	public static boolean ripe(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		return state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= BERRY_RIPE_AGE;
	}

	/** Picks the crop and plants it again: what it would have dropped. */
	private static List<ItemStack> pick(ServerLevel level, BlockPos pos, BlockState state) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, null);
		if (state.getBlock() instanceof CropBlock crop) {
			level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
		} else {
			level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, BERRY_REGROW_AGE), Block.UPDATE_ALL);
		}
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.0);
		return drops;
	}

	/** Lets the bee go: what it carries falls where it is, and it is a wild bee. */
	public static void abandon(Bee bee) {
		Job job = bee.getAttached(JOB);
		bee.removeAttached(JOB);
		if (job != null && bee.level() instanceof ServerLevel level) {
			for (ItemStack stack : job.load()) {
				Block.popResource(level, bee.blockPosition(), stack);
			}
		}
	}

	private static final class WorkGoal extends Goal {
		private final Bee bee;

		WorkGoal(Bee bee) {
			this.bee = bee;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !bee.level().isClientSide() && bee.getAttached(JOB) != null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public boolean isInterruptable() {
			return false;
		}

		@Override
		public void tick() {
			Job job = bee.getAttached(JOB);
			if (job == null || !(bee.level() instanceof ServerLevel level)) {
				return;
			}
			if (!(level.getBlockEntity(job.hive()) instanceof HarvestHiveEntity hive)) {
				abandon(bee);
				return;
			}
			long out = level.getGameTime() - job.since();
			if (out > LOST_TICKS) {
				abandon(bee);
				return;
			}
			boolean home = job.returning() || job.target().isEmpty() || out > TRIP_TICKS || !HarvestHiveEntity.workable(level);
			boolean repath = bee.tickCount % REPATH_TICKS == 0 || bee.getNavigation().isDone();
			if (!home) {
				BlockPos target = job.target().get();
				Vec3 spot = Vec3.atBottomCenterOf(target).add(0.0, 0.8, 0.0);
				if (bee.distanceToSqr(spot) < AT_CROP_SQR) {
					List<ItemStack> load = new ArrayList<>(job.load());
					BlockState state = level.getBlockState(target);
					if (ripe(state)) {
						load.addAll(pick(level, target, state));
					}
					bee.setAttached(JOB, new Job(job.hive(), Optional.empty(), true, load, job.since()));
				} else if (repath) {
					bee.getNavigation().moveTo(spot.x, spot.y, spot.z, SPEED);
				}
				return;
			}
			if (!job.returning()) {
				job = new Job(job.hive(), Optional.empty(), true, job.load(), job.since());
				bee.setAttached(JOB, job);
			}
			Vec3 door = Vec3.atCenterOf(job.hive());
			if (bee.distanceToSqr(door) < AT_HOME_SQR) {
				hive.takeIn(bee, job);
			} else if (repath) {
				bee.getNavigation().moveTo(door.x, door.y, door.z, SPEED);
			}
		}
	}
}
