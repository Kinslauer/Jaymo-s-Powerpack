package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.7.65) Sandbags: a block of burlap sacks laid in two courses, the top one set over the joints of the bottom, the
 * sacks lying along {@link #AXIS} (so a row of them reads as one wall). Players can make them (string and sand); a
 * realm at war puts them up round its seat ({@link WarAlert}), and those are {@link #RAISED}: the realm's own, taken
 * down again at peace, and they drop nothing.
 */
public final class Sandbags {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty RAISED = BooleanProperty.create("raised");

	public static Block SANDBAG;
	public static Item SANDBAG_ITEM;

	private Sandbags() {
	}

	static final class SandbagBlock extends Block {
		SandbagBlock(BlockBehaviour.Properties properties) {
			super(properties);
			this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(RAISED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS, RAISED);
		}

		/** Laid across the way you face, as you'd build a wall in front of you. */
		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return this.defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
		}

		@Override
		protected BlockState rotate(BlockState state, Rotation rotation) {
			if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
				return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
			}
			return state;
		}
	}

	/** A sandbag the realm puts up, its sacks lying along {@code axis}. */
	static BlockState raised(Direction.Axis axis) {
		return SANDBAG.defaultBlockState().setValue(AXIS, axis).setValue(RAISED, true);
	}

	static boolean isRaised(BlockState state) {
		return state.is(SANDBAG) && state.getValue(RAISED);
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("sandbag"));
		// (heavy with sand: slow to dig through, and it soaks up a blast)
		SANDBAG = Registry.register(BuiltInRegistries.BLOCK, key, new SandbagBlock(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.SAND).strength(1.2F, 6.0F).sound(SoundType.SAND).noOcclusion()));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sandbag"));
		SANDBAG_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(SANDBAG, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.accept(SANDBAG_ITEM));
	}
}
