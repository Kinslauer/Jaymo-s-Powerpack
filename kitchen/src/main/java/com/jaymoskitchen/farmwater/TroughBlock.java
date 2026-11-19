package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A feeding trough (Let's Do's idea, our own): fill it with hay or grain and the animals of its pen come to it, eat
 * and are fed ({@link Troughs}). Each unit of {@link #FILL} is a few bites; it is only ever food, nothing is lost by
 * leaving it empty.
 */
public class TroughBlock extends Block {
	public static final int FULL = 3;
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, FULL);
	/** Hay, grain and root crops (what the animals of a farm eat). */
	public static final TagKey<Item> FEED = TagKey.create(Registries.ITEM, JaymosKitchen.id("trough_feed"));
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 7, 15);

	public TroughBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FILL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FILL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!held.is(FEED) || state.getValue(FILL) >= FULL) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			// a bale of hay fills it, a handful of grain or a root makes one level
			int to = held.is(net.minecraft.world.item.Items.HAY_BLOCK) ? FULL : state.getValue(FILL) + 1;
			server.setBlockAndUpdate(pos, state.setValue(FILL, to));
			held.consume(1, player);
			server.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
			server.scheduleTick(pos, this, Troughs.CHECK);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && state.getValue(FILL) > 0) {
			server.scheduleTick(pos, this, Troughs.CHECK);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Troughs.tend(level, pos, state, random)) {
			level.scheduleTick(pos, this, Troughs.CHECK);
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
		return state.getValue(FILL) * 5;
	}
}
