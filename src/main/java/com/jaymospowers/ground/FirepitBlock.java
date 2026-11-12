package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Andy: "placing a maximum pile of sticks, and then stones all around it or vice versa gives you a firepit you can light,
 * like terrafirma craft", then "the stone in a circle and the fire inside [...] within one block like the campfire [...]
 * closer surrounded by stone. it can accomodate the spit and a pan raised on a grate made from sticks or iron bars."
 * <p>
 * A full pile of 4 {@link LooseSticksBlock loose sticks} with a full ring of {@link LooseSticksBlock#RING} rocks set
 * round it (rocks right-clicked onto the pile) becomes a firepit ({@link #form}). Nine firepits in a 3x3 become a
 * {@link BonfireBlock bonfire}.
 * <p>
 * It is a campfire underneath (in the campfires tag, sharing the vanilla campfire block entity): flint and steel, a flint
 * strike or a fire charge lights it, it cooks four foods, smokes, burns what stands in it, a shovel or water puts it out,
 * and Kitchen's spit goes on top. Unlike a campfire it burns {@link #FUEL fuel}: each unit lasts {@link #BURN_TICKS}; a
 * stick adds one, a plank {@link #PLANK_FUEL}, a log {@link #LOG_FUEL} (TFC's burn times). With none left it goes out
 * to a bed of ash, rain puts it out, and with no fuel it won't take a light.
 * <p>
 * A {@link Grate grate} over it (iron bars right-clicked on, or {@link #GRATE_STICKS} sticks sneak-clicked on) holds
 * Kitchen's cooking pot and frying pan above the flames; without one a pan can't be set there ({@link GroundSticks}).
 * Sneak + right click with an empty hand takes the grate off. A stick grate chars through in time.
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
	/** Sticks a stick grate takes. */
	public static final int GRATE_STICKS = 4;
	/** Chance, each unit of fuel burnt, that a stick grate chars through and falls in. */
	public static final float STICK_GRATE_BURN = 0.1F;

	/** What's laid over the fire for pans. */
	public enum Grate implements StringRepresentable {
		NONE, STICKS, IRON;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, MAX_FUEL);
	public static final EnumProperty<Grate> GRATE = EnumProperty.create("grate", Grate.class);
	private static final VoxelShape SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, 3.0, 15.5);
	private static final VoxelShape GRATE_SHAPE = Shapes.or(SHAPE, Block.box(1.0, 14.5, 1.0, 15.0, 15.5, 15.0));

	public FirepitBlock(BlockBehaviour.Properties properties) {
		super(true, 1, properties);
		this.registerDefaultState(this.defaultBlockState().setValue(FUEL, 4).setValue(LIT, false).setValue(GRATE, Grate.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FUEL, GRATE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(GRATE) == Grate.NONE ? SHAPE : GRATE_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * Turns the stick pile about to be set at pos (pile) into a firepit when it is full and fully ringed. Whether it did;
	 * if not, the caller sets the pile.
	 */
	public static boolean form(Level level, BlockPos pos, BlockState pile) {
		if (level.isClientSide() || !pile.is(GroundSticks.LOOSE_STICKS) || pile.getValue(LooseSticksBlock.STICKS) < 4
			|| pile.getValue(LooseSticksBlock.STONES) < LooseSticksBlock.RING || pile.getValue(LooseSticksBlock.WATERLOGGED)) {
			return false;
		}
		level.setBlock(pos, GroundSticks.FIREPIT.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.9F, 0.7F);
		level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
		return true;
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

	/** Feeds fuel from stack into a fire whose fuel is prop (up to max). The use result, or null if it isn't fuel. */
	static InteractionResult feed(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, IntegerProperty prop, int max) {
		int fuel = fuelOf(stack);
		if (fuel <= 0) {
			return null;
		}
		if (state.getValue(prop) >= max) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(prop, Math.min(max, state.getValue(prop) + fuel)), Block.UPDATE_ALL);
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (stack.is(Items.IRON_BARS) && state.getValue(GRATE) == Grate.NONE) {
			return setGrate(state, level, pos, player, Grate.IRON, () -> stack.consume(1, player));
		}
		InteractionResult fed = feed(stack, state, level, pos, player, FUEL, MAX_FUEL);
		return fed != null ? fed : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		Grate grate = state.getValue(GRATE);
		if (player.isSecondaryUseActive() && grate != Grate.NONE) {
			return setGrate(state, level, pos, player, Grate.NONE, () -> {
				if (!player.getAbilities().instabuild) {
					ItemStack back = grate == Grate.IRON ? new ItemStack(Items.IRON_BARS) : new ItemStack(Items.STICK, GRATE_STICKS);
					player.getInventory().placeItemBackInInventory(back, Prediction.SERVER_ONLY);
				}
			});
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	/** Lays a grate over the pit, or (Grate.NONE) takes it off, tipping off whatever pan sat on it. */
	static InteractionResult setGrate(BlockState state, Level level, BlockPos pos, Player player, Grate grate, Runnable pay) {
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(GRATE, grate), Block.UPDATE_ALL);
			pay.run();
			level.playSound(null, pos, grate == Grate.STICKS || state.getValue(GRATE) == Grate.STICKS ? SoundEvents.WOOD_PLACE
				: SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			if (grate == Grate.NONE) {
				dropCookware(level, pos.above());
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Kitchen's pot or pan (as an item), which needs a grate under it. */
	static boolean isCookware(ItemStack stack) {
		return isCookwareId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private static boolean isCookwareId(net.minecraft.resources.Identifier id) {
		return "jaymoskitchen".equals(id.getNamespace()) && ("cooking_pot".equals(id.getPath()) || "frying_pan".equals(id.getPath()));
	}

	private static void dropCookware(Level level, BlockPos above) {
		if (isCookwareId(BuiltInRegistries.BLOCK.getKey(level.getBlockState(above).getBlock()))) {
			level.destroyBlock(above, true);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		boolean wasLit = oldState.is(this) && oldState.getValue(LIT);
		if (state.getValue(LIT) && !wasLit) {
			// nothing to burn: the sparks die at once
			level.scheduleTick(pos, this, state.getValue(FUEL) == 0 ? 10 : BURN_TICKS);
		}
		if (!oldState.is(this) && !level.isClientSide()) {
			BonfireBlock.tryMerge(level, pos);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		if (!state.getValue(LIT)) {
			return;
		}
		int fuel = state.getValue(FUEL) - 1;
		BlockState next = state.setValue(FUEL, Math.max(0, fuel));
		if (state.getValue(GRATE) == Grate.STICKS && random.nextFloat() < STICK_GRATE_BURN) {
			next = next.setValue(GRATE, Grate.NONE);
			dropCookware(level, pos.above());
		}
		if (fuel <= 0 || level.isRainingAt(pos.above())) {
			level.setBlock(pos, next.setValue(LIT, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
			return;
		}
		level.setBlock(pos, next, Block.UPDATE_ALL);
		level.scheduleTick(pos, this, BURN_TICKS);
	}
}
