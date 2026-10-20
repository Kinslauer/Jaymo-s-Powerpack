package com.jaymospowers.power.cryo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.14.0) Every block of ice Cryokinesis puts in the world, and its melting. Nothing the power places is left behind:
 * each block is kept here with what it replaced and when it melts; at that time, if it's still our ice, it goes back
 * (water to water, anything else to air). A server stopping melts it all at once (as Kingdoms' {@code FrostPrison} does).
 * Ice only ever goes where nothing solid stands: air, a water source, a snow layer or a plant ({@link Mode}).
 */
public final class CryoIce {
	/** A block that melts while its chunk isn't loaded waits this long (ticks) and tries again. */
	static final int UNLOADED_RETRY = 100;

	/** Where a block of ice may go. */
	public enum Mode {
		/** Only into air (Flash Freeze's shell: nothing is broken to make it). */
		AIR,
		/** Only onto a water source with air over it (the Ice Slide's lane, Frost Breath). */
		WATER,
		/** Air, a water source, a snow layer or a plant (the Ice Wall). */
		OPEN
	}

	/** One block of ice: the block it is, what goes back when it melts, and when (game time). */
	private static final class Placed {
		final Block ice;
		final BlockState restore;
		long until;

		Placed(Block ice, BlockState restore, long until) {
			this.ice = ice;
			this.restore = restore;
			this.until = until;
		}
	}

	private static final Map<ServerLevel, Map<Long, Placed>> PLACED = new HashMap<>();

	private CryoIce() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CryoIce::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// (no ice left standing over a restart)
			for (Map.Entry<ServerLevel, Map<Long, Placed>> level : PLACED.entrySet()) {
				for (Map.Entry<Long, Placed> entry : level.getValue().entrySet()) {
					melt(level.getKey(), BlockPos.of(entry.getKey()), entry.getValue(), false);
				}
			}
			PLACED.clear();
		});
	}

	/** Whether ice may go at {@code pos} in this {@code mode}. */
	static boolean fits(ServerLevel level, BlockPos pos, Mode mode) {
		if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		boolean water = state.is(Blocks.WATER) && state.getFluidState().isSource();
		return switch (mode) {
			case AIR -> state.isAir();
			case WATER -> water && level.getBlockState(pos.above()).isAir();
			case OPEN -> state.isAir() || water || state.is(Blocks.SNOW)
				|| state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !(state.getBlock() instanceof DoublePlantBlock);
		};
	}

	/**
	 * Puts {@code ice} at {@code pos} for {@code ticks} if it {@link #fits}; ice of ours already there just lasts longer.
	 * Returns whether there's our ice there now.
	 */
	public static boolean place(ServerLevel level, BlockPos pos, BlockState ice, int ticks, Mode mode) {
		long key = pos.asLong();
		long until = level.getGameTime() + ticks;
		Map<Long, Placed> here = PLACED.computeIfAbsent(level, k -> new HashMap<>());
		Placed had = here.get(key);
		if (had != null && level.getBlockState(pos).is(had.ice)) {
			had.until = Math.max(had.until, until);
			return true;
		}
		if (!fits(level, pos, mode)) {
			return false;
		}
		BlockState was = level.getBlockState(pos);
		BlockState restore = was.is(Blocks.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
		level.setBlockAndUpdate(pos, ice);
		here.put(key, new Placed(ice.getBlock(), restore, until));
		return true;
	}

	/**
	 * Melts the given blocks of ours now (a prison whose prisoner died). A block another shell or wall has since made
	 * last longer than {@code until} is theirs too, and stays.
	 */
	public static void meltNow(ServerLevel level, List<BlockPos> blocks, long until) {
		Map<Long, Placed> here = PLACED.get(level);
		if (here == null) {
			return;
		}
		for (BlockPos pos : blocks) {
			Placed placed = here.get(pos.asLong());
			if (placed != null && placed.until <= until) {
				here.remove(pos.asLong());
				melt(level, pos, placed, true);
			}
		}
	}

	/** Whether the block at {@code pos} is ice we placed. */
	public static boolean ours(ServerLevel level, BlockPos pos) {
		Map<Long, Placed> here = PLACED.get(level);
		return here != null && here.containsKey(pos.asLong());
	}

	private static void tick(MinecraftServer server) {
		if (PLACED.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<ServerLevel, Map<Long, Placed>>> levels = PLACED.entrySet().iterator(); levels.hasNext();) {
			Map.Entry<ServerLevel, Map<Long, Placed>> level = levels.next();
			ServerLevel world = level.getKey();
			long now = world.getGameTime();
			List<Long> done = new ArrayList<>();
			for (Map.Entry<Long, Placed> entry : level.getValue().entrySet()) {
				Placed placed = entry.getValue();
				BlockPos at = BlockPos.of(entry.getKey());
				if (world.isLoaded(at) && !world.getBlockState(at).is(placed.ice)) {
					// broken, blown up or built over: no longer ours, so forget it without touching what's there
					done.add(entry.getKey());
					continue;
				}
				if (placed.until > now) {
					continue;
				}
				BlockPos pos = BlockPos.of(entry.getKey());
				if (!world.isLoaded(pos)) {
					placed.until = now + UNLOADED_RETRY;
					continue;
				}
				melt(world, pos, placed, world.getRandom().nextInt(4) == 0);
				done.add(entry.getKey());
			}
			for (Long key : done) {
				level.getValue().remove(key);
			}
			if (level.getValue().isEmpty()) {
				levels.remove();
			}
		}
	}

	/** Back to what it was, if it's still our ice (a block broken or built over stays as it is). */
	private static void melt(ServerLevel level, BlockPos pos, Placed placed, boolean show) {
		if (!level.getBlockState(pos).is(placed.ice)) {
			return;
		}
		level.setBlockAndUpdate(pos, placed.restore);
		if (show) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, placed.ice.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
				pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
			level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.3F, 1.4F);
		}
	}
}
