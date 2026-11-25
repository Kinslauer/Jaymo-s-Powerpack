package com.jaymospowers.mixin.alexscaves;

import com.jaymospowers.compat.AlexsCavesCompat;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alex's Caves' Magnetic Caves stone ({@link AlexsCavesCompat}): its galena becomes our ferrosite and its scarlet, azure
 * and neutral energized galena bands ichorite. Its rules are built once while it starts; if that is before our blocks
 * exist, the build waits for {@link AlexsCavesCompat#init}.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.level.surface.ACSurfaceRules", remap = false)
abstract class MagneticCaveStoneMixin {
	@Inject(method = "setup", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$waitForOurStone(CallbackInfo ci) {
		if (!AlexsCavesCompat.ready) {
			AlexsCavesCompat.deferred = true;
			ci.cancel();
		}
	}

	@Redirect(method = "createMagneticCavesRules", require = 0, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
		target = "Lcom/github/alexmodguy/alexscaves/server/block/ACBlockRegistry;GALENA:Ljava/util/function/Supplier;"))
	private static Supplier<Block> jaymospowers$ferrosite() {
		return AlexsCavesCompat.FERROSITE;
	}

	@Redirect(method = "createMagneticCavesRules", require = 0, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
		target = "Lcom/github/alexmodguy/alexscaves/server/block/ACBlockRegistry;ENERGIZED_GALENA_SCARLET:Ljava/util/function/Supplier;"))
	private static Supplier<Block> jaymospowers$ichoriteForScarlet() {
		return AlexsCavesCompat.ICHORITE;
	}

	@Redirect(method = "createMagneticCavesRules", require = 0, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
		target = "Lcom/github/alexmodguy/alexscaves/server/block/ACBlockRegistry;ENERGIZED_GALENA_AZURE:Ljava/util/function/Supplier;"))
	private static Supplier<Block> jaymospowers$ichoriteForAzure() {
		return AlexsCavesCompat.ICHORITE;
	}

	@Redirect(method = "createMagneticCavesRules", require = 0, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
		target = "Lcom/github/alexmodguy/alexscaves/server/block/ACBlockRegistry;ENERGIZED_GALENA_NEUTRAL:Ljava/util/function/Supplier;"))
	private static Supplier<Block> jaymospowers$ichoriteForNeutral() {
		return AlexsCavesCompat.ICHORITE;
	}
}
