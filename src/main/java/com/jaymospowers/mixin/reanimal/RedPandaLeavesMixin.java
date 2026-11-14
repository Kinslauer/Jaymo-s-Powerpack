package com.jaymospowers.mixin.reanimal;

import com.jaymospowers.core.Loaded;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re:Animal (all rights reserved, Shatterbyte; shipped unchanged, nothing of it copied) has its red panda look for leaves
 * all round it, block by block, even while it's being loaded out of a saved chunk. Beside the edge of the loaded land
 * those blocks are in a chunk not made or loaded yet, and reading one waited on the server thread for it: on the Steam
 * Deck, with Chunky pre-generating a new world, 61 s and the server down (crash 2026-11-13 22:06). Now a box that
 * reaches into land not loaded isn't in leaves: the panda finds them again once it moves. Skipped when Re:Animal isn't
 * there.
 */
@Pseudo
@Mixin(targets = "it.hurts.shatterbyte.reanimal.common.entity.red_panda.RedPandaEntity", remap = false)
abstract class RedPandaLeavesMixin {
	@Inject(method = "isInLeaves(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$loadedOnly(AABB box, CallbackInfoReturnable<Boolean> cir) {
		Level level = ((Entity) (Object) this).level();
		if (!Loaded.made(level, BlockPos.containing(box.minX, box.minY, box.minZ)) || !Loaded.made(level, BlockPos.containing(box.maxX, box.minY, box.minZ))
			|| !Loaded.made(level, BlockPos.containing(box.minX, box.minY, box.maxZ)) || !Loaded.made(level, BlockPos.containing(box.maxX, box.minY, box.maxZ))) {
			cir.setReturnValue(false);
		}
	}
}
