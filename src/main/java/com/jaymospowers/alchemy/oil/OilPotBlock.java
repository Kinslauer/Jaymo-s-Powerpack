package com.jaymospowers.alchemy.oil;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The oil pot (Hunter's Road, patch 7, "T-C1"): a squat clay pot that stands where it is put and holds one kind of oil
 * ({@link #OIL}), up to {@link #FULL} measures ({@link #FILL}), as good as the poorest flask poured into it ({@link #GRADE}).
 * <ul>
 * <li>Pour a flask in ({@link #POUR} measures; a flask of another oil does not mix) and a glass bottle comes back.</li>
 * <li>Use a blade (or, for a mending oil, a tool) on it to <b>dip</b> it: the oil goes on, one measure down ({@link Oils#coat}).</li>
 * <li>Use a sash or bandolier on it to fill it ({@link OilSashItem}); use a glass bottle on it to draw a flask off
 * ({@link #POUR} measures).</li>
 * <li>The pot <b>stays and refills</b>: while it holds any oil it slowly makes up measures on its own, but only as far as
 * {@link #SELF_REFILL_TO}; the rest has to be poured in. Broken full, it spills ({@link OilSlickBlock}).</li>
 * </ul>
 */
public final class OilPotBlock extends Block {
	/** Measures in a full pot; measures a flask pours in or a bottle draws off; measures a dip takes. */
	public static final int FULL = 4, POUR = 2, DIP = 1;
	/** A random tick in this many makes up a measure, and the most it makes up to by itself. */
	public static final int REFILL_ODDS = 4, SELF_REFILL_TO = 2;
	/** Throws of a sash one measure of a pot fills. */
	public static final int CHARGES_PER_MEASURE = 4;

	public static final EnumProperty<OilKind> OIL = EnumProperty.create("oil", OilKind.class);
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, FULL);
	/** The grade (1 to 3) of what is in it: the poorest poured since it was last empty. */
	public static final IntegerProperty GRADE = IntegerProperty.create("grade", 1, 3);

	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 10, 14);

	public static Block POT;

	private OilPotBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(OIL, OilKind.POISON).setValue(FILL, 0).setValue(GRADE, 1));
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("oil_pot"));
		POT = Registry.register(BuiltInRegistries.BLOCK, key, new OilPotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
			.strength(0.8F).sound(SoundType.DECORATED_POT).noOcclusion().randomTicks().setId(key)));
		Alchemy.item("oil_pot", p -> new BlockItem(POT, p.useBlockDescriptionPrefix()));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OIL, FILL, GRADE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		OilKind flask = OilKind.of(stack);
		boolean sash = stack.getItem() instanceof OilSashItem;
		int fill = state.getValue(FILL);
		OilKind held = state.getValue(OIL);
		boolean dip = fill > 0 && Oils.fits(held, stack);
		boolean draw = stack.is(Items.GLASS_BOTTLE) && fill >= POUR;
		boolean pour = flask != null && fill < FULL && (fill == 0 || flask == held);
		if (!(pour || dip || draw || sash && fill > 0) || flask != null && !pour) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (pour) {
			int grade = Math.max(1, Math.min(3, Oils.tier(stack)));
			level.setBlockAndUpdate(pos, state.setValue(OIL, flask).setValue(FILL, Math.min(FULL, fill + POUR))
				.setValue(GRADE, fill == 0 ? grade : Math.min(grade, state.getValue(GRADE))));
			stack.consume(1, player);
			if (!player.hasInfiniteMaterials()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
			}
			server.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
		} else if (sash) {
			int used = OilSashItem.fill(stack, held, state.getValue(GRADE), fill * CHARGES_PER_MEASURE);
			if (used <= 0) {
				return InteractionResult.PASS;
			}
			int measures = (used + CHARGES_PER_MEASURE - 1) / CHARGES_PER_MEASURE;
			level.setBlockAndUpdate(pos, state.setValue(FILL, Math.max(0, fill - measures)));
			server.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 0.9F);
		} else if (draw) {
			stack.consume(1, player);
			ItemStack out = Oils.flask(held, state.getValue(GRADE));
			player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
			level.setBlockAndUpdate(pos, state.setValue(FILL, fill - POUR));
			server.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.1F);
		} else if (Oils.coat(sp, stack, held, state.getValue(GRADE))) {
			level.setBlockAndUpdate(pos, state.setValue(FILL, fill - DIP));
		} else {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int fill = state.getValue(FILL);
		if (fill > 0 && fill < SELF_REFILL_TO && random.nextInt(REFILL_ODDS) == 0) {
			level.setBlockAndUpdate(pos, state.setValue(FILL, fill + 1));
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && state.getValue(FILL) > 0 && state.getValue(OIL).group != OilKind.Group.SPLASH) {
			OilSlickBlock.spill(server, pos, 1);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
