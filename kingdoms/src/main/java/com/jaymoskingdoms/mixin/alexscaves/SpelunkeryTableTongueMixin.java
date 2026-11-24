package com.jaymoskingdoms.mixin.alexscaves;

import com.jaymoskingdoms.IllagerTongue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alex's Caves' Spelunkery Table: a Cave Tablet translated (the screen's word game won, {@code pass}, with a tablet still
 * in its slot) teaches some of the illager tongue, whose glyphs the tablets are written in ({@link IllagerTongue#tablet}).
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.inventory.SpelunkeryTableMenu", remap = false)
abstract class SpelunkeryTableTongueMixin {
	@Inject(method = "onMessageFromScreen", at = @At("HEAD"), require = 0)
	private void jaymoskingdoms$tabletTeachesTongue(Player player, boolean pass, CallbackInfo ci) {
		if (pass && player instanceof ServerPlayer sp && !((AbstractContainerMenu) (Object) this).getSlot(0).getItem().isEmpty()) {
			IllagerTongue.tablet(sp);
		}
	}
}
