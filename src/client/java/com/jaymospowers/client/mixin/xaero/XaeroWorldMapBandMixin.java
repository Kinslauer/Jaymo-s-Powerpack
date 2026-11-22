package com.jaymospowers.client.mixin.xaero;

import com.jaymospowers.client.aether.AetherVeil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Andy: "minimap still shows aether". With Xaero's World Map in the pack, the minimap draws from the World Map's tiles, and
 * those are written by the World Map's own writer, which {@link XaeroMinimapBandMixin} doesn't reach: it started each column
 * at the top block of the whole stacked level, so under the Aether it mapped the islands. Here, as there, a column is read
 * from the top of the band you're in down to its floor ({@link AetherVeil#mapTop}, {@link AetherVeil#mapBottom}).
 */
@Pseudo
@Mixin(targets = "xaero.map.MapWriter", remap = false)
abstract class XaeroWorldMapBandMixin {
	@WrapOperation(method = "writeChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxY()I"), require = 0)
	private int jaymospowers$bandTop(Level level, Operation<Integer> original) {
		return Math.min(original.call(level), AetherVeil.mapTop());
	}

	@WrapOperation(method = "writeChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"), require = 0)
	private int jaymospowers$bandFloor(Level level, Operation<Integer> original) {
		return Math.max(original.call(level), AetherVeil.mapBottom());
	}
}
