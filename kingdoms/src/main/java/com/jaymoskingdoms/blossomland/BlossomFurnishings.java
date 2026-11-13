package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.mixin.tweaks.PoiTypeAccessor;
import com.jaymoskingdoms.mixin.tweaks.PoiTypesInvoker;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What stands inside a blossom house (docs/BlossomLands-Plan.md, patch 3; ideas from Andy's Sengoku Jidai picks, the
 * blocks and art ours), each a vanilla block in everything but looks, so nothing else has to learn it:
 * <ul>
 * <li>the <b>tansu</b>, a chest of drawers: a barrel (same store, same block entity) that opens toward whoever set it;</li>
 * <li>the <b>futon</b>: a bed three sixteenths high, drawn from its model, a home to villagers as any bed is;</li>
 * <li>the <b>sunken hearth</b>: a campfire in a low wooden frame (cooks four foods, takes Kitchen's spit, smokes), its
 * coals banked under ash, so stepping over it burns nobody;</li>
 * <li>the <b>sliding door</b>: a door that slides along its wall toward its hinge side instead of swinging, opened by
 * villagers as a wooden door is (the wooden doors tag, {@code Doorways});</li>
 * <li><b>lacquer sap</b>: a bowl held to the bark of a living cherry tree scores that log (it is left stripped) and
 * fills. Each log gives once, so a grove gives what it has until more is grown. Lacquered planks are made with it.</li>
 * </ul>
 * Models, textures, recipes, loot and names come from {@code kingdoms/tools/gen_blossom_blocks.py}.
 */
public final class BlossomFurnishings {
	/** How high the futon and the hearth's frame stand (sixteenths of a block). */
	static final int FUTON_HEIGHT = 3, HEARTH_HEIGHT = 4;
	/** Light of the hearth's coals. */
	static final int HEARTH_LIGHT = 13;
	/** How much of an open sliding door still stands in its own block (sixteenths). */
	static final int DOOR_SLIVER = 3;
	/** How far round and above a scored log leaves are looked for: a living tree, not a post or a beam. */
	static final int LEAVES_OUT = 3, LEAVES_UP = 8;

	public static Block TANSU, FUTON, HEARTH, SLIDING_DOOR;
	public static Item SAP;

	private BlossomFurnishings() {
	}

	static void register() {
		TANSU = BlossomBlocks.block("tansu", new BarrelBlock(BlossomBlocks.copy("tansu", Blocks.BARREL).mapColor(MapColor.COLOR_BROWN)),
			BlossomBlocks.PLANK_BURN, BlossomBlocks.PLANK_SPREAD);
		BlockEntityTypes.BARREL.addValidBlock(TANSU);
		FUTON = BlossomBlocks.block("futon", new Futon(BlossomBlocks.props("futon").mapColor(MapColor.WOOL).sound(SoundType.WOOL)
			.strength(0.2F).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)), BlossomBlocks.PAPER_BURN, BlossomBlocks.PAPER_SPREAD);
		HEARTH = BlossomBlocks.block("sunken_hearth", new Hearth(BlossomBlocks.props("sunken_hearth").mapColor(MapColor.WOOD).sound(SoundType.WOOD)
			.strength(2.0F).noOcclusion().lightLevel(state -> state.getValue(CampfireBlock.LIT) ? HEARTH_LIGHT : 0)), 0, 0);
		BlockEntityTypes.CAMPFIRE.addValidBlock(HEARTH);
		SLIDING_DOOR = BlossomBlocks.block("sliding_door", new SlidingDoor(BlossomBlocks.props("sliding_door").mapColor(MapColor.SNOW)
			.sound(SoundType.WOOD).strength(1.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)),
			BlossomBlocks.PAPER_BURN, BlossomBlocks.PAPER_SPREAD, DoubleHighBlockItem::new);
		ResourceKey<Item> sap = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("lacquer_sap"));
		SAP = Registry.register(BuiltInRegistries.ITEM, sap, new Item(new Item.Properties().setId(sap).stacksTo(16).craftRemainder(Items.BOWL)));
		BlossomBlocks.ITEMS.add(SAP);
		homes();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> tap(player, level, player.getItemInHand(hand), hit.getBlockPos()));
	}

	/** A futon's head is a home to villagers, as a bed's is (vanilla lists its own beds and no others). */
	private static void homes() {
		Holder<PoiType> home = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.HOME);
		Set<BlockState> heads = new HashSet<>();
		for (BlockState state : FUTON.getStateDefinition().getPossibleStates()) {
			if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
				heads.add(state);
			}
		}
		Set<BlockState> all = new HashSet<>(home.value().matchingStates());
		all.addAll(heads);
		((PoiTypeAccessor)(Object)home.value()).jaymoskingdoms$matchingStates(Set.copyOf(all));
		PoiTypesInvoker.jaymoskingdoms$registerBlockStates(home, heads);
	}

	/** A bowl held to a living cherry tree's bark: the log is scored (left stripped) and the bowl fills with sap. */
	private static InteractionResult tap(Player player, Level level, ItemStack held, BlockPos pos) {
		if (player.isSpectator() || !held.is(Items.BOWL)) {
			return InteractionResult.PASS;
		}
		BlockState log = level.getBlockState(pos);
		if (!log.is(Blocks.CHERRY_LOG) || !living(level, pos)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		level.setBlock(pos, Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, log.getValue(RotatedPillarBlock.AXIS)),
			Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
		held.consume(1, player);
		ItemStack sap = new ItemStack(SAP);
		if (!player.getInventory().add(sap)) {
			player.drop(sap, false, Prediction.SERVER_ONLY);
		}
		return InteractionResult.SUCCESS;
	}

	/** Leaves that grew (not ones a player set) stand close above or beside {@code pos}. */
	private static boolean living(Level level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-LEAVES_OUT, 0, -LEAVES_OUT), pos.offset(LEAVES_OUT, LEAVES_UP, LEAVES_OUT))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	/** A bed laid on the floor: no colour, no block entity, drawn by its model (as {@code InfirmaryCot} is). */
	static final class Futon extends BedBlock {
		private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, FUTON_HEIGHT, 16.0);

		Futon(BlockBehaviour.Properties properties) {
			super(DyeColor.WHITE, properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.MODEL;
		}
	}

	/** A campfire in a low frame whose coals lie under ash: it cooks and smokes, and burns nobody who steps over it. */
	static final class Hearth extends CampfireBlock {
		private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEARTH_HEIGHT, 16.0);

		Hearth(BlockBehaviour.Properties properties) {
			super(true, 0, properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean inside) {
		}
	}

	/**
	 * A door that slides: shut, it is a vanilla door's panel; open, the panel has run along the wall toward its hinge
	 * side, and only a {@link #DOOR_SLIVER} of it still stands in the doorway.
	 */
	static final class SlidingDoor extends DoorBlock {
		/** The open door's sliver, by facing (2D index) and hinge. */
		private static final VoxelShape[][] OPEN_SHAPE = new VoxelShape[4][2];

		static {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				for (DoorHingeSide hinge : DoorHingeSide.values()) {
					Direction to = hinge == DoorHingeSide.RIGHT ? facing.getClockWise() : facing.getCounterClockWise();
					// the shut panel lies against the side the door faces away from; the sliver is its end on the hinge side
					double x0 = facing == Direction.WEST ? 13 : 0, x1 = facing == Direction.EAST ? 3 : 16;
					double z0 = facing == Direction.NORTH ? 13 : 0, z1 = facing == Direction.SOUTH ? 3 : 16;
					switch (to) {
						case EAST -> x0 = 16 - DOOR_SLIVER;
						case WEST -> x1 = DOOR_SLIVER;
						case SOUTH -> z0 = 16 - DOOR_SLIVER;
						default -> z1 = DOOR_SLIVER;
					}
					OPEN_SHAPE[facing.get2DDataValue()][hinge.ordinal()] = Block.box(x0, 0, z0, x1, 16, z1);
				}
			}
		}

		SlidingDoor(BlockBehaviour.Properties properties) {
			super(BlockSetType.BAMBOO, properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			if (!state.getValue(OPEN)) {
				return super.getShape(state, level, pos, context);
			}
			return OPEN_SHAPE[state.getValue(FACING).get2DDataValue()][state.getValue(HINGE).ordinal()];
		}
	}
}
