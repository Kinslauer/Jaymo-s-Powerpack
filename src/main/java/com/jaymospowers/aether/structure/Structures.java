package com.jaymospowers.aether.structure;

/**
 * The Aether II structures beyond the Sentry Ruins (docs/aether.md, "Aether II structures"): outposts, camps, watchtowers, animal dens,
 * the Veradexian ruins, henge, library and aqueduct, Brexallen ruins, the undercloud mineshaft, irradiated remnants and the infected
 * guardian tree, with the blocks they bring. {@link #init()} is called by {@code Aether.init()}.
 */
public final class Structures {
	/** An axe strips guardian wood (vanilla's stripping only knows vanilla logs). */
	private static net.minecraft.world.level.block.Block stripped(net.minecraft.world.level.block.Block block) {
		if (block == StructureBlocks.GUARDIAN_LOG || block == StructureBlocks.INFECTED_GUARDIAN_LOG) {
			return StructureBlocks.STRIPPED_GUARDIAN_LOG;
		}
		return block == StructureBlocks.GUARDIAN_WOOD || block == StructureBlocks.INFECTED_GUARDIAN_WOOD ? StructureBlocks.STRIPPED_GUARDIAN_WOOD : null;
	}

	private Structures() {
	}

	public static void init() {
		StructureBlocks.init();
		AetherStructure.init();
		OutpostTracker.init();
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(hit.getBlockPos());
			net.minecraft.world.level.block.Block stripped = stripped(state.getBlock());
			net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
			if (stripped == null || player.isShiftKeyDown() || !held.is(net.minecraft.tags.ItemTags.AXES)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (level instanceof net.minecraft.server.level.ServerLevel server && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				server.setBlockAndUpdate(hit.getBlockPos(), stripped.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
					state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)));
				server.playSound(null, hit.getBlockPos(), net.minecraft.sounds.SoundEvents.AXE_STRIP.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
				held.hurtAndBreak(1, sp, hand);
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		});
	}
}
