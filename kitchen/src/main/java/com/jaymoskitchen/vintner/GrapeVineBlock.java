package com.jaymoskitchen.vintner;

import com.jaymoskitchen.season.Season;
import com.jaymoskitchen.season.Seasons;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A vine on a trellis (our own grape crop). Sympathy: vines fruit on last year's wood, so a vine pruned with shears in
 * winter bears {@link #FULL_YIELD} bunches, and one left alone bears one fewer for each bunch-crop it has gone unpruned
 * ({@link #NEGLECT}), down to one. Feedback: ripe bunches left out in the open are stripped by birds, and a net
 * (string) keeps them off and adds {@link #NET_BONUS} to the picking. Stewardship: pruned, netted vines give the most;
 * left alone, a vine still gives something. The crimson and warped vines grow only near lava, on the Nether's shores,
 * and are pruned at any time (no seasons there).
 */
public class GrapeVineBlock extends VegetationBlock {
	public enum Kind {
		GRAPE, CRIMSON, WARPED;

		public boolean nether() {
			return this != GRAPE;
		}
	}

	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	/** Ripe: the last stage. */
	public static final int RIPE = 3;
	/** What a vine drops back to after picking (it fruits again on the same wood). */
	public static final int REGROW_AGE = 0;
	public static final int MAX_NEGLECT = 3;
	public static final BooleanProperty PRUNED = BooleanProperty.create("pruned");
	public static final BooleanProperty NETTED = BooleanProperty.create("netted");
	/** Picked this year: the vine stays bare until it has been through a winter (it fruits on last year's wood). */
	public static final BooleanProperty BARE = BooleanProperty.create("bare");
	public static final IntegerProperty NEGLECT = IntegerProperty.create("neglect", 0, MAX_NEGLECT);

	/** Bunches a pruned vine bears, and what a net adds. */
	public static final int FULL_YIELD = 3, NET_BONUS = 1;
	/** A growth step comes on 1 in this many random ticks; light needed in the Overworld. */
	private static final int GROW_ODDS = 12, GROW_LIGHT = 9;
	/** Chance a random tick of a ripe, unnetted vine under the open sky lets the birds at it (1 in this). */
	private static final int BIRD_ODDS = 8;
	/** Lands without winters (and the Nether): a bare vine rests for 1 in this many random ticks instead. */
	private static final int REST_ODDS = 600;
	/** How far from lava the Nether vines still grow. */
	private static final int LAVA_REACH = 3;
	private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

	public final Kind kind;

	public GrapeVineBlock(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		// worldgen places the default state: a wild vine, ripe (planting one starts it young, see getStateForPlacement)
		registerDefaultState(stateDefinition.any().setValue(AGE, RIPE).setValue(PRUNED, false).setValue(NETTED, false).setValue(BARE, false).setValue(NEGLECT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE, PRUNED, NETTED, BARE, NEGLECT);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.setValue(AGE, 0);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		if (kind.nether()) {
			return state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.NYLIUM) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
		}
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SUPPORTS_CROPS);
	}

	/** The grapes this vine bears. */
	public Item grapes() {
		return switch (kind) {
			case GRAPE -> Vintner.GRAPES;
			case CRIMSON -> Vintner.CRIMSON_GRAPES;
			case WARPED -> Vintner.WARPED_GRAPES;
		};
	}

	/** Bunches a picking gives from this vine as it stands. */
	public static int bunches(BlockState state) {
		int count = state.getValue(PRUNED) ? FULL_YIELD : Math.max(1, FULL_YIELD - state.getValue(NEGLECT));
		return state.getValue(NETTED) ? count + NET_BONUS : count;
	}

	// --- being tended ---

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (held.is(Items.SHEARS) || held.is(ConventionalItemTags.SHEAR_TOOLS)) {
			if (state.getValue(PRUNED) || kind == Kind.GRAPE && Seasons.of(level) != Season.WINTER) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			level.setBlock(pos, state.setValue(PRUNED, true), Block.UPDATE_CLIENTS);
			level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
			if (level instanceof ServerLevel server) {
				held.hurtAndBreak(1, player, hand);
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					6, 0.25, 0.25, 0.25, 0.001D);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(Items.STRING) && !state.getValue(NETTED) && !kind.nether()) {
			level.setBlock(pos, state.setValue(NETTED, true), Block.UPDATE_CLIENTS);
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.2F);
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(AGE) < RIPE || state.getValue(BARE)) {
			return InteractionResult.PASS;
		}
		popResource(level, pos, new ItemStack(grapes(), bunches(state)));
		int neglect = state.getValue(PRUNED) ? 0 : Math.min(MAX_NEGLECT, state.getValue(NEGLECT) + 1);
		level.setBlock(pos, state.setValue(AGE, REGROW_AGE).setValue(BARE, true).setValue(PRUNED, false).setValue(NEGLECT, neglect), Block.UPDATE_CLIENTS);
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
		return InteractionResult.SUCCESS;
	}

	// --- growing ---

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			return;
		}
		if (state.getValue(BARE)) {
			boolean winters = !kind.nether() && Seasons.temperate(level.getBiome(pos).value().getBaseTemperature());
			if (winters ? Seasons.of(level) == Season.WINTER : random.nextInt(REST_ODDS) == 0) {
				level.setBlock(pos, state.setValue(BARE, false), Block.UPDATE_CLIENTS);
			}
			return;
		}
		int age = state.getValue(AGE);
		if (age == RIPE) {
			if (!kind.nether() && !state.getValue(NETTED) && level.canSeeSky(pos.above()) && level.getOverworldClockTime() % 24000L < 12000L
				&& random.nextInt(BIRD_ODDS) == 0 && !com.jaymoskitchen.farmwater.Scarecrows.guarded(level, pos)) {
				// the birds got the bunches: they ripen again
				level.setBlock(pos, state.setValue(AGE, RIPE - 1), Block.UPDATE_CLIENTS);
				level.playSound(null, pos, SoundEvents.BAT_TAKEOFF, SoundSource.BLOCKS, 0.6F, 1.6F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
					5, 0.25, 0.2, 0.25, 0.001D);
			}
			return;
		}
		if (!grows(level, pos, age) || random.nextInt(GROW_ODDS) != 0) {
			return;
		}
		level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
	}

	/** Whether the place and the time of year let this vine take its next step. */
	private boolean grows(ServerLevel level, BlockPos pos, int age) {
		if (kind.nether()) {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-LAVA_REACH, -2, -LAVA_REACH), pos.offset(LAVA_REACH, 1, LAVA_REACH))) {
				if (level.getFluidState(p).is(FluidTags.LAVA)) {
					return true;
				}
			}
			return false;
		}
		if (level.getRawBrightness(pos, 0) < GROW_LIGHT) {
			return false;
		}
		if (!Seasons.temperate(level.getBiome(pos).value().getBaseTemperature())) {
			return true;
		}
		Season season = Seasons.of(level);
		// it sleeps through the winter, shoots in spring, and the bunches ripen in summer and autumn
		return season != Season.WINTER && (age < RIPE - 1 || season != Season.SPRING);
	}
}
