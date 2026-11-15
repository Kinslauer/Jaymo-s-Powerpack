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
 * the heap over: let air in (more than the one gap), or let it grow past {@link CharcoalPit#MAX_LOGS}, and it flares, the
 * lit log goes back to the log it was and fire takes every opening; left buried for {@link CharcoalPit#BURN_TICKS}, every
 * log of the heap becomes a charred log. Smoke seeps out through the gap (or up through the cover) while it smoulders.
 * <p>
 * Its time is its own: each tick it counts on by the world's clock or the day's, whichever moved further (so a night
 * slept through or a pit far from anyone, finished when its land is next loaded, both count), and a Time Bloom's extra
 * ticks ({@code power/chrono/TimeBloom}, which ticks block entities again within the same game tick) count one each.
 */
public class SmoulderingLogEntity extends BlockEntity {
	private BlockState log = Blocks.OAK_LOG.defaultBlockState();
	/** Ticks it has smouldered, and the world's and the day's clocks when it last counted (0: not yet). */
	private long burnt, lastGame, lastDay;

	public SmoulderingLogEntity(BlockPos pos, BlockState state) {
		super(CokeAndSteel.SMOULDERING_LOG_ENTITY, pos, state);
	}

	void light(BlockState was, Level level) {
		// never the smouldering log itself: that's what a flare puts back
		log = was.is(CokeAndSteel.SMOULDERING_LOG) ? Blocks.OAK_LOG.defaultBlockState() : was;
		burnt = 0;
		lastGame = level.getGameTime();
		lastDay = level.getDefaultClockTime();
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SmoulderingLogEntity core) {
		long game = level.getGameTime();
		boolean extra = game == core.lastGame;
		core.count(level);
		if (!extra && game % CharcoalPit.CHECK_TICKS == Math.floorMod(pos.asLong(), CharcoalPit.CHECK_TICKS)) {
			core.check((ServerLevel) level, pos);
		}
	}

	/** Counts on to now: the further of the two clocks, or one tick for a Time Bloom's extra tick. */
	private void count(Level level) {
		long game = level.getGameTime(), day = level.getDefaultClockTime();
		if (lastGame <= 0) {
			// lit some other way (placed, or from an old save): its clock starts now
			lastGame = game;
			lastDay = day;
			setChanged();
			return;
		}
		burnt += game == lastGame ? 1 : Math.max(game - lastGame, Math.max(0L, day - lastDay));
		lastGame = game;
		lastDay = day;
		setChanged();
	}

	private void check(ServerLevel level, BlockPos pos) {
		Set<BlockPos> heap = CharcoalPit.heap(level, pos);
		if (heap == null) {
			// part of it lies where the world isn't loaded: look again later
			return;
		}
		if (heap.isEmpty()) {
			// grown past a pit: it can't be kept from the air
			flare(level, pos, CharcoalPit.openings(level, Set.of(pos)));
			return;
		}
		List<BlockPos> open = CharcoalPit.openings(level, heap);
		if (open.size() > 1) {
			flare(level, pos, open);
			return;
		}
		if (burnt >= CharcoalPit.BURN_TICKS) {
			for (BlockPos at : heap) {
				// the lit log keeps the axis of the log it was
				BlockState was = at.equals(pos) ? log : level.getBlockState(at);
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
		level.setBlock(pos, log.is(CokeAndSteel.SMOULDERING_LOG) ? Blocks.OAK_LOG.defaultBlockState() : log, Block.UPDATE_ALL);
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
		burnt = in.getLongOr("burnt", 0L);
		lastGame = in.getLongOr("last_game", 0L);
		lastDay = in.getLongOr("last_day", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.store("log", BlockState.CODEC, log);
		out.putLong("burnt", burnt);
		out.putLong("last_game", lastGame);
		out.putLong("last_day", lastDay);
	}
}
