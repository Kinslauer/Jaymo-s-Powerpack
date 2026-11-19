package com.jaymospowers.client.mixin.xaero;

import com.jaymospowers.client.aether.AetherVeil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** The World Map's automatic cave mode only counts a roof in your own band, so the Aether overhead isn't a cave roof ({@link XaeroWorldMapBandMixin}). */
@Pseudo
@Mixin(targets = "xaero.map.misc.CaveStartCalculator", remap = false)
abstract class XaeroWorldMapCaveMixin {
	@WrapOperation(method = "getCaving", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxY()I"), require = 0)
	private int jaymospowers$roofTop(Level level, Operation<Integer> original) {
		return Math.min(original.call(level), AetherVeil.mapTop());
	}

	@WrapOperation(method = "getCaving", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"), require = 0)
	private int jaymospowers$roofFloor(Level level, Operation<Integer> original) {
		return Math.max(original.call(level), AetherVeil.mapBottom());
	}
}
