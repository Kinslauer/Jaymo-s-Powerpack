package com.jaymospowers.mixin.portalspread;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nether Portal Spread (Serilum, All Rights Reserved, shipped unchanged, nothing of it copied) checks each new portal
 * from its level tick. The check walks the blocks around the portal with Level.getBlockState, which loads, and waits
 * for, any chunk that isn't there yet. A portal in land that is still generating (a player teleported far outside the
 * pre-generated area) froze the server thread until the watchdog killed the server. A portal whose surroundings aren't
 * all loaded is now put back at the end of the mod's queue instead of being checked, and is looked at again when its
 * land is there. Everything else it does is left alone. Skipped when the mod isn't there or its code has changed.
 */
@Pseudo
@Mixin(targets = "com.serilum.netherportalspread_common_fabric.events.SpreadEvent", remap = false)
abstract class PortalSpreadQueueMixin {
	// The check follows a portal's frame at most one portal width (21 blocks) from the block it was given, and looks 3 blocks around it.
	private static final int REACH = 25;

	@Shadow(remap = false)
	private static HashMap<Level, CopyOnWriteArrayList<BlockPos>> portals_to_process;

	@WrapOperation(method = "onWorldTick", require = 0, remap = false,
		at = @At(value = "INVOKE", remap = false,
			target = "Lcom/serilum/netherportalspread_common_fabric/util/Util;validatePortalAndAdd(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
	private static void jaymospowers$onlyWhereLoaded(Level level, BlockPos pos, Operation<Void> original) {
		for (int cx = (pos.getX() - REACH) >> 4; cx <= (pos.getX() + REACH) >> 4; cx++) {
			for (int cz = (pos.getZ() - REACH) >> 4; cz <= (pos.getZ() + REACH) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					// onWorldTick removes the head of this queue right after the check, so add it to the tail first.
					portals_to_process.get(level).add(pos);
					return;
				}
			}
		}
		original.call(level, pos);
	}
}
