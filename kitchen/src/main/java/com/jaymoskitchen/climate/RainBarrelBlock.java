package com.jaymoskitchen.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

/**
 * A rain barrel (our own: a cooper's open staved barrel with two iron hoops): set out under the open sky, it fills with
 * clean rain, a level at a time ({@link #LEVEL}, up to {@link #MOST}), while rain falls on it. Rain is read where it
 * falls, at the barrel's own open top ({@code Level#precipitationAt}, not the world's weather flag), so a barrel in a dry
 * land, under a roof or in the snow gathers nothing. A glass bottle, a waterskin (any tier) or an empty bowl takes a
 * level from it as clean water ({@link Climate#CLEAN_WATER}, a skin filled with clean water, a bowl of clean water). A
 * comparator reads how full it is. Kingdoms' builders set one beside each village house.
 */
public class RainBarrelBlock extends Block {
	public static final int MOST = 4;
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MOST);
	/** The share of the game's rain ticks on this barrel that fill it a level (a cauldron takes a twentieth). */
	static final float FILL_CHANCE = 0.1F;
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0), Block.box(1.0, 1.0, 1.0, 15.0, 15.0, 2.0),
		Block.box(1.0, 1.0, 14.0, 15.0, 15.0, 15.0), Block.box(1.0, 1.0, 2.0, 2.0, 15.0, 14.0), Block.box(14.0, 1.0, 2.0, 15.0, 15.0, 14.0));
	private static final VoxelShape OUTLINE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);

	public RainBarrelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** The game drops rain on the top block of a column it falls on: this barrel, if its open top is under the sky. */
	@Override
	public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
		if (level.isClientSide() || precipitation != Biome.Precipitation.RAIN || state.getValue(LEVEL) >= MOST) {
			return;
		}
		BlockPos above = pos.above();
		if (level.precipitationAt(above) != Biome.Precipitation.RAIN || !level.canSeeSky(above) || level.getRandom().nextFloat() >= FILL_CHANCE) {
			return;
		}
		level.setBlock(pos, state.setValue(LEVEL, state.getValue(LEVEL) + 1), Block.UPDATE_ALL);
		level.updateNeighbourForOutputSignal(pos, this);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int fill = state.getValue(LEVEL);
		if (fill <= 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		ItemStack given;
		if (held.is(Items.GLASS_BOTTLE)) {
			given = new ItemStack(Climate.CLEAN_WATER);
		} else if (held.is(Items.BOWL)) {
			given = new ItemStack(WaterBowls.CLEAN_WATER_BOWL);
		} else if (held.getItem() instanceof WaterskinItem skin) {
			WaterskinItem.Water water = WaterskinItem.water(held);
			if (water.sips() >= skin.maxSips() && water.clean()) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (!level.isClientSide()) {
				// topped up from the barrel, the whole skin is clean rain
				held.set(Climate.WATERSKIN_WATER, new WaterskinItem.Water(skin.maxSips(), true));
				take(level, pos, state);
			}
			return InteractionResult.SUCCESS;
		} else {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, given));
			take(level, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	/** One level drawn off. */
	private static void take(Level level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(LEVEL, state.getValue(LEVEL) - 1), Block.UPDATE_ALL);
		level.updateNeighbourForOutputSignal(pos, state.getBlock());
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
		return state.getValue(LEVEL) * 15 / MOST;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (state.getValue(LEVEL) < MOST && level.precipitationAt(pos.above()) == Biome.Precipitation.RAIN && level.canSeeSky(pos.above())
			&& random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.RAIN, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.95, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
				0.0, 0.0, 0.0);
		}
	}
}
