package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.climate.Climate;
import com.jaymoskitchen.climate.Thirst;
import com.jaymoskitchen.climate.WaterskinItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A water barrel (Medieval Deco's idea, our own): {@link #FULL} levels of water. A glass bottle takes a level as clean
 * water (Thirst), a bucket takes the lot, a water bucket or bottle tips water in, a waterskin or watering can fills from
 * it, and an empty hand drinks a sip. Rain under the open sky fills it, slowly.
 */
public class WaterBarrelBlock extends Block {
	public static final int FULL = 3;
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, FULL);
	/** A random tick in the rain fills a level one time in this many. */
	static final int RAIN_ONE_IN = 2;
	/** An empty-handed sip: the water it gives (clean), and one sip in this many takes a level. */
	static final int SIP = 4, SIPS_PER_LEVEL = 3;

	public WaterBarrelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(LEVEL) < FULL;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.isRainingAt(pos.above()) && random.nextInt(RAIN_ONE_IN) == 0) {
			level.setBlockAndUpdate(pos, state.setValue(LEVEL, state.getValue(LEVEL) + 1));
		}
	}

	private static void change(Level level, BlockPos pos, BlockState state, int to) {
		level.setBlockAndUpdate(pos, state.setValue(LEVEL, to));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int water = state.getValue(LEVEL);
		ItemStack result = null;
		int to = water;
		if (held.is(Items.GLASS_BOTTLE) && water > 0) {
			result = new ItemStack(Climate.CLEAN_WATER);
			to = water - 1;
		} else if (held.is(Items.BUCKET) && water == FULL) {
			result = new ItemStack(Items.WATER_BUCKET);
			to = 0;
		} else if (held.is(Items.WATER_BUCKET) && water == 0) {
			result = new ItemStack(Items.BUCKET);
			to = FULL;
		} else if (Thirst.bottledWater(held) && water < FULL) {
			result = new ItemStack(Items.GLASS_BOTTLE);
			to = water + 1;
		} else if (held.getItem() instanceof WaterskinItem skin && water > 0 && WaterskinItem.water(held).sips() < skin.maxSips()) {
			if (!level.isClientSide()) {
				held.set(Climate.WATERSKIN_WATER, new WaterskinItem.Water(skin.maxSips(), true));
				change(level, pos, state, water - 1);
				level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		} else if (held.getItem() instanceof WateringCanItem && water > 0 && held.getDamageValue() > 0) {
			if (!level.isClientSide()) {
				held.setDamageValue(0);
				change(level, pos, state, water - 1);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.1F);
			}
			return InteractionResult.SUCCESS;
		}
		if (result == null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
			change(level, pos, state, to);
			level.playSound(null, pos, to < water ? SoundEvents.BOTTLE_FILL : SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int water = state.getValue(LEVEL);
		if (water == 0 || !(player instanceof ServerPlayer server) || Thirst.water(server) >= Thirst.MAX) {
			return InteractionResult.PASS;
		}
		Thirst.drink(server, SIP, true, 0.0F);
		if (level.getRandom().nextInt(SIPS_PER_LEVEL) == 0) {
			change(level, pos, state, water - 1);
		}
		level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.6F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
		return state.getValue(LEVEL) * 5;
	}
}
