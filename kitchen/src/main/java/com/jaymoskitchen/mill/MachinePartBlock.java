package com.jaymoskitchen.mill;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Never placed: its states just hold the machines' moving parts as block models (the hand grindstone's turning
 * stone, the chopper's blade, the press plate, the mill's flour), for the machine renderer to draw where they've
 * got to.
 */
public class MachinePartBlock extends Block {
	public enum Part implements StringRepresentable {
		HAND_GRINDSTONE_CENTER, CHOPPER_BLADE, PRESS_TOP, GRINDSTONE_FLOUR;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	public MachinePartBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PART, Part.HAND_GRINDSTONE_CENTER));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}

	public BlockState part(Part part) {
		return defaultBlockState().setValue(PART, part);
	}
}
