package com.jaymoskingdoms.props;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (0.7.440) A window box or a pot planter: plant a small flower in it and it grows (random ticks) to full bloom, when a
 * bare hand picks a flower or two and it grows on. Breaking it gives the flower back.
 */
public class PlanterBlock extends PropBlock {
	/** What grows in it (0: nothing; else {@link #FLOWERS} by place, from 1). */
	public static final List<Item> FLOWERS = List.of(Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
		Items.RED_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY);
	public static final IntegerProperty FLOWER = IntegerProperty.create("flower", 0, 9);
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
	/** Its chance each random tick to grow a stage; how many flowers a full bloom gives at most; the stage it's cut back to. */
	static final float GROW_CHANCE = 0.35F;
	static final int MOST_PICKED = 2, PICKED_TO = 1;

	public PlanterBlock(BlockBehaviour.Properties properties, Kind kind) {
		super(properties.randomTicks(), kind);
		this.registerDefaultState(this.defaultBlockState().setValue(FLOWER, 0).setValue(AGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FLOWER, AGE);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		int flower = FLOWERS.indexOf(stack.getItem()) + 1;
		if (flower > 0 && state.getValue(FLOWER) == 0) {
			level.setBlock(pos, state.setValue(FLOWER, flower).setValue(AGE, 0), Block.UPDATE_ALL);
			level.playSound(player, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int flower = state.getValue(FLOWER);
		if (flower > 0 && state.getValue(AGE) == 3) {
			if (level instanceof ServerLevel server) {
				Block.popResource(server, pos.above(), new ItemStack(FLOWERS.get(flower - 1), 1 + level.getRandom().nextInt(MOST_PICKED)));
			}
			level.setBlock(pos, state.setValue(AGE, PICKED_TO), Block.UPDATE_ALL);
			level.playSound(player, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.2F);
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(FLOWER) > 0 && state.getValue(AGE) < 3;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextFloat() < GROW_CHANCE) {
			level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		int flower = state.getValue(FLOWER);
		if (flower > 0 && level instanceof ServerLevel server && !player.isCreative()) {
			Block.popResource(server, pos, new ItemStack(FLOWERS.get(flower - 1)));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
