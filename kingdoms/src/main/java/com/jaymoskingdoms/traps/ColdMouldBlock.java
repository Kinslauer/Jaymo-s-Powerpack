package com.jaymoskingdoms.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Cold mould (Andy's pick F3: brown mould that drinks warmth), a grey-brown fur on the rock of a cave. It lives on heat:
 * <ul>
 * <li><b>Drains.</b> Anything living within {@link #DRAIN_RANGE} blocks is chilled ({@link #CHILL} frozen ticks a second,
 * the shiver of powder snow), and a flame within {@link #EAT_RANGE} is drunk: a torch, lantern or fire goes out, a campfire
 * is put out. Each flame it drinks feeds it.</li>
 * <li><b>Grows toward fire.</b> It looks for heat within {@link #SMELL_RANGE} blocks and, fed, spreads one block closer to
 * it. A torch lit in a mouldy cave draws it onto you; a camp built near it is eaten.</li>
 * <li><b>Runs out.</b> With no heat in reach it starves: now and then a patch dies back to bare rock, so a cave without
 * fire loses its mould; left alone it does no harm to anyone who keeps away (nobody is punished for ignoring it).</li>
 * <li><b>The answer is cold.</b> Ice, packed ice, snow or powder snow beside it (or water) kills it at once; fire only
 * feeds it.</li>
 * </ul>
 * A patch that is kept in a cellar also keeps it cold (see {@link Traps}: Jaymo's Kitchen counts it as a cold place).
 */
public class ColdMouldBlock extends Block {
	// ---- tuning ----
	static final double DRAIN_RANGE = 3.0;
	static final int CHILL = 40, SMELL_RANGE = 6, EAT_RANGE = 2, TICK = 20;
	/** Chances each random tick: to spread when fed, to starve with no heat in reach. */
	static final float SPREAD = 0.35F, STARVE = 0.12F;

	public ColdMouldBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		level.scheduleTick(pos, this, TICK);
	}

	/** The chill, each second. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(DRAIN_RANGE))) {
			if (e.canFreeze()) {
				e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + CHILL));
			}
		}
		if (random.nextInt(4) == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.7, 0.7, 0.7, 0.01);
		}
		level.scheduleTick(pos, this, TICK);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// the answer: cold, or water
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.is(BlockTags.ICE) || n.is(Blocks.SNOW_BLOCK) || n.is(Blocks.POWDER_SNOW) || n.is(Blocks.WATER)) {
				die(level, pos, "cold");
				return;
			}
		}
		BlockPos heat = nearestHeat(level, pos, SMELL_RANGE);
		if (heat == null) {
			if (random.nextFloat() < STARVE) {
				die(level, pos, "starved");
			}
			return;
		}
		boolean fed = false;
		if (heat.distSqr(pos) <= EAT_RANGE * EAT_RANGE) {
			drink(level, heat);
			fed = true;
		}
		if ((fed || random.nextFloat() < SPREAD) && random.nextFloat() < SPREAD * 2.0F) {
			BlockPos next = Surfaces.neighbour(level, pos, heat, random, false);
			if (next != null && next.distSqr(heat) < pos.distSqr(heat) + 1.0) {
				level.setBlockAndUpdate(next, defaultBlockState());
			}
		}
	}

	/** The nearest flame or heat within {@code range}, or null. */
	static @Nullable BlockPos nearestHeat(ServerLevel level, BlockPos pos, int range) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-range, -range, -range), pos.offset(range, range, range))) {
			if (Surfaces.heat(level.getBlockState(p))) {
				double d = p.distSqr(pos);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** Drinks the heat at {@code at}: a flame goes out. */
	private static void drink(ServerLevel level, BlockPos at) {
		BlockState s = level.getBlockState(at);
		if (s.is(BlockTags.CAMPFIRES) && s.getValueOrElse(CampfireBlock.LIT, false)) {
			level.setBlockAndUpdate(at, s.setValue(CampfireBlock.LIT, false));
		} else if (s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.FURNACE)) {
			return;
		} else {
			level.destroyBlock(at, true);
		}
		level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.7F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
	}

	private static void die(ServerLevel level, BlockPos pos, String how) {
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.5, 0.5, 0.5, 0.02);
		level.playSound(null, pos, SoundEvents.POWDER_SNOW_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
	}
}
