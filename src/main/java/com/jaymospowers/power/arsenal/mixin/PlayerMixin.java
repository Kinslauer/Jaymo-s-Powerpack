package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalPower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Summoned weapons go home when their holder dies, before the inventory spills: they never become loot. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void jaymospowers$arsenalStrip(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			ArsenalPower.stripOnDeath(player);
		}
	}
}
