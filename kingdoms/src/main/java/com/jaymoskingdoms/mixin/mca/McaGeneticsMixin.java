package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.SkinTones;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.44) A new villager's skin follows where their family is from: darker from the desert, paler from the snow ({@link SkinTones}). */
@Mixin(Genetics.class)
abstract class McaGeneticsMixin {
	@Shadow
	@Final
	private VillagerLike<?> entity;
	@Shadow
	private RandomSource random;

	@Inject(method = "randomize", at = @At("TAIL"))
	private void jaymoskingdoms$climateSkin(CallbackInfo ci) {
		Mob mob = this.entity.asEntity();
		if (mob.level() instanceof ServerLevel) {
			((Genetics)(Object)this).setGene(Genetics.MELANIN, SkinTones.melanin(mob, this.random));
		}
	}
}
