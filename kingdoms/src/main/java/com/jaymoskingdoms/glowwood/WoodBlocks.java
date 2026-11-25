package com.jaymoskingdoms.glowwood;

import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Public constructors for the wood-set blocks whose vanilla constructors are protected (ported from Spore & Stem, Apache-2.0). */
public final class WoodBlocks {
	private WoodBlocks() {
	}

	public static class Stairs extends StairBlock {
		public Stairs(BlockState base, Properties properties) {
			super(base, properties);
		}
	}

	public static class Door extends DoorBlock {
		public Door(BlockSetType type, Properties properties) {
			super(type, properties);
		}
	}

	public static class Trapdoor extends TrapDoorBlock {
		public Trapdoor(BlockSetType type, Properties properties) {
			super(type, properties);
		}
	}

	public static class Button extends ButtonBlock {
		public Button(BlockSetType type, int ticksToStayPressed, Properties properties) {
			super(type, ticksToStayPressed, properties);
		}
	}

	public static class PressurePlate extends PressurePlateBlock {
		public PressurePlate(BlockSetType type, Properties properties) {
			super(type, properties);
		}
	}
}
