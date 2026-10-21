package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sentry current: how the ruins light up. Sentry stone that has a LIT state ({@link DungeonBlocks#CARRIES_SENTRY_CURRENT})
 * glows when lit and, a few ticks after, lights the unlit sentry stone touching it, so a lit block spreads along a
 * wall. A redstone signal reaching a block toggles it (the first time it is powered), and a sprung trap sends a
 * pulse that lights the stone and wakes the spawners round it. (Lit sentry stone does not give off a redstone
 * signal here; the current only moves through the stone.)
 */
public final class SentryCurrent {
	// ---- tuning ----
	/** Ticks between a block lighting and the stone beside it following. */
	public static final int DELAY = 3;
	/** A trap's pulse reaches this far (blocks, a cube), lights at most {@link #PULSE_LIGHTS} blocks and wakes any spawner. */
	public static final int PULSE_RADIUS = 12, PULSE_LIGHTS = 24;

	private SentryCurrent() {
	}

	/** Whether {@code state} is a block that has a LIT state. */
	public static boolean carries(BlockState state) {
		return state.hasProperty(SentryBlock.LIT);
	}

	/** A block saw a neighbour change: a redstone signal arriving toggles it, one leaving only lets it be toggled again. */
	static void signal(BlockState state, Level level, BlockPos pos) {
		boolean powered = level.hasNeighborSignal(pos);
		if (powered == state.getValue(SentryBlock.POWERED)) {
			return;
		}
		BlockState next = state.setValue(SentryBlock.POWERED, powered);
		if (powered) {
			next = next.setValue(SentryBlock.LIT, !state.getValue(SentryBlock.LIT));
		}
		level.setBlock(pos, next, 3);
		if (next.getValue(SentryBlock.LIT) && level instanceof ServerLevel server) {
			server.scheduleTick(pos, state.getBlock(), DELAY);
		}
	}

	/** A lit block lights the unlit current-carrying blocks beside it, and each of those does the same in turn. */
	static void spread(BlockState state, ServerLevel level, BlockPos pos) {
		if (!carries(state) || !state.getValue(SentryBlock.LIT)) {
			return;
		}
		for (Direction direction : Direction.values()) {
			BlockPos beside = pos.relative(direction);
			BlockState there = level.getBlockState(beside);
			if (carries(there) && !there.getValue(SentryBlock.LIT) && !(there.getBlock() instanceof SentrySpawnerBlock)) {
				light(level, beside);
			}
		}
	}

	/** Lights one block (sentry stone, or a spawner, which then wakes), and has it pass the current on. */
	public static void light(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!carries(state) || state.getValue(SentryBlock.LIT)) {
			return;
		}
		level.setBlock(pos, state.setValue(SentryBlock.LIT, true), 3);
		level.scheduleTick(pos, state.getBlock(), DELAY);
		if (level.getBlockEntity(pos) instanceof SentrySpawnerEntity spawner) {
			spawner.arm();
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.02);
	}

	/** A trap has sprung at {@code origin}: every spawner within the radius wakes, and some sentry stone round it lights. */
	public static void pulse(ServerLevel level, BlockPos origin) {
		int lit = 0;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-PULSE_RADIUS, -PULSE_RADIUS, -PULSE_RADIUS), origin.offset(PULSE_RADIUS, PULSE_RADIUS, PULSE_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (!carries(state) || state.getValue(SentryBlock.LIT)) {
				continue;
			}
			boolean spawner = state.getBlock() instanceof SentrySpawnerBlock;
			if (spawner || lit < PULSE_LIGHTS && level.getRandom().nextInt(6) == 0) {
				light(level, pos.immutable());
				if (!spawner) {
					lit++;
				}
			}
		}
	}
}
