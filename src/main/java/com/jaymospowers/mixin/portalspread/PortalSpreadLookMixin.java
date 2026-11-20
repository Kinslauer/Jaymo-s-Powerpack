package com.jaymospowers.mixin.portalspread;

import com.jaymospowers.compat.PortalSpreadCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nether Portal Spread (Serilum, All Rights Reserved, shipped unchanged, nothing of it copied) spreads from each known
 * portal now and then (spreadNextBlock). It first asks Level.hasChunk for the portal's chunk and skips the round when that
 * is false, then reads the portal's block (gone means the portal is forgotten for good) and every block within its spread
 * radius with Level.getBlockState, which loads, and waits for, a chunk that isn't ready. hasChunk only asks for a ticket,
 * so it is true while the chunk is still loading or generating: the reads then held the server thread. That first
 * question now answers true only when every chunk within the radius is already at FULL (PortalSpreadCompat), so the
 * round is skipped, as the mod already does for an unloaded portal, and nothing is read until its land is there; the
 * portal is never mistaken for broken. Skipped when the mod isn't there or its code has changed.
 */
@Pseudo
@Mixin(targets = "com.serilum.netherportalspread_common_fabric.util.Util", remap = false)
abstract class PortalSpreadLookMixin {
	@WrapOperation(method = "spreadNextBlock", require = 0, remap = false,
		at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/world/level/Level;hasChunk(II)Z"))
	private static boolean jaymospowers$allReady(Level level, int chunkX, int chunkZ, Operation<Boolean> original,
		@Local(argsOnly = true) BlockPos portal) {
		return original.call(level, chunkX, chunkZ) && PortalSpreadCompat.ready(level, portal, PortalSpreadCompat.spreadRadius());
	}
}
