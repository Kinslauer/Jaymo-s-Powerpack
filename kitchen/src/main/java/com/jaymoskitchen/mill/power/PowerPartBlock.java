package com.jaymoskitchen.mill.power;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Never placed: its states hold the windmill's and waterwheel's pieces as block models (the hub, a length of bare
 * spar, a length of spar with its sail, a paddle on its spokes), for the rotor renderer to draw round and round.
 */
public class PowerPartBlock extends Block {
	public enum Part implements StringRepresentable {
		WINDMILL_HUB, WINDMILL_SPAR, WINDMILL_SAIL, WATERWHEEL_HUB, WATERWHEEL_PADDLE;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	public PowerPartBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PART, Part.WINDMILL_HUB));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}

	public BlockState part(Part part) {
		return defaultBlockState().setValue(PART, part);
	}
}
