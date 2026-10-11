package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktidePower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dying with Tide Arms out: the conjured blades go and the real items come back before the inventory spills. */
@Mixin(Player.class)
abstract class DarktideDropMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void jaymospowers$darktideArms(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			DarktidePower.beforeDrops(player);
		}
	}
}
