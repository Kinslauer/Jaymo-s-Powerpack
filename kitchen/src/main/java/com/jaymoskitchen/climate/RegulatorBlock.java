package com.jaymoskitchen.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The climate stove (our own; a smith's iron firebox with a brass-doored mouth): the hearth and the icebox in one block.
 * Loaded with fuel (coal, charcoal, logs, planks, sticks) it burns and warms the closed room round it as a hearth does;
 * loaded with ice (ice, packed ice, blue ice, a snow block) it cools the room as an icebox does ({@link Warmth#room}, the
 * hearth's own room fill, {@link HearthEntity}). It does one thing at a time: {@link #COLD} says which, and the last thing
 * loaded decides, swapping whatever was in it for the new load. Use it with fuel or ice in hand to load it, or with an
 * empty hand to open the hearth's screen, which shows it as a hearth or an icebox by its present job.
 */
public class RegulatorBlock extends HearthBlock {
	/** True while it is loaded with ice and cooling; false when it holds fuel (or nothing) and warms. */
	public static final BooleanProperty COLD = BooleanProperty.create("cold");

	public RegulatorBlock(BlockBehaviour.Properties properties) {
		super(false, properties);
		registerDefaultState(defaultBlockState().setValue(COLD, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(COLD);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int ice = Warmth.regulatorIce(held);
		int fuel = ice > 0 ? ice : Warmth.burnTicks(held, false);
		if (fuel <= 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.getBlockEntity(pos) instanceof HearthEntity entity && !level.isClientSide()) {
			if (!entity.load(fuel, ice > 0)) {
				return InteractionResult.PASS;
			}
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		if (state.getValue(COLD)) {
			// cold air spilling over the lid
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SNOWFLAKE, x + (random.nextDouble() - 0.5) * 0.8, y + 1.02, z + (random.nextDouble() - 0.5) * 0.8,
					0.0, -0.01, 0.0);
			}
			return;
		}
		Direction facing = state.getValue(FACING);
		double fx = x + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.4 * Math.abs(facing.getStepZ());
		double fz = z + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.4 * Math.abs(facing.getStepX());
		level.addParticle(ParticleTypes.FLAME, fx, y + 0.35 + random.nextDouble() * 0.2, fz, 0.0, 0.0, 0.0);
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.1, z, 0.0, 0.05, 0.0);
		}
	}
}
