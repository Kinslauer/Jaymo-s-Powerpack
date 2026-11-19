package com.jaymoskitchen.farm;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "the dung should drop on ground and be visible and pile high like offal and rocks do", and "can different land
 * animals drop different piles? so rangers have a new way to track?"
 * <p>
 * Droppings: 1-{@link #MAX} pats in one block, spreading out first and then heaping up. Each {@link Kind} looks like the
 * real thing (cow pats, horse apples, deer and sheep pellets, pig logs, bird splashes, a hunter's scat full of fur), so
 * knowing them tells you what passed. They dry with time ({@link #AGE}: fresh, crusted, dry), and dry droppings out under
 * the open sky weather away a pat at a time; a heap under a roof keeps. Animals leave theirs here ({@link Manure});
 * manure in hand sets down a plain dung heap or adds to whatever is there, as offal and loose rocks do. Walk-through,
 * gone with a punch (a manure a pat), a right click takes one back. Not an
 * {@link com.jaymoskitchen.butchery.OffalPileBlock}, so scavengers leave it alone.
 * <p>
 * <b>Reading spoor:</b> someone who knows how ({@link #setReader}; Jaymo's Kingdoms' Rangers and Read the Ground) who
 * sneaks and right-clicks droppings with an empty hand is told what left them and how old they are, and the animal that
 * left them shows its trail to them ({@link #setTrail}), if it's still about.
 */
public class ManurePileBlock extends Block {
	public static final int MAX = 8;
	public static final IntegerProperty PATS = IntegerProperty.create("pats", 1, MAX);
	/** 0 fresh, 1 crusted over (hours old), 2 dried out (a day or more). */
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	/** A random tick (about one a minute) dries fresh droppings to crusted 1 in this many, crusted to dry 1 in this many. */
	static final int CRUST_ONE_IN = 4, DRY_ONE_IN = 18;
	/** A random tick weathers a pat off dry droppings under open sky 1 in this many. */
	static final int WEATHER_ONE_IN = 35;
	/** Spoor remembered (what left it, when) is forgotten after this long (ticks), and no more than this many kept. */
	static final long SPOOR_KEEP = 24000L * 4;
	static final int SPOOR_MOST = 4000;

	/** What left the droppings, by their look. HEAP is a plain dung heap, as a player sets one down. */
	public enum Kind implements StringRepresentable {
		HEAP, PATS, APPLES, PELLETS, LOGS, BIRD, SCAT;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** Outlines by kind and size (made by tools/gen_manure_pile.py, which prints this table). */
	private static final VoxelShape[][] SHAPES = {
		// SHAPES-BEGIN
		{Block.box(5.0, 0.0, 5.0, 11.0, 3.0, 11.0), Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0), Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0), Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0), Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0), Block.box(4.0, 0.0, 4.0, 12.0, 7.0, 12.0), Block.box(3.0, 0.0, 3.0, 13.0, 7.0, 13.0), Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0)},
		{Block.box(5.0, 0.0, 5.0, 11.0, 2.0, 11.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0)},
		{Block.box(5.0, 0.0, 5.0, 11.0, 3.0, 11.0), Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0), Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 5.0, 15.0), Block.box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0), Block.box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0), Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0)},
		{Block.box(5.0, 0.0, 5.0, 11.0, 1.0, 11.0), Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0)},
		{Block.box(5.0, 0.0, 5.0, 11.0, 3.0, 11.0), Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 5.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 5.0, 15.0), Block.box(1.0, 0.0, 1.0, 15.0, 7.0, 15.0)},
		{Block.box(5.0, 0.0, 5.0, 11.0, 2.0, 11.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0)},
		{Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0), Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0), Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0), Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0), Block.box(3.0, 0.0, 3.0, 13.0, 4.0, 13.0), Block.box(3.0, 0.0, 3.0, 13.0, 4.0, 13.0), Block.box(3.0, 0.0, 3.0, 13.0, 4.0, 13.0), Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0)},
		// SHAPES-END
	};

	/** Who can read spoor; nobody unless a mod that teaches it says so. */
	private static Predicate<Player> reader = player -> false;
	/** Shows a reader the trail of the animal that left spoor. */
	private static BiConsumer<ServerPlayer, Entity> trail = (player, maker) -> {
	};
	/** What left the droppings at each spot, and when (kept in memory only: a restart forgets it). */
	private static final Map<ServerLevel, Map<BlockPos, Spoor>> SPOOR = new WeakHashMap<>();

	private record Spoor(UUID maker, EntityType<?> type, long at) {
	}

	public ManurePileBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PATS, 1).setValue(AGE, 0).setValue(KIND, Kind.HEAP));
	}

	/** Who can read spoor (Jaymo's Kingdoms sets it, by reflection). */
	public static void setReader(Predicate<Player> reads) {
		reader = reads;
	}

	/** How a reader is shown the trail of what left the spoor (Jaymo's Kingdoms sets it, by reflection). */
	public static void setTrail(BiConsumer<ServerPlayer, Entity> shows) {
		trail = shows;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PATS, AGE, KIND);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape[] sizes = SHAPES[Math.min(state.getValue(KIND).ordinal(), SHAPES.length - 1)];
		return sizes[state.getValue(PATS) - 1];
	}

	/**
	 * One more pat of {@code kind}, left by {@code maker}, at {@code pos}: new droppings on bare ground, or fresh ones on top
	 * of what's there (which keeps its look). False if there's no room (a full heap, water, a block in the way, or no firm
	 * ground under it).
	 */
	public boolean addPat(ServerLevel level, BlockPos pos, Kind kind, @Nullable Entity maker) {
		BlockState there = level.getBlockState(pos);
		BlockState now;
		if (there.is(this)) {
			int pats = there.getValue(PATS);
			if (pats >= MAX) {
				return false;
			}
			now = there.setValue(PATS, pats + 1).setValue(AGE, 0);
		} else {
			now = this.defaultBlockState().setValue(KIND, kind);
			if (!there.canBeReplaced() || !there.getFluidState().isEmpty() || !now.canSurvive(level, pos)) {
				return false;
			}
		}
		level.setBlock(pos, now, Block.UPDATE_ALL);
		if (maker != null) {
			remember(level, pos, maker);
		}
		return true;
	}

	private static void remember(ServerLevel level, BlockPos pos, Entity maker) {
		Map<BlockPos, Spoor> spoor = SPOOR.computeIfAbsent(level, l -> new HashMap<>());
		long now = level.getGameTime();
		if (spoor.size() >= SPOOR_MOST) {
			for (Iterator<Spoor> it = spoor.values().iterator(); it.hasNext();) {
				if (now - it.next().at > SPOOR_KEEP / 4) {
					it.remove();
				}
			}
			if (spoor.size() >= SPOOR_MOST) {
				spoor.clear();
			}
		}
		spoor.put(pos.immutable(), new Spoor(maker.getUUID(), maker.getType(), now));
	}

	/** One pat fewer at {@code pos} (the droppings gone with the last). */
	public static void takeOne(Level level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(PATS)) {
			return;
		}
		int left = state.getValue(PATS) - 1;
		level.setBlock(pos, left > 0 ? state.setValue(PATS, left) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// more manure in hand goes on the heap (the item's own placing does it, through canBeReplaced)
		if (stack.is(this.asItem()) && state.getValue(PATS) < MAX) {
			return InteractionResult.PASS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown() && reader.test(player)) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer reading) {
				read(server, pos, state, reading);
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			takeOne(level, pos, state);
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(this.asItem()), Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, this.soundType.getPlaceSound(), SoundSource.PLAYERS, 0.4F, 0.6F + level.getRandom().nextFloat() * 0.3F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** A reader crouches over the droppings: what left them and how old they are, and that animal's trail if it's about. */
	private static void read(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		Map<BlockPos, Spoor> known = SPOOR.get(level);
		Spoor spoor = known == null ? null : known.get(pos);
		if (spoor != null && level.getGameTime() - spoor.at > SPOOR_KEEP) {
			spoor = null;
		}
		Component what = spoor != null ? Component.translatable("message.jaymoskitchen.spoor.of", spoor.type.getDescription())
			: Component.translatable("message.jaymoskitchen.spoor." + state.getValue(KIND).getSerializedName());
		Component age = Component.translatable("message.jaymoskitchen.spoor.age." + state.getValue(AGE));
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spoor", what, age));
		level.playSound(null, pos, SoundEvents.MUD_STEP, SoundSource.PLAYERS, 0.4F, 0.7F);
		Entity maker = spoor == null ? null : level.getEntity(spoor.maker);
		if (maker != null && maker.isAlive()) {
			trail.accept(player, maker);
		}
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(PATS) < MAX
			|| super.canBeReplaced(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		return there.is(this) ? there.setValue(PATS, Math.min(MAX, there.getValue(PATS) + 1)).setValue(AGE, 0) : this.defaultBlockState();
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	/** Droppings dry; dry ones out in the weather crumble away a pat at a time. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		if (age == 0 && random.nextInt(CRUST_ONE_IN) == 0 || age == 1 && random.nextInt(DRY_ONE_IN) == 0) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		} else if (age == 2 && random.nextInt(WEATHER_ONE_IN) == 0 && level.canSeeSky(pos)) {
			takeOne(level, pos, state);
		}
	}

	/** Flies over fresh droppings, fewer once crusted, none once dry; more over a big heap. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE), pats = state.getValue(PATS);
		if (age < 2 && random.nextInt(10 + age * 10) < 2 + pats / 2) {
			double top = this.getShape(state, level, pos, CollisionContext.empty()).max(Direction.Axis.Y);
			level.addParticle(ParticleTypes.MYCELIUM, pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + top + 0.1 + random.nextDouble() * 0.3,
				pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}
}
