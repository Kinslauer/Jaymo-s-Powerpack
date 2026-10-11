package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.McaCompat;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player's MCA rank in a village also answers to the faction there (see {@link McaCompat#rankSay}). */
@Mixin(Tasks.class)
abstract class McaTasksMixin {
	@Inject(method = "getRank", at = @At("RETURN"), cancellable = true)
	private static void jaymoskingdoms$factionRank(Village village, ServerPlayer player, CallbackInfoReturnable<Rank> cir) {
		if (player == null || village == null) {
			return;
		}
		Vec3i center = village.getCenter();
		int say = McaCompat.rankSay(player, new BlockPos(center.getX(), center.getY(), center.getZ()));
		Rank rank = cir.getReturnValue();
		if (say == McaCompat.OUTLAW) {
			cir.setReturnValue(Rank.OUTLAW);
		} else if (say > rank.ordinal()) {
			cir.setReturnValue(Rank.values()[say]);
		}
	}
}
