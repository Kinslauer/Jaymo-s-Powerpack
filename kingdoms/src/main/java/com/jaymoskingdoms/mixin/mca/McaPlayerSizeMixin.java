package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.McaCompat;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.87) Andy: "max height should only be accessible to verdant". A player's MCA look is kept to their heritage's
 * body as it's saved (0.7.288: Size and Width, {@code BodySizes}; {@link McaCompat#capSaved}). The editor hands this same tag on to
 * every client, so they see it held too.
 */
@Mixin(PlayerSaveData.class)
abstract class McaPlayerSizeMixin {
	@Inject(method = "setEntityData", at = @At("HEAD"))
	private void jaymoskingdoms$capSize(CompoundTag data, CallbackInfo ci) {
		PlayerSaveData save = (PlayerSaveData) (Object) this;
		if (save.getWorld() != null) {
			ServerPlayer player = save.getWorld().getServer().getPlayerList().getPlayer(save.getUUID());
			if (player != null) {
				McaCompat.capSaved(player, data);
			}
		}
	}
}
