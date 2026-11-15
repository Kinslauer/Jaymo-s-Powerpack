package com.jaymospowers.tinkering.coke;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A charcoal pit's clock, in the log it was lit at ({@link CharcoalPit}). Every {@link CharcoalPit#CHECK_TICKS} it looks
 * the heap over: let air in (more than the one gap) and it flares, the lit log goes back to the log it was and fire takes
 * every opening; left buried for {@link CharcoalPit#BURN_TICKS}, every log of the heap becomes a charred log. Smoke seeps
 * out through the gap (or up through the cover) while it smoulders. Counted on the world's clock, so a pit far from
 * anyone finishes when its land is next loaded.
 */
public class SmoulderingLogEntity extends BlockEntity {
	private BlockState log = Blocks.OAK_LOG.defaultBlockState();
	private long litAt;

	public SmoulderingLogEntity(BlockPos pos, BlockState state) {
		super(CokeAndSteel.SMOULDERING_LOG_ENTITY, pos, state);
	}

	void light(BlockState was, long now) {
		log = was;
		litAt = now;
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SmoulderingLogEntity core) {
		if (level.getGameTime() % CharcoalPit.CHECK_TICKS == Math.floorMod(pos.asLong(), CharcoalPit.CHECK_TICKS)) {
			core.check((ServerLevel) level, pos);
		}
	}

	private void check(ServerLevel level, BlockPos pos) {
		Set<BlockPos> heap = CharcoalPit.heap(level, pos);
		if (heap == null) {
			return;
		}
		List<BlockPos> open = CharcoalPit.openings(level, heap);
		if (open.size() > 1) {
			flare(level, pos, open);
			return;
		}
		if (level.getGameTime() - litAt >= CharcoalPit.BURN_TICKS) {
			for (BlockPos at : heap) {
				BlockState was = level.getBlockState(at);
				BlockState charred = CokeAndSteel.CHARRED_LOG.defaultBlockState();
				if (was.hasProperty(RotatedPillarBlock.AXIS)) {
					charred = charred.setValue(RotatedPillarBlock.AXIS, was.getValue(RotatedPillarBlock.AXIS));
				}
				level.setBlock(at, charred, Block.UPDATE_ALL);
			}
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.6F);
			return;
		}
		BlockPos out = open.isEmpty() ? highest(heap).above() : open.getFirst();
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, out.getX() + 0.5, out.getY() + 0.5, out.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.005);
	}

	private static BlockPos highest(Set<BlockPos> heap) {
		BlockPos top = null;
		for (BlockPos at : heap) {
			if (top == null || at.getY() > top.getY()) {
				top = at;
			}
		}
		return top.above();
	}

	/** Air got in: the lit log is plain wood again and fire takes every opening that will hold one. */
	private void flare(ServerLevel level, BlockPos pos, List<BlockPos> open) {
		level.setBlock(pos, log, Block.UPDATE_ALL);
		for (BlockPos at : open) {
			if (level.isEmptyBlock(at)) {
				level.setBlock(at, BaseFireBlock.getState(level, at), Block.UPDATE_ALL);
			}
		}
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		log = in.read("log", BlockState.CODEC).orElse(Blocks.OAK_LOG.defaultBlockState());
		litAt = in.getLongOr("lit_at", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.store("log", BlockState.CODEC, log);
		out.putLong("lit_at", litAt);
	}
}
