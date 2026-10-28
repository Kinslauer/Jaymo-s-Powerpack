package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.VillagerHeritage;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.44) A new villager's skin follows where their family is from: darker from the desert, paler from the snow ({@link SkinTones}).
 * A child's heritage is one of its parents' ({@link VillagerHeritage#inherit}).
 */
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
			// (0.7.236: kept to their blood's range, if they're of one yet)
			((Genetics)(Object)this).setGene(Genetics.MELANIN, SkinTones.bornSkin(com.jaymoskingdoms.VillagerHeritage.of(mob), SkinTones.melanin(mob, this.random), this.random));
			// (0.7.288) their height and width are spread across their blood's body once they're born to it (BodySizes)
			mob.addTag(com.jaymoskingdoms.BodySizes.FRESH);
		}
	}

	@Inject(method = "combine(Lnet/conczin/mca/entity/ai/Genetics;Lnet/conczin/mca/entity/ai/Genetics;J)V", at = @At("TAIL"))
	private void jaymoskingdoms$inheritBlood(Genetics mother, Genetics father, long seed, CallbackInfo ci) {
		this.jaymoskingdoms$inherit(mother, father);
	}

	@Inject(method = "combine(Lnet/conczin/mca/entity/ai/Genetics;Lnet/conczin/mca/entity/ai/Genetics;)V", at = @At("TAIL"))
	private void jaymoskingdoms$inheritBloodBorn(Genetics mother, Genetics father, CallbackInfo ci) {
		this.jaymoskingdoms$inherit(mother, father);
	}

	private void jaymoskingdoms$inherit(Genetics mother, Genetics father) {
		Mob child = this.entity.asEntity();
		if (child.level() instanceof ServerLevel) {
			VillagerHeritage.inherit(child, parent(mother), parent(father));
		}
	}

	private static @Nullable Entity parent(@Nullable Genetics genes) {
		VillagerLike<?> who = genes == null ? null : ((McaGeneticsAccessor)(Object)genes).jaymoskingdoms$entity();
		return who == null ? null : who.asEntity();
	}
}
