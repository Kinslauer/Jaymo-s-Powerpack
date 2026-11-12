package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Powers, ground sticks) Andy: "placing a maximum pile of sticks, and then stones all around it or vice versa gives you a
 * firepit you can light, like terrafirma craft". A full pile of 4 {@link LooseSticksBlock loose sticks} with a rock pile on
 * all 8 blocks around it turns into a firepit ({@link #tryForm}), whichever is laid down last.
 * <p>
 * It is a campfire underneath (in the campfires tag, sharing the vanilla campfire block entity): flint and steel, a flint
 * strike or a fire charge lights it, it cooks four foods, smokes, burns what stands in it, a shovel or water puts it out.
 * Unlike a campfire it burns {@link #FUEL fuel}: each unit lasts {@link #BURN_TICKS}; a stick adds one, a plank
 * {@link #PLANK_FUEL}, a log {@link #LOG_FUEL} (TFC's burn times). With none left it goes out to a bed of ash, rain puts
 * it out, and with no fuel it won't take a light.
 */
public class FirepitBlock extends CampfireBlock {
	// ---- tuning ----
	public static final int MAX_FUEL = 15;
	/** How long one unit of fuel (a stick) burns: 22.5 s, so the first pile of 4 sticks gives 1.5 minutes. */
	public static final int BURN_TICKS = 450;
	/** Fuel a log (or wood, stem, hyphae) adds: 5 units, 1 min 52 s, as long as a log burns in TFC's firepit. */
	public static final int LOG_FUEL = 5;
	/** Fuel a plank adds. */
	public static final int PLANK_FUEL = 2;
	/** Light of a lit firepit (a campfire gives 15). */
	public static final int LIGHT = 14;

	public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, MAX_FUEL);
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0);

	public FirepitBlock(BlockBehaviour.Properties properties) {
		super(true, 1, properties);
		this.registerDefaultState(this.defaultBlockState().setValue(FUEL, 4).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FUEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Turns a full stick pile at pos into a firepit when every block around it holds a rock pile. Whether it did. */
	public static boolean tryForm(Level level, BlockPos pos) {
		BlockState sticks = level.getBlockState(pos);
		if (level.isClientSide() || !sticks.is(GroundSticks.LOOSE_STICKS) || sticks.getValue(LooseSticksBlock.STICKS) < 4
			|| sticks.getValue(LooseSticksBlock.WATERLOGGED)) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && !isRock(level.getBlockState(pos.offset(dx, 0, dz)))) {
					return false;
				}
			}
		}
		level.setBlock(pos, GroundSticks.FIREPIT.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
		level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
		return true;
	}

	/** Called when a rock pile is laid: a full stick pile next to it may now be ringed. */
	public static void rockLaid(Level level, BlockPos rock) {
		if (level.isClientSide()) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && level.getBlockState(rock.offset(dx, 0, dz)).is(GroundSticks.LOOSE_STICKS)) {
					tryForm(level, rock.offset(dx, 0, dz));
				}
			}
		}
	}

	private static boolean isRock(BlockState state) {
		return GroundRocks.ROCKS.containsValue(state.getBlock());
	}

	/** Fuel an item adds: a stick 1, a plank {@link #PLANK_FUEL}, a log {@link #LOG_FUEL}, else 0. */
	public static int fuelOf(ItemStack stack) {
		if (stack.is(Items.STICK)) {
			return 1;
		}
		if (stack.is(ItemTags.PLANKS) && !stack.is(ItemTags.NON_FLAMMABLE_WOOD)) {
			return PLANK_FUEL;
		}
		return stack.is(ItemTags.LOGS_THAT_BURN) ? LOG_FUEL : 0;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int fuel = fuelOf(stack);
		if (fuel > 0) {
			if (state.getValue(FUEL) >= MAX_FUEL) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(FUEL, Math.min(MAX_FUEL, state.getValue(FUEL) + fuel)), Block.UPDATE_ALL);
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		boolean wasLit = oldState.is(this) && oldState.getValue(LIT);
		if (state.getValue(LIT) && !wasLit) {
			// nothing to burn: the sparks die at once
			level.scheduleTick(pos, this, state.getValue(FUEL) == 0 ? 10 : BURN_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		if (!state.getValue(LIT)) {
			return;
		}
		int fuel = state.getValue(FUEL) - 1;
		if (fuel <= 0 || level.isRainingAt(pos.above())) {
			level.setBlock(pos, state.setValue(LIT, false).setValue(FUEL, Math.max(0, fuel)), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
			return;
		}
		level.setBlock(pos, state.setValue(FUEL, fuel), Block.UPDATE_ALL);
		level.scheduleTick(pos, this, BURN_TICKS);
	}
}
