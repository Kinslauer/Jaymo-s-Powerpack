package com.jaymospowers.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Falling snow (after Falling Snow by CobaltMC, CC0): snow left on nothing comes down instead of vanishing, as it does in
 * Bedrock. When what held a layer of snow goes (a leaf decays, a log is chopped out, the ground is dug from under it), the
 * layer falls like sand ({@code SnowFallMixin}); where it lands on snow it piles onto it, layer on layer up to a full
 * block and the rest above ({@code SnowLandMixin}); into water it melts. The same goes for anything laid in layers like
 * snow (Jaymo's Kingdoms' drifted sand).
 * <p>
 * Snow that builds up while it snows and melts back is the pack's Immersive Snow Reloaded (with the game's own
 * {@code max_snow_accumulation_height}) and Jaymo's Kitchen's seasons ({@code SeasonalWeather}); this only adds the fall.
 * <p>
 * A whole snowy wood losing its leaves at once could start hundreds of falls in one tick: at most {@link #PER_TICK} start a
 * tick in each world, and the rest wait their turn ({@link #DELAY} ticks on).
 */
public final class FallingSnow {
	/** Ticks between a layer losing its hold and falling. */
	public static final int DELAY = 2;
	/** The most layers that start falling in one world in one tick. */
	static final int PER_TICK = 48;
	/** Snow kicked up where a fall lands. */
	static final int PUFF = 12;

	private static long tickOf = Long.MIN_VALUE;
	private static int started;

	private FallingSnow() {
	}

	/** Whether the layer at {@code pos} has lost its hold and should fall, {@code below} being under it. */
	public static boolean loose(LevelReader level, BlockPos pos, BlockState below) {
		return level instanceof ServerLevel server && pos.getY() > server.getMinY() && free(below);
	}

	/** Open below: air, fire, water, or something a falling block goes through. Never more snow: that holds it. */
	private static boolean free(BlockState below) {
		return FallingBlock.isFree(below) && !(below.getBlock() instanceof SnowLayerBlock);
	}

	/** The scheduled tick of a layer that lost its hold: it falls, if it's still loose and the tick's budget allows. */
	public static void tick(BlockState state, ServerLevel level, BlockPos pos) {
		if (!free(level.getBlockState(pos.below())) || pos.getY() <= level.getMinY()) {
			return;
		}
		long now = level.getGameTime();
		if (tickOf != now) {
			tickOf = now;
			started = 0;
		}
		if (++started > PER_TICK) {
			level.scheduleTick(pos, state.getBlock(), DELAY + level.getRandom().nextInt(DELAY * 4));
			return;
		}
		FallingBlockEntity fall = FallingBlockEntity.fall(level, pos, state);
		fall.dropItem = false;
	}

	/**
	 * A falling layer comes down (the entity is on the ground): it piles onto layers of its kind there or just under it, sits
	 * where it can, melts into water and breaks up where it can't lie. Returns false for any other falling block, which
	 * lands as the game lands it.
	 */
	public static boolean land(FallingBlockEntity fall) {
		BlockState state = fall.getBlockState();
		if (!(state.getBlock() instanceof SnowLayerBlock) || !(fall.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos pos = fall.blockPosition();
		fall.discard();
		if (!level.getFluidState(pos).isEmpty()) {
			puff(level, pos, state);
			return true;
		}
		BlockState at = level.getBlockState(pos);
		if (!at.is(state.getBlock())) {
			BlockState below = level.getBlockState(pos.below());
			if (below.is(state.getBlock()) && below.getValue(SnowLayerBlock.LAYERS) < SnowLayerBlock.MAX_HEIGHT && (at.isAir() || at.canBeReplaced())) {
				pos = pos.below();
				at = below;
			}
		}
		int layers = state.getValue(SnowLayerBlock.LAYERS);
		if (at.is(state.getBlock())) {
			int total = at.getValue(SnowLayerBlock.LAYERS) + layers;
			level.setBlockAndUpdate(pos, at.setValue(SnowLayerBlock.LAYERS, Math.min(SnowLayerBlock.MAX_HEIGHT, total)));
			if (total > SnowLayerBlock.MAX_HEIGHT && level.getBlockState(pos.above()).isAir()) {
				level.setBlockAndUpdate(pos.above(), state.setValue(SnowLayerBlock.LAYERS, total - SnowLayerBlock.MAX_HEIGHT));
			}
		} else if ((at.isAir() || at.canBeReplaced()) && state.canSurvive(level, pos) && !free(level.getBlockState(pos.below()))) {
			level.setBlockAndUpdate(pos, state);
		}
		puff(level, pos, state);
		return true;
	}

	/** Snow (or sand) kicked up where a layer landed, and its soft thump. */
	private static void puff(ServerLevel level, BlockPos pos, BlockState state) {
		double top = pos.getY() + 0.125 * state.getValue(SnowLayerBlock.LAYERS);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, top, pos.getZ() + 0.5, PUFF, 0.35, 0.1, 0.35, 0.05);
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
	}
}
