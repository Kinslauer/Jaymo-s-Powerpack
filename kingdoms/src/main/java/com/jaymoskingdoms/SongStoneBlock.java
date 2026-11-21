package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A song stone (a medieval deco pick): the stone in a capital's square where the realm's firsts are cut ({@link Firsts}):
 * the first to slay each great foe, the first to found a realm, the first to reach a far world. Using it opens what is
 * cut on it, and a villager standing by names one of the names. Its face turns the way the square's plan sets it.
 */
public final class SongStoneBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);
	public static Block STONE;

	public SongStoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("song_stone"));
		STONE = Registry.register(BuiltInRegistries.BLOCK, key, new SongStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.strength(3.0F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("song_stone"));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(STONE, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(item));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) {
			Firsts.readStone(sp, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
