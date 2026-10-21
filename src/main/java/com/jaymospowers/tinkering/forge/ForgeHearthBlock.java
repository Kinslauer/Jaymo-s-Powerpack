package com.jaymospowers.tinkering.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The forge hearth: a brick hearth that burns coal or charcoal (right-click it in, up to {@link #HEARTH_FUEL_MAX}) and,
 * once lit (flint and steel, a fire charge, or an Emberborn's bare hand), burns hot: heat 2 for a kiln over it and
 * anything else that reads heat ({@link com.jaymospowers.tinkering.kiln.Heat}). Each piece lasts {@link #HEARTH_BURN_TICKS}.
 */
public class ForgeHearthBlock extends HorizontalDirectionalBlock {
	/** Coal it holds at most, and how long each piece burns (ticks; a furnace gets as long from one). */
	public static final int HEARTH_FUEL_MAX = 8, HEARTH_BURN_TICKS = 1600;
	/** Light it gives while lit. */
	public static final int HEARTH_LIGHT = 13;
	/** Jaymo's Kingdoms tags its Emberborn players with this: they light a hearth with a touch. */
	public static final String EMBERBORN_TAG = "jaymoskingdoms_heritage_emberborn";
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, HEARTH_FUEL_MAX);

	public ForgeHearthBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(FUEL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, FUEL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		int fuel = state.getValue(FUEL);
		if (stack.is(ItemTags.COALS)) {
			if (fuel >= HEARTH_FUEL_MAX) {
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(FUEL, fuel + 1), Block.UPDATE_ALL);
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
			return InteractionResult.SUCCESS;
		}
		boolean flint = stack.is(Items.FLINT_AND_STEEL);
		if ((flint || stack.is(Items.FIRE_CHARGE)) && !state.getValue(LIT)) {
			if (fuel == 0) {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.forge_hearth.no_fuel"));
				}
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				light((ServerLevel) level, pos, state);
				level.playSound(null, pos, flint ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F,
					0.9F + level.getRandom().nextFloat() * 0.2F);
				if (flint) {
					stack.hurtAndBreak(1, player, hand);
				} else {
					stack.consume(1, player);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** An Emberborn lights it with a bare hand. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(LIT) || !player.entityTags().contains(EMBERBORN_TAG)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			if (state.getValue(FUEL) == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.forge_hearth.no_fuel"));
			} else {
				light((ServerLevel) level, pos, state);
				level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
				((ServerLevel) level).sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.02);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static void light(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, state.getBlock(), HEARTH_BURN_TICKS);
	}

	/** A piece of coal has burnt through: the next catches, or the hearth goes out. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		int fuel = Math.max(0, state.getValue(FUEL) - 1);
		if (fuel > 0) {
			level.setBlock(pos, state.setValue(FUEL, fuel), Block.UPDATE_ALL);
			level.scheduleTick(pos, state.getBlock(), HEARTH_BURN_TICKS);
		} else {
			level.setBlock(pos, state.setValue(FUEL, 0).setValue(LIT, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		if (random.nextInt(8) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
		}
		level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.01, 0.0);
		}
		Direction front = state.getValue(FACING);
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.FLAME, x + front.getStepX() * 0.52, pos.getY() + 0.3, z + front.getStepZ() * 0.52, 0.0, 0.0, 0.0);
		}
	}
}
