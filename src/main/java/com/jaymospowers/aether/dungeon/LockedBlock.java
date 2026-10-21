package com.jaymospowers.aether.dungeon;

import net.minecraft.world.level.block.Block;

/** A block of a dungeon wall that cannot be broken: it wears the look of the stone it stands for (one block id per look). */
public class LockedBlock extends Block {
	public LockedBlock(Properties properties) {
		super(properties);
	}
}
