package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A sentry crate: a chest of the ruins that no explosion harms. One in three (set by the structure) is a mimic, which
 * leaps out as a {@link Mimic} when it is opened or broken. Mined with a pickaxe it drops itself and spills what it holds.
 */
public class SentryCrateBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public SentryCrateBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SentryCrateEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof SentryCrateEntity crate)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (crate.isMimic()) {
				unleash(server, pos, sp);
			} else {
				sp.openMenu(crate);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && !sp.isCreative()
			&& level.getBlockEntity(pos) instanceof SentryCrateEntity crate && crate.isMimic()) {
			unleash(server, pos, sp);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** The crate was a mimic: it becomes one, already after {@code player}. */
	private static void unleash(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (level.getBlockEntity(pos) instanceof SentryCrateEntity crate) {
			crate.setMimic(false);
			crate.clearContent();
		}
		level.removeBlock(pos, false);
		Mimic mimic = DungeonMobs.MIMIC.spawn(level, pos, EntitySpawnReason.TRIGGERED);
		if (mimic != null) {
			mimic.setTarget(player);
			mimic.setPersistenceRequired();
			level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.04);
		}
	}
}
