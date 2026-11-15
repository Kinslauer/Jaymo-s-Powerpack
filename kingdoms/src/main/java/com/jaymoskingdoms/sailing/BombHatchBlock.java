package com.jaymoskingdoms.sailing;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A bomb hatch: an iron-bound trapdoor for a balloon's deck (the medieval picks' vp-bombs). Ashore it is only a trapdoor. In
 * the deck of a ship in the air, used, it lets a powder keg from her hold fall through on what's below ({@code SkyShips#hatch});
 * no keg in her hold, nothing falls. The sky pirates' and patrols' airships are built with one; a player can make one (a dark
 * oak trapdoor and an iron ingot) for their own. Any other trapdoor in a deck stays a trapdoor.
 */
public final class BombHatchBlock extends TrapDoorBlock {
	public BombHatchBlock(BlockBehaviour.Properties properties) {
		super(BlockSetType.DARK_OAK, properties);
	}
}
