package com.jaymospowers.tinkering.forge;

import com.jaymospowers.tinkering.kiln.Kiln;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** The forge's blocks: {@link BellowsBlock} and {@link ForgeHearthBlock}. */
public final class Forge {
	public static Block BELLOWS, FORGE_HEARTH;

	private Forge() {
	}

	public static void register() {
		BELLOWS = Kiln.blockWithTooltip("bellows", BellowsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
		FORGE_HEARTH = Kiln.blockWithTooltip("forge_hearth", ForgeHearthBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0F, 6.0F).requiresCorrectToolForDrops()
			.lightLevel(state -> state.getValue(ForgeHearthBlock.LIT) ? ForgeHearthBlock.HEARTH_LIGHT : 0));
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(BELLOWS);
			output.accept(FORGE_HEARTH);
		});
	}
}
