package com.jaymospowers.mixin.portalspread;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nether Portal Spread (Serilum, All Rights Reserved, shipped unchanged, nothing of it copied) looks at the blocks
 * within its spread radius of a portal with Level.getBlockState, which loads, and waits for, a chunk that isn't there
 * yet (a portal near a chunk edge, next to land still generating). Those looks now see air where the chunk isn't
 * loaded, so a block there is neither counted nor spread to this round, and the next round looks again. Only the
 * spreading and counting methods are changed; the one that reads saved portals at world load is not, since air there
 * would forget a portal. Skipped when the mod isn't there or its code has changed.
 */
@Pseudo
@Mixin(targets = "com.serilum.netherportalspread_common_fabric.util.Util", remap = false)
abstract class PortalSpreadLookMixin {
	@WrapOperation(method = {"spreadNextBlock", "isNetherTarget", "spreadNetherToBlock"}, require = 0, remap = false,
		at = @At(value = "INVOKE", remap = false,
			target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private static BlockState jaymospowers$loadedOnly(Level level, BlockPos pos, Operation<BlockState> original) {
		// (getChunkNow: only a chunk already at FULL, never scheduled or waited for; isLoaded only asks for a ticket)
		if (level.isInValidBounds(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return original.call(level, pos);
	}
}
