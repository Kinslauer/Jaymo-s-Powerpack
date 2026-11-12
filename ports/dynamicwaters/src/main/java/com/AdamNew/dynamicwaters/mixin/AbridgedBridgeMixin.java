package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.AbridgedCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "dev.worldgen.abridged.worldgen.structure.BridgeStructure")
public class AbridgedBridgeMixin {
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dynamicwaters$overWater(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (AbridgedCompat.active() && !AbridgedCompat.overWater(context)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getHeightmap", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void dynamicwaters$groundAsCut(BlockPos pos, Structure.GenerationContext context,
            CallbackInfoReturnable<Integer> cir) {
        if (AbridgedCompat.active()) {
            cir.setReturnValue(AbridgedCompat.groundAsCut(pos.getX(), pos.getZ(), cir.getReturnValue(), context));
        }
    }
}
