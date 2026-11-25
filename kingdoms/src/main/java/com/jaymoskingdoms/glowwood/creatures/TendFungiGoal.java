package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.GlowHavenLife;
import com.jaymoskingdoms.glowwood.FungusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Now and then a Sporeling wanders to a small mushroom nearby, fusses over it, and sometimes a new one sprouts beside
 * it (a slow, gentle bone meal: it never grows huge fungi).
 */
public class TendFungiGoal extends Goal {
	private static final int RANGE = 6, DAMP_REACH = 3;
	private final SporelingEntity mob;
	private BlockPos target;
	private int cooldown;
	private int ticks;

	public TendFungiGoal(SporelingEntity mob) {
		this.mob = mob;
		this.cooldown = 200 + mob.getRandom().nextInt(600);
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mob.isInSittingPose() || mob.isOrderedToSit() || mob.isBaby() || --cooldown > 0) {
			return false;
		}
		cooldown = 600 + mob.getRandom().nextInt(1200);
		BlockPos origin = mob.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RANGE, -2, -RANGE), origin.offset(RANGE, 2, RANGE))) {
			if (mob.level().getBlockState(pos).getBlock() instanceof MushroomBlock) {
				double d = pos.distSqr(origin);
				if (d < bestDist) {
					bestDist = d;
					best = pos.immutable();
				}
			}
		}
		target = best;
		return target != null;
	}

	@Override
	public void start() {
		ticks = 0;
		mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9);
	}

	@Override
	public boolean canContinueToUse() {
		return target != null && ticks < 200 && mob.level().getBlockState(target).getBlock() instanceof MushroomBlock
				&& !mob.isOrderedToSit();
	}

	@Override
	public void tick() {
		ticks++;
		mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.3, target.getZ() + 0.5);
		if (mob.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) < 2.6) {
			mob.getNavigation().stop();
			if (mob.level() instanceof ServerLevel level) {
				BlockState state = level.getBlockState(target);
				mob.celebrate();
				level.sendParticles(GlowFx.spore(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
						12, 0.3, 0.2, 0.3, 0.01);
				if (mob.getRandom().nextFloat() < 0.35F && mayGrow(level, target)) {
					FungusBlock.spread(level, target, state, mob.getRandom());
				}
			}
			target = null;
		}
	}

	/**
	 * Feedback: a sporeling only spreads mushrooms where they would live (shaded, damp ground) and only while a giant fungus
	 * still stands near; once the giants are cut it tends what is there and spreads nothing.
	 */
	private static boolean mayGrow(ServerLevel level, BlockPos pos) {
		return GlowHavenLife.shaded(level, pos) && damp(level, pos) && GlowCreatures.nearGiant(level, pos, level.getRandom());
	}

	/** Damp: raining, or water, mud, moss, mycelium or podzol within a few blocks. */
	static boolean damp(ServerLevel level, BlockPos pos) {
		if (level.isRainingAt(pos.above())) {
			return true;
		}
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-DAMP_REACH, -1, -DAMP_REACH), pos.offset(DAMP_REACH, 0, DAMP_REACH))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL) || !s.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void stop() {
		target = null;
	}
}
