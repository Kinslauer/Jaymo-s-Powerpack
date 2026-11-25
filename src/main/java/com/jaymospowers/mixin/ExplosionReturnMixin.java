package com.jaymospowers.mixin;

import com.jaymospowers.core.TerrainReturn;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A big blast a player's power throws (a meteor's rings, a ki blast: the player is the blast's own source, not a block of TNT
 * or a creeper) records the ground it is about to break, so the ground comes back ({@link TerrainReturn}).
 */
@Mixin(ServerExplosion.class)
abstract class ExplosionReturnMixin {
	/** Blasts smaller than this (a thrown firework, a small charge) leave their holes. */
	private static final float BIG_BLAST = 3.0F;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void jaymospowers$record(List<BlockPos> blown, CallbackInfo ci) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		if (!TerrainReturn.enabled() || self.getBlockInteraction() == Explosion.BlockInteraction.KEEP || self.radius() < BIG_BLAST
			|| !(self.getDirectSourceEntity() instanceof ServerPlayer)) {
			return;
		}
		ServerLevel level = self.level();
		for (BlockPos pos : blown) {
			TerrainReturn.record(level, pos);
		}
	}
}
