package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A sentry trap: floor that looks like sentry flagstones. The first player to step on it sets it off (it goes spent): a
 * detonation sentry rises from the block above it and a pulse of sentry current wakes the spawners and lights the stone
 * nearby. The Slider springs the loaded traps of its room too, one for every sixteenth of its health.
 */
public class SentryTrapBlock extends Block {
	public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

	public SentryTrapBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LOADED, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LOADED);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && state.getValue(LOADED) && entity instanceof Player player && !player.isSpectator()) {
			spring(server, pos);
		}
		super.stepOn(level, pos, state, entity);
	}

	/** Sets the trap at {@code pos} off (nothing happens if it is spent or not a trap). */
	public static void spring(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(DungeonBlocks.SENTRY_TRAP) || !state.getValue(LOADED)) {
			return;
		}
		level.setBlock(pos, state.setValue(LOADED, false), 3);
		level.playSound(null, pos, SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.03);
		BlockPos above = pos.above();
		if (level.getBlockState(above).isAir()) {
			DungeonMobs.DETONATION_SENTRY.spawn(level, above, EntitySpawnReason.TRIGGERED);
		}
		SentryCurrent.pulse(level, pos);
	}
}
