package com.jaymospowers.client.mixin.xaero;

import com.jaymospowers.client.aether.AetherVeil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.21.2, Andy: "the minimap shows the aether instead of the overworld") In a One Dimension world the Aether's islands
 * are the top blocks of the Overworld level, and Xaero's Minimap draws each column from its top block (or from the top of
 * the world) down, so under the Aether it mapped the islands. Here its world is the band you're in ({@link AetherVeil#mapTop},
 * {@link AetherVeil#mapBottom}): a column is read from that band's top down to its floor, and the automatic cave mode only
 * counts a roof in your own band. Up in the Aether the map is the Aether's, with the void between its islands instead of
 * the Overworld far below. Ordinary worlds are untouched (the heights are then the game's).
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.write.MinimapWriter", remap = false)
abstract class XaeroMinimapBandMixin {
	@WrapOperation(method = "loadBlockColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxY()I"), require = 0)
	private int jaymospowers$bandTop(ClientLevel level, Operation<Integer> original) {
		return Math.min(original.call(level), AetherVeil.mapTop());
	}

	@WrapOperation(method = "loadBlockColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"), require = 0)
	private int jaymospowers$bandFloor(ClientLevel level, Operation<Integer> original) {
		return Math.max(original.call(level), AetherVeil.mapBottom());
	}

	@WrapOperation(method = "getCaving", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxY()I"), require = 0)
	private int jaymospowers$roofTop(Level level, Operation<Integer> original) {
		return Math.min(original.call(level), AetherVeil.mapTop());
	}

	@WrapOperation(method = "getCaving", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"), require = 0)
	private int jaymospowers$roofFloor(Level level, Operation<Integer> original) {
		return Math.max(original.call(level), AetherVeil.mapBottom());
	}
}
