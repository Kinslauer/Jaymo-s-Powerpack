package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Copper lab furniture (Hunter's Road, patch 6, "T-D2"): four decorative blocks for a hunter's workroom, with no use but to
 * stand in it: a vial rack, a bell jar, a copper scale and a heap of loose papers. They break by hand or pick and drop
 * themselves.
 */
public final class LabFurniture {
	public static Block VIAL_RACK, BELL_JAR, COPPER_SCALE, LOOSE_PAPERS;

	private static final VoxelShape RACK = Shapes.or(Block.box(1, 0, 4, 15, 2, 12), Block.box(1, 2, 4, 15, 10, 12));
	private static final VoxelShape JAR = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(4, 2, 4, 12, 13, 12));
	private static final VoxelShape SCALE = Shapes.or(Block.box(5, 0, 5, 11, 2, 11), Block.box(7, 2, 7, 9, 12, 9), Block.box(1, 12, 6, 15, 13, 10));
	private static final VoxelShape PAPERS = Block.box(2, 0, 2, 14, 2, 14);

	private LabFurniture() {
	}

	static void init() {
		VIAL_RACK = block("vial_rack", RACK, SoundType.COPPER, MapColor.COLOR_ORANGE);
		BELL_JAR = block("bell_jar", JAR, SoundType.GLASS, MapColor.NONE);
		COPPER_SCALE = block("copper_scale", SCALE, SoundType.COPPER, MapColor.COLOR_ORANGE);
		LOOSE_PAPERS = block("loose_papers", PAPERS, SoundType.WOOL, MapColor.SAND);
	}

	private static Block block(String id, VoxelShape shape, SoundType sound, MapColor colour) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Furniture(BlockBehaviour.Properties.of().mapColor(colour)
			.strength(0.8F).sound(sound).noOcclusion().setId(key), shape));
		Alchemy.item(id, p -> new BlockItem(block, p.useBlockDescriptionPrefix()));
		return block;
	}

	/** A block that is only its shape. */
	private static final class Furniture extends Block {
		private final VoxelShape shape;

		Furniture(Block.Properties properties, VoxelShape shape) {
			super(properties);
			this.shape = shape;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return this.shape;
		}
	}
}
